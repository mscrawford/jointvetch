package jointvetch;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.geo.*;
import sim.util.Bag;
import sim.field.grid.IntGrid2D;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.planargraph.DirectedEdgeStar;
import com.vividsolutions.jts.planargraph.Node;
import com.vividsolutions.jts.operation.distance.GeometryLocation;
import com.vividsolutions.jts.operation.distance.DistanceOp;

/**
 * A representation of a joint-vetch seed. Of importance is the logic pertaining to hydrochory, or seed
 * travel via water. In this area, the water movement is heavily influenced by the tides. Each seed, at the
 * end of this object's existence, will either implant, bank, or die.
 * @author Michael Crawford
 */
class MobileSeed implements Steppable
{
    private HoltsCreek hc;
    private Environment e;

    private MasonGeometry location;

    /* hydrochory parameters */
    private static final int TIDAL_PERIOD = 13; /* hours */
    private int maxFloatTime; // represents how long any given seed will survive in the river
    private int floatTimer = 0; // counter to maxFloatTime
    private LengthIndexedLine river_lil = null;
    private double startIndex = 0.0; // start position of current line
    private double endIndex = 0.0;
    private double currentIndex = 0.0;
    private PointMoveTo pmt = new PointMoveTo();
    private boolean deadEnd = false;

    /**
     * Represents a seed agent, with the ability to float down a river and implant in a new location.
     * @param dropLocation The coordinate pair that indicates where, on land, the seed dropped.
     * @param entryLocation The LocationGeometry that holds the point on the LineSegment that represents
     * where the seed will begin it's hydrochory journey down the river. It also holds the LineSegment itself.
     * This is useful for our initial step to begin hydrochory.
     */
    MobileSeed(Coordinate dropLocation, GeometryLocation entryLocation)
    {
        hc = HoltsCreek.instance();
        e = Environment.instance();

        location = new MasonGeometry(hc.factory.createPoint(dropLocation));
        location.isMovable = true;

        drop(entryLocation);
    }

    /**
     * Step will only be called if the seed is floating.
     */
    public void step(SimState state) {
        hydrochory();
    }

    /**
     * Drop the seed. This may result in one of two outcomes: either it implants immediately
     * where it is, or it enters the closest river. If the seed does enter the river,
     * this method transfers the geometry into the river and schedules the seed.
     * If it does not enter the river, the seed implants. When the seed implants,
     * it will either be making a new object (BankedSeed or Plant) or die (not reschedule itself).
     */
    private void drop(GeometryLocation entryLocation)
    {
        if (entryLocation != null)
        {
            Point entryPoint = hc.factory.createPoint(entryLocation.getCoordinate());
            LineString closestRiverString = (LineString) entryLocation.getGeometryComponent();

            int x = hc.redRaster_gf.toXCoord((Point) location.getGeometry());
            int y = hc.redRaster_gf.toYCoord((Point) location.getGeometry());
            int rasterColor = ((IntGrid2D) hc.redRaster_gf.getGrid()).get(x, y);

            if (hc.random.nextBoolean(Parameters.HYDROCHORY_PROB) || rasterColor == HoltsCreek.RIVER_RASTER_COLOR)
            {
                pickMaxFloatTime(); // changes a global variable

                if (Parameters.hydrochoryBool)
                {
                    // drop the seed into the closestRiverString segment, at the closestRiverPoint point.
                    pmt.setCoordinate(entryPoint.getCoordinate());
                    location.getGeometry().apply(pmt);
                    setupHydrochory(closestRiverString, (Point) location.getGeometry());

                    hc.schedule.scheduleOnce(hc.schedule.getTime() + hc.random.nextDouble() * TIDAL_PERIOD * 2, this);
                }   
                else
                {   
                    // aggregate implantation check
                    double b = 1-Math.pow( (1-Parameters.implantationRate), (double) maxFloatTime );
                    assert (b < 1 && b >= 0) : "Aggregate implantation check probability is nonsensical.";
                    if (hc.random.nextBoolean(b))
                    {
                        implant();
                    }
                }   
            }
            else implant();
        }
        else implant(); // We're implanting right where we initially dropped.
    }

    private void pickMaxFloatTime()
    {
        int n = hc.random.nextInt(hc.seedFloatTimes.length);
        maxFloatTime = hc.seedFloatTimes[n];
        while (n != 0 && (maxFloatTime - hc.seedFloatTimes[n - 1]) == 0)
        {
            n--;
        }
        if (n != 0 && (maxFloatTime - hc.seedFloatTimes[n - 1]) > 6)
        {
            /* correct nightly accumulation of dead seeds */
            maxFloatTime = hc.random.nextInt(hc.seedFloatTimes[n] - hc.seedFloatTimes[n - 1]) + hc.seedFloatTimes[n - 1];
        }
    }

    /**
     * An exciting description of hydrochory!
     */
    private void hydrochory()
    {
        if (hc.random.nextBoolean(Parameters.implantationRate))
        {
            DistanceOp riverToWaterbody = new DistanceOp( hc.tidal_mp, location.getGeometry() );
            Coordinate[] riverToWaterbodyCoords = riverToWaterbody.nearestPoints();
            Coordinate waterbodyCoord = (Coordinate) riverToWaterbodyCoords[0].clone();
            Coordinate riverCoord = (Coordinate) riverToWaterbodyCoords[1].clone();

            /* Continue the line from the seed's current location on the river to the nearest marsh border.
                Project this line into the marsh and drop the seed within 4m of the marsh edge. */
            double slope = (waterbodyCoord.y - riverCoord.y) / (waterbodyCoord.x - riverCoord.x);
            double angle;
            if ( Double.isNaN(slope) ) {
                assert (hc.tidal_mp.intersects(location.getGeometry()));
                angle = hc.random.nextDouble() * 2 * Math.PI;
            } else {
                angle = Math.atan(slope);
            }

            double dist = hc.random.nextDouble() * Parameters.IMPLANTATION_MAXIMUM_DISTANCE; // uniform dist from 0 - 4m
            double xOffset = dist * Math.cos(angle);
            double yOffset = dist * Math.sin(angle);
            Coordinate implantationCoordinate;
            if (riverCoord.x <= waterbodyCoord.x) {
                implantationCoordinate = new Coordinate(waterbodyCoord.x + xOffset, waterbodyCoord.y + yOffset);
            } else {
                implantationCoordinate = new Coordinate(waterbodyCoord.x - xOffset, waterbodyCoord.y - yOffset); 
            }

            pmt.setCoordinate(implantationCoordinate);
            location.getGeometry().apply(pmt);
            implant();
        }
        else if (floatTimer <= maxFloatTime) // keep on hydrochorying
        {
            double distanceThisHour = tidalRateFunction( hc.schedule.getTime() );
            double distanceTraveledThisHour = 0; // an absolute value
            final double SCALE = 1000000.0;
            double distanceToTravel = (Math.round( Math.abs(distanceThisHour)*SCALE )/SCALE)-1; // why -1? refresh this

            /* because each edge is a different length and the seed must travel a predetermined distance each hour,
             *   each seed will continue moving up/down a given edge until it reaches a terminus. Then it will pick
             *   a new edge (one that travels in the same direction) and transition onto it. */
            while (distanceTraveledThisHour < distanceToTravel && !deadEnd)
            {
                int direction = (distanceThisHour >= 0) ? 1 : -1; // upstream or downstream
                if (!arrivedAtJunction(direction))
                {
                    double myStartIndex = currentIndex;

                    // going from end -> start (upstream)
                    if (distanceThisHour < 0)
                    {
                        currentIndex = currentIndex + (distanceThisHour + distanceTraveledThisHour); // (-) + (+)
                        if (currentIndex < startIndex) {
                            currentIndex = startIndex;
                        }
                    }

                    // going from start -> end (downstream)
                    else if (distanceThisHour > 0)
                    {
                        currentIndex = currentIndex + (distanceThisHour - distanceTraveledThisHour);
                        if (currentIndex > endIndex) {
                            currentIndex = endIndex;
                        }
                    }

                    distanceTraveledThisHour = distanceTraveledThisHour + Math.abs(currentIndex-myStartIndex);
                }
                else
                {
                    findNewPath(direction);
                }
                Coordinate currentPos = river_lil.extractPoint(currentIndex);
                pmt.setCoordinate(currentPos);
                location.getGeometry().apply(pmt);
            }

            deadEnd = false;
            floatTimer++;
            hc.schedule.scheduleOnce(hc.schedule.getTime() + 1, this);
        }
        // else the seed is now dead.
    }

    /**
     * The seed has successfully implanted in the soil.
     */
    private void implant()
    {
        int x = hc.redRaster_gf.toXCoord((Point) location.getGeometry());
        int y = hc.redRaster_gf.toYCoord((Point) location.getGeometry());

        if (x < hc.gridWidth && x >= 0 && y < hc.gridHeight && y >= 0) // very rare OOB exception
        {
            if (hc.random.nextBoolean(Parameters.WINTER_SURVIVAL_RATE))
            {
                Plant p = new Plant(location, false);
            }

            // implicit seed death          
        }
    }

    /**
     * If the direction is downstream (negative) and the seed is at the end of an edge, it should continue to another edge.
     * Likewise a seed could go upstream and need to continue on when it gets to the "beginning" of an edge. Put another way,
     * edges inherently go downstream.
     * @return true if at junction, false if not.
     */
    private boolean arrivedAtJunction(double direction)
    {
        return (direction > 0 && currentIndex == endIndex) || (direction < 0 && currentIndex == startIndex);
    }

    private void findNewPath(int direction)
    {
        // find the "node" we're on
        Node currentJunction = hc.riverNetwork.findNode( location.getGeometry().getCoordinate() );

        if (currentJunction != null)
        {
            DirectedEdgeStar directedEdgeStar = currentJunction.getOutEdges(); // all of its directed edges
            Bag edges = new Bag(directedEdgeStar.getEdges().toArray());
            Bag culledLineStrings = new Bag(); // paths the seed could take

            /* the seed can only float upstream if the direction is negative and downstream if positive,
                    so we must only use a subset when setting up hydrochory. (Go down the main stream rather than 
                    another tributary.) */
            for (int i = 0; i < edges.size(); i++)
            {
                GeomPlanarGraphDirectedEdge directedEdge = (GeomPlanarGraphDirectedEdge) edges.get(i);
                GeomPlanarGraphEdge edge = (GeomPlanarGraphEdge) directedEdge.getEdge();

                LineString newRoute = edge.getLine();
                Point startPoint = newRoute.getStartPoint();
                Point endPoint = newRoute.getEndPoint();

                if (direction > 0 && startPoint.equals(location.getGeometry())) // paths going downstream
                {
                    culledLineStrings.add(newRoute);
                }
                else if (direction < 0 && endPoint.equals(location.getGeometry())) // tributaries going upstream
                {
                    culledLineStrings.add(newRoute);
                }
            }

            if (culledLineStrings.size() > 0)
            {
                // pick an edge at random and start moving along it
                int r = hc.random.nextInt(culledLineStrings.size());
                LineString newRoute = (LineString) culledLineStrings.get(r);
                Point startPoint = newRoute.getStartPoint();
                Point endPoint = newRoute.getEndPoint();

                if (startPoint.equals(location.getGeometry()))
                {
                    setupHydrochory(newRoute, startPoint);
                }
                else if (endPoint.equals(location.getGeometry()))
                {
                    setupHydrochory(newRoute, endPoint);
                }
                else throw new AssertionError();
            }
            else
            {
                deadEnd = true;
            }
        }
        else throw new AssertionError();
    }

    /**
    * @param riverString Initially, the portion of the GeometryLocation that represents
    * the LineSegment (i.e. portion of the river) that the riverPoint is on. Afterwards,
    * it is the current river portion that the seed is floating down.
    * @param riverPoint The point on the river segment that represents the seed. This is also
    * initially passed inside a GeometryLocation.
    */
    private void setupHydrochory(LineString riverString, Point riverPoint)
    {
        river_lil = new LengthIndexedLine(riverString);
        startIndex = river_lil.getStartIndex();
        endIndex = river_lil.getEndIndex();
        currentIndex = river_lil.indexOf(riverPoint.getCoordinate());
    }

    /**
     * This function returns a rate (in meters per hour). Multiplied by one hour timesteps,
     * we can see how far the seed went in one hour. See methods for details.
     * @param time time(t) the seed has been moving
     * @return The rate at time(t) the seed is moving.
     */
    private double tidalRateFunction(double time) {
        return 769.5 * Math.sin( time*Math.PI/TIDAL_PERIOD ) + 13.5;
    }

}
