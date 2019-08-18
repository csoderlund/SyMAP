package symapQuery;

import java.util.Iterator;
import java.util.Vector;
import java.util.TreeSet;
import java.util.TreeMap;

import backend.Utils;

public class FieldData {
	//Groups and descriptions for the hit fields
 	private static final String [] GROUP_NAMES_LOCAL = {"General"};
	private static final String [] GROUP_DESCRIPTIONS_LOCAL = {"General information"};
	
	private static final String [] GENERAL_COLUMNS =	 { "Row", "PgeneF", "PgFSize", "HitIdx", "BlockNum", "BlockScore","RunSize"};//,"RStat"};
	private static final Boolean [] GENERAL_COLUMN_DEF =  {true   , true   , true     , false   , true      , true        ,false};//,"RStat"};
	
	//****************************************************************************
	//* Static methods
	//****************************************************************************

	public static String [] getGeneralColumns() {
		return GENERAL_COLUMNS; 
	}
	public static Boolean [] getGeneralColumnDef() {
		return GENERAL_COLUMN_DEF; 
	}
	/**
	 * Adds the POG fields for column select
	 * @param frame controlling frame, null if fields are for column selection
	 * @return the fields to select
	 */
	public static FieldData getLocalFields(String [] annos, String[] specNames) {
		FieldData fd = new FieldData();

		// IF THESE ARE CHANGED, MUST UPDATE addRowsWithProgress
		fd.addField("Annotation", String.class, "PA", "name", "ANAME", GROUP_NAMES_LOCAL[0], "Annotation attributes");
		fd.addField("Species 1", String.class, "P1", "value", "SPECNAME1", GROUP_NAMES_LOCAL[0], "Species 1");
		fd.addField("Species 2", String.class, "P2", "value", "SPECNAME2", GROUP_NAMES_LOCAL[0], "Species 2");
		fd.addField("Chr 1", String.class, "G1", "name", "GRPNAME1", GROUP_NAMES_LOCAL[0], "Chromosome 1");
		fd.addField("Chr 2", String.class, "G2", "name", "GRPNAME2", GROUP_NAMES_LOCAL[0], "Chromosome 2");
		fd.addField("Start 1", Integer.class, "PH", "start1", "GRP1START", GROUP_NAMES_LOCAL[0], "Group 1 start");
		fd.addField("Start 2", Integer.class, "PH", "start2", "GRP2START", GROUP_NAMES_LOCAL[0], "Group 2 start");
		fd.addField("End 1", Integer.class, "PH", "end1", "GRP1END", GROUP_NAMES_LOCAL[0], "Group 1 end");
		fd.addField("End 2", Integer.class, "PH", "end2", "GRP2END", GROUP_NAMES_LOCAL[0], "Group 2 end");
		// Next 3 fields just for internal use
		fd.addField("Proj 1 idx", Integer.class, "PH", "proj1_idx", "PROJ1IDX", GROUP_NAMES_LOCAL[0], "");
		fd.addField("Proj 2 idx", Integer.class, "PH", "proj2_idx", "PROJ2IDX", GROUP_NAMES_LOCAL[0], "");
		fd.addField("Group 1 idx", Integer.class, "PH", "grp1_idx", "GRP1IDX", GROUP_NAMES_LOCAL[0], "");
		fd.addField("Group 2 idx", Integer.class, "PH", "grp2_idx", "GRP2IDX", GROUP_NAMES_LOCAL[0], "");
		fd.addField("AnnotStart", String.class, "PA", "start", "ASTART", GROUP_NAMES_LOCAL[0], "Annotation start");
		fd.addField("AnnotEnd", String.class, "PA", "end", "AEND", GROUP_NAMES_LOCAL[0], "Annotation end");
		
		fd.addField("HitIdx", String.class, "PH", "idx", "HITIDX", GROUP_NAMES_LOCAL[0], "Hit idx");
		fd.addField("AnnotGrpIdx", String.class, "PA", "grp_idx", "AGIDX", GROUP_NAMES_LOCAL[0], "Annotation grp idx");
		fd.addField("AnnotIdx", String.class, "PA", "idx", "AIDX", GROUP_NAMES_LOCAL[0], "Annotation idx");
		fd.addField("AnnotType", String.class, "PA", "type", "ATYPE", GROUP_NAMES_LOCAL[0], "Annotation type");
		fd.addField("Grp1Idx", String.class, "G1", "idx", "GRPIDX1", GROUP_NAMES_LOCAL[0], "GrpIdx 1");
		fd.addField("Grp2Idx", String.class, "G2", "idx", "GRPIDX2", GROUP_NAMES_LOCAL[0], "GrpIdx 2");
		fd.addField("ProjIdx", String.class, "G1", "proj_idx", "PROJIDX", GROUP_NAMES_LOCAL[0], "Proj Idx (for annots)");
		fd.addField("BlockNum", String.class, "B", "blocknum", "BNUM", GROUP_NAMES_LOCAL[0], "Block Number");
		fd.addField("BlockScore", String.class, "B", "score", "BSCORE", GROUP_NAMES_LOCAL[0], "Block Score (#Anchors)");
		fd.addField("RunSize", String.class, "PH", "runsize", "RSIZE", GROUP_NAMES_LOCAL[0], "Collinear run size");
		//fd.addField("RStat", Integer.class, "PH", "runsize", "RSTAT", GROUP_NAMES_LOCAL[0], "Gene family rstat measure");

		for (int x = 0; x < specNames.length; x++)
		{
			fd.addField(specNames[x]+"\n#RGN", Integer.class, "PH", "runsize", "PGF" + x, GROUP_NAMES_LOCAL[0], "RGN#");			
		}
		
		// Note, next ones go in backwards
		for(int x=annos.length-1;x>=0; x--) {
			fd.insertField(annos[x], String.class, null, null, "ANNO" + x, GROUP_NAMES_LOCAL[0], "");
		}
		fd.insertField("PgFSize", Integer.class, null, null, "GSIZE",GROUP_NAMES_LOCAL[0] ,"HitGroupSize");
		fd.insertField("PgeneF", Integer.class, null, null, "GRP",GROUP_NAMES_LOCAL[0] ,"HitGroup");
		
		fd.addLeftJoin("pseudo_hits_annot", "PH.idx = PHA.hit_idx", "PHA");
		fd.addLeftJoin("pseudo_annot", "PHA.annot_idx = PA.idx", "PA");
		fd.addLeftJoin("groups", "G.idx = PA.grp_idx", "G");
		fd.addJoin("groups", "G1.idx = PH.grp1_idx", "G1");
		fd.addJoin("groups", "G2.idx = PH.grp2_idx", "G2");
		fd.addJoin("proj_props", "P1.proj_idx = PH.proj1_idx", "P1");
		fd.addJoin("proj_props", "P2.proj_idx = PH.proj2_idx", "P2");

		return fd;
	}
                
	//****************************************************************************
	//* Constructors
	//****************************************************************************

	/**
	 * Builds an empty field data object
	 */
	public FieldData() {
		theViewFields = new Vector<FieldItem> ();
		theJoins = new Vector<JoinItem> ();
	}
        
	//****************************************************************************
	//* Public methods
	//****************************************************************************

	public int getNumFields() { return theViewFields.size(); }
	
	//****************************************************************************
	//* Methods by position
	//****************************************************************************
	public String getFieldNameAt(int pos) { return theViewFields.get(pos).getFieldName(); }
	public String getGroupNameAt(int pos) { return theViewFields.get(pos).getGroupName(); }
	public String getDescriptionAt(int pos) { return theViewFields.get(pos).getDescription(); }

	public int getFieldPosition(String field) {
		int position = -1;
		for(int x=0; x<theViewFields.size() && position < 0; x++) {
			if(theViewFields.get(x).getFieldName().equals(field)) {
				position = x;
			}
		}

		return position;
	}

	public int getFieldSymbolPosition(String symbol) {
		int position = -1;
		for(int x=0; x<theViewFields.size() && position < 0; x++) {
			if(theViewFields.get(x).getDBSymbolName().equals(symbol)) {
				position = x;
			}
		}

		return position;
	}

	//****************************************************************************
	//* Methods by group
	//****************************************************************************
	public String getGroupDescription(String groupName) {
		String retVal = "";
		
		retVal = getGroupDescriptionLocal(groupName);
		
		return retVal;
	}
	
	private String getGroupDescriptionLocal(String groupName) {
		String retVal = "";

		for(int x=0; retVal.length() == 0 && x<GROUP_NAMES_LOCAL.length; x++) 
			if(GROUP_NAMES_LOCAL[x].equals(groupName))
				retVal = GROUP_DESCRIPTIONS_LOCAL[x];

		return retVal;
	}

	//****************************************************************************
	//* Methods by field
	//****************************************************************************
	public String [] getAllDisplayFields() {
		if(theViewFields.size() == 0) return null;

		String [] retVal = new String[theViewFields.size()];
		Iterator<FieldItem> iter = theViewFields.iterator();
		int x = 0;
		while(iter.hasNext())
			retVal[x++] = iter.next().getFieldName();

		return retVal;
	}

	public int getNumDisplayFields() {
		return theViewFields.size();
	}

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

	public String [] getDBFields() {
		if(theViewFields.size() == 0) return null;

		String [] retVal = new String[getNumFields()];
		Iterator<FieldItem> iter = theViewFields.iterator();
        int x = 0;
        FieldItem item = null;

        while(iter.hasNext()) {
        	item = iter.next();
        	retVal[x++] = item.getFieldName();
        }

        return retVal;
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

	public Class<?> [] getDBFieldTypes() {
		if(theViewFields.size() == 0) return null;

		Class<?> [] retVal = new Class<?>[getNumFields()];
		Iterator<FieldItem> iter = theViewFields.iterator();
		int x = 0;
		FieldItem item = null;

		while(iter.hasNext()) {
			item = iter.next();
			retVal[x++] = item.getFieldType();
		}

		return retVal;
	}

	public String getDBFieldQueryList() {
		Iterator<FieldItem> iter = theViewFields.iterator();
		FieldItem item = null;
		if(iter.hasNext()) {
			item = iter.next();
		}

		String retVal = getFieldReference(item);
/*		if (retVal.startsWith("PH.idx"))
		{
			retVal = "IFNULL(PH.idx,0) as HITIDX";
		}
*/		
		while(iter.hasNext()) {
			item = iter.next();
			retVal += ", " + getFieldReference(item);
		}
		
		return retVal;
	}
	public String getAnnotFieldQueryList() {
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
	
	public String getLocalFilterQuery(String subQuery, String annotSubQuery, boolean isSynteny, 
					boolean isCollinear, TreeMap<Integer,TreeSet<Integer>> annotGrpIdxList) 
	{
		// First build the subquery which gets the hit_idx list
		String strQ1 = "SELECT PH.idx \nFROM pseudo_hits AS PH ";
		if (hasJoins()) strQ1 += "\n" + getJoins();
		if (isSynteny) strQ1 += "\nJOIN pseudo_block_hits as PBH ON PBH.hit_idx = PH.idx ";
		strQ1 += "\nWHERE 1 ";
		if(subQuery.length() > 0)
			strQ1 += "\nAND " + subQuery;
		if (isCollinear)
			strQ1 += "\nAND PH.runsize >= 2";
		
		// Now build the outer query 
		
		String strQ = "SELECT " + getDBFieldQueryList() + " \n, 0 as isAnnot FROM pseudo_hits AS PH";
		strQ += "\nLEFT JOIN pseudo_block_hits as PBH on PBH.hit_idx=PH.idx ";
		strQ += "\nLEFT JOIN blocks as B on B.idx=PBH.block_idx ";

        
		if(hasJoins())
			strQ += "\n" + getJoins();
		strQ += "\nWHERE PH.idx in (" + strQ1 + ") AND P1.name='display_name' AND P2.name='display_name'";
		//strQ += "\n ORDER BY HITIDX asc";
		if (!annotGrpIdxList.isEmpty())
		{
/*			
			// build the include list of projects/groups for the annot query
			// it is overkill as there can only be one group in the group sets...
			Vector<Integer> projlist = new Vector<Integer>();
			projlist.add(0);
			Vector<Integer> grplist = new Vector<Integer>();
			grplist.add(0);
			for (int pidx : annotGrpIdxList.keySet())
			{
				TreeSet<Integer> grps = annotGrpIdxList.get(pidx);
				grplist.addAll(grps);

			}
			String inClause = " PA.type='gene' and PA.grp_idx in (" + Utils.join(grplist,",") + ") ";
			*/
			strQ += "\nUNION\n(\nSELECT " + getDBFieldQueryList() + " \n, 1 as isAnnot FROM pseudo_annot as PA ";
			strQ += "\nJOIN groups as G1 on G1.idx=PA.grp_idx ";
			strQ += "\nJOIN proj_props as P1 on P1.proj_idx=G1.proj_idx and P1.name='display_name'";
			//strQ += "\nleft join pseudo_hits_annot as PHA on PHA.annot_idx=PA.idx ";
			strQ += "\nleft join pseudo_hits as PH on PH.idx is null ";	
			strQ += "\nleft join blocks as B on B.idx is null ";	
			strQ += "\nleft join groups as G2 on G2.idx is null ";
			strQ += "\nleft join proj_props as P2 on P2.proj_idx is null and P2.name='display_name' ";
			strQ += "\nwhere " + annotSubQuery ;
			strQ += "\n)\n";
			
		}
		strQ += "\n ORDER BY isAnnot asc, HITIDX asc";
		return strQ;
	}

	public void addField(	String fieldName, Class<?> type, String dbTable, String dbField, String dbSymbol, String group, String description) {
		FieldItem fd = new FieldItem(fieldName, type, group, description);
		fd.setQuery(dbTable, dbField, dbSymbol);
		theViewFields.add(fd);
	}
	
	public void addField(FieldItem item) {
		theViewFields.add(item);
	}
	
	public void insertField(String fieldName, Class<?> type, String dbTable, String dbField, String dbSymbol, String group, String description) {
		FieldItem fd = new FieldItem(fieldName, type, group, description);
		fd.setQuery(dbTable, dbField, dbSymbol);
		theViewFields.insertElementAt(fd, 0);
	}

	public String [] getFieldGroups() {
		if(theViewFields.size() == 0) return null;

		Vector<String> groups = new Vector<String> ();
		Iterator<FieldItem> iter = theViewFields.iterator();
		while(iter.hasNext()) {
			String group = iter.next().getGroupName();
			if(!groups.contains(group))
				groups.add(group);
		}

		String [] retVal = new String[groups.size()];
		groups.toArray(retVal);
		groups.clear();

		return retVal;
	}

	public String [] getFieldNamesByGroup(String group) {
		if(theViewFields.size() == 0) return null;

		Vector<String> fields = new Vector<String> ();
		Iterator<FieldItem> iter = theViewFields.iterator();
		while(iter.hasNext()) {
			FieldItem item = iter.next();
			if(group.equals(item.getGroupName()))
				fields.add(item.getFieldName());
		}

		String [] retVal = new String[fields.size()];
		fields.toArray(retVal);
		fields.clear();

		return retVal;
	}
	
	private FieldItem getFieldByGroupPos(String group, int pos) {
		int x=-1;
		FieldItem item = null;
		Iterator<FieldItem> iter = theViewFields.iterator();
		while(x < pos && iter.hasNext()) {
			item = iter.next();
			if(item.getGroupName().equals(group)) x++;
		}
		return item;
	}
	
	public String getFieldNameAt(String group, int pos) { return getFieldByGroupPos(group, pos).getFieldName(); } 
	public String getDescriptionAt(String group, int pos) { return getFieldByGroupPos(group, pos).getDescription(); }
	public void addJoin(String table, String condition, String strSymbol) 
		{ theJoins.add(new JoinItem(table, condition, strSymbol,false)); }
	public void addLeftJoin(String table, String condition, String strSymbol) 
	{ theJoins.add(new JoinItem(table, condition, strSymbol,true)); }

	public void setJoins(Vector<JoinItem> joins) {
		if(joins != null) {
			theJoins.clear();
			Iterator<JoinItem> iter = joins.iterator();
			while(iter.hasNext())
				theJoins.add(iter.next());
		}
	}
	public boolean hasJoins() { return !theJoins.isEmpty(); }
	public String getJoins() { 
		Iterator<JoinItem> iter = theJoins.iterator();
		String retVal = "";
		while(iter.hasNext()) {
			if(retVal.length() == 0)
				retVal = iter.next().getJoin();
			else
				retVal += " " + iter.next().getJoin();
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
			String retVal = (bleft ? " LEFT JOIN " : " JOIN ") + strTable;
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

		//Display name for a table
		private String strFieldName;
		//Data type for the column
		private Class<?> cType = null;
		//True=value read directly from DB, False=value calculated
		//Source table for the value
		private String strDBTable;
		//Source field in the named DB table
		private String strDBField;
		//Symbolic name for query value 
		private String strDBSymbol = "";
		//Name of the group
		private String strGroup;
		//Description of the field
		private String strDescription;
	}
}
