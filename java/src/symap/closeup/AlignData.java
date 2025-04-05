package symap.closeup;

import util.ErrorReport;

/********************************************
 * Input 2 seqs and a type (NT, AA, AAframe)
 * The caller may get: getAlignSeq1, getAlignSeq2, matchSeq 
 */
public class AlignData {
	public static boolean TRACE=false;
	public static final int NT=0;
	public static final int AA=1;
	public static final int AAframe=2;
	
	public static final String  gapStr = "-";
	public static final char    gapCh  = '-';
	public static final char    stopCh = '*';
	private static final String AA_POS = "+"; // aa pos sub
	private static final String AA_NEG = " "; // aa neg sub
	
	private String alignSeq1="", alignSeq2="", matchSeq="";
	private int nEndAlign=0, cntMatch=0, startOffset=0, cntGap=0, cntMis=0, cntTrimS=0, cntTrimE=0;
	private boolean isNT=true;
	private int alignType=0;
	private String traceMsg="", trimMsg=null;
	
	public AlignData() {}
	
	public boolean align(int type, String seq1, String seq2, boolean bTrim, String traceMsg) {
		alignType=type;
		isNT = (alignType==NT); 
		this.traceMsg = traceMsg;
		
		AlignPair ap = new AlignPair();
		if (alignType==AAframe) {
			
		}
		else {
			ap.DPalign(seq1, seq2, isNT);
			alignSeq1 = ap.getAlignResult1();
			alignSeq2 = ap.getAlignResult2();
			
			if (bTrim) trimEnds();
			
			if (isNT) 	computeMatchNT();
			else 		computeMatchAA();
		}
		
		return true;
	}
	public String getAlignSeq1() { return alignSeq1;}
	public String getAlignSeq2() { return alignSeq2;}
	public String getAlignMatch() { return matchSeq;}
	
	public String getScore() 	{// CAS563 more informative header
		double sim = ((double) cntMatch/(double) alignSeq1.length())*100.0;
		String s =  String.format("%s %.1f", " DP %Id", sim);
		String m =  String.format("Match %,d", cntMatch);
		String mm = String.format("Mismatch %,d", cntMis);
		String g =  String.format("Gap %,d", cntGap);
		String len =  String.format("Len %,d",  alignSeq1.length());
		
		String t = (cntTrimS>0 || cntTrimE>0) ? "Trim " + cntTrimS + "," + cntTrimE : "";
		return String.format("%s  %s  %s  %s  %s  %s", s, len, m, mm, g, t);
	}
	public int getStartOffset() {return startOffset;}
	
	// Trim non-match off ends (this is different from TCW trimming gap overhangs)
	private void trimEnds() {
		try {
			String s1 = alignSeq1.toUpperCase();
			String s2 = alignSeq2.toUpperCase();
			
			int len=alignSeq1.length()-1;
			
			int start=0, end=len, gapS1=0, gapE1=0, gapS2=0, gapE2=0;
			
			for (int i=0; i<=len; i++) {
				if (s1.charAt(i)!=s2.charAt(i)) {
					start++; cntTrimS++;
					if (s1.charAt(i)==gapCh) gapS1++;
					if (s2.charAt(i)==gapCh) gapS2++;
				}
				else break;
			}
			for (int i=len; i>=0; i--) {
				if (s1.charAt(i)!=s2.charAt(i)) {
					end--; cntTrimE++;
					if (s1.charAt(i)==gapCh) gapE1++;
					if (s2.charAt(i)==gapCh) gapE2++;
				}	
				else break;
			}
			startOffset=start;
			
			if (start>0 || end<len) {
				if (TRACE) {
					trimMsg = String.format("Trim: %2d (%d,%d) %2d (%d,%d) %4d", 
							cntTrimS, gapS1, gapS2, cntTrimE, gapE1, gapE2, len);
				}
				
				alignSeq1 = alignSeq1.substring(start, end+1);
				alignSeq2 = alignSeq2.substring(start, end+1);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Trim ends"); }
	}
	/*****************************************************************/
	private void computeMatchNT() {
		byte [] q = alignSeq1.toUpperCase().getBytes();
		byte [] t = alignSeq2.toUpperCase().getBytes();
		char ret[] = new char[q.length];
		cntMatch=0;
		for (int i = 0;  i < q.length;  i++) {
			if (q[i] == t[i]) {
				ret[i] = '|';
				cntMatch++;
			}
			else {
				ret[i] = ' ';
				if (q[i]!=gapCh && t[i] != gapCh) cntMis++;
			}
			if (q[i]==gapCh || t[i] == gapCh) cntGap++;
		}
		matchSeq = new String(ret);
		
		if (TRACE && trimMsg!=null) {
			System.out.format("%-32s %-80s %s\n", trimMsg, traceMsg, getScore());
		}
	 }
	 private void computeMatchAA() { 
		char gapCh = '-';
		String gapStr = "-";
		int len = alignSeq1.length(); // both same length because are padded for alignment
		int s1=1, e1=len, s2=1, e2=len, nStartAlign=0;
		boolean hasStart=false, isGap=false, doSeq1=false, doSeq2=false;
		StringBuffer sb = new StringBuffer (len);
			
		// find last non-gap match from end. Doing this check fixes problem with
		// ------xxxxxxx
		// xxxxxx-xxxxx 
		if (alignSeq1.endsWith(gapStr)) doSeq2=true;
		if (alignSeq2.endsWith(gapStr)) doSeq1=true;
		if (doSeq1 && doSeq2) System.err.println("Can't happen: " + alignSeq1.substring(len-5,len) + " " + alignSeq2.substring(len-5,len));
		for (int i=len-1; i>0; i--) {
    			if (doSeq1 && alignSeq2.charAt(i)==gapCh) e1--;
    			else if (doSeq2 && alignSeq1.charAt(i)==gapCh) e2--;
    			else {nEndAlign=i+1; break;}
		}
		if (doSeq1) e1++;
		if (doSeq2) e2++;
		
		// find first non-gap match from beginning
		doSeq1=doSeq2=false;
		if (alignSeq1.startsWith(gapStr)) doSeq2=true;
		if (alignSeq2.startsWith(gapStr)) doSeq1=true;
		if (doSeq1 && doSeq2) System.err.println("Can't happen: " + alignSeq1.substring(0,5) + " " + alignSeq2.substring(0,5));

		int open=-10, extend=-1;
		
    	for (int i=0; i<len; i++) {
    		char c1 = alignSeq1.charAt(i);
    		char c2 = alignSeq2.charAt(i);
    		if (!hasStart) { // executes until first non-gap match
    			if (c1==gapCh || c2==gapCh) {
    				sb.append(" ");
    				continue;
    			}
    			else {
    				hasStart=true; 
    				if (doSeq1) s1=i+1;
    				if (doSeq2) s2=i+1;
    				nStartAlign=i;
    			}
    		}
			int s=0;
    		if (c1==gapCh || c2==gapCh) {
    			sb.append(" ");
    			if (isGap) s=open;
    			else {s=extend; isGap=true;}
    		}
    		else {
    			String m=" ";
    			if (isNT) { 
    				if (c1==c2) m=c1+"";
    			}
    			else {
    				m = getSubChar(c1,  c2);
    			}
    			sb.append(m);
    			isGap=false;
    			if (i<nEndAlign) cntMatch += s;
    		}
    	}
    	matchSeq = sb.toString(); 
	}
	 private static String getSubChar(char a1, char a2) {
		if (a1==a2) return a1+"";
		if (a1==gapCh || a2==gapCh) return " ";
		if (a1==' ' || a2==' ') return " "; // extended ends
		
		int score = AlignPair.getBlosum(a1, a2);
		if (score>0) 	return AA_POS;
		else 			return AA_NEG;
	}
}
