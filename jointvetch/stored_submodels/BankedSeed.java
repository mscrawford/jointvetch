package jointvetch;

import java.util.*;
import sim.engine.*;
import sim.util.geo.MasonGeometry;
import sim.field.grid.IntGrid2D;
import sim.field.grid.ObjectGrid2D;
import sim.field.geo.GeomGridField;
import sim.field.geo.GeomGridField.GridDataType;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.distance.GeometryLocation;

/**
 * A seed inside the seed bank.
 * @author Michael Crawford
 */

public class BankedSeed implements Steppable
{
	private HoltsCreek hc;
	private Environment e;

public static int bankedSeedsAlive = 0;

	private MasonGeometry location;
	private int x, y, rasterColor;
	private Plot myPlot;

	private static final Environment.Date bankedSeedExitDate =
		new Environment.Date(Environment.Month.APR, 10);

	public BankedSeed(MasonGeometry location)
	{
		hc = HoltsCreek.instance();
		e = Environment.instance();

		this.location = location;
		x = hc.colorRaster_GridField.toXCoord((Point) location.getGeometry());
		y = hc.colorRaster_GridField.toYCoord((Point) location.getGeometry());

		bankedSeedsAlive++;
		myPlot = e.getPlot(x, y);

		hc.schedule.scheduleOnce(e.getClockTimeForNext(bankedSeedExitDate), this);
	}

	public void step(SimState state)
	{
		hc = HoltsCreek.instance();
		e = Environment.instance();

		double prob = hc.random.nextDouble();
		double baselineGermProb = myPlot.getGerminationProb();
		double realGermProb = (1-Parameters.seedBankRate) * baselineGermProb;

		if (hc.random.nextBoolean(Parameters.WINTER_SURVIVAL_RATE))
		{			
			if (prob < realGermProb)
			{
				Plant p = new Plant(location, false);
				bankedSeedsAlive--;
			} 
			else if (prob < realGermProb + Parameters.seedBankRate)
			{
				hc.schedule.scheduleOnce(e.getClockTimeForNext(bankedSeedExitDate), this);
			}
			else
			{
				bankedSeedsAlive--;
			}
		}
		else
		{
			bankedSeedsAlive--;
		}
	}
}
