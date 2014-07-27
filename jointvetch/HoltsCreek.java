package jointvetch;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.net.URL;
import sim.engine.*;
import sim.io.geo.*;
import sim.util.*;
import sim.util.geo.GeomPlanarGraph;
import sim.util.geo.MasonGeometry;
import sim.field.geo.GeomVectorField;
import sim.field.geo.GeomGridField;
import sim.field.geo.GeomGridField.GridDataType;
import sim.field.grid.ObjectGrid2D;
import com.vividsolutions.jts.geom.*;

/**
 * @author Michael Crawford
 */
class HoltsCreek extends SimState
{
    private static HoltsCreek instance;
    static long seed;

    /* data files */
    private static final String pampoint = "/data/Pampoint/Pampoint_All.shp"; // initial plants (points)
    private static final String flowline = "/data/riverFlow/riverFlow.shp"; // river network (lines)
    private static final String waterbody = "/data/Waterbody/waterbody.shp"; // tidal marsh (geometries)
    private static final String rasterFile = "data/waterbody_raster/waterbody_raster.asc.gz"; /* goodness */
    private static final String seedFloatTimesFile = "data/seedFloatTimes.txt";
    private static final String CACHED_RASTER = "data/waterbody_raster/buttslow.obj";

    GeometryFactory factory = new GeometryFactory();
    private Envelope MBR = new Envelope();

    /* plant geometries */
    GeomVectorField initialPlants_vf = new GeomVectorField();
    GeomVectorField reproducingPlants_vf = new GeomVectorField(); // used by DBSCAN

    /* geographic geometries, the network of rivers, and tidally inundated landmass */
    GeomVectorField riverLines_vf = new GeomVectorField();
    GeomVectorField tidal_vf = new GeomVectorField();
    GeomVectorField boundary_vf = new GeomVectorField();
    GeomPlanarGraph riverNetwork = new GeomPlanarGraph(); // the directed graph/network

    /* competition & carrying capacity rasters */
    GeomGridField redRaster_gf = new GeomGridField();
    GeomGridField plotGrid_gf = new GeomGridField();
    int gridHeight, gridWidth;

    /* geometries for distance detection */
    MultiLineString river_mls;
    MultiPolygon tidal_mp;
    Geometry tidalBoundary_g;
    
    /* independent variables */
    private final double adjustmentFactor;
    private final double stochMax;
    private final double implantationRate;
    private final boolean hydrochoryBool;

    /* seed floatation data, see methods */
    int[] seedFloatTimes = new int[499];

    static final int RIVER_RASTER_COLOR = -9999;

    private static final double PLANT_DROP_DIST_MAX = 2.0; // meters

    /* creating the instance */
    static synchronized HoltsCreek instance(long seed, String[] args)
    {
        if (instance == null)
        {
            instance = new HoltsCreek(seed, args);
            Parameters.initParams();
        }
        return instance;
    }

    /* getting the instance */
    static synchronized HoltsCreek instance()
    {
        if (instance == null) throw new AssertionError();
            
        return instance;
    }

    private HoltsCreek(long seed, String[] args)
    {     
        super(seed);
        this.seed = seed;
        
        stochMax = Double.parseDouble(args[0]);
        hydrochoryBool = Boolean.parseBoolean(args[1]);
        implantationRate = Double.parseDouble(args[2]);
        adjustmentFactor = Double.parseDouble(args[3]);
        try {
            for (int i=0; i<args.length; i++) {
                if (args[i].equals("-verbose")) {
                    Parameters.VERBOSE = true;
                }
                if (args[i].equals("-tag")) {
                    Parameters.SIM_TAG = Integer.valueOf(args[i+1]);
                }
                if (args[i].equals("-years")) {
                    Parameters.MAX_YEAR_COUNT = Integer.valueOf(args[i+1]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            printUsage();
            System.exit(1);
        }
        System.out.println("Seed:"+seed);
        System.out.println("stochMax = " + stochMax);
        System.out.println("hydrochoryBool = " + hydrochoryBool);
        System.out.println("implantationRate = " + implantationRate);
        System.out.println("adjustmentFactor = " + adjustmentFactor);
        System.out.println("Parameters.VERBOSE = " + Parameters.VERBOSE);
        System.out.println("Parameters.SIM_TAG = " + Parameters.SIM_TAG);
        System.out.println("Parameters.MAX_YEAR_COUNT = " +
            Parameters.MAX_YEAR_COUNT);

        assert (stochMax >= 1.0) : "Stochasticity must be 1 or greater.";
    }

    private static void printUsage() {
        System.err.println(
            "Usage: HoltsCreek stochMax hydrochoryBool implantationRate" +
            " adjustmentFactor [-verbose] [-quiet]" +
            " [-tag simtagInt] [-years maxYears]" +
            " [-seed seed].");
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length < 4) {
            printUsage();
            System.exit(1);
        }
        doLoop(new MakesSimState()
        {
            public SimState newInstance(long seed, String[] args)
            {
                return HoltsCreek.instance(seed, args);
            }
            public Class simulationClass()
            {
                return HoltsCreek.class;
            }
        }, args);
        System.exit(0);
    }

    public void start()
    {
        super.start();
        readData();
        populateGeometryCollections();
        setupInitialPlantPopulations();
        setupEnvironment();
    }

    /**
     * Read in the requisite data, do preliminary indexing of rivers and tidal geometries,
     * lay the groundwork for geometry related calculations and visual effects.
     */
    private void readData()
    {
        try {
            initialPlants_vf.clear();
            reproducingPlants_vf.clear();

            riverLines_vf.clear();
            tidal_vf.clear();
            boundary_vf.clear();

            redRaster_gf.clear();
            plotGrid_gf.clear();

            // joint-vetch Populations
            URL pampnt = HoltsCreek.class.getResource(pampoint);
            ShapeFileImporter.read(pampnt, initialPlants_vf);
            MBR.expandToInclude(initialPlants_vf.getMBR());

            // river network
            URL riverFlow = HoltsCreek.class.getResource(flowline);
            ShapeFileImporter.read(riverFlow, riverLines_vf);
            MBR.expandToInclude(riverLines_vf.getMBR());

            // tidal areas
            URL tidalArea = HoltsCreek.class.getResource(waterbody);
            ShapeFileImporter.read(tidalArea, tidal_vf);
            MBR.expandToInclude(tidal_vf.getMBR());

            // set up the river's network
            riverNetwork.createFromGeomField(riverLines_vf);

            // red raster / competition map
            if (new File(CACHED_RASTER).exists()) {
                System.out.println("(Using cached raster object.)");
                java.io.ObjectInputStream ois = 
                    new java.io.ObjectInputStream(
                        new java.io.FileInputStream(CACHED_RASTER));
                redRaster_gf = (GeomGridField) ois.readObject();
            } else {
                System.out.println("No cached raster object." +
                    " Paying the piper with one-time startup cost.\n" +
                    " Be patient...");
                GZIPInputStream cis = new GZIPInputStream(
                    new java.io.FileInputStream(rasterFile));
                ArcInfoASCGridImporter.read(cis, GridDataType.INTEGER, 
                    redRaster_gf);
                cis.close();
                System.out.println("Writing cached raster object...");
                java.io.ObjectOutputStream oos = 
                    new java.io.ObjectOutputStream(
                        new java.io.FileOutputStream(CACHED_RASTER));
                oos.writeObject(redRaster_gf);
                oos.close();
                System.out.println("...done.");
            }
            MBR.expandToInclude(redRaster_gf.getMBR());
            gridHeight = redRaster_gf.getGridHeight();
            gridWidth = redRaster_gf.getGridWidth();

            // plot grid
            ObjectGrid2D plotGrid = new ObjectGrid2D(gridWidth, gridHeight);
            plotGrid_gf.setGrid(plotGrid);
            MBR.expandToInclude(plotGrid_gf.getMBR());

            // set MBR for all GeomVectorFields
            initialPlants_vf.setMBR(MBR);
            reproducingPlants_vf.setMBR(MBR);

            riverLines_vf.setMBR(MBR);
            tidal_vf.setMBR(MBR);
            boundary_vf.setMBR(MBR);
            redRaster_gf.setMBR(MBR);
            plotGrid_gf.setMBR(MBR);

            /* ------------------------
            * Pixel height has to be set or else the raster grid will be off alignment.
            * ------------------------ */
            redRaster_gf.setPixelHeight(1.0); 
            redRaster_gf.setPixelWidth(1.0);

            plotGrid_gf.setPixelHeight(1.0);
            plotGrid_gf.setPixelWidth(1.0);

            // Read in Dr. Griffith's seed floatation data, ceiling it to whole integers.
            Scanner s = new Scanner(new File(seedFloatTimesFile));
            int index = 0;
            while (s.hasNextDouble())
            {
                seedFloatTimes[index++] = (int) Math.ceil( s.nextDouble() );
            }
            s.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void populateGeometryCollections()
    {
        /* Insert the riverLines_vf geometries into a GeometryCollection (MultiLineString)
            for distance sorting, see Plant.java */
        Bag r_bag = riverLines_vf.getGeometries();
        LineString[] r_arr = new LineString[r_bag.size()];
        for (int i = 0, s = r_bag.size(); i < s; i++)
        {
            r_arr[i] = (LineString) ( (MasonGeometry) r_bag.get(i) ).getGeometry();
        }
        river_mls = new MultiLineString(r_arr, factory);

        /* Insert the waterBody geometries into a GeometryCollection (MultiPolygon) for
            distance sorting, see Plant.java */
        Bag t_bag = tidal_vf.getGeometries();
        Polygon[] t_arr = new Polygon[t_bag.size()];
        for (int i = 0, s = t_bag.size(); i < s; i++)
        {
            t_arr[i] = (Polygon) ( (MasonGeometry) t_bag.get(i) ).getGeometry();
        }
        tidal_mp = new MultiPolygon(t_arr, factory);

        tidalBoundary_g = tidal_mp.getBoundary(); /* Take the boundary of these geometries so
            we can find how far the seed is to the river in Plant.java. */
        boundary_vf.addGeometry(new MasonGeometry(tidalBoundary_g));
    }

    /**
     * Drops the plant populations from the pampoint shapefiles into Holts Creek. These plants
     * are adults. They are dropped in a uniform field around the maternal plant.
     */
    private void setupInitialPlantPopulations()
    {
        Bag jointvetch_Bag = new Bag(initialPlants_vf.getGeometries());
        for (int i = 0, s = jointvetch_Bag.size(); i < s; i++)
        {
            MasonGeometry curPopulation = (MasonGeometry) jointvetch_Bag.get(i);
            int popSize = curPopulation.getIntegerAttribute("POPSIZE");
            for (int j = 0; j < popSize; j++)
            {
                double plantAngle = random.nextDouble() * 2 * Math.PI;
                double plantDist = random.nextDouble() * PLANT_DROP_DIST_MAX;
                double xOffset = plantDist * Math.cos(plantAngle);
                double yOffset = plantDist * Math.sin(plantAngle);
                Coordinate plantLoc = (Coordinate) curPopulation.getGeometry().getCoordinate().clone();
                plantLoc.x += xOffset;
                plantLoc.y += yOffset;
                Plant p = new Plant(new MasonGeometry(factory.createPoint(plantLoc)), true);
            }
        }
    }

    /**
     * Schedule each special environment once in the future, at the
     * next appropriate time. When it wakes up, the Environment object
     * will be responsible for re-scheduling itself in future years.
     */
    private void setupEnvironment()
    {
        schedule.scheduleOnce(Environment.instance().getClockTimeForNextNewYearDate(), Environment.instance());
    }

    public void finish() {
        super.finish();
    }

    double getStochMax() {
        return stochMax;
    }

    double getAdjustmentFactor() {
        return adjustmentFactor;
    }

    double getImplantationRate() {
        return implantationRate;
    }

    boolean getHydrochoryBool() {
        return hydrochoryBool;
    }

}
