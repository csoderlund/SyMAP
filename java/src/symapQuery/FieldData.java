package symapQuery;

/*************************************************
 * The columns for the query result table
 */
import java.util.Iterator;
import java.util.Vector;
import java.util.TreeSet;
import java.util.TreeMap;

public class FieldData {
 	private static final String [] GROUP_NAMES_LOCAL = {"General"};
	
	private static final String [] GENERAL_COLUMNS =	 { "Row", Q.pgenef, "PgFSize", "HitIdx", Q.blocknum, "BlockScore","RunSize"};
	private static final Boolean [] GENERAL_COLUMN_DEF =  {true   , false   , false     , false   , true      , true        ,false};
	
	//****************************************************************************
	//* Static methods
	//****************************************************************************

	public static String [] getGeneralColumns() {
		return GENERAL_COLUMNS; 
	}
	public static Boolean [] getGeneralColumnDef() {
		return GENERAL_COLUMN_DEF; 
	}
	
	public static FieldData getLocalFields(String [] annos, String[] specNames) {
		FieldData fd = new FieldData();

		// IF THESE ARE CHANGED, MUST UPDATE addRowsWithProgress
		fd.addField(Q.annotation, String.class, Q.PA, "name", Q.ANAME, GROUP_NAMES_LOCAL[0], "Annotation attributes");
		fd.addField("Species 1", String.class, Q.P1, "value", Q.SPECNAME1, GROUP_NAMES_LOCAL[0], "Species 1");
		fd.addField("Species 2", String.class, Q.P2, "value", Q.SPECNAME2, GROUP_NAMES_LOCAL[0], "Species 2");
		fd.addField("Chr 1", String.class, Q.G1, "name", Q.GRPNAME1, GROUP_NAMES_LOCAL[0], "Chromosome 1");
		fd.addField("Chr 2", String.class, Q.G2, "name", Q.GRPNAME2, GROUP_NAMES_LOCAL[0], "Chromosome 2");
		fd.addField("Start 1", Integer.class, Q.PH, "start1", Q.GRP1START, GROUP_NAMES_LOCAL[0], "Group 1 start");
		fd.addField("Start 2", Integer.class, Q.PH, "start2", Q.GRP2START, GROUP_NAMES_LOCAL[0], "Group 2 start");
		fd.addField("End 1", Integer.class, Q.PH, "end1", Q.GRP1END, GROUP_NAMES_LOCAL[0], "Group 1 end");
		fd.addField("End 2", Integer.class, Q.PH, "end2", Q.GRP2END, GROUP_NAMES_LOCAL[0], "Group 2 end");
		// Next 3 fields just for internal use
		fd.addField("Proj 1 idx", Integer.class, Q.PH, "proj1_idx", Q.PROJ1IDX, GROUP_NAMES_LOCAL[0], "");
		fd.addField("Proj 2 idx", Integer.class, Q.PH, "proj2_idx", Q.PROJ2IDX, GROUP_NAMES_LOCAL[0], "");
		fd.addField("Group 1 idx", Integer.class, Q.PH, "grp1_idx", Q.GRP1IDX, GROUP_NAMES_LOCAL[0], "");
		fd.addField("Group 2 idx", Integer.class, Q.PH, "grp2_idx", Q.GRP2IDX, GROUP_NAMES_LOCAL[0], "");
		fd.addField("AnnotStart", String.class, Q.PA, "start", Q.ASTART, GROUP_NAMES_LOCAL[0], "Annotation start");
		fd.addField("AnnotEnd", String.class, Q.PA, "end", Q.AEND, GROUP_NAMES_LOCAL[0], "Annotation end");
		
		fd.addField("HitIdx", String.class, Q.PH, "idx", Q.HITIDX, GROUP_NAMES_LOCAL[0], "Hit idx");
		fd.addField("AnnotGrpIdx", String.class, Q.PA, "grp_idx", Q.AGIDX, GROUP_NAMES_LOCAL[0], "Annotation grp idx");
		fd.addField("AnnotIdx", String.class, Q.PA, "idx", Q.AIDX, GROUP_NAMES_LOCAL[0], "Annotation idx");
		fd.addField("AnnotType", String.class, Q.PA, "type", Q.ATYPE, GROUP_NAMES_LOCAL[0], "Annotation type");
		fd.addField("Grp1Idx", String.class, Q.G1, "idx", Q.GRPIDX1, GROUP_NAMES_LOCAL[0], "GrpIdx 1");
		fd.addField("Grp2Idx", String.class, Q.G2, "idx", Q.GRPIDX2, GROUP_NAMES_LOCAL[0], "GrpIdx 2");
		fd.addField("ProjIdx", String.class, Q.G1, "proj_idx", Q.PROJIDX, GROUP_NAMES_LOCAL[0], "Proj Idx (for annots)");
		fd.addField(Q.blocknum, String.class, Q.B, "blocknum", Q.BNUM, GROUP_NAMES_LOCAL[0], "Block Number");
		fd.addField("BlockScore", String.class, Q.B, "score", Q.BSCORE, GROUP_NAMES_LOCAL[0], "Block Score (#Anchors)");
		fd.addField("RunSize", String.class, Q.PH, "runsize", Q.RSIZE, GROUP_NAMES_LOCAL[0], "Collinear run size");

		for (int x = 0; x < specNames.length; x++)
		{
			fd.addField(specNames[x]+"\n" + Q.nRGN, Integer.class, Q.PH, "runsize", Q.PGF + x, GROUP_NAMES_LOCAL[0], Q.RGNn);			
		}
		// Note, next ones go in backwards
		for(int x=annos.length-1;x>=0; x--) {
			fd.insertField(annos[x], String.class, null, null, Q.ANNO + x, GROUP_NAMES_LOCAL[0], "");
		}
		fd.insertField("PgFSize", Integer.class, null, null, Q.GSIZE, GROUP_NAMES_LOCAL[0] ,"HitGroupSize");
		fd.insertField(Q.pgenef,  Integer.class, null, null, Q.GRP, GROUP_NAMES_LOCAL[0] ,"HitGroup");
		
		fd.addLeftJoin("pseudo_hits_annot", 	"PH.idx = PHA.hit_idx", 		 Q.PHA);
		fd.addLeftJoin("pseudo_annot", 		"PHA.annot_idx = PA.idx", 	 Q.PA);
		fd.addLeftJoin("groups", 			"G.idx = PA.grp_idx", 		 Q.G);
		fd.addJoin("groups", 				"G1.idx = PH.grp1_idx", 		 Q.G1);
		fd.addJoin("groups", 				"G2.idx = PH.grp2_idx", 		 Q.G2);
		fd.addJoin("proj_props", 			"P1.proj_idx = PH.proj1_idx", Q.P1);
		fd.addJoin("proj_props", 			"P2.proj_idx = PH.proj2_idx", Q.P2);

		return fd;
	}
                
	//****************************************************************************
	//* Constructors
	//****************************************************************************

	public FieldData() {
		theViewFields = new Vector<FieldItem> ();
		theJoins = new Vector<JoinItem> ();
	}
        
	//****************************************************************************
	//* Public methods
	//****************************************************************************

	public int getNumFields() { return theViewFields.size(); }
	public String getFieldNameAt(int pos) { return theViewFields.get(pos).getFieldName(); }
	public String [] getDisplayFields() {
		if(theViewFields.size() == 0) return null;

		Vector<String> retVal = new Vector<String> ();
		Iterator<FieldItem> iter = theViewFields.iterator();
		FieldItem item = null;

		while(iter.hasNext()) {
			item = iter.next();
			String field = item.getFieldName();
			retVal.addElement(field);
		}
		return retVal.toArray(new String[retVal.size()]);
	}
	public String [] getDisplayFieldSymbols() {
		if(theViewFields.size() == 0) return null;

		Vector<String> retVal = new Vector<String> ();
		Iterator<FieldItem> iter = theViewFields.iterator();
		FieldItem item = null;

		while(iter.hasNext()) {
			item = iter.next();
			String symbol = item.getDBSymbolName();
			retVal.addElement(symbol);
		}
		return retVal.toArray(new String[retVal.size()]);
	}

	public Class<?> [] getDisplayTypes() {
		if(theViewFields.size() == 0) return null;

		Vector<Class<?>> retVal = new Vector<Class<?>> ();
		Iterator<FieldItem> iter = theViewFields.iterator();
		FieldItem item = null;

		while(iter.hasNext()) {
			item = iter.next();
			Class<?> type = item.getFieldType();
			retVal.addElement(type);
		}
		return retVal.toArray(new Class<?>[retVal.size()]);
	}
	public String [] getDBFieldDescriptions() {
		if(theViewFields.size() == 0) return null;

		String [] retVal = new String[getNumFields()];
		Iterator<FieldItem> iter = theViewFields.iterator();
        int x = 0;
        FieldItem item = null;

        while(iter.hasNext()) {
        		item = iter.next();
        		retVal[x++] = item.getDescription();
        }
        return retVal;
	}
	private String getDBFieldQueryList() {
		Iterator<FieldItem> iter = theViewFields.iterator();
		FieldItem item = null;
		if(iter.hasNext()) {
			item = iter.next();
		}
		String retVal = getFieldReference(item);	
		while(iter.hasNext()) {
			item = iter.next();
			retVal += ", " + getFieldReference(item);
		}	
		return retVal;
	}
	private String getFieldReference(FieldItem item) {
		String retVal;
		if(item.getDBFieldName() == null) 
			retVal = "NULL";
		else
			retVal = item.getDBTable() + "." + item.getDBFieldName();
		
		if(item.getDBSymbolName().length() > 0)
			retVal += " AS " + item.getDBSymbolName();	
		return retVal;
	}
	
	// XXX Called from ListDataPanel.loadData, where the two subQuerys are from LocalQuery Panel
	// the \n are so ListDataPanel can write it to file nicely formatted 
	// The Q constants are added so can find where the symbols are referenced from Q.java 
	public String getLocalFilterQuery(String subQuery, String annotSubQuery, boolean isSynteny, 
					boolean isCollinear, TreeMap<Integer,TreeSet<Integer>> annotGrpIdxList) 
	{
		// First build the subquery which gets the hit_idx list
		String strQ1 = "SELECT PH.idx \nFROM pseudo_hits AS PH ";
		if (hasJoins()) 				strQ1 += "\n" + getJoins();
		if (isSynteny)  				strQ1 += "\nJOIN pseudo_block_hits as "+ Q.PBH+ " ON PBH.hit_idx = PH.idx ";
		strQ1 += "\nWHERE 1 ";
		if(subQuery.length() > 0) 	strQ1 += "\nAND " + subQuery;
		if (isCollinear)			  	strQ1 += "\nAND PH.runsize >= 2";
		
		// Now build the outer query 
		String strQ = "SELECT " + getDBFieldQueryList() + " \n, 0 as isAnnot " +
				" FROM pseudo_hits AS " + Q.PH +
				"\nLEFT JOIN pseudo_block_hits as " + Q.PBH + " on PBH.hit_idx=PH.idx " +
				"\nLEFT JOIN blocks            as " + Q.B  + "  on B.idx=PBH.block_idx ";

		if(hasJoins())  strQ += "\n" + getJoins();
		strQ += "\nWHERE PH.idx in (" + strQ1 + ") AND P1.name='display_name' AND P2.name='display_name'";
		
		if (!annotGrpIdxList.isEmpty()) // Only if Orphan flag
		{
			strQ += "\nUNION\n(\nSELECT " + getDBFieldQueryList();
			strQ += " \n, 1 as isAnnot FROM pseudo_annot as PA ";
			strQ += "\nJOIN 	groups           as G1 	on G1.idx=PA.grp_idx ";
			strQ += "\nJOIN proj_props       as P1   on P1.proj_idx=G1.proj_idx and P1.name='display_name'";
			strQ += "\nleft join pseudo_hits as PH   on PH.idx is null ";	
			strQ += "\nleft join blocks      as B    on B.idx is null ";	
			strQ += "\nleft join groups      as G2   on G2.idx is null ";
			strQ += "\nleft join proj_props as  P2 on P2.proj_idx is null and P2.name='display_name' ";
			strQ += "\nwhere " + annotSubQuery ;
			strQ += "\n)\n";	
		}
		strQ += "\n ORDER BY isAnnot asc, HITIDX asc"; // isAnnot is fixed value?
		return strQ;
	}

	private void addField(	String fieldName, Class<?> type, String dbTable, String dbField, String dbSymbol, String group, String description) {
		FieldItem fd = new FieldItem(fieldName, type, group, description);
		fd.setQuery(dbTable, dbField, dbSymbol);
		theViewFields.add(fd);
	}
	
	private void insertField(String fieldName, Class<?> type, String dbTable, String dbField, String dbSymbol, String group, String description) {
		FieldItem fd = new FieldItem(fieldName, type, group, description);
		fd.setQuery(dbTable, dbField, dbSymbol);
		theViewFields.insertElementAt(fd, 0);
	}
	
	private void addJoin(String table, String condition, String strSymbol) { 
		theJoins.add(new JoinItem(table, condition, strSymbol,false)); }
	
	private void addLeftJoin(String table, String condition, String strSymbol) {
		theJoins.add(new JoinItem(table, condition, strSymbol,true)); }

	private boolean hasJoins() { return !theJoins.isEmpty(); }
	
	private String getJoins() { 
		Iterator<JoinItem> iter = theJoins.iterator();
		String retVal = "";
		while(iter.hasNext()) {
			if(retVal.length() == 0) retVal = iter.next().getJoin();
			else              retVal += " " + iter.next().getJoin();
		}
		return retVal;
	}

	private Vector<FieldItem> theViewFields = null;
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
		public FieldItem(String fieldName, Class<?> type, String groupName, String description) {
			strFieldName = fieldName;
			cType = type;
			strGroup = groupName;
			strDescription = description;
 		}

		public void setQuery(String table, String field, String symbol) {
			strDBTable = table;
			strDBField = field;
			strDBSymbol = symbol;
		}

		public String getFieldName() { return strFieldName; }
		public void setFieldName(String name) { strFieldName = name; }

		public Class<?> getFieldType() { return cType; }
		public void setFieldType(Class<?> newType) { cType = newType; }

		public String getDBTable() { return strDBTable; }
		public void setDBTable(String dbTableName) { strDBTable = dbTableName; }

		public String getDBFieldName() { return strDBField; }
		public void setDBFieldName(String dbFieldName) { strDBField = dbFieldName; }
		
		public String getDBSymbolName() { return strDBSymbol; }
		public void setDBSymbolName(String dbSymbol) { strDBSymbol = dbSymbol; }

		public String getGroupName() { return strGroup; }
		public void setGroupName(String groupName) { strGroup = groupName; }

		public String getDescription() { return strDescription; }
		public void setDescription(String description) { strDescription = description; }

		private String strFieldName;  	//Display name for a table
		private Class<?> cType = null; 	//Data type for the column
		private String strDBTable; 		//Source table for the value
		private String strDBField; 		//Source field in the named DB table
		private String strDBSymbol = "";	//Symbolic name for query value
		private String strGroup;			//Name of the group
		private String strDescription;	//Description of the field
	}
}
