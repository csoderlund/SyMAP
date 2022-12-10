package symap.projectmanager.common;

/****************************************************
 * There are TWO Project.java, the other one is in backend
 * CAS522 remove FPC
 */
import java.awt.Color;
import java.io.File;
import java.util.Date;
import java.util.Vector;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

import backend.Constants;
import util.ErrorReport;
import util.ProgressDialog;
import util.PropertiesReader;
import util.Utilities;
import backend.UpdatePool;

public class Project implements Comparable <Project> {//CAS513 for TreeSet sort
	public String strDBName;
	public String chrLabel = ""; 
	private int numAnnot = 0, numGene = 0, numGap = 0;
	private long length=0;
	
	private int nIdx;		// unique database index
	private String strDisplayName, strDescription, strCategory, strDate="";
	private String strAbbrevName; // CAS519 for Symap query columns
	private boolean hasAbbrev=true; // CAS519 if not set in Parameter, then this set to false
	private int numGroups;
	private Color color;
	private String pathName=""; // CAS512 display on 'params' window
	private int numSynteny=0;	// CAS513 number of synteny it is in
	
	private Vector<Integer> grpIdxList;
	
	private String orderAgainst = "";
	private boolean isMasked=false;   	// CAS500 put on Summary
	
	// CAS504 Not loaded, put on summary
	private String annotKeywords=""; 	
	private boolean isDefaultFiles=true;
	private String min_size="100000";
	
	private short nStatus = STATUS_IN_DB; // bitmap - maybe bitmap not necessary?
	public static final short STATUS_ON_DISK = 0x0001; // project exists in file hierarchy
	public static final short STATUS_IN_DB   = 0x0002; // project exists in the database
	
	public Project(int nIdx, String strName, String loaddate) { 
		this.nIdx = nIdx;
		this.strDBName = strName;
		
		if (loaddate!="")  // CAS513 add loaddate for display
			strDate = reformatDate(loaddate);
		
		strCategory = "Uncategorized";
	}
	public Project(int nIdx, String strName,  String strDisplayName, String loaddate) { 
		this(nIdx, strName, loaddate);
		this.strDisplayName = strDisplayName;
	}
	
	public int compareTo(Project b) {
		return strDisplayName.compareTo(b.strDisplayName);
	}
	public boolean hasGenes() {return numGene>0;}
	public void setAnno(int numGene, int numGap, int numAnnot) {
		this.numGene =  numGene;
		this.numGap =   numGap;
		this.numAnnot = numAnnot;
	}
	public String getAnnoStr() {
		String msg= String.format("Annotations: %,d ", numAnnot);
		if (numGene>0&&numGap>0) msg += String.format("(Genes: %,d, Gap: %,d)", numGene, numGap);
		else if (numGene>0) msg += String.format("(Genes: %,d)", numGene);
		else if (numGap>0)  msg += String.format("(Gaps: %,d)", numGap);
		
		return msg;
	}
	public void setCntSynteny(int cnt) {numSynteny=cnt;} // CAS513 add
	public String getCntSynteny() {
		if (numSynteny==0) return "";
		else return "[" + numSynteny + "]";
	}
	public void setLength(long length) {this.length = length;}
	public long getLength() {return length;}
	public Color getColor() { return color; }
	public void setColor( Color c ) { color = c; }
	public String getPathName() { return pathName;}
	
	public int getID() { return nIdx; }
	public String getDBName() { return strDBName; }
	public String getLoadDate() {return strDate;}
	
	public String getDisplayName() { return strDisplayName; }
	public void setDisplayName(String s) { strDisplayName = s; }
	
	public boolean hasAbbrev() {return hasAbbrev;}
	public String getAbbrevName() {return strAbbrevName; }
	public void setAbbrevName(String s) { 
		if (s==null || s.length()==0 || s.contentEquals("null")) {
			s = Utilities.formatAbbrev(strDisplayName);
			hasAbbrev=false;
		}
		strAbbrevName = s; 
	}
	
	public short getStatus() { return nStatus; }
	public void setStatus(short n) { nStatus = n; }
	
	public String getDescription() { return strDescription; }
	public void setDescription(String s) { strDescription = s; }
	
	public String chrLabel() { return chrLabel;}
	public void setChrLabel(String lbl) {chrLabel = lbl;}
	
	public String getCategory() { return strCategory; }
	public void setCategory(String s) { strCategory = s; }
	
	public int getNumGroups() { return numGroups; }
	public void setNumGroups(int n) { numGroups = n; } 
	
	public String getOrderAgainst() { return orderAgainst; }
	public void setOrderAgainst(String s) { orderAgainst = s; }
	
	public boolean getIsMasked()  { return isMasked;} // CAS504
	public void setIsMasked(boolean m) {isMasked=m;}
	
	// if not loaded, parameters read from disk here (Read again in PropertyFrame)
	// if loaded, read from database in ProjectManagerFrameCommone
	public void getPropsFromDisk(File dir)
	{
		pathName = dir.getName(); 
		File pfile = new File(dir,Constants.paramsFile); 
		if (!pfile.isFile()) return;
		
		PropertiesReader props = new PropertiesReader( pfile);
		if (props.getProperty("category") != null && !props.getProperty("category").equals(""))
		{
			strCategory = props.getProperty("category");
		}
		if (props.getProperty("display_name") != null && !props.getProperty("display_name").equals(""))
		{
			strDisplayName = props.getProperty("display_name");	
		}
		if (props.getProperty("abbrev_name") != null && !props.getProperty("abbrev_name").equals("")) // CAS519 add
		{
			strAbbrevName = props.getProperty("abbrev_name");	
		}
		if (props.getProperty("order_against") != null && !props.getProperty("order_against").equals(""))
		{
			orderAgainst = props.getProperty("order_against");	
		}					
		if (props.getProperty("grp_type") != null && !props.getProperty("grp_type").equals(""))
		{
			chrLabel = props.getProperty("grp_type");	
		}
		if (props.getProperty("description") != null && !props.getProperty("description").equals(""))
		{
			strDescription = props.getProperty("description"); // CAS500 wasn't read	
		}
		// CAS504 added following to put on Summary for Unloaded
		if (props.getProperty("mask_all_but_genes") != null && !props.getProperty("mask_all_but_genes").equals(""))
		{
			String d = props.getProperty("mask_all_but_genes");
			isMasked = (d.equals("1")); 	
		}
		if (props.getProperty("annot_keywords") != null && !props.getProperty("annot_keywords").equals(""))
		{
			annotKeywords = props.getProperty("annot_keywords"); 	
		}
		if (props.getProperty("min_size") != null && !props.getProperty("min_size").equals(""))
		{
			min_size = props.getProperty("min_size"); 	
		}
		if (props.getProperty("sequence_files") != null && !props.getProperty("sequence_files").equals(""))
		{
			isDefaultFiles=false;	
		}
		if (props.getProperty("anno_files") != null && !props.getProperty("anno_files").equals(""))
		{
			isDefaultFiles=false;	
		}
	}
	public String notLoadedInfo() {
		String msg="Parameters: ";
		if (isDefaultFiles) msg += "Default file locations; ";
		else msg += "User file locations; ";
		if (annotKeywords.equals("")) msg += "All anno keywords; ";
		else msg += "Keywords " + annotKeywords + "; ";
		msg += " Min size " + min_size;
		return msg;
	}
	public void updateParameters(UpdatePool db, ProgressDialog progress) {
		try {
			String dir = Constants.seqDataDir;
			dir += strDBName;
			
			if (progress != null)
				progress.msg("Read params file from " + dir + "\n");	
			
			File pfile = new File(dir,Constants.paramsFile);
			if (!pfile.isFile()) {
				System.out.println("Cannot open params file in " + dir);	
				if (progress != null)
					progress.msg("Cannot open params file in " + dir);	
				return;
			}
			/**************************************************/
			db.executeUpdate("delete from proj_props where proj_idx=" + nIdx);
			
			// update proj_props
			PropertiesReader props = new PropertiesReader( pfile);
			int n = 0;
			
			for (Object obj : props.keySet()) {
				String pname = obj.toString().trim();
				String value = props.getProperty(pname).trim();
				
				db.executeUpdate("insert into proj_props (proj_idx,name,value) values(" + nIdx + ",'" + pname + "','" + value + "')");
				if (progress != null)
					progress.msg(pname + " = " + value);	
				n++;
			}
			
			/** CAS519 delete this!! update annot_key
			 * if (pname.equals("annot_keywords")) annotKW = value; was above
			if (!annotKW.equals("")) {
				db.executeUpdate("delete from annot_key where proj_idx=" + nIdx);
				
				String[] kws = annotKW.trim().split(",");
				for (String kw : kws) {
					kw = kw.trim();
		        	db.executeUpdate("insert into annot_key (proj_idx,keyname,count) values (" + 
		        			nIdx + ",'" + kw + "',0)");
				}
			}
			*/
			if (progress != null) progress.msg("\nloaded " + n + " properties");
		}
		catch (Exception e){ErrorReport.print(e, "Failed to update parameters");}
	}
	public void updateGrpPrefix(UpdatePool db, ProgressDialog progress) {
		try {
			Vector <Integer> idxSet = new Vector <Integer> ();
			Vector <String> nameSet = new Vector <String> ();
			
			String prefix="";
			ResultSet rs = db.executeQuery("select value from proj_props "
					+ "where proj_idx=" + nIdx + " and name='grp_prefix'");
			if (rs.next()) prefix=rs.getString(1);
			else {
				System.err.println("No grp_prefix defined for " + strDisplayName);
				return;
			}
			
			rs = db.executeQuery("select idx, name from xgroups where proj_idx=" + nIdx);
			while (rs.next()) {
				int ix = rs.getInt(1);
				String name = rs.getString(2);
				if (name.startsWith(prefix)) {
					name = name.substring(prefix.length());
					idxSet.add(ix);
					nameSet.add(name);
				}
			}
			rs.close();
			
			if (progress != null) progress.msg("Change " + idxSet.size() + " grp names");
			else System.err.println("Change " + idxSet.size() + " group names to remove '" + prefix + "'" );
			
			for (int i=0; i<idxSet.size(); i++) {					
					db.executeUpdate("update xgroups set name='" + nameSet.get(i) + 
												  "' WHERE idx=" + idxSet.get(i));
			}
		}
		catch (Exception e){ErrorReport.print(e, "Failed to update parameters");}
	}
	public boolean equals(Object o) {
		if (o instanceof Project) {
			Project p = (Project)o;
			return (strDBName != null && strDBName.equals(p.getDBName()));
		}
		return false;
	}
	public void loadGroups(UpdatePool db) throws SQLException
	{
		grpIdxList = new Vector<Integer>();
		ResultSet rs = db.executeQuery("select idx from xgroups where proj_idx=" + nIdx);
		while (rs.next()) {
			grpIdxList.add(rs.getInt(1));
		}
	}
	public String toString() { return strDBName; }
	
	// CAS513 add for date shown on left
	private String reformatDate(String load) {
		try {
			String dt;
			if (load.indexOf(" ")>0) dt = load.substring(0, load.indexOf(" "));
			else if (load.startsWith("20")) dt = load;
			else return "loaded";
			
			SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd"); 
	        Date d = sdf.parse(dt);
	        sdf = new SimpleDateFormat("ddMMMyy"); 
	        return sdf.format(d);
		}
		catch (Exception e) {return "loaded";}
	}
}
