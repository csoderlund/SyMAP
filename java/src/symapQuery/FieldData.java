package symapQuery;

import java.util.Iterator;
import java.util.Vector;

/*************************************************
 * The columns for the query result table
 * Added in TableDataPanel. Database query in DBdata. 
 * Any changes to columns need to be changed in DBdata too.
 * The PA.name field contains all the attributes, which is parsed for that section and display
 */

public class FieldData {
 	// type is all Integer, included Block, see Column Comparator, which sorts it correctly
	// leave Q.rowCol for placement, the actual row is computed in DBdata (CAS514 HitIdx->Hit#, #Gene->Gene#)
	// TableDataPanel.createGeneralSelectPanel expects 6 hit columns
	private static final String [] GENERAL_COLUMNS =	 
		{Q.rowCol, Q.blockCol, "Block\nScore",Q.runCol,"PgeneF", "PgFSize",
		Q.hitCol,    "Hit\n%Id", "Hit\n%Sim","Hit\n#Sub", "Hit\nSt", "Hit\nCov"}; // CAS516 add these 4; CAS520 add st; CAS540 add Len
	
	private static final Class <?> []  GENERAL_TYPES = 					// CAS520 had to add for String
		{Integer.class,Integer.class,Integer.class,Integer.class,Integer.class,Integer.class,
		 Integer.class,Integer.class,Integer.class,Integer.class, String.class, Integer.class};
	
	private static final boolean [] GENERAL_COLUMN_DEF =  
		{true, true, true, true, false, false, true, false, false, false, false, false}; // CAS513 HitID=f, Score=t
	
	private static final String [] GENERAL_COLUMN_DESC = 
		{"Row number", 
		 "Block: Synteny block number (chr.chr.#)", 
		 "Block score: Number of hits in the synteny block",
		 "Collinear: Number of adjacent genes and set number (chr.chr.N.#)", 
		 "PgeneF: (Compute PgeneF only) putative gene family number", 
		 "PgFSize: (Compute PgeneF only) putative gene family size",
		 "Hit#: Number representing the hit", 
		 "Hit %Id: Approximate percent identity (exact if one hit)",
		 "Hit %Sim: Approximate percent similarity (exact if one hit)", 
		 "Hit #Sub: Number of subhits in the cluster, where 1 is a single hit",
		 "Hit St: '=' is both hit ends are to the same strand, '!=' otherwise",
		 "Hit Cov: The largest summed subhit lengths of the two species."
		};
	
/* Prefixed with Species: CAS519 add hit and gene start/end/len/strand; CAS543 mv Gene# to start */
// If change here, change in DBdata.makeRow
	private static final String []     SPECIES_COLUMNS = 
		{Q.gNCol, Q.chrCol, Q.gStartCol, Q.gEndCol, Q.gLenCol, Q.gStrandCol,  Q.hStartCol, Q.hEndCol, Q.hLenCol};
	
	private static final Class <?> []  SPECIES_TYPES =   // CAS545 changed 5th to integer to fix 543 bug after moving Gene#
		{String.class, String.class,  Integer.class,Integer.class, Integer.class, String.class, Integer.class, Integer.class,Integer.class};
	
	private static final boolean []    SPECIES_COLUMN_DEF =  
		{ true, false, false, false, false , false, false, false, false};
	
	private static String [] SPECIES_COLUMN_DESC = {
		"Gene#: Sequential. Overlap genes have same number (chr.#.{a-z})", 
		"Chr: Chromosome (or Scaffold, etc)", 
		"Gstart: Start coordinate of gene", 
		"Gend: End coordinate of gene", 
		"Glen: Length of gene", 
		"Gst: Gene strand", 
		"Hstart: Start coordinate of clustered hits", 
		"Hend: End coordinate of clustered hits", 
		"Hlen: Hend-Hstart+1"
	};
	
	// CAS541 made single columns separate so can add NumHits
	private static final String []     SSPECIES_COLUMNS = 
		{ Q.gNCol, Q.chrCol, Q.gStartCol, Q.gEndCol, Q.gLenCol, Q.gStrandCol, Q.gHitNumCol};
	
	private static final Class <?> []  SSPECIES_TYPES =   
		{Integer.class, String.class,  Integer.class,Integer.class, String.class, String.class, Integer.class};
	
	private static final boolean []    SSPECIES_COLUMN_DEF =  
		{ true, false, false, false, false , false, false};
	
	private static String [] SSPECIES_COLUMN_DESC = {
		"Gene#: Sequential. Overlap genes have same number (chr.#.{a-z})", 
		"Chr: Chromosome (or Scaffold, etc)", 
		"Gstart: Start coordinate of gene", 
		"Gend: End coordinate of gene", 
		"Glen: Length of gene", 
		"Gst: Gene strand", 
		
		"NumHits: Number of hits for to the gene in the entire database."
	};
	
	
	//****************************************************************************
	//* Static methods
	//****************************************************************************
	public static String [] getGeneralColHead() 	 {return GENERAL_COLUMNS; }
	public static Class <?> [] getGeneralColType() 	 {return GENERAL_TYPES; }
	public static String [] getGeneralColDesc() 	 {return GENERAL_COLUMN_DESC; }
	public static boolean [] getGeneralColDefaults() {return GENERAL_COLUMN_DEF; }
	
	public static String [] getSpeciesColHead(boolean s)     {if (s) return SSPECIES_COLUMNS;  else return SPECIES_COLUMNS;}
	public static Class <?> [] getSpeciesColType(boolean s)  {if (s) return SSPECIES_TYPES; else return SPECIES_TYPES;}
	public static String [] getSpeciesColDesc(boolean s) 	 {if (s) return SSPECIES_COLUMN_DESC;  else  return SPECIES_COLUMN_DESC;}
	public static boolean [] getSpeciesColDefaults(boolean s){if (s) return SSPECIES_COLUMN_DEF; else return SPECIES_COLUMN_DEF;}
	
	// XXX If change this, change number in Q.java, as they are the numeric index into ResultSet
	// Columns loaded from database, do not correspond to query table columns
	
	public static int getSpColumnCount(boolean isSingle) { // CAS519
		if (isSingle) return SSPECIES_COLUMNS.length;
		else return SPECIES_COLUMNS.length;
	}
	public static int getGenColumnCount(boolean isSingle) { // CAS519
		if (isSingle) return 1; // row number only
		else return GENERAL_COLUMNS.length;
	}
	
	// MySQL fields to load
	public static FieldData getFields() {
		FieldData fd = new FieldData();
		// type not used, see above       sql.table.field    order#		Description  
		fd.addField(String.class, Q.PA, "idx",     Q.AIDX,       "Annotation index");
		fd.addField(String.class, Q.PA, "grp_idx", Q.AGIDX,      "Annotation chromosome idx");
		fd.addField(String.class, Q.PA, "start",   Q.ASTART,     "Annotation start");
		fd.addField(String.class, Q.PA, "end",     Q.AEND,       "Annotation end");
		fd.addField(String.class, Q.PA, "strand",  Q.ASTRAND,    "Annotation strand");
		fd.addField(String.class, Q.PA, "name",    Q.ANAME,      "Annotation attributes"); // Split for "Gene Annotation"
		fd.addField(String.class, Q.PA, "tag", Q.AGENE,      "Annotation gene#"); // CAS518 chg to String/tag; tag is needed to suffix
		fd.addField(String.class, Q.PA, "numhits", Q.ANUMHITS,   "Annotation numHits");// CAS541 Singles only, ignored for Hits
		
		fd.addField(Integer.class,Q.PH, "idx",      Q.HITIDX,    "Hit index"); 
		fd.addField(Integer.class,Q.PH, "hitnum",   Q.HITNUM,    "Hit number"); // CAS520 add
		fd.addField(Integer.class,Q.PH, "proj1_idx",Q.PROJ1IDX,  "1st species index");
		fd.addField(Integer.class,Q.PH, "proj2_idx",Q.PROJ2IDX,  "2nd species index");
		fd.addField(Integer.class,Q.PH, "grp1_idx", Q.GRP1IDX,   "1st chromosome index");
		fd.addField(Integer.class,Q.PH, "grp2_idx", Q.GRP2IDX,   "2nd chromosome index");
		fd.addField(Integer.class,Q.PH, "start1",   Q.HIT1START, "1st hit start");
		fd.addField(Integer.class,Q.PH, "start2",   Q.HIT2START, "2nd hit start");
		fd.addField(Integer.class,Q.PH, "end1",     Q.HIT1END,   "1st hit end");
		fd.addField(Integer.class,Q.PH, "end2",     Q.HIT2END,   "2nd hit end");
		fd.addField(String.class, Q.PH, "pctid",    Q.PID,     	 "Hit Average %Identity"); // CAS516 add these 3
		fd.addField(String.class, Q.PH, "cvgpct",   Q.PSIM,      "Hit Average %Similarity");
		fd.addField(String.class, Q.PH, "countpct", Q.HCNT,      "Clustered hits");
		fd.addField(String.class, Q.PH, "strand",   Q.HST,       "Strand +/-, /-, etc");
		fd.addField(String.class, Q.PH, "score",    Q.HSCORE,    "Summed clustered hits");
		fd.addField(String.class, Q.PH, "runsize",  Q.COSIZE,    "Collinear run size");
		fd.addField(String.class, Q.PH, "runnum",   Q.CONUM,     "Collinear number"); // CAS520 add

		fd.addField(String.class, Q.B, "blocknum",  Q.BNUM,      "Block Number");
		fd.addField(String.class, Q.B, "score",     Q.BSCORE,    "Block Score (#Anchors)");
		
		fd.addField(Integer.class,Q.PH, "annot1_idx",Q.ANNOT1IDX,"Index of 1st anno");
		fd.addField(Integer.class,Q.PH, "annot2_idx",Q.ANNOT2IDX,"Index of 2nd anno");
		
		// The MySQL LEFT JOIN joins two tables and fetches rows based on a condition, which is matching in both the tables and 
		// the unmatched rows will also be available from the table written before the JOIN clause.
		
		fd.addLeftJoin("pseudo_hits_annot", "PH.idx = PHA.hit_idx", 	 Q.PHA);
		fd.addLeftJoin("pseudo_annot", 		"PHA.annot_idx = PA.idx", 	 Q.PA);
		fd.addLeftJoin("pseudo_block_hits", "PBH.hit_idx=PH.idx", 	 	 Q.PBH);
		fd.addLeftJoin("blocks", 			"B.idx=PBH.block_idx", 	 	 Q.B);

		return fd;
	}
	
	//****************************************************************************
	//* Constructors
	//****************************************************************************

	public FieldData() {
		theFields = new Vector<FieldItem> ();
		theJoins = new Vector<JoinItem> ();
	}
        
	//****************************************************************************
	//* Public methods
	//****************************************************************************

	public int getNumFields() { return theFields.size(); }
	
	public String [] getDBFieldDescriptions() {
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
	public String getDBFieldList(boolean isOrphan) {
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
	public String getJoins() { 
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
	private Vector<JoinItem> theJoins = null;
	
	private class JoinItem {
		public JoinItem(String table, String condition, String symbol, boolean left) {
			strTable = table;
			strCondition = condition;
			strSymbol = symbol;
			bleft = left;
		}
		public String getJoin() {
			String retVal = (bleft ? "\nLEFT JOIN " : "\nJOIN ") + strTable; // CAS503 add \n for debugging
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
		public FieldItem(Class<?> type, String description) {
			// cType = type;
			strDescription = description;
 		}

		public void setQuery(String table, String field, int num) {
			strDBTable = table;
			strDBField = field;
			// dbNum = num; index into rs.get; see Q.java
		}
		public String getDBTable() { return strDBTable; }
		public String getDBFieldName() { return strDBField; }
		public String getDescription() { return strDescription; }
		
		private String strDBTable; 		//Source table for the value
		private String strDBField; 		//Source field in the named DB table
		private String strDescription;	//Description of the field
	}
}
