package jointvetch;

import java.util.*;
import sim.util.*;
import sim.engine.*;
import sim.util.distribution.Gamma;
import sim.util.geo.MasonGeometry;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.distance.DistanceOp;
import com.vividsolutions.jts.operation.distance.GeometryLocation;
import sim.field.grid.IntGrid2D;
import sim.field.grid.ObjectGrid2D;
import sim.field.geo.GeomGridField;
import sim.field.geo.GeomGridField.GridDataType;

/**
 * A Plant object represents any joint-vetch plant after germination. This
 * includes not only fully-grown, seed-bearing adults, but also seedlings,
 * germinated seeds, and even un-germinated seeds waiting out the winter
 * months before germinating in spring. (Un-germinated seeds that are
 * waiting in a seed bank to emerge in a future year, however, are
 * <i>not</i> represented by this class.)
 * @author Michael Crawford
 */
class Plant implements Steppable
{
	private HoltsCreek hc;
	private Environment e;

	private MasonGeometry location;
	private int x, y;
	private Plot myPlot;

	enum LifeStage { DEAD, IMPLANTED, ADULT };
	private LifeStage stage;

	private static final double MAX_DISTANCE_TO_STREAM_EDGE = 4.0; /* meters */
	
	/* seed drop data */
	private static final double SEED_DROP_DIST_MEAN = 0.3;
	private static final double SEED_DROP_DIST_SD = 0.25;
	private static Gamma gammaDistro;

	/* dates */
	private static final Environment.Date seedlingSurvivalDate =
		new Environment.Date(Environment.Month.SEP, 28);

	private static final Environment.Date reproductionDate =
		new Environment.Date(Environment.Month.OCT, 1);

	static
	{
		double seedDropDistVar = SEED_DROP_DIST_SD * SEED_DROP_DIST_SD;
		double seedDropDistAlpha = SEED_DROP_DIST_MEAN * SEED_DROP_DIST_MEAN / seedDropDistVar;
		double seedDropDistLambda = SEED_DROP_DIST_MEAN / seedDropDistVar;
		gammaDistro = new Gamma(seedDropDistAlpha, seedDropDistLambda, HoltsCreek.instance().random);
	}

	/**
	 * Each Adult object <i>has</i> a MasonGeometry that represents its
	 * location. This is given to it when it's created from the
	 * jointvetch_vectorField. After the initial construction of the first
	 * step's adults, new adults are created by seeds, according to the
	 * vital rates.
	 */
	Plant(MasonGeometry seed_location, boolean isFirstGen)
	{
		hc = HoltsCreek.instance();
		e = Environment.instance();

		location = seed_location;
		x = hc.redRaster_gf.toXCoord((Point) location.getGeometry());
		y = hc.redRaster_gf.toYCoord((Point) location.getGeometry());
		
		myPlot = e.getPlot(x, y);
		if (isFirstGen)
		{
			myPlot.registerNewPlant();

			stage = LifeStage.ADULT;
			hc.schedule.scheduleOnce(e.getClockTimeForNext(reproductionDate), this);
		}
		else
		{
			stage = LifeStage.IMPLANTED;
			hc.schedule.scheduleOnce(e.getClockTimeForNext(seedlingSurvivalDate), this);
		}
	}

	public void step(SimState state)
	{
		hc = HoltsCreek.instance();

		if (stage == LifeStage.IMPLANTED)
		{
			if (hc.random.nextBoolean(myPlot.getSurvivalProb()))
			{
				stage = LifeStage.ADULT;
				myPlot.registerNewPlant();
				hc.schedule.scheduleOnce(e.getClockTimeForNext(reproductionDate), this);
			}
			else
			{
				stage = LifeStage.DEAD;
			}
		}
		else if (stage == LifeStage.ADULT)
		{
			if (hc.random.nextBoolean(myPlot.getCarryingCapacityAdjustment()))
			{
				reproduce();
				hc.reproducingPlants_vf.addGeometry(location); // will be cleared on Dec 31.
			}
			else
			{
				myPlot.deregisterPlant();
				stage = LifeStage.DEAD;
			}
		}
		else throw new AssertionError();
	}

	/**
	 * The seeds created in this function are going to float around for, probably, at most 5 days. By then
	 * they'll all either be dead or germinated plants, having entered the constructor and then rescheduled themselves.
	 */
	private void reproduce()
	{
		GeometryLocation entryLocation = null;
		DistanceOp toRiver = new DistanceOp(hc.river_mls, location.getGeometry());

		if (toRiver.distance() < MAX_DISTANCE_TO_STREAM_EDGE)
		{
			entryLocation = toRiver.nearestLocations()[0];
 		}
		else
		{
			DistanceOp toEdge = new DistanceOp( hc.tidalBoundary_g, location.getGeometry() );
			if (toEdge.distance() < MAX_DISTANCE_TO_STREAM_EDGE)
			{
				DistanceOp edgeToRiver = new DistanceOp( hc.river_mls,
					hc.factory.createPoint( toEdge.nearestPoints()[0] ));
				entryLocation = edgeToRiver.nearestLocations()[0];
			}
		}
		
		for (int i = 0, s = myPlot.getFecundity(); i < s; i++)
		{
			double seedAngle = hc.random.nextDouble() * 2 * Math.PI;
			double seedDist = gammaDistro.nextDouble();
			while (seedDist > 1.5) seedDist = gammaDistro.nextDouble();
			double xOffset = seedDist * Math.cos(seedAngle);
			double yOffset = seedDist * Math.sin(seedAngle);
			Coordinate seedLoc = (Coordinate) location.getGeometry().getCoordinate().clone();
			seedLoc.x += xOffset;
			seedLoc.y += yOffset;
			MobileSeed seed = new MobileSeed(seedLoc, entryLocation); // Each seed decides whether or not it will disperse.
		}
	}
}
