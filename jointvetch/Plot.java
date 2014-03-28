package jointvetch;

import java.util.*;
import sim.field.grid.IntGrid2D;

class Plot
{
	private HoltsCreek hc;
	private Environment e;

	enum PlotType { UNKNOWN, TRANSIENT, MEDIOCRE, THRIVING, DEAD };

	private static final int JUDGEMENT_GRACE_PERIOD = 3;
	private static final int JUDGEMENT_WINDOW = 5;
	private static final int CARRYING_CAPACITY_THRESHOLD = 40;
	private static final int TRANSIENT_THESHOLD = 10;

	private int rasterColor;
	private double germRate, survRate;
	private int fecundity;

	private int year;
	private int instantiationYear;
	private int skipYears;
	private List<Integer> historyCounts;

	private int population, culled;
	private double fecundityCompetitionModifier;

private int carryingCapacity;

	Plot(int x, int y)
	{
		hc = HoltsCreek.instance();
		e = Environment.instance();

		rasterColor = ( (IntGrid2D) hc.redRaster_gf.getGrid() ).get(x,y);
		population = 0;

		historyCounts = new ArrayList<Integer>();
		instantiationYear = year = e.getYear();
		for (int i = 0; i < year; i++) historyCounts.add(0);
		skipYears = instantiationYear + JUDGEMENT_GRACE_PERIOD;

		survRate = Parameters.getSurvRate(rasterColor);
		fecundity = Parameters.getFecundity(rasterColor);
		carryingCapacity = Parameters.getCarryingCapacity(rasterColor);
	}

	void registerYearEnd(int year)
	{
		historyCounts.add(population-culled);
		population = culled = 0;
		this.year = year;
	}

	double getCarryingCapacityAdjustment()
	{
		double adjusted;
		double naive = (double) population;
		if (naive > carryingCapacity)
			adjusted = carryingCapacity;
		else
			adjusted = naive;

		fecundityCompetitionModifier = adjusted/naive;

		assert (fecundityCompetitionModifier <= 1.0);
		return (fecundityCompetitionModifier);
	}

	void registerNewPlant() {
		population++;
	}

	void deregisterPlant() {
		culled++;
	}

	double getSurvivalProb() {
		double n = survRate * e.getEnvironmentalStochasticity() * Math.sqrt(Parameters.getAdjustment());
		return (n > 1.0 ? 1.0 : n);
	}

	int getFecundity() {
		int n = (int) (fecundity * e.getEnvironmentalStochasticity() * Math.sqrt(Parameters.getAdjustment()) * fecundityCompetitionModifier);
		return n;
	}

	PlotType getPlotType()
	{
		if (year < skipYears || year == 0) 
		{
			return PlotType.UNKNOWN;
		}
		else if (year != 0 && historyCounts.get(historyCounts.size()-1) == 0)
		{
			return PlotType.DEAD;
		}
		else
		{
			double sum = 0;
			int count = 0;
			for (int i = 0, s = historyCounts.size(); i < s; i++)
			{
				if (i >= skipYears && i >= historyCounts.size() - JUDGEMENT_WINDOW) // counting the last three years
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

	List<Integer> getHistoryCounts()
	{
		return historyCounts;
	}

	int getColor()
	{
		return rasterColor;
	}

}
