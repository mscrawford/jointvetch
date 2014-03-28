package jointvetch;

import java.io.*;
import java.util.*;
import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.filechooser.FileSystemView;
import java.awt.Graphics;
import javax.swing.JFileChooser;
import java.io.File;
import sim.engine.*;
import sim.util.*;
import sim.io.geo.*;
import sim.field.geo.GeomVectorField;
import sim.portrayal.geo.GeomPortrayal;
import sim.portrayal.geo.GeomVectorFieldPortrayal;
import sim.display.GUIState;
import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.portrayal.grid.ValueGridPortrayal2D;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.ParseException;
import sim.portrayal.grid.FastValueGridPortrayal2D;
import sim.util.gui.SimpleColorMap;

/**
 * GUI interface of HoltsCreek.
 * @author Mike
 */

class HoltsCreekWithUI extends GUIState
{
	private static final int HEIGHT = 700; 
	private static final int WIDTH = 650;

	static Display2D display;
	static JFrame displayFrame;

	private static GeomVectorFieldPortrayal river_p = new GeomVectorFieldPortrayal();
	private static GeomVectorFieldPortrayal tidal_p = new GeomVectorFieldPortrayal();
	private static GeomVectorFieldPortrayal tidalBoundary_p = new GeomVectorFieldPortrayal();   
	private static GeomVectorFieldPortrayal reproducingPlants_p = new GeomVectorFieldPortrayal();
	private static FastValueGridPortrayal2D grid_p = new FastValueGridPortrayal2D();

	public HoltsCreekWithUI(String[] args) throws ParseException
	{
		super(HoltsCreek.instance(System.currentTimeMillis(), args));
	}
	
	public static void main(String[] args)
	{
		HoltsCreekWithUI hcGUI = null;
		
		try {
			hcGUI = new HoltsCreekWithUI(args);
		} catch (ParseException ex) {
			ex.printStackTrace();
		}
		
		Console c = new Console(hcGUI);
		c.setVisible(true);
		hcGUI.start();
	}

	public void start()
	{
		super.start();
		setupPortrayals();
	}
	
	static void setupPortrayals()
	{
		HoltsCreek hc = HoltsCreek.instance();

		river_p.setField(hc.riverLines_vf);
		river_p.setPortrayalForAll(new GeomPortrayal(Color.BLUE, .2, true));

		tidal_p.setField(hc.tidal_vf);
		tidal_p.setPortrayalForAll(new GeomPortrayal(Color.GRAY, 1, true));

		tidalBoundary_p.setField(hc.boundary_vf);
		tidalBoundary_p.setPortrayalForAll(new GeomPortrayal(Color.BLACK, .2, true)); 

		reproducingPlants_p.setField(hc.reproducingPlants_vf);
		reproducingPlants_p.setPortrayalForAll(new GeomPortrayal(Color.RED, .1, true));

		grid_p.setField(hc.redRaster_gf.getGrid());
		grid_p.setMap(new SimpleColorMap(0, 255, Color.black, Color.white));

		display.reset();
        display.setBackdrop(Color.WHITE);
		display.repaint();
	}

	public void init(Controller c)
	{
		super.init(c);
		display = new Display2D(WIDTH, HEIGHT, this);		

		display.attach(tidal_p, "Tidal areas", true);
		
		display.attach(tidalBoundary_p, "Tidal boundary", true);
		display.attach(reproducingPlants_p, "Reproducing Plants", true);
		display.attach(grid_p, "My Grid Layer");
		display.attach(river_p, "Rivers", true);

		displayFrame = display.createFrame();
		c.registerFrame(displayFrame);
		displayFrame.setVisible(true);
		display.removeListeners();	
	}

	static void takeSnapshot()
	{
		String pathname = "/users/michaelcrawford/Desktop/runs/" + 
			Integer.toString(new Integer(Environment.instance().getYear()));
		System.out.println(pathname);
		JFileChooser jfc = new JFileChooser();
		try 
		{
			jfc.getFileSystemView().createFileObject(pathname).createNewFile();
			display.takeSnapshot(jfc.getFileSystemView().createFileObject(pathname), 1); 
		} catch (Exception e) {
			System.out.println("Screenshot fail");
		}
	}

	public void quit()
	{
		super.quit();
		if (displayFrame != null) { 
			displayFrame.dispose(); 
		}
		displayFrame = null;
		display = null;
	}
}
