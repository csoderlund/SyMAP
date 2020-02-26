package symap.projectmanager.common;

/****************************************************
 * There are TWO Project.java, the other one is in symap.projectmanager.common
 */
import java.awt.Color;
import java.io.File;
import java.util.Vector;
import java.sql.ResultSet;
import java.sql.SQLException;

import backend.Constants;
import util.ErrorReport;
import util.ProgressDialog;
import util.PropertiesReader;
import backend.UpdatePool;

public class Project {
	public String strDBName;
	public String chrLabel = ""; 
	public int numAnnot = 0;
	public long length=0;
	
	private int nIdx;		// unique database index
	private String strType, strDisplayName, strDescription, strCategory;
	private int numGroups;
	private boolean unOrdered = false;
	private Color color;
	
	private String orderAgainst = "";
	private Vector<Integer> grpIdxList;
	private String masked=null;   // CAS500 put on Summary
	
	private short nStatus = STATUS_IN_DB; // bitmap - maybe bitmap not necessary?
	public static final short STATUS_ON_DISK = 0x0001; // project exists in file hierarchy
	public static final short STATUS_IN_DB   = 0x0002; // project exists in the database
	
	public Project(int nIdx, String strName, String strType) { 
		this.nIdx = nIdx;
		this.strDBName = strName;
		this.strType = strType;
		// CAS500 The type is from the directory name under /data, which is 'seq'
		// The type in the db is 'pseudo'.
		if (strType.equals(Constants.seqType)) this.strType=Constants.dbSeqType;
		strCategory = "Uncategorized";
	}
	
	public Project(int nIdx, String strName, String strType, String strDisplayName) { 
		this(nIdx, strName, strType);
		this.strDisplayName = strDisplayName;
	}
	
	public Color getColor() { return color; }
	public void setColor( Color c ) { color = c; }

	public int getID() { return nIdx; }
	public String getDBName() { return strDBName; }
	
	public String getDisplayName() { return strDisplayName; }
	public void setDisplayName(String s) { strDisplayName = s; }
	
	public String getType() { return strType; }
	public boolean isPseudo() { 
		return (strType.equals(Constants.seqType) || strType.equals(Constants.dbSeqType)); 
	}
	public boolean isFPC() { return strType.equals(Constants.fpcType); }
	
	public short getStatus() { return nStatus; }
	public void setStatus(short n) { nStatus = n; }
	
	public String getDescription() { return strDescription; }
	public void setDescription(String s) { strDescription = s; }
	public String getOrderAgainst() { return orderAgainst; }
	public void setOrderAgainst(String s) { orderAgainst = s; }
	
	public String getCategory() { return strCategory; }
	public void setCategory(String s) { strCategory = s; }
	
	public int getNumGroups() { return numGroups; }
	public void setNumGroups(int n) { numGroups = n; } 
	
	public boolean isUnordered() { return unOrdered;}
	public void setUnordered(boolean unord) { unOrdered=false;}
			
	public String chrLabel() { return chrLabel;}
	public void setChrLabel(String lbl) {chrLabel = lbl;}
	
	public void getPropsFromDisk(File dir)
	{
		File pfile = new File(dir,Constants.paramsFile);
		if (pfile.isFile())
		{
			PropertiesReader props = new PropertiesReader( pfile);
			if (props.getProperty("category") != null && !props.getProperty("category").equals(""))
			{
				strCategory = props.getProperty("category");
			}
			if (props.getProperty("display_name") != null && !props.getProperty("display_name").equals(""))
			{
				strDisplayName = props.getProperty("display_name");	
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
				strDescription = props.getProperty("description"); // CAS500 wasn't be read	
			}	
		}
	}
	public void updateParameters(UpdatePool db, ProgressDialog progress) 
	{
		try
		{
			String dir = isPseudo() ? Constants.seqDataDir : Constants.fpcDataDir;
			dir += strDBName;
			
			if (progress != null)
			{
				progress.msg("Read params file from " + dir + "\n");	
			}
			File pfile = new File(dir,Constants.paramsFile);
			if (pfile.isFile())
			{
				PropertiesReader props = new PropertiesReader( pfile);
				db.executeUpdate("delete from proj_props where proj_idx=" + nIdx);
				int n = 0;
				String annotKW = "";
				for (Object obj : props.keySet())
				{
					String pname = obj.toString().trim();
					String value = props.getProperty(pname).trim();
					if (pname.equals("annot_keywords")) annotKW = value;
					db.executeUpdate("insert into proj_props (proj_idx,name,value) values(" + nIdx + ",'" + pname + "','" + value + "')");
					if (progress != null)
					{
						progress.msg(pname + " = " + value);	
					}
					n++;
				}
				if (!annotKW.equals(""))
				{
					db.executeUpdate("delete from annot_key where proj_idx=" + nIdx);
					String[] kws = annotKW.trim().split(",");
					for (String kw : kws)
					{
						kw = kw.trim();
			        		db.executeUpdate("insert into annot_key (proj_idx,keyname,count) values (" + 
			        			nIdx + ",'" + kw + "',0)");
					}
				}
				if (progress != null) progress.msg("\nloaded " + n + " properties");
				if (Constants.TRACE)
					System.out.println("Loaded " + n + " properties for " + strDBName);
			}
			else
			{
				System.out.println("Cannot open params file in " + dir);	
				if (progress != null)
				{
					progress.msg("Cannot open params file in " + dir);	
				}
			}
		}
		catch (Exception e){ErrorReport.print(e, "Failed to update parameters");}
	}
	
	public boolean equals(Object o) {
		if (o instanceof Project) {
			Project p = (Project)o;
			return (strDBName != null && strDBName.equals(p.getDBName())
					&& strType != null && strType.equals(p.getType()));
		}
		
		return false;
	}
	public void loadGroups(UpdatePool db) throws SQLException
	{
		grpIdxList = new Vector<Integer>();
		ResultSet rs = db.executeQuery("select idx from groups where proj_idx=" + nIdx);
		while (rs.next())
		{
			grpIdxList.add(rs.getInt(1));
		}
	}
	public String toString() { return strDBName; }
	
	/************************************************************
	 * CAS500 - needs to be on Summary whether masked is set
	 */
	public boolean isMask(UpdatePool db)  {
		try {
			if (masked!=null) {
				if (masked.equals("yes")) return true;
				else return false;
			}
			
			String value="0";
			String st = "SELECT value FROM proj_props " +
					"WHERE proj_idx='" + nIdx + "' AND name='mask_all_but_genes'";
			ResultSet rs = db.executeQuery(st);
			if (rs.next())
				value = rs.getString(1);
			rs.close();
			
			if (value != null && value.equals("1")) {
				masked="yes";
				return true;
			}
			else {
				masked = "no";
				return false;
			}
		}
		catch (Exception e) {ErrorReport.print(e,"Determine mask status for " + strDBName);}
		return false;
	}
	public void print() {
		if (Constants.TRACE) System.out.format("XYZ %2d %10s %5s status=%d\n", nIdx, strDBName, strType, nStatus);
	}
}
