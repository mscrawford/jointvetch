package jointvetch;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.*;
import sim.util.geo.MasonGeometry;
import sim.util.distribution.Normal;
import sim.field.geo.GeomVectorField;
import sim.field.geo.GeomGridField.GridDataType;
import sim.field.geo.GeomGridField;
import sim.field.grid.IntGrid2D;
import sim.field.grid.ObjectGrid2D;
import com.vividsolutions.jts.geom.*;
import java.util.*;
import java.io.*;

/**
 * A representation of the plants' environment, specifically embodying
 * those features which impact a plant's lifecycle. This is a singleton
 * class, designed to be owned by a governing simulation (subclass of
 * SimState.)
 * @author Stephen
 */
public class Environment implements Steppable
{
	private static Environment theInstance;
	private HoltsCreek hc;

	/* general parameters */
	private int yearCount = 0;

	/* timekeeping */
	public enum Month { JAN,FEB,MAR,APR,MAY,JUN,JUL,AUG,SEP,OCT,NOV,DEC };
	private static final int DAYS[] = { 31,28,31,30,31,30,31,31,30,31,30,31 };
	private static final int CUM_DAYS[] = { 31,59,90,120,151,181,212,243,273,304,334,365 };
	private static final int HOURS_PER_DAY = 24;
	private static final int DAYS_PER_YEAR = 365;
	
	private Date today;
	private Date lastDate = new Date(Month.JAN, 1);
	private static final Date newYearDate = new Date(Month.DEC, 31);

	/* environmental stochasticity */
	private double thisYearsStochasticity;
	public static ArrayList<Double> environmentalHistory;

	/* records */
	private static ArrayList<Integer> populationHistory;
	private static boolean PRINT_POPULATION_HISTORY = true;
	private static boolean RUN_CLUSTERING_ANALYSIS = true;

	public static synchronized Environment instance()
	{
		if (theInstance == null)
		{
			theInstance = new Environment();
		}
		return theInstance;
	}

	private Environment()
	{
		hc = HoltsCreek.instance();
	
		thisYearsStochasticity = generateEnvironStochasticity();

		environmentalHistory = new ArrayList<Double>();
		environmentalHistory.add(thisYearsStochasticity);

		populationHistory = new ArrayList<Integer>();
		populationHistory.add(hc.reproducingPlants_vectorField.getGeometries().size());
	}

	/* ------------------------
	 * Called at the end of each year.
	 * ------------------------ */
	public void step(SimState state)
	{
		hc = HoltsCreek.instance();
		today = getDateForTime(hc.schedule.getTime());
		notifyEndOfYear();
		populationHistory.add(hc.reproducingPlants_vectorField.getGeometries().size());
		yearCount++;
		if (today.equals(newYearDate))
		{
			// HoltsCreekWithUI.takeSnapshot();
			thisYearsStochasticity = generateEnvironStochasticity();
			int n = hc.reproducingPlants_vectorField.getGeometries().size();
			if (n == 0 || n > Parameters.MAX_POPULATION_COUNT || yearCount >= Parameters.MAX_YEAR_COUNT)
			{
				printFinalStatistics();

				System.out.print("Failed implants (vector method): " + MobileSeed.vectorFailedImplants + " out of: " + MobileSeed.implants);
				System.out.println("; ratio (vector): " + MobileSeed.vectorFailedImplants/(double) MobileSeed.implants);
				System.out.print("Failed implants (raster method): " + MobileSeed.rasterFailedImplants + "; out of: " + MobileSeed.implants);
				System.out.println("; ratio (raster): " + MobileSeed.rasterFailedImplants/(double) MobileSeed.implants);
				System.out.println("Water drops: " + MobileSeed.waterDrops);




	    		try {
          			File file = new File("/Users/Theodore/Documents/Google_Drive/SJV_EcologicalModelling_Paper/Analysis/Seed_Embankment_Algorithm/","PLANT_COORDS.txt");
          			BufferedWriter output = new BufferedWriter(new FileWriter(file));
          			for (int i = 0; i < hc.reproducingPlants_vectorField.getGeometries().size(); i++)
					{
						MasonGeometry mg = (MasonGeometry) hc.reproducingPlants_vectorField.getGeometries().get(i);
						output.write(mg.getGeometry().getCoordinate().x + ", " + mg.getGeometry().getCoordinate().y + "\n");
					}
          			output.close();
        		} catch ( IOException e ) {
           			e.printStackTrace();
        		}





				if (PRINT_POPULATION_HISTORY) System.out.println("Population History: " + populationHistory);
				if (RUN_CLUSTERING_ANALYSIS) runClusteringAnalysis();
				System.exit(0);
			}
			environmentalHistory.add(thisYearsStochasticity);
			hc.plants_vectorField.clear();
			hc.reproducingPlants_vectorField.clear();
			hc.schedule.scheduleOnce(getClockTimeForNextNewYearDate(), this);
		}
		else
		{
			System.out.println("ERROR in Environment. Clock time: " + hc.schedule.getTime());
			System.exit(1);
		}
	}

/* ------------------------------
 * Environmental stochasticity
 * ------------------------------ */
	private double generateEnvironStochasticity()
	{
		double u = hc.random.nextDouble();
		double stochMax = Parameters.stochMax;
		double stochMin = 1/stochMax;
		double adjustmentFactor = Parameters.adjustmentFactor;

		return (Math.exp((u*Math.log(stochMax)) + ((1-u)*Math.log(stochMin)))) * adjustmentFactor;
	}

	public double getEnvironmentalStochasticity()
	{
		return thisYearsStochasticity;
	}

/* ------------------------------
 * Dealing with plots
 * ------------------------------ */
	public Plot getPlot(int x, int y)
	{
		hc = HoltsCreek.instance();
		Plot plot = (Plot) ((ObjectGrid2D) hc.plotGrid_GridField.getGrid()).get(x, y);
		if (plot == null)
		{
			plot = new Plot(x, y);
			((ObjectGrid2D) hc.plotGrid_GridField.getGrid()).set(x, y, plot);
		}
		return plot;
	}

	private void notifyEndOfYear()
	{
		Bag plots = (Bag) ((ObjectGrid2D) hc.plotGrid_GridField.getGrid()).elements();
		for(int i=0; i<plots.size(); i++)
		{
			Plot p = (Plot) plots.get(i);
			p.registerYearEnd();
		}
	}

	public int getYear() {
		return yearCount;
	}

	/* ------------------------
	 * Printing information
	 * ------------------------ */
	public void printLowestValueOfThrivingPopulations()
	{
		Bag plots = (Bag) ((ObjectGrid2D) hc.plotGrid_GridField.getGrid()).elements();
		ArrayList<Integer> printList = new ArrayList<Integer>();
		for(int i=0; i<plots.size(); i++)
		{
			Plot p = (Plot) plots.get(i);
			ArrayList<Integer> hc = p.getHistoryCounts();

			// System.out.println(hc.toString());
			// System.out.println(p.getColor());

			int absurdNumber = 100000000;
			int lowestAfterFifty = absurdNumber;
			boolean afterAtLeastOneFifty = false;
			for(int j=0; j<hc.size(); j++)
			{
				if(hc.get(j)>=50 && afterAtLeastOneFifty==false)
				{
					afterAtLeastOneFifty=true; // you've found at least one 50
				}
				else if(hc.get(j)<lowestAfterFifty && afterAtLeastOneFifty==true)
				{
					lowestAfterFifty=hc.get(j);
				}
			}
			if (lowestAfterFifty != absurdNumber)
			{
				printList.add(lowestAfterFifty);
			}
		}
		System.out.println(printList.toString());
	}

	public void printFinalStatistics()
	{
		int u=0, t=0, m=0, tr=0, d=0;

		Bag plots = (Bag) ((ObjectGrid2D) hc.plotGrid_GridField.getGrid()).elements();
		for(int i=0; i<plots.size(); i++)
		{
			Plot p = (Plot) plots.get(i);
			Plot.PlotType pt = p.getPlotType();
			if (pt.equals(Plot.PlotType.UNKNOWN))
			{
				u++;
			}
			else if (pt.equals(Plot.PlotType.THRIVING))
			{
				t++;
			}
			else if (pt.equals(Plot.PlotType.MEDIOCRE))
			{
				m++;
			}
			else if (pt.equals(Plot.PlotType.TRANSIENT))
			{
				tr++;
			}
			else if (pt.equals(Plot.PlotType.DEAD))
			{
				d++;
			}
		}

		System.out.println(
			Parameters.stochMax + " " +
			Parameters.adjustmentFactor + " " +
			Parameters.hydrochoryBool + " " +
			Parameters.implantationRate + " " +
			Parameters.seedBankRate + " " +
			(yearCount + 1) + " " +
			plots.size() + " " +
			u + " " + // unknown
			d + " " + // dead
			t + " " + // thriving
			m + " " + // mediocre
			tr + " " + // transient
			hc.reproducingPlants_vectorField.getGeometries().size());
	}

	public void runClusteringAnalysis()
	{
		DBSCAN dbscan = new DBSCAN();
		Bag clusters = dbscan.getPopulations(
			hc.reproducingPlants_vectorField.getGeometries(), Parameters.EPSILON, Parameters.MIN_POINTS);

		if (clusters.size() > 0)
		{
			System.out.println("Number of clusters: " + clusters.size());
			System.out.print("clusterPops: [");
		}
		else
		{
			System.out.println("Number of clusters: 0");
			System.out.println("clusterPops: [0]");
		}

		for (int i=0;i<clusters.size();i++)
		{
			Bag cluster = (Bag) clusters.get(i);
			if (i < clusters.size()-1)
			{
				System.out.print(cluster.size() + ", ");
			}
			else
			{
				System.out.println(cluster.size() + "]");
			}
		}
	}

/* ------------------------
 * Keeping track of the date.
 * ------------------------ */
	public static double getClockTimeForNextNewYearDate(){
		return getClockTimeForNext(Month.DEC, 31);
	}

	public static class Date
	{
		public Month month;
		public int dayWithinMonth;

		public Date(Month month, int dayWithinMonth)
		{
			this.month = month;
			this.dayWithinMonth = dayWithinMonth;
		}
		public boolean equals(Date d)
		{
			if (d.month == month && d.dayWithinMonth == dayWithinMonth)
			{
				return true;
			}
			return false;
		}
	}

	public void printDateIfNew()
	{
		double time = HoltsCreek.instance().schedule.getTime();
		Date date = getDateForTime(time);
		if (!date.equals(lastDate))
		{
			lastDate = date;
			System.out.println("Time: " + date.month + " " +
				date.dayWithinMonth + ", " +
				(2012 + (int) (time / (HOURS_PER_DAY * DAYS_PER_YEAR))));
		}
	}

	private static Date getDateForTime(double time) {
		double numDays = (time % (HOURS_PER_DAY * DAYS_PER_YEAR)) / HOURS_PER_DAY;
		Month month = Month.JAN;
		int date = 0;
		for (int i=0; i<CUM_DAYS.length; i++)
		{
			if (numDays < CUM_DAYS[i])
			{
				month = Month.values()[i];
				int cumDays = 0;
				if (i>0)
				{
					cumDays = CUM_DAYS[i-1];
				}
				date = (int) numDays - cumDays + 1;
				break;
			}
		}
		return new Date(month,date);
	}

	private static double computeHoursPastNewYears(double clock) {
		return clock % (365 * HOURS_PER_DAY);
	}

	/**
	 * Returns the (future) simulation clock time, in hours, for the Date
	 * object passed as an argument. (See documentation under
	 * {@link getClockTimeForNext(Month, int)}.)
	 */
	public static double getClockTimeForNext(Date date) {
		return getClockTimeForNext(date.month, date.dayWithinMonth);
	}

	/**
	 * Returns the (future) simulation clock time, in hours, for the month
	 * and day passed as arguments. In other words, the method pretends to
	 * fast forward time until the <i>next</i> occurrence of FEB 23
	 * (which could be in either the current or next calendar year), and
	 * returns the absolute clock time (in units of hours) corresponding to
	 * the midnight initiating that day.
	 * @param month One of the 12 legal values of {@link Month}. Any
	 * illegal value results in undefined behavior.
	 * @param dayWithinMonth An integer giving the day of the month. No
	 * bounds-related error checking is provided.
	 */
	public static double getClockTimeForNext(Month month, int dayWithinMonth)
	{
		double currentTime = HoltsCreek.instance().schedule.getTime();
		if (currentTime < 0)
		{
			currentTime = 0;
		}
		double currHoursPastNewYears = computeHoursPastNewYears(currentTime);
		double currYear = Math.floor(currentTime / (365 * HOURS_PER_DAY));
		double desiredHoursPastNewYears = HOURS_PER_DAY *
			(CUM_DAYS[month.ordinal()] - DAYS[month.ordinal()]) +
			HOURS_PER_DAY * (dayWithinMonth-1);
		if (desiredHoursPastNewYears > currHoursPastNewYears)
		{
			// We're not there yet in this calendar year. Return the time
			// later on in this year.
			return currYear * 365 * HOURS_PER_DAY +
			desiredHoursPastNewYears;
		} else
		{
			// We're already past the requested date in this calendar year.
			// Return the time in the next calendar year.
			return (currYear+1) * 365 * HOURS_PER_DAY +
			desiredHoursPastNewYears;
		}
	}
}
