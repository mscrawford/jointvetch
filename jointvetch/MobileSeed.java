package jointvetch;

import java.util.*;
import java.io.*;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.geo.GeomPlanarGraphDirectedEdge;
import sim.util.geo.GeomPlanarGraphEdge;
import sim.util.geo.MasonGeometry;
import sim.util.geo.PointMoveTo;
import sim.util.Bag;
import sim.field.grid.IntGrid2D;
import sim.field.grid.ObjectGrid2D;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.planargraph.DirectedEdgeStar;
import com.vividsolutions.jts.planargraph.Node;
import com.vividsolutions.jts.operation.distance.GeometryLocation;
import com.vividsolutions.jts.operation.distance.DistanceOp;

/**
 * A representation of a joint-vetch seed. Of importance is the logic pertaining to hydrochory, or seed
 * travel via water. In this area, the water movement is heavility influenced by the tides. Each seed, at the
 * end of this object's exhistence, will either implant, bank, or die.
 * @author Michael Crawford
 */
public class MobileSeed implements Steppable
{
	
	public static int implants = 0;
	public static int vectorFailedImplants = 0;
	public static int rasterFailedImplants = 0;
	public static int waterDrops = 0;








	private HoltsCreek hc;
	private Environment e;

	private MasonGeometry location;
	private int x, y, rasterColor;
	private Plot myPlot;

	/* hydrochory parameters */
	private static final int TIDAL_PERIOD = 13; /* hours */
	private int maxFloatTime; // represents	how long any given seed will survive in the river
	private int floatTimer = 0; // counter to maxFloatTime
	private double distanceThisHour; /* determined by Dr. Griffith's flow rate data, while taking into
	account a sinusoidal tidal motion. */
	private GeometryLocation entryLocation;  // the river point closest to the maternal plant
	private LengthIndexedLine river_LengthIndexedLine = null;
	private double startIndex = 0.0; // start position of current line
	private double endIndex = 0.0;
	private double currentIndex = 0.0;
	private PointMoveTo pointMoveTo = new PointMoveTo();
	private boolean deadEnd = false;

	/**
	 * Represents a seed agent, with the ability to float down a river and implant in a new location.
	 * @param dropLocation The coordinate pair that indicates where, on land, the seed dropped.
	 * @param entryLocation The LocationGeometry that holds the point on the LineSegment that represents
	 * where the seed will begin it's hydrochory journey down the river. It also holds the LineSegment itself.
	 * This is useful for our initial step to begin hydrochory.
	 * @author Michael Crawford
	 */
	public MobileSeed(Coordinate dropLocation, GeometryLocation entryLocation)
	{
		hc = HoltsCreek.instance();
		e = Environment.instance();

		location = new MasonGeometry(hc.factory.createPoint(dropLocation));
		location.isMovable = true;
		this.entryLocation = entryLocation;

		drop();
	}

	/**
	 * Step will only be called if the seed is floating.
	 * @author Michael Crawford
	 */
	public void step(SimState state)
	{
		hc = HoltsCreek.instance();
		e = Environment.instance();
		hydrochory();
	}

	/**
	 * Drop the seed. This may result in one of two outcomes: either it implants immediately
	 * where it is, or it enters the closest river. If the seed does enter the river,
	 * this method transfers the geometry into the river and schedules the seed.
	 * If it does not enter the river, the seed implants. When the seed implants,
	 * it will either be making a new object (BankedSeed or Plant) or die (not reschedule itself).
	 * @author Michael Crawford
	 */
	private void drop()
	{
		if (entryLocation != null)
		{
			Point entryPoint = hc.factory.createPoint(entryLocation.getCoordinate());
			LineString closestRiverString = (LineString) entryLocation.getGeometryComponent();

			
// DID YOU DROP INTO THE WATER IMMEDIATELY?

			// if (hc.random.nextBoolean(Parameters.HYDROCHORY_PROB))
			int x = hc.colorRaster_GridField.toXCoord((Point) location.getGeometry());
			int y = hc.colorRaster_GridField.toYCoord((Point) location.getGeometry());
			int rasterColor = ((IntGrid2D) hc.colorRaster_GridField.getGrid()).get(x, y);
//			System.out.println(rasterColor);
			if (rasterColor < 0)
			{
				waterDrops++;
			}
			if (hc.random.nextBoolean(Parameters.HYDROCHORY_PROB) || rasterColor < 0)

/////////////////////////////////////////////

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
					maxFloatTime = hc.random.nextInt(hc.seedFloatTimes[n]
						 - hc.seedFloatTimes[n - 1])
							+ hc.seedFloatTimes[n - 1];
				}

				if (Parameters.hydrochoryBool == true)
				{
					// drop the seed into the closestRiverString segment, at the closestRiverPoint point.
					pointMoveTo.setCoordinate(entryPoint.getCoordinate());
					location.getGeometry().apply(pointMoveTo);
					setupHydrochory(closestRiverString, entryPoint);

					hc.schedule.scheduleOnce(hc.schedule.getTime() + hc.random.nextDouble() * TIDAL_PERIOD * 2, this);
				}	
				else
				{	
					// aggregate implantation check
					double b = 1-Math.pow((1-Parameters.implantationRate), (double) maxFloatTime);
					if (hc.random.nextBoolean(b))
					{
						implant();
					}
				}	
			}
			else
			{
				implant();
			}
		}
		else
		{
			implant(); // We're implanting right where we initially dropped.
		}
	}

	/**
	 * An exciting description of hydrochory!
	 * @author Michael Crawford
	 */
	private void hydrochory()
	{
		if (hc.random.nextBoolean(Parameters.implantationRate))
		{
			DistanceOp riverToBoundary = new DistanceOp( hc.tidal_Boundary, location.getGeometry() );
	
			// Coordinate[] riverToBoundaryCoords = riverToBoundary.nearestPoints();
			// Coordinate boundaryCoord = riverToBoundaryCoords[0];
			// Coordinate riverCoord = riverToBoundaryCoords[1];

			// double slope = (riverCoord.y - boundaryCoord.y) / (riverCoord.x - boundaryCoord.x); // slope
			// double angle = Math.atan(slope); // inverse-tangent for the angle
			
			// double dist = 0; // sample the empirical distribution of distances to stream edge for this seed's distance
			// double xOffset = dist * Math.cos(angle);
			// double yOffset = dist * Math.sin(angle);

			// Coordinate implantationCoord = new Coordinate(boundaryCoord.x + xOffset, boundaryCoord.y + yOffset); 

			// assert( Math.sqrt( 
			// 	(Math.pow(implantationCoord.x - boundaryCoord.x, 2)) + 
			// 	(Math.pow(implantationCoord.y - boundaryCoord.y, 2))) == dist);

			Coordinate implantationCoord = riverToBoundary.nearestPoints()[0];
			pointMoveTo.setCoordinate(implantationCoord);
			location.getGeometry().apply(pointMoveTo);

// I THINK THEY MIGHT BE INSIDE BOTH, BECAUSE THERE ARE PLACES WHERE THE LINES AND THE POLYGONS INTERSECT!

// ARE WE FALLING INTO THE WATER / GETTING TOSSED OUT?			
if (hc.tidal_Geometries.disjoint(location.getGeometry()))
{
	vectorFailedImplants++;
}
// implants++;

int x = hc.colorRaster_GridField.toXCoord((Point) location.getGeometry());
int y = hc.colorRaster_GridField.toYCoord((Point) location.getGeometry());
int rasterColor = ((IntGrid2D) hc.colorRaster_GridField.getGrid()).get(x, y);
if (rasterColor < 0)
{
	rasterFailedImplants++;
}	

implants++;

			implant();
		}
		else if (floatTimer <= maxFloatTime && deadEnd == false) // keep on hydrochorying
		{

			distanceThisHour = tidalRateFunction( hc.schedule.getTime() );
			double distanceTraveled = 0;
			double numForRounding = 1000000.0;
			double distanceToTravel = (Math.round(Math.abs(distanceThisHour)*numForRounding)/numForRounding)-1;

			while (distanceTraveled < distanceToTravel && deadEnd == false)
			{
				if (!arrivedAtJunction())
				{
					double myStartIndex = currentIndex;

					// going from end -> start
					if (distanceThisHour < 0)
					{
						currentIndex = currentIndex + (distanceThisHour + distanceTraveled); // (-) + (+)
						if (currentIndex < startIndex) {
							currentIndex = startIndex;
						}
					}

					// going from start -> end
					else if (distanceThisHour > 0)
					{
						currentIndex = currentIndex + (distanceThisHour - distanceTraveled);
						if (currentIndex > endIndex) {
							currentIndex = endIndex;
						}
					}

					distanceTraveled = distanceTraveled + Math.abs(currentIndex-myStartIndex);
				}
				else
				{
					findNewPath();
				}
				Coordinate currentPos = river_LengthIndexedLine.extractPoint(currentIndex);
				pointMoveTo.setCoordinate(currentPos);
				location.getGeometry().apply(pointMoveTo);
			}
			floatTimer++;
			hc.schedule.scheduleOnce(hc.schedule.getTime() + 1, this);
		}
	}

	/**
	 * The seed has successfully implanted in the soil.
	 * @author Michael Crawford
	 */
	private void implant()
	{
		x = hc.colorRaster_GridField.toXCoord((Point) location.getGeometry());
		y = hc.colorRaster_GridField.toYCoord((Point) location.getGeometry());

		myPlot = e.getPlot(x, y);
		if (hc.random.nextBoolean(Parameters.WINTER_SURVIVAL_RATE))
		{
			double prob = hc.random.nextDouble();
			double germProb = myPlot.getGerminationProb();

			if (prob < germProb)
			{
				Plant p = new Plant(location, false);
			}
			else if (prob < germProb + Parameters.seedBankRate)
			{
				BankedSeed bs = new BankedSeed(location);
			}
			// some seeds are dying implicitly here.
		}
	}

	/**
	 * @return true if at junction, false if not.
	 * @author Michael Crawford
	 */
	private boolean arrivedAtJunction()
	{
		if ( (distanceThisHour > 0 && currentIndex == endIndex)
			|| (distanceThisHour < 0 && currentIndex == startIndex) )
		{
			return true;
		}
		return false;
	}

	/**
	 * @author Michael Crawford
	 */
	private void findNewPath()
	{
		// find all the adjacent junctions
		Node currentJunction = hc.river_Network.findNode( location.getGeometry().getCoordinate() );

		if (currentJunction != null)
		{
			DirectedEdgeStar directedEdgeStar = currentJunction.getOutEdges();
			Object[] edges = directedEdgeStar.getEdges().toArray();

			if (edges.length > 1)
			{
				int i = hc.random.nextInt(edges.length);
				GeomPlanarGraphDirectedEdge directedEdge = (GeomPlanarGraphDirectedEdge) edges[i];
				GeomPlanarGraphEdge edge = (GeomPlanarGraphEdge) directedEdge.getEdge();

				// and start moving along it
				LineString newRoute = edge.getLine();
				Point startPoint = newRoute.getStartPoint();
				Point endPoint = newRoute.getEndPoint();

				if (startPoint.equals(location.geometry))
				{
					setupHydrochory(newRoute, startPoint);
				}
				else if (endPoint.equals(location.geometry))
				{
					setupHydrochory(newRoute, endPoint);
				}
				// else if ()
				// {
				// 	TODO: This does not account for seeds entering in THE MIDDLE of lineStrings. (A T intersection)
				// }
				else System.out.println("Error 1 in MobileSeed:findNewPath.");
			}
			else if (edges.length <= 1)
			{
				deadEnd = true;
			}
			else System.out.println("Error 2 in MobileSeed:findNewPath.");
		}
		else System.out.println("Error 3 in MobileSeed:findNewPath.");
	}

	/**
	* @param riverString Initially, the portion of the GeometryLocation that represents
	* the LineSegment (i.e. portion of the river) that the riverPoint is on. Afterwards,
	* it is the current river portion that the seed is floating down.
	* @param riverPoint The point on the river segment that represents the seed. This is also
	* initially passed inside a GeometryLocation.
	* @author Michael Crawford
	*/
	private void setupHydrochory(LineString riverString, Point riverPoint)
	{
		river_LengthIndexedLine = new LengthIndexedLine(riverString);
		startIndex = river_LengthIndexedLine.getStartIndex();
		endIndex = river_LengthIndexedLine.getEndIndex();
		currentIndex = river_LengthIndexedLine.indexOf(riverPoint.getCoordinate());
	}

	/**
	 * This function returns a rate (in meters per hour). Multiplied by one hour timesteps,
	 * we can see how far the seed went in one hour. See methods for details.
	 * @param time time(t) the seed has been moving
	 * @return The rate at time(t) the seed is moving.
	 * @author Michael Crawford
	 */
	private double tidalRateFunction(double time) {
		return 769.5 * Math.sin( time*Math.PI/TIDAL_PERIOD ) + 13.5;
	}

}