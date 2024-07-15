package symapQuery;

import java.util.HashMap;
import java.util.Vector;

import util.ErrorReport;
import util.Utilities;

/***************************************************
 * Translates a row from a vector of objects to the correct data types for easy access,
 * plus adds the species and chromosome index
 * 
 * CAS504 added this class to take the place of using hidden columns to get data
 * TheTableData.getRowData(row) returns the chr,start,end of all species in line,
 * where some can be blank. CAS520 also return block and collinear
 * CAS521 also return hit# and Gene# for Align; use short name for species
 * CAS556 make separate file cause used in TableExport, TableReport and TableDataPanel
 */
public class TmpRowData {
	private TableDataPanel tdp;
	
	protected TmpRowData (TableDataPanel tdp) {
		this.tdp = tdp;
	}
	
	protected String loadRowAsString(int row) {// show row
		try {
			String outLine = tdp.theTableData.getRowData(row); // colName, value
			return outLine;
		} catch (Exception e) {ErrorReport.print(e, "Getting row data"); return "error";}
	}
	
	protected boolean loadRow(int row) {// showAlignment, showSynteny, writeFasta, TableReport
		try {
			nRow = row;
			HashMap <String, Object> colHeadVal = tdp.theTableData.getRowLocData(row); // only needed columns are returned
			HashMap <String, Integer> sp2x = new HashMap <String, Integer> ();
			int isp=0;
			
			for (String colHead : colHeadVal.keySet()) {
				Object colVal = colHeadVal.get(colHead);
				
				if (colVal instanceof String) { // if >2 species, ignores ones w/o values, so only get 2
					String str = (String) colVal;
					if (str.equals("") || str.equals(Q.empty)) continue; // the blanks species
				}  
				if (colHead.contentEquals(Q.blockCol)) { // CAS520 add block and collinear
					String x = (String) colVal;
					x =  (x.contains(".")) ? x.substring(x.lastIndexOf(".")+1) : "0";
					blockN = Integer.parseInt(x);
					continue;
				}
				if (colHead.contentEquals(Q.runCol)) { // chr.chr.size.N
					String x = (String) colVal;
					String [] tok = x.split("\\.");
					if (tok.length==4) {
						collinearSz = Integer.parseInt(tok[2]); // CAS556 add Sz
						collinearN = Integer.parseInt(tok[3]);
					}
					else symap.Globals.dprt(x + " " + tok.length);
					continue;
				}
				if (colHead.contentEquals(Q.hitCol)) {	// CAS521
					String x = String.valueOf(colVal);
					hitnum = Integer.parseInt(x);
					continue;
				}
				if (colHead.contentEquals(Q.grpCol)) {	// CAS555
					String x = String.valueOf(colVal);
					groupN = Integer.parseInt(x);
					continue;
				}
			
				String [] field = colHead.split(Q.delim); // speciesName\nChr or Start or End
				if (field.length!=2) continue;
				String species=field[0];
				String col=field[1];
				
				String sVal="";
				int iVal=0;
				if (col.equals(Q.chrCol)) {
					if (colVal instanceof Integer) 	sVal = String.valueOf(colVal);
					else                      		sVal = (String) colVal;
				}
				else if (colVal instanceof Integer) { iVal = (Integer) colVal; }
				else if (colVal instanceof String)  { sVal = (String)  colVal; }
				else {
					symap.Globals.eprt("Row Data " + colHead + " " + colVal + " is not type string or integer");
					return false;
				}
				
				int i0or1=0;						// only two species, none blank
				if (sp2x.containsKey(species)) 
					i0or1 = sp2x.get(species);
				else {
					sp2x.put(species, isp);
					spAbbr[isp] = species;
					i0or1 = isp;
					isp++;
					if (isp>2) {symap.Globals.eprt("species " + isp); break;} // should not happen
				}
				if (col.equals(Q.chrCol)) 		  chrNum[i0or1] = sVal;
				else if (col.equals(Q.hStartCol)) start[i0or1] = iVal;
				else if (col.equals(Q.hEndCol))   end[i0or1] = iVal;
				else if (col.equals(Q.gNCol))	  geneTag[i0or1] = sVal; // this will be N.N.[b] or N.-  CAS521  add
			}
			
			// get supporting values
			if (spAbbr[1]==null || spAbbr[1]=="") {
				Utilities.showWarningMessage("The abbrev_names are the same, cannot continue...");
				return false;
			}
			spName[0] = tdp.theParentFrame.getDisplayFromAbbrev(spAbbr[0]);
			spName[1] = tdp.theParentFrame.getDisplayFromAbbrev(spAbbr[1]);
			
			SpeciesSelectPanel spPanel = tdp.theQueryPanel.getSpeciesPanel();
			for (isp=0; isp<2; isp++) {
				spIdx[isp] =  spPanel.getSpIdxFromSpName(spName[isp]);
				chrIdx[isp] = spPanel.getChrIdxFromChrNumSpIdx(chrNum[isp], spIdx[isp]);
			}
			return true;
		} catch (Exception e) {ErrorReport.print(e, "Getting row data"); return false;}
	}
	// Show Group
	// loadRow occurs before this, so the selected row values are known for each row.
	// If the group, chr[0], chr[1] are the same, get min start and max end
	// grpIdxVec is hitNums to highlight for Groups in Show Synteny
	protected double [] loadGroup(Vector <Integer> grpIdxVec) {
	try {
		double [] coords = {0.0,0.0,0.0,0.0};
		coords[0] = start[0]; coords[1] = end[0]; coords[2] = start[1]; coords[3] = end[1];
		
		for (int r=0; r<tdp.theTableData.getNumRows(); r++) {
			HashMap <String, Object> colHeadVal = tdp.theTableData.getRowLocData(r); 
			
			Object val = colHeadVal.get(Q.grpCol);
			int grp = (Integer) val;
			if (grp!=groupN) continue;     // only rows with this groupN
			
			int hitNum=0;
			int [] s = {0,0};
			int [] e = {0,0};
			boolean bGoodChr=true;
			
			for (String colHead : colHeadVal.keySet()) {
				Object colVal = colHeadVal.get(colHead);

				if (colVal instanceof String) {
					String str = (String) colVal;
					if (str.equals("") || str.equals(Q.empty)) continue; // ignore blank species
				}  
				
				if (colHead.equals(Q.hitCol)) { // see isHitSelected
					hitNum = (Integer) colVal;
					continue;
				}
				
				String [] field = colHead.split(Q.delim); // speciesName\nChr or Start or End
				if (field.length!=2) continue;
				
				String species=field[0];
				String col=field[1];
				int x = (species.equals(spAbbr[0])) ? 0 : 1;
				
				if (col.equals(Q.chrCol)) { // if SameChr is not checked, make sure on same chrs as selected
					String sVal;
					if (colVal instanceof Integer) 	sVal = String.valueOf(colVal);
					else                      		sVal = (String) colVal;
					if (!sVal.equals(chrNum[x])) {
						bGoodChr = false;
						break; 				// skip rest of row
					}
				}
				else if (colVal instanceof Integer) { 
					if (col.equals(Q.hStartCol)) 	  s[x] = (Integer) colVal;
					else if (col.equals(Q.hEndCol))   e[x] = (Integer) colVal;
				}
			} // finish row
			if (bGoodChr) {
				grpIdxVec.add(hitNum);
				coords[0] = Math.min(coords[0], s[0]);
				coords[1] = Math.max(coords[1], e[0]);
				coords[2] = Math.min(coords[2], s[1]);
				coords[3] = Math.max(coords[3], e[1]);
			}
		} // finish all rows
		return coords;
	} catch (Exception e) {ErrorReport.print(e, "Getting row data"); return null;}
	}
	/* CAS556 add for TableReport **/
	protected String getAnnoFromKey(String spAbbr, int row) {
	try {
		HashMap <String, String> annoMap = tdp.theTableData.getAllAnno(row);
		
		for (String colHead : annoMap.keySet()) { // All_Anno for all species in query
			if (colHead.startsWith(spAbbr) && colHead.endsWith(Q.All_Anno)) 
				return annoMap.get(colHead);
		}
		return "***" + spAbbr + row;
	} catch (Exception e) {ErrorReport.print(e, "Getting row data"); return null;}
	}
	
	// Values for selected row
	protected int nRow=0;
	protected String [] spName = {"",""}; 
	protected String [] spAbbr = {"",""}; 
	protected int [] 	spIdx = {0,0};
	
	protected int [] 	chrIdx = {0,0};
	protected String [] chrNum = {"",""};
	
	protected int [] 	start = {0,0};
	protected int [] 	end = {0,0};
	
	protected String [] geneTag = {"",""};
	
	protected int hitnum=0, blockN=0;
	protected int collinearN=0, collinearSz=0; 	// CAS556 add sz for TableReport
	protected int groupN=0; 					// CAS555 add for Show Group
}
