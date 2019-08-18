package dotplot;

import java.util.Observable;
import java.util.Arrays;

public class ScoreBounds extends Observable implements DotPlotConstants {

    private double evalues[][] = {new double[NUM_HIT_TYPES],new double[NUM_HIT_TYPES]};
    private double pctids[][]  = {new double[NUM_HIT_TYPES],new double[NUM_HIT_TYPES]};

    public ScoreBounds() {
	defaults();
    }

    public boolean equals(Object obj) {
	if (obj instanceof ScoreBounds) {
	    ScoreBounds b = (ScoreBounds)obj;
	    return Arrays.equals(b.evalues[MIN],evalues[MIN]) && Arrays.equals(b.evalues[MAX],evalues[MAX]) &&
		Arrays.equals(b.pctids[MIN],pctids[MIN]) && Arrays.equals(b.pctids[MAX],pctids[MAX]);
	}
	return false;
    }

    private void defaults() {
	Arrays.fill(evalues[MIN],ANY_EVALUE);
	Arrays.fill(evalues[MAX],NO_EVALUE);
	Arrays.fill(pctids[MIN], NO_PCTID);
	Arrays.fill(pctids[MAX], ANY_PCTID);
    }

    public void setDefaults() {
	if (!equals(new ScoreBounds())) {
	    defaults();
	    update(true,true);
	}
    }

    public void condSetBounds(Hit hit) {
	condSetEvalue(hit.getType(),hit.getEvalue(),hit.getEvalue());
	condSetPctid(hit.getType(),hit.getPctid(),hit.getPctid());
    }

    public void condSetEvalue(int hitType, double min, double max) {
	update(min > 0 && min < evalues[MIN][hitType] && setEvalue(MIN,hitType,min),
	       max > evalues[MAX][hitType] && setEvalue(MAX,hitType,max));
    }	

    public void condSetPctid(int hitType, double min, double max) {
	update(min > 0 && min < pctids[MIN][hitType] && setPctid(MIN,hitType,min),
	       max > pctids[MAX][hitType] && setPctid(MAX,hitType,max));
    }

    public double getEvalue(int type, int hitType) {
	return evalues[type][hitType];
    }

    public double getPctid(int type, int hitType) {
	return pctids[type][hitType];
    }

    private boolean setEvalue(int type, int hitType, double s) {
	if (s != evalues[type][hitType]) {
	    evalues[type][hitType] = s;
	    return true;
	}
	return false;
    }

    private boolean setPctid(int type, int hitType, double s) {
	if (s != pctids[type][hitType]) {
	    pctids[type][hitType] = s;
	    return true;
	}
	return false;
    }

    private void update(boolean c1, boolean c2) {
	if (c1 || c2) {
	    setChanged();
	    notifyObservers();
	}
    }
}
