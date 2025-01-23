package symap.closeup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;

import symap.Globals;
import symap.sequence.Annotation;
import util.ErrorReport;

/*******************************************************
 * Create sequence information associated with TextShowInfo
 * CAS560 was part of SeqData; split off into new file
 */
public class SeqDataInfo {
	
	  // CAS517 - format exon list (e.g. Exon #1:20:50,Exon #1:20:50); CAS531 moved from Utilities
    static public String formatExon(String exonList) {	
    	String exonTag = Globals.exonTag;
    	int rm = exonTag.length();
    	String list="";
    	String [] exons = exonList.split(",");
    	int dist=0, last=0;
    	
    	String exCol = Globals.exonTag.substring(0, Globals.exonTag.indexOf(" "));
    	String [] fields = {"#", exCol + " Coords","Exon", "Intron"}; 
		int [] justify =   {1,    0,    0,     0};
		int nRow = exons.length;
	    int nCol=  fields.length;
	    String [][] rows = new String[nRow][nCol];
	    int r=0, c=0;
	    
	    int x1, x2;
    	for (String x : exons) {
    		String [] y = x.split(":");
    		if (y.length!=3) {System.err.println("Parsing y: " + exonList); return exonList;}
    		
    		if (!y[0].startsWith(exonTag)) System.err.println(exonList + "\n" + y[0]);
    		String n = y[0].substring(rm);
    		try {
    			x1 = Integer.parseInt(y[1]);
    			x2 = Integer.parseInt(y[2]);
    		}
    		catch (Exception e) {System.err.println("Parsing int: " + exonList); return exonList;}
    		
			if (x1>x2) System.err.println("ERROR start>end " + x1 + "," + x2);
		
			rows[r][c++] = n; 
			rows[r][c++] =  String.format("%,d - %,d",x1, x2);
			rows[r][c++] =  String.format("%,d",(x2-x1+1));
			
			if (last>0) {
				dist = Math.abs(last-x1)+1; 
				rows[r][c] = String.format("%,d",dist); 
			}
			else rows[r][c] = "-";
			last = x2;
			
    		r++; c=0;
    	}
    	list = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
    	return list;
    }
    /*************************************************************
     * Methods for TextShowInfo for hits
     */
    /* Format a clustered hit (e.g. 100:200,300:400); hitListStr is order stored in DB, which is by Q
     * CAS531 sorted by start so easier to read and determine overlap or gap
     * CAS548 only called by TextShowInfo for SeqHits.popupDesc to draw hit popup
     * CAS560 add args:
     * 	 aObj   - for ExonVec && if -ii gStart and gEnd 
     *   title  - merge, order, remove
     * 	 bSort  - default&Merge yes, target only, sort by start; so # may be out-of-order 
     *            Order&Remove   no, keep ordered by #; so numbers may be out-of-order
     *   isInv  - reverse hits; used with !bSort since not sorted and would be displayed wrong order
     * always sorted ascending by Q, even if negative strand 
    */
    static protected int cntMergeNeg=0, cntDisorder; // TextShowInfo access this to determine whether to show Merge
    
    static protected String formatHit(int X, String name, Annotation aObj, 
    		String hitListStr, String title, boolean bIsInv, boolean bSort) { 
    	
    	if (hitListStr==null || hitListStr.equals("")) return "hitListStr Error";
    	
    	boolean bMerge = title.startsWith(TextShowInfo.titleMerge);
    	boolean bOrder = title.startsWith(TextShowInfo.titleOrder);
    	
    	String colType;
    	if (bMerge) 					 colType = "Merged subhits";
    	else if (bOrder && X==Globals.T) colType = "Unsorted subhits";
    	else 							 colType = "Sorted subhits";
    	
    	cntMergeNeg=cntDisorder=0;
    	Vector <Annotation> exonVec = (aObj!=null) ? aObj.getExonVec() : null;
    	int s=0, e=0, nOrder=1;
    	
    	String [] tokc = hitListStr.split(",");
    	ArrayList <Hit> hitSort = new ArrayList <Hit> (tokc.length);
    	
    	for (String coords : tokc) {
    		try {
    			String [] y = coords.split(":");
    			s = Integer.parseInt(y[0]);
    			e = Integer.parseInt(y[1]);
    		}
    		catch (Exception err) {System.err.println("Parsing int: " + coords); return "parse error";}
			
			hitSort.add(new Hit(nOrder++, s, e));
    	}
    	if (bSort)      sortForFormat(hitSort);      // target may be out of order; need ordered by start
    	else if (bIsInv) sortForOrder(hitSort, true); // for inverse order
    	
    	int sumTotLen=0, sumTotGap=0, lastE=0, lastS=0;
    	int lastO = (bIsInv) ? -1 : 1;
    	
    	String [] fields = {"#", colType,"Length", "Gap", ""}; 
		int [] justify =   {1,    0,      0,        0,    1};
		int nRow = tokc.length;		
		if (bMerge) nRow++;
	    int nCol=  fields.length;
	    String [][] rows = new String[nRow][nCol];
	    
	    int r=0, c=0, o=0, len=0, gapCol=0;
	    
    	for (int i=0; i<tokc.length; i++) {
    		Hit h = hitSort.get(i);
    		o = h.order;
    		s = h.start;
    		e = h.end;
    		len = e-s+1;
    		
			rows[r][c++] =  o+""; 
			rows[r][c++] =  String.format("%,d - %,d", s, e);
			rows[r][c++] =  String.format("%,d", len); 
			
			if (lastE>0) { 					// gap column
				gapCol = Globals.pGap_nOlap(s,e,lastS,lastE); // CAS560 was off by one with s-lastE; change to olapOrGap
				
				rows[r][c++] = String.format("%,d",gapCol);
				if (gapCol<=0) cntMergeNeg++;
			}
			else rows[r][c++] = "-";
			
			/* last column */
			// disorder sign
			String star = " ";
			if (bOrder) {
				  if (lastS > s) { 
					  star = TextShowInfo.disOrder+TextShowInfo.disOrder;
					  cntDisorder++;
				  }
				  else star = "  ";
			}
			else {
				int oo = (r>0) ? o-lastO : lastO;
				if ((!bIsInv && oo != 1) || (bIsInv && oo != -1)) {
					star = TextShowInfo.disOrder;
					cntDisorder++;
				}
			}
			
			// exon - almost always Y for plants, not so for hsq-pan; Also extend pass gene
			String ex = "  ";
			if (Globals.INFO) {
				if (exonVec!=null) {
					for  (Annotation exon : exonVec) {
						int olap = Globals.pGap_nOlap(s, e, exon.getStart(), exon.getEnd());  
						if (olap<0) {
							ex = " E "; 
							break;
						}
					}
				}
				// Extend: could have embedded hit, messing up true end, so check all
				if (aObj!=null && aObj.getStart() > hitSort.get(i).start) { 
					ex += String.format(" s %,d", (aObj.getStart()- hitSort.get(i).start));
				}
				if (aObj!=null && aObj.getEnd() < hitSort.get(i).end) 
					ex += String.format(" e %,d", ( hitSort.get(i).end - aObj.getEnd()));
			}
			rows[r][c++] = star + ex;
			
			r++; c=0;
			
			lastO = o; lastE = e; lastS = s;
    		if (bMerge) {sumTotLen+=len; sumTotGap += gapCol;}
    	}
    	if (bMerge && tokc.length>1) {
    		rows[r][c++] = "--";	
    		rows[r][c++] =  String.format("%s %,d",TextShowInfo.totalMerge, sumTotLen+sumTotGap); 
    		rows[r][c++] =  String.format("%,d",sumTotLen);
    		rows[r][c++] =  String.format("%,d",sumTotGap);
    		rows[r][c++] = "--";
    		r++;
    	}
  
    	String list = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
    	return name + "\n" + list;
    }
    
    /*************************************************************
     * Merge overlapping hits: CAS560 add
     * This method is in:
     * 	  mapper.HitData:  for 2D display where it is not sorted first, which can leave gaps but does not hurt for display
     *    anchor2.HitPair: where the input is a vector of hits and the output is a merged vector
     *  This is yet another calculation because the input and output is a string, but the input must be sorted
     *  so the display does not show gaps so it uses many objects
     */
    static protected String calcMergeHits(int X, String subHitStr, boolean isInv) { 
		try {
			if (subHitStr == null || subHitStr.length() == 0) return ""; // uses start,end
				
			String[] subs = subHitStr.split(",");
			ArrayList <Hit> subHits = new ArrayList <Hit> (subs.length);
			
			int order=1;
			for (String st : subs) {
				String [] tok = st.split(":");
				int s = Integer.parseInt(tok[0]);
				int e = Integer.parseInt(tok[1]);
			
				subHits.add(new Hit(order++, s,e));
			}
			sortForMerge(subHits); // sort by start ascending, end descending
			
			ArrayList <Hit> mergeHits = new ArrayList <Hit> ();
			for (Hit sh : subHits) {
				boolean found=false;
				
				for (Hit mh : mergeHits) {
					int olap = Globals.pGap_nOlap(sh.start, sh.end, mh.start, mh.end); 
					if (olap > 0) continue; 
					
					found=true;
					if (sh.end>mh.end)     mh.end = sh.end;
					if (sh.start<mh.start) mh.start = sh.start;
					break;
				}
				if (!found) {
					mergeHits.add(new Hit(order++, sh.start, sh.end));
				}
			}
			
			if (mergeHits.size()==0) return subHitStr; 
			
			if (isInv) sortForInv(mergeHits); // sort
			
			StringBuffer sb = new StringBuffer();
			for (Hit sh : mergeHits) {
				if (sb.length()==0) sb.append(sh.start + ":" + sh.end);
				else sb.append("," + sh.start + ":" + sh.end);
			}
			return sb.toString();
		}
		catch (Exception e) {e.printStackTrace(); return null;}	
	}
    /* Reverse hits */
	static protected String calcRevHits(String hitStr) {
	try {
		int x1, x2;
		String [] tHits = hitStr.split(",");
    	ArrayList <Hit> tHitSort = new ArrayList <Hit> (tHits.length);
    	
    	for (String coords : tHits) { // ordered by query; if isInv, then descending
    		String [] y = coords.split(":");
    		x1 = Integer.parseInt(y[0]); 
    		x2 = Integer.parseInt(y[1]);
    		
			tHitSort.add(new Hit(0, x1, x2));
    	}
    	
    	StringBuffer sb = new StringBuffer();
 		for (int i=tHits.length-1; i>=0; i--) {
 			Hit ht = tHitSort.get(i);
 			if (sb.length()==0) sb.append(ht.start + ":" + ht.end);
 			else sb.append("," + ht.start + ":" + ht.end);
 		}
 		return sb.toString();
	}
	catch (Exception e) {e.printStackTrace(); return null;}	
	}
    /* CAS560 remove disordered hits */
    static protected String [] calcRemoveDisorder(String queryHits, String targetHits, boolean isInv) {
     	try {
     		String [] retHits = new String [2];
        	
        	int nOrder=1, x1=0, x2=0;
     		String [] qHits = queryHits.split(",");
        	TreeMap <Integer, Hit> qHitSort = new TreeMap <Integer, Hit> ();
        	for (String coords : qHits) { 				// ordered by start	
        		String [] y = coords.split(":");
        		x1 = Integer.parseInt(y[0]); 
        		x2 = Integer.parseInt(y[1]);
        		
    			qHitSort.put(nOrder, new Hit(nOrder++, x1, x2));
        	}
        	
        	nOrder=1;
        	String [] tHits = targetHits.split(",");
        	TreeMap <Integer, Hit> tHitSort = new TreeMap <Integer, Hit> ();
        	for (String coords : tHits) { 				// ordered by query; if isInv, then descending	
        		String [] y = coords.split(":");
        		x1 = Integer.parseInt(y[0]); 
        		x2 = Integer.parseInt(y[1]);
        		
    			tHitSort.put(nOrder, new Hit(nOrder++, x1, x2));
        	}
        	
        	Hit last=null;
        	for (int order : tHitSort.keySet()) {
        		Hit ht=tHitSort.get(order);
        		if (last!=null) {
        			if (isInv) {
        				if (last.start<ht.start) {
        					qHitSort.get(ht.order).order=0;
        					ht.order = 0;
        				}
        			}
        			else {
        				if (last.start>ht.start) {
        					qHitSort.get(ht.order).order=0;
        					ht.order = 0;
        				}
        			}
        		}
        		last = ht;
        	}
     		StringBuffer sb = new StringBuffer();
     		for (Hit ht : tHitSort.values()) {
     			if (ht.order>0) {
     				if (sb.length()==0) sb.append(ht.start + ":" + ht.end);
     				else sb.append("," + ht.start + ":" + ht.end);
     			}
     		}
     		retHits[Globals.T] = sb.toString();
     		
     		sb = new StringBuffer();
     		for (Hit ht : qHitSort.values()) {
     			if (ht.order>0) {
     				if (sb.length()==0) sb.append(ht.start + ":" + ht.end);
     				else sb.append("," + ht.start + ":" + ht.end);
     			}
     		}
     		retHits[Globals.Q] = sb.toString();
     		
     		return retHits;
     	}
     	catch (Exception e) {ErrorReport.print(e, "min hits"); return null;}	
    }
    
    protected static void sortForFormat(ArrayList <Hit> hitList) { 
		Collections.sort(hitList, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					if (h1.start == h2.start) return h1.order - h2.order; // retain input order
		    		return h1.start - h2.start; 
				}
		});
    }
    protected static void sortForOrder(ArrayList <Hit> hitList, boolean isInv) { // calcOrderTarget
		Collections.sort(hitList, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					if (isInv) return h2.order - h1.order;
					else return h1.order - h2.order;
				}
		});
    }
    protected static void sortForInv(ArrayList <Hit> hitList) { 
		Collections.sort(hitList, 
			new Comparator<Hit>() {
				public int compare(Hit h1, Hit h2) {
					if (h2.start == h1.start) return h2.order - h1.order; // retain input order
					return h2.start - h1.start; 
				}
		});
    }
    protected static void sortForMerge(ArrayList <Hit> hitList) { 
  		Collections.sort(hitList, 
  			new Comparator<Hit>() {
  				public int compare(Hit h1, Hit h2) {
  					if (h1.start==h2.start) return h2.end-h1.end;
  					return h1.start - h2.start; 
  				}
  		});
    }
    /* CAS560 added but not used; Only called if target is disordered; not used, switched to not-sort; 
     * return new queryHits and targetHits ordered by target start; 
     */
    static protected String [] calcOrderTarget_NotUsed(String queryHits, String targetHits, boolean isInv) {
     	try {
     		String [] retHits = new String [2];
        	
        	int nOrder=1, x1=0, x2=0;
     		String [] qHits = queryHits.split(",");
        	ArrayList <Hit> qHitSort = new ArrayList <Hit> (qHits.length);
        	for (String coords : qHits) { // ordered by start
        		String [] y = coords.split(":");
        		try {x1 = Integer.parseInt(y[0]); x2 = Integer.parseInt(y[1]);}
        		catch (Exception e) {System.err.println("Parsing int: " + coords); return retHits;}
    			
    			qHitSort.add(new Hit(nOrder++, x1, x2));
        	}
        	
        	nOrder=1;
        	String [] tHits = targetHits.split(",");
        	ArrayList <Hit> tHitSort = new ArrayList <Hit> (tHits.length);
        	for (String coords : tHits) { // ordered by query; if isInv, then descending
        		String [] y = coords.split(":");
        		try {x1 = Integer.parseInt(y[0]); x2 = Integer.parseInt(y[1]);}
        		catch (Exception e) {System.err.println("Parsing int: " + coords); return retHits;}
    			
    			tHitSort.add(new Hit(nOrder++, x1, x2));
        	}
        	if (isInv) sortForInv(tHitSort);  
        	else       sortForFormat(tHitSort); // target may be out of order; need ordered by start
        	
        	HashMap <Integer, Integer> orderMap = new HashMap <Integer, Integer> (nOrder);
        	nOrder=1;
        	for (Hit ht : tHitSort) orderMap.put(ht.order, nOrder++);
        	
        	for (Hit ht : tHitSort) ht.order = orderMap.get(ht.order);
        	for (Hit ht : qHitSort) ht.order = orderMap.get(ht.order);
       
     		sortForOrder(tHitSort, false); 
     		sortForOrder(qHitSort, false);
     		
     		StringBuffer sb = new StringBuffer();
     		for (Hit ht : tHitSort) {
     			if (sb.length()==0) sb.append(ht.start + ":" + ht.end);
     			else sb.append("," + ht.start + ":" + ht.end);
     		}
     		retHits[Globals.T] = sb.toString();
     		
     		sb = new StringBuffer();
     		for (Hit ht : qHitSort) {
     			if (sb.length()==0) sb.append(ht.start + ":" + ht.end);
     			else sb.append("," + ht.start + ":" + ht.end);
     		}
     		retHits[Globals.Q] = sb.toString();
     		
     		return retHits;
     	}
     	catch (Exception e) {ErrorReport.print(e, "min hits"); return null;}	
    }
    static private class Hit {
    	private int start, end, order;
    	
    	public Hit (int order, int start, int end) {
    		this.order=order; this.start=start; this.end=end;
    	}
    }
}
