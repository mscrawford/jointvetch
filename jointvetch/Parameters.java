package jointvetch;

class Parameters
{
    // independent variables
    static double stochMax;
    static boolean hydrochoryBool;
    static double implantationRate;
    static double adjustmentFactor;

    // for first warmUp years, adjustment factor does not exist
    static final int warmUp = 3;
    
    // fuzzy distance measurements to correct for error
    static final double BUFFER_SIZE = 0.01;

    // set parameters
    static final double HYDROCHORY_PROB = 0.340;
    static final double WINTER_SURVIVAL_RATE = 0.379;
    static final double IMPLANTATION_MAXIMUM_DISTANCE = 4.0;

    // environment's parameters
    static int SIM_TAG;
    static int MAX_YEAR_COUNT = 100;
    static final int MAX_POPULATION_COUNT = 150000;
    static final int DBSCAN_CUTOFF = 25000; // too big a runtime for clustering analysis
    static boolean VERBOSE = false;
    static String SIM_STATS_FILE = "/tmp/sim_stats.csv";
    static String CLUSTER_STATS_FILE = "/tmp/cluster_stats.csv";

    // DBSCAN implementation
    static final double EPSILON = 25.0;
    static final int MIN_POINTS = 1;

    // plot parameters
    static final int CARRYING_CAPACITY = 50; // per m^2 "plot"

    private static final int[] FEC_QEXP = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 3, 0, 8, 0, 1, 4, 6, 0, 2, 5, 6, 1, 1, 
        2, 3, 1, 1, 4, 3, 3, 2, 4, 5, 4, 6, 7, 8, 9, 10, 8, 11, 10, 13, 16, 14, 15, 
        18, 15, 19, 20, 23, 21, 26, 30, 24, 27, 33, 31, 37, 28, 44, 34, 39, 41, 48, 
        45, 55, 50, 64, 51, 58, 74, 80, 66, 69, 71, 85, 59, 93, 88, 98, 101, 106, 
        114, 120, 108, 126, 110, 132, 135, 157, 140, 145, 173, 188, 209, 165, 160, 
        209, 126, 148, 146, 136, 191, 137, 140, 44, 93, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 126, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    /* Survival distribution */
    private static final double[] SURV_QEXP = { 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00486, 0.00024, 0.00024, 0.00024, 0.00278, 0.00024, 0.0021, 0.00024, 
        0.00485, 0.00024, 9e-04, 0.00275, 0.00398, 0.00024, 0.00112, 0.00326, 
        0.00388, 0.00055, 0.00071, 0.00101, 0.00162, 0.00084, 0.00045, 0.00246, 
        0.00189, 0.00206, 0.00142, 0.0023, 0.00317, 0.0027, 0.00372, 0.00426, 
        0.00479, 0.00553, 0.00616, 0.0052, 0.00726, 0.00654, 0.00802, 0.01028, 
        0.00883, 0.0093, 0.01121, 0.00978, 0.01218, 0.01275, 0.01442, 0.01332, 
        0.01673, 0.01883, 0.01551, 0.01742, 0.02103, 0.01958, 0.02334, 0.01806, 
        0.0276, 0.02177, 0.02484, 0.02571, 0.0305, 0.02857, 0.0348, 0.03154, 
        0.0403, 0.03254, 0.03684, 0.04707, 0.05093, 0.04158, 0.04373, 0.04501, 
        0.05408, 0.03751, 0.05891, 0.05562, 0.06199, 0.06395, 0.0671, 0.07218, 
        0.0761, 0.06858, 0.07976, 0.06978, 0.08355, 0.0858, 0.09978, 0.08874, 
        0.09205, 0.10953, 0.11891, 0.1325, 0.1049, 0.10146, 0.1325, 0.0801, 
        0.09362, 0.09259, 0.08633, 0.12126, 0.08674, 0.089, 0.02761, 0.05894, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.08017, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 0.00024, 
        0.00024, 0.00024, 0.00024, 0.00024 };

    static void initParams()
    {
        stochMax = HoltsCreek.instance().getStochMax();
        hydrochoryBool = HoltsCreek.instance().getHydrochoryBool();
        implantationRate = HoltsCreek.instance().getImplantationRate();
        adjustmentFactor = HoltsCreek.instance().getAdjustmentFactor();
        SIM_STATS_FILE = "/tmp/sim_stats.csv" + SIM_TAG;
        CLUSTER_STATS_FILE = "/tmp/cluster_stats.csv" + SIM_TAG;
    }

    // Suppress default constructor for noninstantiability
    private Parameters() {
        throw new AssertionError();
    }

    static double getSurvRate(int color)
    {
        assert (color >= 0 && color < 256 || color == HoltsCreek.RIVER_RASTER_COLOR);

        return (color != HoltsCreek.RIVER_RASTER_COLOR) ? SURV_QEXP[color] : 0;
    }

    static int getFecundity(int color)
    {
        assert (color >= 0 && color < 256 || color == HoltsCreek.RIVER_RASTER_COLOR);

        return (color != HoltsCreek.RIVER_RASTER_COLOR) ? FEC_QEXP[color] : 0;
    }

    static double getAdjustment() 
    {
        if (Environment.instance().getYear() < warmUp) return 1.0;

        return adjustmentFactor;
    }

}
