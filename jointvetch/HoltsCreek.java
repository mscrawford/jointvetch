package jointvetch;

import java.io.*;
import java.util.*;
import java.net.URL;
import sim.engine.*;
import sim.io.geo.*;
import java.io.InputStream;
import java.io.FileInputStream;
import sim.util.*;
import sim.util.geo.GeomPlanarGraph;
import sim.util.distribution.Uniform;
import sim.util.geo.MasonGeometry;
import sim.field.geo.GeomVectorField;
import sim.field.grid.IntGrid2D;
import sim.field.grid.ObjectGrid2D;
import sim.field.geo.GeomGridField;
import sim.field.geo.GeomGridField.GridDataType;
import sim.io.geo.ArcInfoASCGridImporter;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

/**
 * @author Michael Crawford
 */
class HoltsCreek extends SimState
{
	private static HoltsCreek instance;

	/* data files */
	private static final String pampoint = "/data/Pampoint/Pampoint_All.shp";
	private static final String flowline = "/data/riverFlow/riverFlow.shp";
	private static final String flowArea = "/data/riverArea/riverArea.shp";
	private static final String waterbody = "/data/Waterbody/waterbody.shp";
	private static final String rasterFile = "data/waterbody_raster/waterbody_raster.asc"; /* goodness */

	private static final String seedFloatTimesFile = "data/seedFloatTimes.txt";

	GeometryFactory factory = new GeometryFactory();
	private Envelope MBR = new Envelope();

	/* plant geometries */
	GeomVectorField initialPlant_vectorField = new GeomVectorField();
	GeomVectorField reproducingPlants_vectorField = new GeomVectorField(); // used by DBSCAN

	/* geographic geometries, the network of rivers and tidally innundated landmass */
	GeomVectorField river_vectorField = new GeomVectorField();
	GeomVectorField riverArea_vectorField = new GeomVectorField();
	GeomVectorField tidal_vectorField = new GeomVectorField();
	GeomVectorField boundary_vectorField = new GeomVectorField();
	GeomPlanarGraph river_Network = new GeomPlanarGraph(); // the directed graph/network

	/* competition & carrying capacity rasters */
	GeomGridField colorRaster_GridField = new GeomGridField();
	GeomGridField plotGrid_GridField = new GeomGridField();
	int rasterHeight, rasterWidth;

	/* geometries for distance detection */
	MultiLineString river_Geometries;
	MultiPolygon tidal_Geometries;
	Geometry tidal_Boundary;
	MultiPolygon riverArea_Geometries;
	
	/* independent variables */
	private double adjustmentFactor;
	private double stochMax;
	private double implantationRate;
	private boolean hydrochoryBool;
	private double seedBankRate;

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

		stochMax = Double.parseDouble(args[0]);
		hydrochoryBool = Boolean.parseBoolean(args[1]);
		implantationRate = Double.parseDouble(args[2]);
		seedBankRate = Double.parseDouble(args[3]); // unused
	}

	public static void main(String[] args) throws Exception
	{
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
		setupInitialPlantPopulations();
		setupEnvironment();
	}

	/**
	 * Read in the requisite data, do preliminary indexing of rivers and tidal geometries,
	 * lay the groundwork for geometry related calculations and visual effects.
	 * @author Michael Crawford
	 */
	private void readData()
	{
		try {
			initialPlant_vectorField.clear();
			reproducingPlants_vectorField.clear();

			river_vectorField.clear();
			riverArea_vectorField.clear();
			tidal_vectorField.clear();
			boundary_vectorField.clear();

			colorRaster_GridField.clear();
			plotGrid_GridField.clear();

			// joint-vetch Populations
			URL pampnt = HoltsCreek.class.getResource(pampoint);
			ShapeFileImporter.read(pampnt, initialPlant_vectorField);
			MBR.expandToInclude(initialPlant_vectorField.getMBR());

			// river network
			URL riverFlow = HoltsCreek.class.getResource(flowline);
			ShapeFileImporter.read(riverFlow, river_vectorField);
			MBR.expandToInclude(river_vectorField.getMBR());

			// river area
			URL riverArea = HoltsCreek.class.getResource(waterbody);
			ShapeFileImporter.read(riverArea, riverArea_vectorField);
			MBR.expandToInclude(riverArea_vectorField.getMBR());

			// tidal areas
			URL tidalArea = HoltsCreek.class.getResource(waterbody);
			ShapeFileImporter.read(tidalArea, tidal_vectorField);
			MBR.expandToInclude(tidal_vectorField.getMBR());

			// set up the river's network
			river_Network.createFromGeomField(river_vectorField);

			// color raster / competition map
			InputStream is = new FileInputStream(rasterFile);
			ArcInfoASCGridImporter.read(is, GridDataType.INTEGER, colorRaster_GridField);
			MBR.expandToInclude(colorRaster_GridField.getMBR());
			rasterHeight = colorRaster_GridField.getGridHeight();
			rasterWidth = colorRaster_GridField.getGridWidth();

			// plot grid
			ObjectGrid2D plotGrid = new ObjectGrid2D(rasterWidth, rasterHeight);
			plotGrid_GridField.setGrid(plotGrid);
			MBR.expandToInclude(plotGrid_GridField.getMBR());

			// set MBR for all GeomVectorFields
			initialPlant_vectorField.setMBR(MBR);
			reproducingPlants_vectorField.setMBR(MBR);

			river_vectorField.setMBR(MBR);
			tidal_vectorField.setMBR(MBR);
			riverArea_vectorField.setMBR(MBR);
			boundary_vectorField.setMBR(MBR);
			colorRaster_GridField.setMBR(MBR);
			plotGrid_GridField.setMBR(MBR);

			/* ------------------------
 			* Pixel height has to be set or else the raster grid will be off alignment.
 			* ------------------------ */
			colorRaster_GridField.setPixelHeight(1.0); 
			colorRaster_GridField.setPixelWidth(1.0);

			plotGrid_GridField.setPixelHeight(1.0);
			plotGrid_GridField.setPixelWidth(1.0);

			/* Insert the river_vectorField geometries into a GeometryCollection (MultiLineString)
				for distance sorting, see Plant.java */
			Bag r_bag = river_vectorField.getGeometries();
			LineString[] r_arr = new LineString[r_bag.size()];
			for (int i = 0, s = r_bag.size(); i < s; i++)
			{
				r_arr[i] = (LineString) ( (MasonGeometry) r_bag.get(i) ).getGeometry();
			}
			river_Geometries = new MultiLineString(r_arr, factory);

			/* Insert the riverArea geometries into a GeometryCollection (MultiPolygon) */
			Bag ra_bag = riverArea_vectorField.getGeometries();
			Polygon[] ra_arr = new Polygon[ra_bag.size()];
			for (int i = 0, s = ra_bag.size(); i < s; i++)
			{
				ra_arr[i] = (Polygon) ( (MasonGeometry) ra_bag.get(i) ).getGeometry();
			}
			riverArea_Geometries = new MultiPolygon(ra_arr, factory);

			/* Insert the waterBody geometries into a GeometryCollection (MultiPolygon) for
				distance sorting, see Plant.java */
			Bag t_bag = tidal_vectorField.getGeometries();
			Polygon[] t_arr = new Polygon[t_bag.size()];
			for (int i = 0, s = t_bag.size(); i < s; i++)
			{
				t_arr[i] = (Polygon) ( (MasonGeometry) t_bag.get(i) ).getGeometry();
			}
			tidal_Geometries = new MultiPolygon(t_arr, factory);

			tidal_Boundary = tidal_Geometries.getBoundary(); /* Take the boundary of these geometries so
				we can find how far the seed is to the river in Plant.java. */
			boundary_vectorField.addGeometry(new MasonGeometry(tidal_Boundary));

			/*
				Read in Dr. Griffith's seed floatation data, ceiling it to whole integers.
				Note: Perhaps ceiling is not the most scientific method of integer conversion.
			*/
			Scanner s = new Scanner(new File(seedFloatTimesFile));
			int index = 0;
			while(s.hasNextDouble())
			{
				seedFloatTimes[index++] = (int) Math.ceil( s.nextDouble() );
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Drops the plant populations from the pampoint shapefiles into Holts Creek. These plants
	 * are adults (they have a modified constructor). The are dropped in a uniform field around the
	 * maternal plant.
	 * @author Michael Crawford
	 */
	private void setupInitialPlantPopulations()
	{
		Bag jointvetch_Bag = new Bag(initialPlant_vectorField.getGeometries());
		// random.setSeed(0);
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
		random.setSeed(seed());
	}

	/**
	 * Schedule each special environment once in the future, at the
	 * next appropriate time. When it wakes up, the Environment object
	 * will be responsible for re-scheduling itself in future years.
	 * @author Michael Crawford
	 */
	private void setupEnvironment()
	{
		schedule.scheduleOnce(
			Environment.instance().getClockTimeForNextNewYearDate(), Environment.instance());
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

	double getSeedBankRate() {
		return seedBankRate;
	}

}
