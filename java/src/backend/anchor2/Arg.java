package backend.anchor2;

import backend.Constants;

// Constants and input Parameters for backend.anchor2 only
public class Arg {
	protected static boolean PRT_STATS=Constants.PRT_STATS; 
	
	protected static final int T=0, Q=1, X=2;
	protected static final String [] side = {"T", "Q", "X"};
	
	protected static final int minIntronLen =  2500, minGeneLen =  50000; // plants
	protected static final int maxIntronLen =  5000, maxGeneLen = 100000; // mammalians
	protected static int [] useIntronLen = {minIntronLen,minIntronLen}; // Q & T should be the same
	protected static int [] useGeneLen =   {minGeneLen,minGeneLen}; // GeneLen currently is not used; values set in AnchorMain
	
	// input args; set in AnchorMain2 from Mpair from Parameters
	protected static int g0MinBases=400, gnMinExon=0, gnMinIntron=200;
	protected static boolean EE=true, EI=true, En=false, II=false, In=false;
	
	protected static int getDiff(int ix, boolean lastIsEQ, Hit ht0, Hit ht1) {
		if (ht0==null) return 0;
		return (lastIsEQ) ? Math.abs(ht1.start[ix] - ht0.end[ix]) 
                          : Math.abs(ht0.start[ix] - ht1.end[ix]);
	}
	protected static double baseScore(Hit ht) {
		return ht.blen*(ht.id/100.0);
	}
	protected static double baseScore(int X, HitCluster ht) {
		return ht.sumLen[X]*(ht.pId/100.0);
	}
	protected static double baseScore(HitCluster ht) {
		return ht.bLen * (ht.pId/100.0);
	}
	protected static boolean isGoodHit(HitCluster cht) {
		if (cht.bBothGene)
			return (Arg.EE && cht.bEE) || (Arg.EI && cht.bEI) || (Arg.II && cht.bII);
		if (cht.bEitherGene) 
			return (Arg.En && cht.bEn) || (Arg.In && cht.bIn);
		return false;
	}
}
