package symapQuery;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;

import database.DBconn2;
import symap.manager.Mproject;
import util.ErrorReport;

/**********************************************************
 * The object has a AnnoSp object for each species containing its annotation keywords.
 * Used by TableDataPanel
 * 
 * CAS532 moved from TableDataPanel to this new class file
 * 		  Made ID and key2 be the first two keywords of panel
 */
public class AnnoData {
	private final String [] key1 = {"ID"};  
	private final String [] key2= {"product", "desc", "description"};
	
	/******************************************************
	 * get keywords of project annotations to display as columns
	 * Called by TableDataPanel for every new table
	 */
	protected static AnnoData loadProjAnnoKeywords(SyMAPQueryFrame theParentFrame) {
		Vector<Mproject> projs = theParentFrame.getProjects();
		
		try {
			AnnoData theAnnos = new AnnoData();
			DBconn2 dbc2 = theParentFrame.getDBC();
			
			for (Mproject p : projs) { 
				if (!p.hasGenes()) continue; // CAS505 add hasGenes check
				
				// CAS513 check for # of genes with keyword
				Mproject tProj = new Mproject();
				int annot_kw_mincount=0;
				ResultSet rset = dbc2.executeQuery("select value from proj_props "
						+ "where proj_idx=" + p.getID() + " and name='" + tProj.getKey(tProj.sANkeyCnt) + "'");
			
				if (rset.next()) {
					String val = rset.getString(1);
					try {annot_kw_mincount=Integer.parseInt(val); }
					catch (Exception e) {} // ok to default to 0 if blank
				}
				
				// use count in query
				String query = 	"SELECT keyname FROM annot_key " +
						" WHERE  proj_idx = " + p.getID() + " and count>=" + annot_kw_mincount +
						" ORDER BY  keyname ASC";
				
				rset = dbc2.executeQuery(query);
				while(rset.next()) {
					theAnnos.addAnnoKeyForSpecies(p, rset.getString(1));
				}
				theAnnos.addAnnoKeyForSpecies(p,  Q.All_Anno);
				rset.close();	
			}
			
			theAnnos.orderKeys();
			return theAnnos;
		} catch(Exception e) {ErrorReport.print(e, "set project annotations"); return null;}
	}
	private AnnoData() { 
		theAnnos = new Vector<AnnoSp> ();
	}
	private void addAnnoKeyForSpecies(Mproject p, String annoID) { // CAS519 changed to use project instead of project ID
		int speciesID = p.getID();
		if (getSpeciesPosition(speciesID) < 0) {
			theAnnos.add(new AnnoSp(p.getDisplayName(), p.getdbAbbrev(), speciesID));
			Collections.sort(theAnnos);
		}
		int pos = getSpeciesPosition(speciesID);
		theAnnos.get(pos).addAnnoKey(annoID);
	}
	
	/***********************************************************
	 * TableDataPanel
	 */
	protected int getNumberAnnosForSpecies(int speciesID) { 
		int pos = getSpeciesPosition(speciesID);
		
		if(pos < 0) return -1;
		
		return theAnnos.get(pos).getNumAnnoIDs();
	}
	
	protected String getAnnoIDforSpeciesAt(int speciesID, int annoPos) {
		int pos = getSpeciesPosition(speciesID);
		return theAnnos.get(pos).getAnnoAt(annoPos);
	}
	
	protected int [] getSpeciesIDList() {
		int [] retVal = new int[theAnnos.size()];
		
		Iterator<AnnoSp> iter = theAnnos.iterator();
		int x = 0;
		while(iter.hasNext()) {
			retVal[x] = iter.next().getSpeciesID();
			x++;
		}
		return retVal;
	}
	protected String getSpeciesAbbrevByID(int speciesID) {
		int pos = getSpeciesPosition(speciesID);
		if(pos < 0) return null;
		return theAnnos.get(pos).getAbbrevName();
	}
	
	 //returns list of all Species::keyword; used in DBdata (species) and to create the column headers (abbrev)
	protected String [] getColumns(boolean bAbbrev) { 
		Vector<String> annoKeyList = new Vector<String> ();
		
		Iterator<AnnoSp> speciesIter = theAnnos.iterator();
		while(speciesIter.hasNext()) {
			AnnoSp key = speciesIter.next();
			String species = (bAbbrev) ? key.getAbbrevName() : key.getSpeciesName();
			for(int x=0; x<key.getNumAnnoIDs(); x++) {
				annoKeyList.add(species + Q.delim + key.getAnnoAt(x)); 
			}
		}
		return annoKeyList.toArray(new String[annoKeyList.size()]);
	}
	
	private int getSpeciesPosition(int speciesID) {
		int pos = 0;
		Iterator<AnnoSp> iter = theAnnos.iterator();
		boolean found = false;
		while(iter.hasNext() && !found) {
			AnnoSp spID = iter.next();
			
			if(spID.getSpeciesID() == speciesID) found = true;
			else	  pos++;
		}
		if (!found) return -1;
		return pos;
	}
	
	private void orderKeys() {
		for (AnnoSp sp : theAnnos) {
			sp.orderKeys();
		}
	}
	private Vector<AnnoSp> theAnnos = null;
	/*******************************************************************/
	private class AnnoSp implements Comparable<AnnoSp> {
		private AnnoSp(String speciesName, String abbvName, int speciesID) {
			theKeys = new Vector<String> ();
			theSpeciesName = speciesName;
			theAbbrevName = abbvName;
			theSpeciesID = speciesID;
		}
		
		private void addAnnoKey(String annoID) {
			theKeys.add(annoID);
		}
		
		private int getNumAnnoIDs() 			{ return theKeys.size(); }
		private String getAnnoAt(int pos) 	{ return theKeys.get(pos); }
		private String getSpeciesName() 		{ return theSpeciesName;}
		private String getAbbrevName() 		{ return theAbbrevName;}
		private int getSpeciesID() 			{ return theSpeciesID; }
		public int compareTo(AnnoSp arg0) {
			return theSpeciesName.compareTo(arg0.theSpeciesName);
		}
		private void orderKeys() {
			String [] tmpKeys = new String [theKeys.size()];
			int idx=0;
			
			String [] keySet;
			for (int x=0; x<2; x++) {
				keySet = (x==0) ? key1 : key2;
				boolean found=false;
				for (String desc : keySet) {
					for (int i=0; i<theKeys.size(); i++) {
						if (theKeys.get(i).equalsIgnoreCase(desc)) {
							tmpKeys[idx] = theKeys.get(i); // keep keyword case
							idx++;
							theKeys.remove(i);
							found = true;
							break;
						}
					}
					if (found) break;
				}
			}
			for (String key : theKeys) {
				tmpKeys[idx] = key;
				idx++;
			}
			theKeys.clear();
			for (String key : tmpKeys) theKeys.add(key);
		}
		
		private String theSpeciesName = "", theAbbrevName = "";
		private int theSpeciesID = -1; 
		private Vector<String> theKeys = null;
	}	
} // end AnnotData class

