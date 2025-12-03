package symapQuery;

import java.util.Iterator;
import java.util.Vector;

import symap.Globals;

/*************************************************
 * The columns for the query result table
 * Added in TableDataPanel. Database query in DBdata. 
 * Any changes to columns need to be changed in DBdata too (and Q.java).
 * The PA.name field contains all the attributes, which is parsed for that section and display
 */

public class TableColumns {
 	// see Column Comparator for sorting Block and Collinear
	// leave Q.rowCol for placement, the actual row is computed in DBdata 
	// TableDataPanel.createGeneralSelectPanel expects N_GENERAL columns
	
	protected static final int N_GEN_HIT=7; // number of Hit columns
	protected static final int COLLINEAR=3; // TableDataPanel.defaultColumns, set collinear if it is the query
	protected static final int GROUP=4;	 // TableDataPanel.defaultColumns, set group if it is the query
	
	private static final String [] GENERAL_COLUMNS =	 
		{Q.rowCol, Q.blockCol, "Block\nHits", Q.cosetCol, Q.grpCol, 
		Q.hitCol,  Q.hitPrefix+"\nCov", Q.hitPrefix+"\n%Id", Q.hitPrefix+"\n%Sim",
		Q.hitPrefix+"\n#Sub", Q.hitSt, Q.hitPrefix+"\nType"}; 
	
	private static final Class <?> []  GENERAL_TYPES = 					
		{Integer.class, Integer.class, Integer.class, String.class, String.class, 
		 Integer.class, Integer.class, Integer.class, Integer.class,
		 Integer.class, String.class, String.class};
	
	private static final boolean [] GENERAL_COLUMN_DEF =  
		{true, true, false, false, false, false, false, false, false, false, false, false}; 
	
	private static final String [] GENERAL_COLUMN_DESC = 
		{"Row number", 
		 "Block: Synteny block number (chr.chr.#)", 
		 "Block Hits: Number of hits in the synteny block",
		 "Collinear: N is number genes, # is set (chr.chr.size:#)", 
		 "Group: N is number genes, # is group (size:#); sort on group #", 		// see ComputeMulti, ComputePgeneF, or Gene#
		 "Hit#: Number representing the hit", 
		 
		 "Hit Cov: The largest summed merged subhit lengths of the two species", // Descriptions are in QueryPanel too
		 "Hit %Id: Approximate percent identity of subhits",					
		 "Hit %Sim: Approximate percent similarity of subhits", 
		 
		 "Hit #Sub: Number of subhits in the cluster, where 1 is a single hit",
		 "Hit St: The strands separated by a '/'",
		 "Algo1: g2, g1, g0; Algo2: E is exon, I is intron, n is neither."
		};
	
/* Prefixed with Species */
// If change here, change in DBdata.makeRow
	private static final String []     SPECIES_COLUMNS = 
		{Q.gNCol, Q.gOlap, Q.chrCol, 
		 Q.gStartCol, Q.gEndCol, Q.gLenCol, Q.gStrandCol,  
		 Q.hStartCol, Q.hEndCol, Q.hLenCol};
	
	private static final Class <?> []  SPECIES_TYPES =   
		{String.class, Integer.class, String.class,  
		 Integer.class,Integer.class, Integer.class, String.class, 
		 Integer.class, Integer.class, Integer.class};
	
	private static final boolean []    SPECIES_COLUMN_DEF =  
		{ true, false, false, false, false , false, false, false, false, false};
	
	private static int OLAP_COL=1; // changed based on Algo
	private static String [] SPECIES_COLUMN_DESC = {
		"Gene#: Sequential. Overlap genes have same number (chr.#.{a-z})", 
		"Olap: Algo1 percent gene overlap, Algo2 percent exon overlap",
		"Chr: Chromosome (or Scaffold, etc)", 
		"Gstart: Start coordinate of gene", 
		"Gend: End coordinate of gene", 
		"Glen: Length of gene", 
		"Gst: Gene strand", 
		"Hstart: Start coordinate of clustered hits", 
		"Hend: End coordinate of clustered hits", 
		"Hlen: Hend-Hstart+1"
	};
	
	// single columns separate so can add NumHits
	private static final String []     SSPECIES_COLUMNS = 
		{ Q.gNCol, Q.gHitNumCol, Q.chrCol, Q.gStartCol, Q.gEndCol, Q.gLenCol, Q.gStrandCol}; 
	
	private static final Class <?> []  SSPECIES_TYPES =   
		{Integer.class, Integer.class, String.class,  Integer.class,Integer.class, Integer.class, String.class}; 
	
	private static final boolean []    SSPECIES_COLUMN_DEF =  
		{ true, false, false, false, false , false, false};
	
	private static String [] SSPECIES_COLUMN_DESC = {
		"Gene#: Sequential. Overlap genes have same number (chr.#.{a-z})", 
		"NumHits: Number of hits to the gene in the entire database.",
		"Chr: Chromosome (or Scaffold, etc)", 
		"Gstart: Start coordinate of gene", 
		"Gend: End coordinate of gene", 
		"Glen: Length of gene", 
		"Gst: Gene strand"
	};
	
	//****************************************************************************
	//* Static methods
	//****************************************************************************
	protected static String [] getGeneralColHead() 	 {return GENERAL_COLUMNS; }
	protected static Class <?> [] getGeneralColType() 	 {return GENERAL_TYPES; }
	protected static String [] getGeneralColDesc() 	 {return GENERAL_COLUMN_DESC; }
	protected static boolean [] getGeneralColDefaults() {return GENERAL_COLUMN_DEF; }
	
	protected static String [] getSpeciesColHead(boolean s)     {if (s) return SSPECIES_COLUMNS;  else return SPECIES_COLUMNS;}
	protected static Class <?> [] getSpeciesColType(boolean s)  {if (s) return SSPECIES_TYPES; else return SPECIES_TYPES;}
	protected static String [] getSpeciesColDesc(boolean s) 	{if (s) return SSPECIES_COLUMN_DESC;  else  return SPECIES_COLUMN_DESC;}
	protected static boolean [] getSpeciesColDefaults(boolean s){if (s) return SSPECIES_COLUMN_DEF; else return SPECIES_COLUMN_DEF;}
	
	// XXX If change this, change number in Q.java, as they are the numeric index into ResultSet
	// Columns loaded from database, do not correspond to query table columns
	
	protected static int getSpColumnCount(boolean isSingle) { 
		if (isSingle) return SSPECIES_COLUMNS.length;
		else return SPECIES_COLUMNS.length;
	}
	protected static int getGenColumnCount(boolean isSingle) { 
		if (isSingle) return 1; // row number only
		else return GENERAL_COLUMNS.length;
	}
	
	// MySQL fields to load; this is for pairs; order must be same as Q numbers
	protected static TableColumns getFields(boolean isIncludeMinor, boolean isAlgo2) {
		TableColumns fd = new TableColumns();
		// type not used, see above       sql.table.field    order#		Descriptions are not used; see COLUMN_DESC
		fd.addField(String.class, Q.PA, "idx",     Q.AIDX,       "Annotation index");
		fd.addField(String.class, Q.PA, "grp_idx", Q.AGIDX,      "Annotation chromosome idx");
		fd.addField(String.class, Q.PA, "start",   Q.ASTART,     "Annotation start");
		fd.addField(String.class, Q.PA, "end",     Q.AEND,       "Annotation end");
		fd.addField(String.class, Q.PA, "strand",  Q.ASTRAND,    "Annotation strand");
		fd.addField(String.class, Q.PA, "name",    Q.ANAME,      "Annotation attributes"); 	// Split for "Gene Annotation"
		fd.addField(String.class, Q.PA, "tag", 	   Q.AGENE,      "Annotation gene#"); 		// tag is needed to suffix
		fd.addField(String.class, Q.PA, "numhits", Q.ANUMHITS,   "Annotation numHits");		// Singles only, ignored for Hits
		
		fd.addField(Integer.class,Q.PH, "idx",      Q.HITIDX,    "Hit index"); 
		fd.addField(Integer.class,Q.PH, "hitnum",   Q.HITNUM,    "Hit number"); 			
		fd.addField(Integer.class,Q.PH, "proj1_idx",Q.PROJ1IDX,  "1st species index");
		fd.addField(Integer.class,Q.PH, "proj2_idx",Q.PROJ2IDX,  "2nd species index");
		fd.addField(Integer.class,Q.PH, "grp1_idx", Q.GRP1IDX,   "1st chromosome index");
		fd.addField(Integer.class,Q.PH, "grp2_idx", Q.GRP2IDX,   "2nd chromosome index");
		fd.addField(Integer.class,Q.PH, "start1",   Q.HIT1START, "1st hit start");
		fd.addField(Integer.class,Q.PH, "start2",   Q.HIT2START, "2nd hit start");
		fd.addField(Integer.class,Q.PH, "end1",     Q.HIT1END,   "1st hit end");
		fd.addField(Integer.class,Q.PH, "end2",     Q.HIT2END,   "2nd hit end");
		fd.addField(String.class, Q.PH, "pctid",    Q.PID,     	 "Hit Average %Identity"); 	
		fd.addField(String.class, Q.PH, "cvgpct",   Q.PSIM,      "Hit Average %Similarity");
		fd.addField(String.class, Q.PH, "countpct", Q.HCNT,      "Clustered hits");
		fd.addField(String.class, Q.PH, "strand",   Q.HST,       "Strand +/-, /-, etc");
		fd.addField(String.class, Q.PH, "score",    Q.HSCORE,    "Summed clustered hits");
		fd.addField(String.class, Q.PH, "htype",    Q.HTYPE,     "Type of Exon (E), Intron(I), neither(n)");
		fd.addField(String.class, Q.PH, "runsize",  Q.COSIZE,    "Collinear run size");
		fd.addField(String.class, Q.PH, "runnum",   Q.CONUM,     "Collinear number"); 		
		
		fd.addField(String.class, Q.B, "blocknum",  Q.BNUM,      "Block Number");
		fd.addField(String.class, Q.B, "score",     Q.BSCORE,    "Block Score (#Anchors)");
		
		if (isAlgo2 && Globals.bQueryOlap==false) {
			fd.addField(Integer.class,Q.PHA, "exlap", Q.AOLAP, "Exon overlap"); 
			SPECIES_COLUMN_DESC[OLAP_COL] = "Percent exon overlap";				// CAS578 make specific
		}
		else { 
			fd.addField(Integer.class,Q.PHA, "olap", Q.AOLAP, "Gene overlap");
			SPECIES_COLUMN_DESC[OLAP_COL] = "Percent gene overlap";
		}
		fd.addField(Integer.class,Q.PH, "annot1_idx",Q.ANNOT1IDX,"Index of 1st anno");  	// Not for display
		fd.addField(Integer.class,Q.PH, "annot2_idx",Q.ANNOT2IDX,"Index of 2nd anno");  	// matched with PA.idx in DBdata
		
		fd.addLeftJoin("pseudo_hits_annot", "PH.idx = PHA.hit_idx",  Q.PHA); 
		fd.addLeftJoin("pseudo_annot", 		"PHA.annot_idx = PA.idx", Q.PA);
		fd.addLeftJoin("pseudo_block_hits", "PBH.hit_idx = PH.idx",  Q.PBH);
		fd.addLeftJoin("blocks", 			"B.idx = PBH.block_idx", Q.B);

		return fd;
	}
	
	//****************************************************************************
	//* Constructors
	//****************************************************************************

	protected TableColumns() {
		theFields = new Vector<FieldItem> ();
		theJoins = new Vector<JoinItem> ();
	}
        
	//****************************************************************************
	//* Public methods
	//****************************************************************************

	protected int getNumFields() { return theFields.size(); }
	
	protected String [] getDBFieldDescriptions() {
		if(theFields.size() == 0) return null;

		String [] retVal = new String[getNumFields()];
		Iterator<FieldItem> iter = theFields.iterator();
        int x = 0;
        FieldItem item = null;

        while(iter.hasNext()) {
        	item = iter.next();
        	retVal[x++] = item.getDescription();
        }
        return retVal;
	}
	// Used in Select getDBFieldList() from ....
	protected String getDBFieldList(boolean isOrphan) {
		Iterator<FieldItem> iter = theFields.iterator();
		FieldItem item = null;
		
		if (iter.hasNext()) item = iter.next(); // first one
		String sqlCols = item.getDBTable() + "." + item.getDBFieldName();	
		int cnt=1;
		
		while(iter.hasNext()) {
			item = iter.next();
			sqlCols += ", " + item.getDBTable() + "." + item.getDBFieldName();
			
			cnt++;
			if (isOrphan && cnt>SSPECIES_COLUMNS.length) break;
		}	
		return sqlCols;
	}
	protected String getJoins() { 
		Iterator<JoinItem> iter = theJoins.iterator();
		String retVal = "";
		while(iter.hasNext()) {
			if(retVal.length() == 0) retVal = iter.next().getJoin();
			else              retVal += " " + iter.next().getJoin();
		}
		return retVal;
	}
	
	/****************************************************
	 * private 
	 */
	private void addField(Class<?> type, String dbTable, String dbField, int num, String description) {
		FieldItem fd = new FieldItem(type, description);
		fd.setQuery(dbTable, dbField, num);
		theFields.add(fd);
	}
	private void addLeftJoin(String table, String condition, String strSymbol) {
		theJoins.add(new JoinItem(table, condition, strSymbol,true)); 
	}
	
	private Vector<FieldItem> theFields = null;
	private Vector<JoinItem>  theJoins = null;
	
	private class JoinItem {
		protected JoinItem(String table, String condition, String symbol, boolean left) {
			strTable = table;
			strCondition = condition;
			strSymbol = symbol;
			bleft = left;
		}
		protected String getJoin() {
			String retVal = (bleft ? "\nLEFT JOIN " : "\nJOIN ") + strTable; 
			if(strSymbol.length() > 0)
				retVal += " AS " + strSymbol;
			retVal += " ON " + strCondition;
			return  retVal;
		}
		
		private String strTable = "";
		private String strCondition = "";
		private String strSymbol = "";
		private boolean bleft = false;
	}

	private class FieldItem {
		protected FieldItem(Class<?> type, String description) {
			// cType = type;
			strDescription = description;
 		}

		protected void setQuery(String table, String field, int num) {
			strDBTable = table;
			strDBField = field;
			// dbNum = num; index into rs.get; see Q.java
		}
		protected String getDBTable() { return strDBTable; }
		protected String getDBFieldName() { return strDBField; }
		protected String getDescription() { return strDescription; }
		
		private String strDBTable; 		//Source table for the value
		private String strDBField; 		//Source field in the named DB table
		private String strDescription;	//Description of the field
	}
}
