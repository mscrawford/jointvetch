package jointvetch;

import java.util.*;
import java.io.*;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.*;
import sim.util.geo.MasonGeometry;
import sim.field.grid.ObjectGrid2D;
import org.apache.commons.math3.stat.StatUtils;

/**
 * A representation of the plants' environment, specifically embodying
 * those features which impact a plant's lifecycle. This is a singleton
 * class, designed to be owned by a governing simulation (subclass of
 * SimState.)
 * @author Stephen
 */
class Environment implements Steppable
{
    private static Environment instance;
    private HoltsCreek hc;

    private PrintWriter simStatsPw;
    private PrintWriter clusterStatsPw;

    /* timekeeping */
    enum Month { JAN,FEB,MAR,APR,MAY,JUN,JUL,AUG,SEP,OCT,NOV,DEC }
    private static final int DAYS[] = { 31,28,31,30,31,30,31,31,30,31,30,31 };
    private static final int CUM_DAYS[] = { 31,59,90,120,151,181,212,243,273,304,334,365 };
    private static final int HOURS_PER_DAY = 24;
    private static final int DAYS_PER_YEAR = 365;

    private int year = 0;
    private Date lastDate = new Date(Month.JAN, 1);
    private static final Date newYearDate = new Date(Month.DEC, 31);

    /* environmental stochasticity */
    private double currentEnvStoch;

    /* records */
    private List<Integer> populationHistory;
    private List<Double> environmentalHistory;

    private static final String coordPath = 
        // "/Users/Theodore/Documents/Google_Drive/SJV_EcologicalModelling_Paper/analysis_and_validation/vital_rate_distro_derivation/";
        "/tmp/";

    static synchronized Environment instance()
    {
        if (instance == null)
        {
            instance = new Environment();
        }
        return instance;
    }

    private Environment()
    {
        hc = HoltsCreek.instance();
    
        populationHistory = new ArrayList<Integer>();
        environmentalHistory = new ArrayList<Double>();
        
        currentEnvStoch = generateEnvironmentalStochasticity();
        environmentalHistory.add(currentEnvStoch);

        try {
            new File(Parameters.SIM_STATS_FILE).delete();
            new File(Parameters.CLUSTER_STATS_FILE).delete();
            simStatsPw = new PrintWriter(
                new FileWriter(Parameters.SIM_STATS_FILE));
            clusterStatsPw = new PrintWriter(
                new FileWriter(Parameters.CLUSTER_STATS_FILE));
            simStatsPw.println("\"year\",\"pop\",\"env\"");
            clusterStatsPw.println("\"year\",\"cluster.pop\"");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Couldn't initialize output files!");
            System.exit(1);
        }
    }

    /* ------------------------
     * Called at the end of each year.
     * ------------------------ */
    public void step(SimState state)
    {
        Date today = getDateForTime(hc.schedule.getTime());

        if (today.equals(newYearDate))
        {
            year++;
            notifyEndOfYearForPlots();

            int n = hc.reproducingPlants_vf.getGeometries().size();
            populationHistory.add(n);
            if (n == 0 || 
                n > Parameters.MAX_POPULATION_COUNT ||
                year >= Parameters.MAX_YEAR_COUNT) {
                try {
                    printStatistics();
                } catch (IOException e) { e.printStackTrace(); }
                if (year < Parameters.MAX_YEAR_COUNT) {
                    simStatsPw.println(Parameters.MAX_YEAR_COUNT+","+
                        populationHistory.get(populationHistory.size()-1)+","+
                        0);
                }
                simStatsPw.close();
                clusterStatsPw.close();
                System.exit(0);
            }

            if (Parameters.VERBOSE) {
                try {
                    printStatistics();
                } catch (IOException e) { e.printStackTrace(); }
            }

            currentEnvStoch = generateEnvironmentalStochasticity();
            environmentalHistory.add(currentEnvStoch);
            
            hc.reproducingPlants_vf.clear();
            hc.schedule.scheduleOnce(getClockTimeForNextNewYearDate(), this);
        }
        else throw new AssertionError();
    }

/* ------------------------------
 * Environmental stochasticity
 * ------------------------------ */
    private double generateEnvironmentalStochasticity()
    {
        double u = hc.random.nextDouble();
        double stochMax = Parameters.stochMax;
        double stochMin = 1/stochMax;

        return Math.exp( (u*Math.log(stochMax)) + ((1 - u) * Math.log(stochMin)) );
    }

    double getEnvironmentalStochasticity() { return currentEnvStoch; }

/* ------------------------------
 * Dealing with plots
 * ------------------------------ */
    Plot getPlot(int x, int y)
    {
        Plot plot = (Plot) ( (ObjectGrid2D) hc.plotGrid_gf.getGrid() ).get(x, y);
        if (plot == null)
        {
            plot = new Plot(x, y);
            ( (ObjectGrid2D) hc.plotGrid_gf.getGrid() ).set(x, y, plot);
        }
        return plot;
    }

    private void notifyEndOfYearForPlots()
    {
        Bag plots = ( (ObjectGrid2D) hc.plotGrid_gf.getGrid() ).elements();
        for (int i = 0, s = plots.size(); i < s; i++)
        { 
            Plot p = (Plot) plots.get(i);
            p.registerYearEnd(year);
        }
    }

    int getYear() {
        return year;
    }

/* ------------------------
 * Printing information
 * ------------------------ */
    private void printStatistics() throws java.io.IOException
    {
        System.out.println("Year " + year + ":");
        double[] popHistArr = new double[ populationHistory.size() ];
        for (int i = 0, s = populationHistory.size(); i < s; i++)
            popHistArr[i] = (double) populationHistory.get(i);

        int u=0, t=0, m=0, tr=0, d=0;

        Bag plots = ( (ObjectGrid2D) hc.plotGrid_gf.getGrid() ).elements();
        for (int i = 0, s = plots.size(); i < s; i++)
        {
            Plot p = (Plot) plots.get(i);
            Plot.PlotType pt = p.getPlotType();
            if (pt.equals(Plot.PlotType.UNKNOWN)) u++;
            else if (pt.equals(Plot.PlotType.THRIVING)) t++;
            else if (pt.equals(Plot.PlotType.MEDIOCRE)) m++;
            else if (pt.equals(Plot.PlotType.TRANSIENT)) tr++;
            else if (pt.equals(Plot.PlotType.DEAD)) d++;
            else throw new AssertionError();
        }

        int finalPopulation = hc.reproducingPlants_vf.getGeometries().size();
        boolean noClusteringAnalysis = finalPopulation > Parameters.DBSCAN_CUTOFF;
        String numClustersStr, clusterPopMeanStr, clusterPopSDDev;
        if (noClusteringAnalysis)
        {
            numClustersStr = "NA";
            clusterPopMeanStr = "NA";
            clusterPopSDDev = "NA";
        }
        else
        {
            Bag clusters = runClusteringAnalysis();
            int numClusters = clusters.size();
            double[] clusterArr = new double[ numClusters ];
            for (int i = 0; i < numClusters; i++)
                clusterArr[i] = (double) ((Integer) clusters.get(i));

            numClustersStr = String.valueOf(numClusters);
            clusterPopMeanStr = String.valueOf((int) StatUtils.mean(clusterArr));
            clusterPopSDDev = String.valueOf((int) Math.sqrt(StatUtils.populationVariance(clusterArr)));
        }

        StringBuilder s = new StringBuilder();
        s.append(hc.seed + " ");
        s.append(Parameters.stochMax + " ");
        s.append(Parameters.hydrochoryBool + " ");
        s.append(Parameters.implantationRate + " ");
        s.append(Parameters.adjustmentFactor + " ");
        s.append(year + " ");
        s.append(d + " "); // dead
        s.append(tr + " "); // transient
        s.append(m + " "); // mediocre
        s.append(t + " "); // thriving
        s.append(numClustersStr + " "); 
        s.append(clusterPopMeanStr + " ");
        s.append(clusterPopSDDev + " "); // stdev of population clusters
        s.append(finalPopulation + " ");
        s.append((int) StatUtils.max(popHistArr) + " "); 
        s.append((int) StatUtils.min(popHistArr) + " ");
        s.append((int) StatUtils.mean(popHistArr) + " ");
        s.append((int) Math.sqrt(StatUtils.populationVariance(popHistArr))); // stdev of population history
        System.out.println(s);

        if (Parameters.VERBOSE)
        {
            System.out.println("Population History: " + populationHistory);
            System.out.println("Environmental History: " + environmentalHistory);
            simStatsPw.println(year+","+
                populationHistory.get(populationHistory.size()-1)+","+
                environmentalHistory.get(environmentalHistory.size()-1));
            simStatsPw.flush();
                
            printCoords();
            System.out.println();
        }
    }

    private Bag runClusteringAnalysis() throws java.io.IOException
    {
        DBSCAN dbscan = new DBSCAN();
        Bag clusters = dbscan.getPopulations(
            hc.reproducingPlants_vf.getGeometries(), Parameters.EPSILON, Parameters.MIN_POINTS);

        if (Parameters.VERBOSE)
        {
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

            for (int i = 0, s = clusters.size(); i < s; i++)
            {
                Bag cluster = (Bag) clusters.get(i);
                clusterStatsPw.println(year+","+cluster.size());
                if (i < clusters.size()-1)
                {
                    System.out.print(cluster.size() + ", ");
                }
                else
                {
                    System.out.println(cluster.size() + "]");
                }
            }
            clusterStatsPw.flush();
        }

        Bag clusterCounts = new Bag();
        for (int i = 0, s = clusters.size(); i < s; i++)
        {
            Bag c = (Bag) clusters.get(i);
            clusterCounts.add(c.size());
        }

        return clusterCounts;
    }

    private void printCoords()
    {       
        try {
            File file = new File(coordPath, "PLANT_COORDS.csv" + 
                Parameters.SIM_TAG + "." + year);
            BufferedWriter output = new BufferedWriter(new FileWriter(file));
            for (int i = 0, s = hc.reproducingPlants_vf.getGeometries().size(); i < s; i++)
            {
                MasonGeometry mg = (MasonGeometry) hc.reproducingPlants_vf.getGeometries().get(i);
                output.write(mg.getGeometry().getCoordinate().x + ", " + mg.getGeometry().getCoordinate().y + "\n");
            }
            output.close();
        } catch ( IOException e ) {
            e.printStackTrace();
        }   
    }

/* ------------------------
 * Keeping track of the date.
 * ------------------------ */
    static class Date
    {
        Month month;
        int dayWithinMonth;

        Date(Month month, int dayWithinMonth)
        {
            this.month = month;
            this.dayWithinMonth = dayWithinMonth;
        }
        
        boolean equals(Date d)
        {
            return (d.month == month && d.dayWithinMonth == dayWithinMonth);
        }
    }
    
    double getClockTimeForNextNewYearDate() {
        return getClockTimeForNext(Month.DEC, 31);
    }

    private static Date getDateForTime(double time) 
    {
        double numDays = (time % (HOURS_PER_DAY * DAYS_PER_YEAR) ) / HOURS_PER_DAY;
        Month month = Month.JAN;
        int date = 0;
        for (int i = 0; i < CUM_DAYS.length; i++)
        {
            if (numDays < CUM_DAYS[i])
            {
                month = Month.values()[i];
                int cumDays = 0;
                if (i > 0)
                {
                    cumDays = CUM_DAYS[i-1];
                }
                date = (int) numDays - cumDays+1;
                break;
            }
        }
        return new Date(month, date);
    }

    private static double computeHoursPastNewYears(double clock) {
        return clock % (365 * HOURS_PER_DAY);
    }

    /**
     * Returns the (future) simulation clock time, in hours, for the Date
     * object passed as an argument. (See documentation under
     * {@link getClockTimeForNext(Month, int)}.)
     */
    double getClockTimeForNext(Date date) {
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
    double getClockTimeForNext(Month month, int dayWithinMonth)
    {
        double currentTime = hc.schedule.getTime();
        if (currentTime < 0) currentTime = 0;
        double currHoursPastNewYears = computeHoursPastNewYears(currentTime);
        double currYear = Math.floor(currentTime / (365 * HOURS_PER_DAY) );
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
