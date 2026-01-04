package symapQuery;

import java.util.HashMap;
import java.util.Vector;

import symap.Globals;
import util.ErrorReport;

/***************************************************
 * Translates a row from a vector of objects to the correct data types for easy access,
 * plus adds the species and chromosome index
 * used in UtilReport, UtilExport writeFasta, and TableMainPanel.showAlignment, showRow, showSynteny
 */
public class TmpRowData {
	private TableMainPanel tPanel;
	
	protected TmpRowData (TableMainPanel tdp) {
		this.tPanel = tdp;
	}
	
	protected String loadRowAsString(int row) {// show row
		try {
			String outLine = tPanel.theTableData.getRowData(row); // colName, value
			return outLine;
		} catch (Exception e) {ErrorReport.print(e, "Getting row data"); return "error";}
	}
	
	/* LoadRow; if add column, add to getRowLocData also */
	protected boolean loadRow(int row) {// showAlign, showSynteny, writeFasta, UtilReport
		try {
			nRow = row;
			
			Vector <String> colNames = new Vector <String> (); // for new 2D-3track
			Vector <Object> colVals = new Vector <Object> ();
			
			tPanel.theTableData.getRowLocData(row, colNames, colVals); // only needed columns are returned
			
			HashMap <String, Integer> sp2x = new HashMap <String, Integer> ();
			int isp=0;
			
			for (int i=0; i<colNames.size(); i++) {
				String colHead = colNames.get(i);
				Object colVal = colVals.get(i);				
				if (colVal instanceof String) { // if >2 species, ignores ones w/o values, so only get 2
					String str = (String) colVal;
					if (str.equals("") || str.equals(Q.empty)) continue; // the blanks species
				}  
				if (colHead.contentEquals(Q.blockCol)) { 
					String x = (String) colVal;
					x =  (x.contains(".")) ? x.substring(x.lastIndexOf(".")+1) : "0";
					blockN = Integer.parseInt(x);
					continue;
				}
				if (colHead.contentEquals(Q.cosetCol)) { // chr.chr.size.N
					String x = (String) colVal;
					String [] tok = x.split(Q.SDOT); 
					if (tok.length==4) {
						collinearSz = Integer.parseInt(tok[2]); 
						collinearN = Integer.parseInt(tok[3]);
					}
					else Globals.dprt("collinear " + x + " " + tok.length);
					continue;
				}
				if (colHead.contentEquals(Q.hitCol)) {	
					String x = String.valueOf(colVal);
					hitnum = Integer.parseInt(x);
					continue;
				}
				if (colHead.contentEquals(Q.hitSt)) {	
					hitSt = String.valueOf(colVal);
					continue;
				}
				if (colHead.contentEquals(Q.grpCol)) {	// ends with grpSz-grpN; 
					String x = (String) colVal;
					String [] tok = x.split(Q.GROUP);
					int len = tok.length-1;
					if (tok.length>=2) {
						groupSz = Integer.parseInt(tok[len-1]); 
						groupN  = Integer.parseInt(tok[len]);
					}
					else Globals.dprt("group " + x + " " + tok.length);
					continue;
				}
			
				// Species columns
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
					Globals.eprt("Row Data " + colHead + " " + colVal + " is not type string or integer");
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
					if (isp>2) {Globals.eprt("species " + isp); break;} // should not happen
				}
				if (col.equals(Q.chrCol)) 		  chrNum[i0or1]  = sVal;
				else if (col.equals(Q.hStartCol)) start[i0or1]   = iVal;
				else if (col.equals(Q.hEndCol))   end[i0or1]     = iVal;
				else if (col.equals(Q.gNCol))	  geneTag[i0or1] = sVal; // this will be N.N.[b] or N.- 
			}
			
			// get supporting values
			if (spAbbr[1]==null || spAbbr[1]=="") {
				util.Popup.showWarningMessage("The abbrev_names are the same, cannot continue...");
				return false;
			}
			spName[0] = tPanel.qFrame.getDisplayFromAbbrev(spAbbr[0]);
			spName[1] = tPanel.qFrame.getDisplayFromAbbrev(spAbbr[1]);
			
			SpeciesPanel spPanel = tPanel.qPanel.getSpeciesPanel();
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
	protected int [] loadGroup(Vector <Integer> grpIdxVec) {
	try {
		int [] coords = {0,0,0,0}; 
		coords[0] = start[0]; coords[1] = end[0]; coords[2] = start[1]; coords[3] = end[1];
		
		for (int r=0; r<tPanel.theTableData.getNumRows(); r++) {
			HashMap <String, Object> colHeadVal = tPanel.theTableData.getRowLocData(r); 
			
			String val = (String) colHeadVal.get(Q.grpCol);
			String [] tok = val.split(Q.GROUP);				
			int grp  = util.Utilities.getInt(tok[tok.length-1]);
			if (grp!=groupN) continue;     // only rows with this groupN (selected row)
			
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
	/* UtilReport **/
	protected String getAnnoFromKey(String spAbbr, int row) {
	try {
		HashMap <String, String> annoMap = tPanel.theTableData.getAllAnno(row);
		
		for (String colHead : annoMap.keySet()) { // All_Anno for all species in query
			if (colHead.startsWith(spAbbr) && colHead.endsWith(Q.All_Anno)) 
				return annoMap.get(colHead);
		}
		return "***" + spAbbr + row;
	} catch (Exception e) {ErrorReport.print(e, "Getting row data"); return null;}
	}
	
	// get gene coordinates;  not used
	protected Object [] getGeneCoords(String spAbbr, int row) {
		return tPanel.theTableData.getGeneCoords(spAbbr, row);
	}
	public String toString() {
		return String.format("%4s %4s  %10s %10s  %5s %5s ", spAbbr[0], spAbbr[1], geneTag[0], geneTag[1], chrNum[0], chrNum[1]);
	}
	// Values for selected row; if add column, ADD in TableData TOO!
	protected int nRow=0;
	protected String [] spName = {"",""}; 
	protected String [] spAbbr = {"",""}; 
	protected int [] 	spIdx = {0,0};
	
	protected int [] 	chrIdx = {0,0};
	protected String [] chrNum = {"",""};
	
	protected int [] 	start = {0,0};
	protected int [] 	end = {0,0};
	
	protected String [] geneTag = {"",""};
	
	protected String hitSt="";
	protected int hitnum=0, blockN=0;
	protected int collinearN=0, collinearSz=0; 	
	protected int groupN=0, groupSz=0; 			// for Show Group; set in loadRow, used in loadGroup
}
