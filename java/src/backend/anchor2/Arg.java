package backend.anchor2;

import backend.Constants;
import symap.Globals;

// Constants and input Parameters for backend.anchor2 only
public class Arg {
	protected static boolean PRT_STATS=Constants.PRT_STATS; 
	protected static boolean TRACE = Globals.TRACE; // for developers
	protected static boolean WRONG_STRAND_EXC = Constants.WRONG_STRAND_EXC;
	protected static boolean WRONG_STRAND_PRT = Constants.WRONG_STRAND_PRT;
	
	// input args; set in AnchorMain2 from Mpair from Parameters
	protected static int g0MinBases=600, gnMinIntron=300, gnMinExon=100;
	protected static boolean EE=true, EI=true, En=false, II=false, In=false;
	
	// other shared
	protected static char UNK='?', MAJOR='+', MINOR='*', FILTER='F', SPLIT='S', PILE='P'; // HitPair flags
	protected static String FLAG_DESC = "? unknown; + major; * minor; F filter; S split; P pile";
		
	protected static final int NONE = -1;
	protected static final int T=0, Q=1, TQ=2;
	protected static final String [] side = {"T", "Q", "TQ"};
	
	protected static final int typeUnk=0, type0=1, type1t=2, type1q=3, type2=4;
	protected static final String [] strType = {"Unk","G0", "G1t", "G1q", "G2"};
	
	protected static final int plantIntronLen =  2500, plantGeneLen =  50000; // plants
	protected static final int mamIntronLen   =  5000, mamGeneLen   = 100000; // mammalians
	protected static int useIntronLen = plantIntronLen; // if not annotation, will not ever be set to mammals
	protected static int useGeneLen =   plantGeneLen;  
	protected static int maxBigGap = 	(useGeneLen/4), maxHugeGap=(useGeneLen/2);
	
	/***** Constants/Heuristics in code ****/
	// HitPair determine malFormed pairs
	protected static final double lowExon = 5.0; 	  // Accept pair if either exon coverage is this good
	protected static final double perHitOfLenWithLow = 5.0;  // Otherwise, if length of subhits/hit.end-hit.start<this, malformed
	protected static final double perHitOfLenWithExon = 1.5;
	
	// HitPair
	protected static final double fudge=0.001;		  // ExonScore great enough to be called exon, e.g. EE type
	
	// GrpPairPile
	protected static final double bigOlap = 0.4; 
	protected static final double fudgeScore = 70.0;
	protected static final int    fudgeLen = 5000; 
	protected static int topN;
	
	/***** functions *****/
	protected static void setLen(int intron, int gene) {
		useIntronLen = intron;
		useGeneLen = gene;
		maxBigGap = useGeneLen/4;
		maxHugeGap = useGeneLen/2;
	}
	protected static int getDiff(int ix, boolean lastIsEQ, Hit ht0, Hit ht1) {
		if (ht0==null) return 0;
		return (lastIsEQ) ? Math.abs(ht1.start[ix] - ht0.end[ix]) 
                          : Math.abs(ht0.start[ix] - ht1.end[ix]);
	}
	// negative is the gap between (s1,e1) and (s2, e2); positive is the overlap
	// used for overlapping hits
	protected static int olapOrGap(int s1, int e1, int s2, int e2) {
		return Math.min(e1,e2) - Math.max(s1,s2); // CAS548 rm +1
	}
	// used to score hits against exons where only overlap is needed
	protected static int olapOnly(int s1, int e1, int s2, int e2) {
		int gap = Math.min(e1,e2) - Math.max(s1,s2);
		if (gap<0) return 0;
		return gap;
	}
	
	protected static double baseScore(Hit ht) {
		return ht.maxLen*(ht.id/100.0);
	}
	protected static double baseScore(int X, HitPair ht) {
		return ht.sumCov[X]*(ht.xSumId/100.0);
	}
	protected static double baseScore(HitPair ht) {
		return ht.xMaxCov * (ht.xSumId/100.0);
	}
	// piles
	protected static boolean isPileSet() {
		return Arg.EE || Arg.EI || Arg.En || Arg.II || Arg.In;
	}
	protected static boolean isGoodPileHit(HitPair cht) {
		if (cht.bBothGene)
			return (Arg.EE && cht.bEE) || (Arg.EI && cht.bEI) || (Arg.II && cht.bII);
		if (cht.bEitherGene) 
			return (Arg.En && cht.bEn) || (Arg.In && cht.bIn);
		return false;
	}
	
	protected static boolean isEqual(String sign) {
		return sign.equals("+/+") || sign.equals("-/-");
	}
	protected static int getInt(String i) { 
		try {
			int x = Integer.parseInt(i);
			return x;
		}
		catch (Exception e) {return -1;}
	}
	/* For trace */
	protected static String charTF(boolean b) {
		return (b) ? "T" : "F";
	}
	protected static String int2Str(int diff) {
		String sdiff = (Math.abs(diff)>=1000) ? (diff/1000) + "k" : diff+"";
		sdiff = (Math.abs(diff)>=1000000) ? (diff/1000000) + "M" : sdiff;
		return sdiff;
	}
	protected static String side(int ix) {
		if (ix<0) return "N";
		if (ix<=2) return side[ix];
		else return "?";
	}
	
	protected static void prt(String msg) {System.out.println(msg);}
	protected static void sprt(String msg) {if (PRT_STATS) System.out.println("  " + msg);}
	protected static void tprt(String msg) {if (Globals.TRACE) System.out.println("TR " + msg);}
	protected static void tprt(int num, String msg) {if (Globals.TRACE) System.out.format("TR %,10d %s\n",num, msg);}
	protected static void tdie(String msg) {if (Globals.TRACE) System.out.println(msg); System.exit(-1);}
}
