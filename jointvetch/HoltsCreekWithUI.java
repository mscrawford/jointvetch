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

/**
 * GUI interface of HoltsCreek.
 * @author Mike
 */

public class HoltsCreekWithUI extends GUIState
{
	private static final int HEIGHT = 700; 
	private static final int WIDTH = 650;

	public static Display2D display;
	public static JFrame displayFrame;

	private static GeomVectorFieldPortrayal riverPortrayal = new GeomVectorFieldPortrayal();
	private static GeomVectorFieldPortrayal tidalPortrayal = new GeomVectorFieldPortrayal();
	private static GeomVectorFieldPortrayal tidalBoundaryPortrayal = new GeomVectorFieldPortrayal();   
	private static GeomVectorFieldPortrayal reproducingPlantsPortrayal = new GeomVectorFieldPortrayal();

	public HoltsCreekWithUI(String[] args) throws ParseException
	{
		super(HoltsCreek.instance(System.currentTimeMillis(), args));
	}
	
	public static void main(String[] args)
	{
		HoltsCreekWithUI worldGUI = null;
		
		try {
			worldGUI = new HoltsCreekWithUI(args);
		} catch (ParseException ex) {
			ex.printStackTrace();
		}
		
		Console c = new Console(worldGUI);
		c.setVisible(true);
		worldGUI.start();
	}

	public void start()
	{
		super.start();
		setupPortrayals();
	}
	
	public static void setupPortrayals()
	{
		HoltsCreek world = HoltsCreek.instance();

		riverPortrayal.setField(world.river_vectorField);
		riverPortrayal.setPortrayalForAll(new GeomPortrayal(Color.BLUE, .2, true));

		tidalPortrayal.setField(world.tidal_vectorField);
		tidalPortrayal.setPortrayalForAll(new GeomPortrayal(Color.GRAY, 1, true));

		tidalBoundaryPortrayal.setField(world.boundary_vectorField);
		tidalBoundaryPortrayal.setPortrayalForAll(new GeomPortrayal(Color.BLACK, .2, true)); 

		reproducingPlantsPortrayal.setField(world.reproducingPlants_vectorField);
		reproducingPlantsPortrayal.setPortrayalForAll(new GeomPortrayal(Color.RED, .1, true));

		display.reset();
        display.setBackdrop(Color.WHITE);
		display.repaint();
	}

	public void init(Controller c)
	{
		super.init(c);
		display = new Display2D(WIDTH, HEIGHT, this);		

		display.attach(tidalPortrayal, "Tidal areas", true);
		display.attach(riverPortrayal, "Rivers", true);
		display.attach(tidalBoundaryPortrayal, "Tidal boundary", true);
		display.attach(reproducingPlantsPortrayal, "Reproducing Plants", true);

		displayFrame = display.createFrame();
		c.registerFrame(displayFrame);
		displayFrame.setVisible(true);
		display.removeListeners();	
	}

	public static void takeSnapshot()
	{
		String pathname = "/users/michaelcrawford/Desktop/runs/" + Integer.toString(new Integer(Environment.instance().getYear()));
		System.out.println(pathname);
		JFileChooser jfc = new JFileChooser();
		try 
		{
			jfc.getFileSystemView().createFileObject(pathname).createNewFile();
			display.takeSnapshot(jfc.getFileSystemView().createFileObject(pathname), 1); 
		} 
		catch (Exception e)
		{
			System.out.println("Screenshot fail");
		}
	}

	public void quit()
	{
		super.quit();
		if (displayFrame != null) 
		{ 
			displayFrame.dispose(); 
		}
		displayFrame = null;
		display = null;
	}
}
