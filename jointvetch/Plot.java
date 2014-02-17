package jointvetch;

import java.util.*;
import sim.field.grid.IntGrid2D;

public class Plot
{
	private HoltsCreek hc;
	private Environment e;

	public enum PlotType { UNKNOWN, TRANSIENT, MEDIOCRE, THRIVING, DEAD };

	public static final int YEARS_BEFORE_JUDGEMENT = 3;
	public static final int CARRYING_CAPACITY_THRESHOLD = 40;
	public static final int TRANSIENT_THESHOLD = 10;

	private int rasterColor;
	private double germRate, survRate;
	private int fecundity;

	private double environmentalFactor;

	private int year;
	private int instantiationYear;
	private int skipYears;
	private ArrayList<Integer> historyCounts;

	private int population;
	private int culled;

	public Plot(int x, int y)
	{
		hc = HoltsCreek.instance();
		e = Environment.instance();

		rasterColor = ((IntGrid2D) hc.colorRaster_GridField.getGrid()).get(x,y);
		population = 0;

		historyCounts = new ArrayList<Integer>();
		instantiationYear = e.getYear();
		year = instantiationYear;
		for (int i=0; i<year; i++)
		{
			historyCounts.add(0);
		}
		skipYears = instantiationYear + YEARS_BEFORE_JUDGEMENT;

		germRate = Parameters.getGermRate(rasterColor);
		survRate = Parameters.getSurvRate(rasterColor);
		fecundity = Parameters.getFecundity(rasterColor);
	}

	public void registerYearEnd()
	{
		historyCounts.add(population-culled);
		population = 0;
		culled = 0;
		year++;
	}

	public double getCarryingCapacityAdjustment()
	{
		double adjusted;
		double naive = (double) population;
		if (naive > Parameters.CARRYING_CAPACITY)
		{
			adjusted = Parameters.CARRYING_CAPACITY;
		}
		else
		{
			adjusted = naive;
		}
		double n = adjusted/naive;

		return (n > 1.0 ? 1 : n);
	}

	public void registerNewPlant() {
		population++;
	}

	public void deregisterPlant() {
		culled++;
	}

	public double getGerminationProb() {
		double n = germRate * e.getEnvironmentalStochasticity();
		return (n > 1.0 ? 1 : n);
	}

	public double getSurvivalProb() {
		double n = survRate * e.getEnvironmentalStochasticity();
		return (n > 1.0 ? 1 : n);
	}

	public int getFecundity() {
		int n = (int) (fecundity * e.getEnvironmentalStochasticity());
		return n;
	}

	public PlotType getPlotType()
	{
		int mostRecentPopulation = historyCounts.get(year-1); // year is 1 ahead of the historyCounts.

		if (year < skipYears) 
		{
			return PlotType.UNKNOWN;
		}
		else if (mostRecentPopulation == 0)
		{
			return PlotType.DEAD;
		}
		else
		{
			double sum = 0;
			int count = 0;
			for (int i=0; i<historyCounts.size(); i++)
			{
				if (i >= skipYears && i >= historyCounts.size() - 3) // counting the last three years
				{
					sum += historyCounts.get(i);
					count++;
				}
			}
			double avg = sum / count;
			if (avg > CARRYING_CAPACITY_THRESHOLD)
			{
				return PlotType.THRIVING;
			}
			else if (avg < TRANSIENT_THESHOLD)
			{
				return PlotType.TRANSIENT;
			}
			else
			{
				return PlotType.MEDIOCRE;
			}
		}
	}

	public ArrayList<Integer> getHistoryCounts()
	{
		return historyCounts;
	}

	public int getColor()
	{
		return rasterColor;
	}

}
