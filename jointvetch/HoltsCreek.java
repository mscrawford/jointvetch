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
public class HoltsCreek extends SimState
{
	private static HoltsCreek theInstance;

	/* data files */
	private static final String pampoint = "/data/Pampoint/Pampoint_All.shp";
	// private static final String pampoint = "/data/testClustersNoHydrochory/testClusters.shp";
	// private static final String pampoint = "/data/testClustersWithHydrochory/testClustersWithHydrochory.shp";

	private static final String flowline = "/data/riverFlow/riverFlow.shp";
	private static final String waterbody = "/data/Waterbody/waterbody.shp";
	private static final String rasterFile = "data/waterbody_raster/waterbody_raster.asc"; /* competition */

	private static final String seedFloatTimesFile = "data/seedFloatTimes.txt";

	public GeometryFactory factory = new GeometryFactory();
	private Envelope MBR = new Envelope();

	/* plant geometries */
	public GeomVectorField initialPlant_vectorField = new GeomVectorField();
	public GeomVectorField reproducingPlants_vectorField = new GeomVectorField(); // used by DBSCAN
	public GeomVectorField plants_vectorField = new GeomVectorField(); // used for carrying capacity

	/* geographic geometries, the network of rivers and tidally innundated landmass */
	public GeomVectorField river_vectorField = new GeomVectorField();
	public GeomVectorField tidal_vectorField = new GeomVectorField();
	public GeomVectorField boundary_vectorField = new GeomVectorField();
	public GeomPlanarGraph river_Network = new GeomPlanarGraph(); // the directed graph/network

	/* competition & carrying capacity rasters */
	public GeomGridField colorRaster_GridField = new GeomGridField();
	public GeomGridField plotGrid_GridField = new GeomGridField();

	/* geometries for distance detection */
	public MultiLineString river_Geometries;
	public Geometry river_Boundary;
	public MultiPolygon tidal_Geometries;
	public Geometry tidal_Boundary;

	/* independent variables */
	private double adjustmentFactor;
	private double stochMax;
	private double implantationRate;
	private boolean hydrochoryBool;
	private double seedBankRate;

	/* seed floatation data, see methods */
	public int[] seedFloatTimes = new int[499];

	private static final double PLANT_DROP_DIST_MAX = 2.0; // meters

	/* creating the instance */
	public static synchronized HoltsCreek instance(long seed, String[] args)
	{
		if (theInstance == null)
		{
			theInstance = new HoltsCreek(seed, args);
		}
		return theInstance;
	}

	/* getting the instance */
	public static synchronized HoltsCreek instance()
	{
		if (theInstance == null)
		{
			System.out.println("There is no instance to work on. Wrong constructor being called.");
			System.exit(0);
		}
		return theInstance;
	}

	private HoltsCreek(long seed, String[] args)
	{
		super(seed);

		stochMax = Double.parseDouble(args[0]);
		adjustmentFactor = Double.parseDouble(args[1]);
		hydrochoryBool = Boolean.parseBoolean(args[2]);
		implantationRate = Double.parseDouble(args[3]);
		seedBankRate = Double.parseDouble(args[4]);
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
			plants_vectorField.clear();

			river_vectorField.clear();
			tidal_vectorField.clear();
			boundary_vectorField.clear();

			colorRaster_GridField.clear();
			plotGrid_GridField.clear();

			// joint-vetch Populations
			URL Pampoint = HoltsCreek.class.getResource(pampoint);
			ShapeFileImporter.read(Pampoint, initialPlant_vectorField);
			MBR.expandToInclude(initialPlant_vectorField.getMBR());

			// river network
			URL riverFlow = HoltsCreek.class.getResource(flowline);
			ShapeFileImporter.read(riverFlow, river_vectorField);
			MBR.expandToInclude(river_vectorField.getMBR());

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
			int rasterHeight = colorRaster_GridField.getGridHeight();
			int rasterWidth = colorRaster_GridField.getGridWidth();

			// plot grid
			ObjectGrid2D plotGrid = new ObjectGrid2D(rasterWidth, rasterWidth);
			plotGrid_GridField.setGrid(plotGrid);
			MBR.expandToInclude(plotGrid_GridField.getMBR());

			// set MBR for all GeomVectorFields
			initialPlant_vectorField.setMBR(MBR);
			reproducingPlants_vectorField.setMBR(MBR);
			plants_vectorField.setMBR(MBR);

			river_vectorField.setMBR(MBR);
			tidal_vectorField.setMBR(MBR);
			boundary_vectorField.setMBR(MBR);

			colorRaster_GridField.setMBR(MBR);
			plotGrid_GridField.setMBR(MBR);

			/* Insert the river_vectorField geometries into a GeometryCollection (MultiLineString)
				for distance sorting, see Plant.java */
			Bag r_bag = river_vectorField.getGeometries();
			LineString[] r_arr = new LineString[r_bag.size()];
			for (int i = 0; i < r_bag.size(); i++)
			{
				r_arr[i] = (LineString) ( (MasonGeometry) r_bag.get(i) ).getGeometry();
			}
			river_Geometries = new MultiLineString(r_arr, factory);

			/* Insert the waterBody geometries into a GeometryCollection (MultiPolygon) for
				distance sorting, see Plant.java */
			Bag t_bag = tidal_vectorField.getGeometries();
			Polygon[] t_arr = new Polygon[t_bag.size()];
			for (int i = 0; i < t_bag.size(); i++)
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
				seedFloatTimes[index++] = (int) Math.ceil(s.nextDouble());
			}

		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
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
		random.setSeed(0);
		for (int i=0; i<jointvetch_Bag.size(); i++)
		{
			sim.util.geo.MasonGeometry curPopulation = (sim.util.geo.MasonGeometry) jointvetch_Bag.get(i);
			int popSize = curPopulation.getIntegerAttribute("POPSIZE");
			for (int j=0; j<popSize; j++)
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

	public double getStochMax() {
		return stochMax;
	}

	public double getAdjustmentFactor() {
		return adjustmentFactor;
	}

	public double getImplantationRate() {
		return implantationRate;
	}

	public boolean getHydrochoryBool() {
		return hydrochoryBool;
	}

	public double getSeedBankRate() {
		return seedBankRate;
	}

}
