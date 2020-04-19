package symapQuery;

/*************************************************
 * The columns for the query result table
 */
import java.util.Iterator;
import java.util.Vector;

public class FieldData {
 	// type is all Integer, included Block, see Column Comparator, which sorts it correctly
	// leave Q.rowCol, though for placement, though the actual row is computed in DBdata
	private static final String [] GENERAL_COLUMNS =	 {Q.rowCol, "PgeneF", "PgFSize", "HitIdx", Q.blockCol, "Block\nScore","Run\nSize"};
	private static final Boolean [] GENERAL_COLUMN_DEF =  {true   , false   , false     , false   , true      , true        ,false};
	
	private static final String [] SPECIES_COLUMNS = {Q.chrCol, Q.startCol,Q.endCol, Q.geneHitCol};
	private static final Class <?> [] SPECIES_TYPES = {String.class, Integer.class, Integer.class, String.class};
	private static final Boolean [] SPECIES_COLUMN_DEF =  {false, false, false     , false};
	
	//****************************************************************************
	//* Static methods
	//****************************************************************************
	public static String [] getGeneralColHead() 		{return GENERAL_COLUMNS; }
	public static Boolean [] getGeneralColDefaults() {return GENERAL_COLUMN_DEF; }
	
	public static String [] getSpeciesColHead() 		{return SPECIES_COLUMNS; }
	public static Class <?> [] getSpeciesColType() 	{return SPECIES_TYPES; }
	public static Boolean [] getSpeciesColDefaults() {return SPECIES_COLUMN_DEF; }
	
	// XXX If change this, change number in Q.java, as they are the numeric index into ResultSet
	// Columns loaded from database, do not correspond to query table columns
	private static int orphanFields = 5; // Columns loaded for orphans
	
	public static FieldData getFields() {
		FieldData fd = new FieldData();
		//     alias not used       type           sql.table.field    order#		Description  
		fd.addField(String.class,    Q.PA, "idx",     Q.AIDX,   "Annotation idx");
		fd.addField(String.class, Q.PA, "grp_idx", Q.AGIDX,  "Annotation grp idx");
		fd.addField(String.class,  Q.PA, "start",   Q.ASTART,  "Annotation start");
		fd.addField(String.class,    Q.PA, "end",     Q.AEND,   "Annotation end");
		fd.addField(String.class,  Q.PA, "name",    Q.ANAME,  "Annotation attributes");
		
		fd.addField(String.class,      Q.PH, "idx",      Q.HITIDX,  "Hit idx");
		fd.addField(Integer.class, 	Q.PH, "proj1_idx", Q.PROJ1IDX,  "Project 1 idx");
		fd.addField(Integer.class, 	Q.PH, "proj2_idx", Q.PROJ2IDX,  "Project 2 idx");
		fd.addField(Integer.class, 		Q.PH, "grp1_idx", Q.GRP1IDX,  "");
		fd.addField(Integer.class, 		Q.PH, "grp2_idx", Q.GRP2IDX,  "");
		fd.addField(Integer.class,     Q.PH, "start1",   Q.GRP1START,  "Group 1 start");
		fd.addField(Integer.class,     Q.PH, "start2",   Q.GRP2START,  "Group 2 start");
		fd.addField(Integer.class,       Q.PH, "end1",     Q.GRP1END,  "Group 1 end");
		fd.addField(Integer.class,       Q.PH, "end2",     Q.GRP2END,  "Group 2 end");
		fd.addField(String.class,     Q.PH, "runsize",  Q.RSIZE,  "Collinear run size");

		fd.addField(String.class,     Q.B, "blocknum",  Q.BNUM,  "Block Number");
		fd.addField(String.class,   Q.B, "score",     Q.BSCORE,  "Block Score (#Anchors)");
		
		fd.addField(Integer.class,     Q.PH, "annot1_idx",  Q.ANNOT1IDX,  "Index of 1st anno");
		fd.addField(Integer.class,     Q.PH, "annot2_idx",  Q.ANNOT2IDX,  "Index of 2nd anno");
		
		// 
		// The MySQL LEFT JOIN joins two tables and fetches rows based on a condition, which is matching in both the tables and 
		// the unmatched rows will also be available from the table written before the JOIN clause.
		fd.addLeftJoin("pseudo_hits_annot", 	"PH.idx = PHA.hit_idx", 		 Q.PHA);
		fd.addLeftJoin("pseudo_annot", 		"PHA.annot_idx = PA.idx", 	 Q.PA);
		fd.addLeftJoin("pseudo_block_hits", 	"PBH.hit_idx=PH.idx", 	 	 Q.PBH);
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
	
	// for column headers
	public Class<?> [] getDisplayTypes() { 
		if(theFields.size() == 0) return null;

		Vector<Class<?>> retVal = new Vector<Class<?>> ();
		Iterator<FieldItem> iter = theFields.iterator();
		FieldItem item = null;

		while(iter.hasNext()) {
			item = iter.next();
			Class<?> type = item.getFieldType();
			retVal.addElement(type);
		}
		return retVal.toArray(new Class<?>[retVal.size()]);
	}
	// used?
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
	private String getDBFieldList(boolean isOrphan) {
		Iterator<FieldItem> iter = theFields.iterator();
		FieldItem item = null;
		
		if (iter.hasNext()) item = iter.next(); // first one
		String sqlCols = item.getDBTable() + "." + item.getDBFieldName();	
		int cnt=1;
		
		while(iter.hasNext()) {
			item = iter.next();
			sqlCols += ", " + item.getDBTable() + "." + item.getDBFieldName();
			
			cnt++;
			if (isOrphan && cnt==orphanFields) break;
		}	
		return sqlCols;
	}
	
	public String makeSQLquery(String hitQuery, String orphanQuery, boolean isOrphan) 
	{
		if (isOrphan) {
			return "SELECT " + getDBFieldList(isOrphan) + 
					" FROM pseudo_annot AS " + Q.PA + orphanQuery;
		}
		return "SELECT " + getDBFieldList(isOrphan) + " FROM pseudo_hits AS " + Q.PH + 
				" \n" + getJoins() +
				" \n" + hitQuery +
				" \n ORDER BY PH.idx  asc";
	}

	private void addField(Class<?> type, String dbTable, String dbField, int num, String description) {
		FieldItem fd = new FieldItem(type, description);
		fd.setQuery(dbTable, dbField, num);
		theFields.add(fd);
	}
	
	private void addJoin(String table, String condition, String strSymbol) { 
		theJoins.add(new JoinItem(table, condition, strSymbol,false)); }
	
	private void addLeftJoin(String table, String condition, String strSymbol) {
		theJoins.add(new JoinItem(table, condition, strSymbol,true)); }
	
	private String getJoins() { 
		Iterator<JoinItem> iter = theJoins.iterator();
		String retVal = "";
		while(iter.hasNext()) {
			if(retVal.length() == 0) retVal = iter.next().getJoin();
			else              retVal += " " + iter.next().getJoin();
		}
		return retVal;
	}

	private Vector<FieldItem> theFields = null;
	private Vector<JoinItem> theJoins = null;
	
	public class JoinItem {
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

	public class FieldItem {
		public FieldItem(Class<?> type, String description) {
			cType = type;
			strDescription = description;
 		}

		public void setQuery(String table, String field, int num) {
			strDBTable = table;
			strDBField = field;
			dbNum = num;
		}
		public Class<?> getFieldType() { return cType; }
		public String getDBTable() { return strDBTable; }
		public String getDBFieldName() { return strDBField; }
		public String getDescription() { return strDescription; }
		public int getOrder() { return dbNum; }
		
		private Class<?> cType = null; 	//Data type for the column
		private String strDBTable; 		//Source table for the value
		private String strDBField; 		//Source field in the named DB table
		private String strDescription;	//Description of the field
		private int dbNum=0;				// index into rs.get; see Q.java
	}
}
