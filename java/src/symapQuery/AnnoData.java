package symapQuery;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;
import java.util.TreeSet;

import database.DBconn2;
import symap.manager.Mproject;
import util.ErrorReport;

/**********************************************************
 * Return AnnoData which has a AnnoSp object for each species containing its annotation keywords.
 * Used by TableDataPanel
 * CAS575 change from looking up species by spIdx to using the position in array; this is for self-synteny which has idx repeated
 */
public class AnnoData {
	protected static final String [] key1 = {"ID"};  // GFF should have an ID, if so, put 1st; used by TableData too
	protected static final String [] key2 = {"product", "desc", "Desc", "description"}; // GFF NCBI/Ensembl, put 2nd 
	
	protected static AnnoData loadProjAnnoKeywords(QueryFrame qFrame) {
		return new AnnoData(qFrame.getProjects(), qFrame.getDBC());
	}
	/*****************************************************************/
	private AnnoData(Vector<Mproject> projs, DBconn2 dbc2) { 
	try {
		spAnnoVec = new Vector<AnnoSp> ();
		int pos=0;
		
		for (Mproject p : projs) { 
			if (!p.hasGenes()) continue; 
			
			AnnoSp ap = new AnnoSp(p.getDisplayName(), p.getdbAbbrev(), p.getIdx(), pos);
			spAnnoVec.add(ap);
			
			Mproject tProj = new Mproject();
			int annot_kw_mincount=0;
			ResultSet rset = dbc2.executeQuery("select value from proj_props "
					+ "where proj_idx=" + p.getIdx() + " and name='" + tProj.getKey(tProj.sANkeyCnt) + "'");
		
			if (rset.next()) {
				String val = rset.getString(1);
				try {annot_kw_mincount=Integer.parseInt(val); }
				catch (Exception e) {} // ok to default to 0 if blank
			}
			
			// use count in query
			String query = 	"SELECT keyname FROM annot_key " +
					" WHERE  proj_idx = " + p.getIdx() + " and count>=" + annot_kw_mincount +
					" ORDER BY  keyname ASC";
			
			rset = dbc2.executeQuery(query);
			while(rset.next()) {
				ap.addAnnoKey(rset.getString(1));
			}
			ap.addAnnoKey(Q.All_Anno);
			rset.close();	
			pos++;
		}
		
		Collections.sort(spAnnoVec);
		pos=0;
		for (AnnoSp sp : spAnnoVec) {
			sp.spPos= pos++;
			sp.orderKeys();
		}
	}
	catch(Exception e) {ErrorReport.print(e, "set project annotations"); }
	}
	
	/* methods called by TableDataPanel */
	protected int getNumberAnnosForSpecies(int pos) { // was speciesID, with lookup; CAS575
		return spAnnoVec.get(pos).getNumAnnoIDs();
	}
	
	protected String getAnnoIDforSpeciesAt(int pos, int annoPos) {
		return spAnnoVec.get(pos).getAnnoAt(annoPos);
	}
	protected int [] getSpPosList() { // concatenates 0,..n where n is number of species
		int [] retVal = new int[spAnnoVec.size()];
		for (int i=0; i<spAnnoVec.size(); i++) {
			retVal[i] = spAnnoVec.get(i).spPos;
		}
		return retVal;
	}
	protected TreeSet <String> getSpeciesAbbrList() {// for TableData.arrangeColumns and UtilSearch
		TreeSet <String> retSet = new TreeSet <String> ();
		
		Iterator<AnnoSp> iter = spAnnoVec.iterator();
		while(iter.hasNext()) {
			retSet.add(iter.next().getAbbrevName());
		}
		return retSet;
	}
	protected String getSpeciesAbbrev(int pos) {// was speciesID, with lookup; CAS575
		return spAnnoVec.get(pos).getAbbrevName();
	}
	
	 //returns list of all Species::keyword; used in DBdata (species) and to create the column headers (abbrev)
	protected String [] getColumns(boolean bAbbrev) { 
		Vector<String> annoKeyList = new Vector<String> ();
		
		Iterator<AnnoSp> speciesIter = spAnnoVec.iterator();
		while(speciesIter.hasNext()) {
			AnnoSp key = speciesIter.next();
			String species = (bAbbrev) ? key.getAbbrevName() : key.getSpeciesName();
			for(int x=0; x<key.getNumAnnoIDs(); x++) {
				annoKeyList.add(species + Q.delim + key.getAnnoAt(x)); 
			}
		}
		return annoKeyList.toArray(new String[annoKeyList.size()]);
	}
	
	/*******************************************************************
	 * XXX Each species has a AnnoSp class held in AnnoData 'theAnnos' vector
	 ************************************************************/
	private class AnnoSp implements Comparable<AnnoSp> {
		private AnnoSp(String speciesName, String abbvName, int speciesID, int pos) {
			spKeys = new Vector<String> ();
			spName = speciesName;
			spAbbrev = abbvName;
			spPos = pos;
		}
	
		private void addAnnoKey(String annoID) {
			spKeys.add(annoID);
		}
		
		private int getNumAnnoIDs() 		{ return spKeys.size(); }
		private String getAnnoAt(int pos) 	{ return spKeys.get(pos); }
		private String getSpeciesName() 	{ return spName;}
		private String getAbbrevName() 		{ return spAbbrev;}
		public int compareTo(AnnoSp arg0) {
			return spName.compareTo(arg0.spName);
		}
		private void orderKeys() {
			String [] tmpKeys = new String [spKeys.size()];
			int idx=0;
			
			String [] keySet;
			for (int x=0; x<2; x++) {
				keySet = (x==0) ? key1 : key2;
				boolean found=false;
				for (String desc : keySet) {
					for (int i=0; i<spKeys.size(); i++) {
						if (spKeys.get(i).equalsIgnoreCase(desc)) {
							tmpKeys[idx] = spKeys.get(i); // keep keyword case
							idx++;
							spKeys.remove(i);
							found = true;
							break;
						}
					}
					if (found) break;
				}
			}
			for (String key : spKeys) {
				tmpKeys[idx] = key;
				idx++;
			}
			spKeys.clear();
			for (String key : tmpKeys) spKeys.add(key);
		}
		
		private String spName = "", spAbbrev = "";
		private int spPos = -1; 
		private Vector<String> spKeys = null;
	}	// end AnnoSp class
	
	/* AnnoData private variable */
	private Vector<AnnoSp> spAnnoVec = null; // renamed CAS575
} // end AnnotData class

