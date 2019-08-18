package symap.projectmanager.common;

import java.awt.Color;
import java.io.File;
import java.util.Vector;
import java.sql.ResultSet;
import java.sql.SQLException;

import util.ErrorCount;
import util.ProgressDialog;
import util.PropertiesReader;
import backend.UpdatePool;

public class Project {
	private int nIdx;		// unique database index
	public String strDBName;
	private String strType;
	private String strDisplayName;
	private String strDescription;
	private String strCategory;
	private int numGroups;
	private boolean unOrdered = false;
	private Color color;
	String orderAgainst = "";
	String chrLabel = "Chromosome";
	public Vector<Integer> grpIdxList;
	public int numAnnot = 0;
	
	private short nStatus = STATUS_IN_DB; // bitmap - maybe bitmap not necessary?
	public static final short STATUS_ON_DISK = 0x0001; // project exists in file hierarchy
	public static final short STATUS_IN_DB   = 0x0002; // project exists in the database
	
	public Project(int nIdx, String strName, String strType) { 
		this.nIdx = nIdx;
		this.strDBName = strName;
		this.strType = strType;
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
	public boolean isPseudo() { return strType.equals("pseudo"); }
	public boolean isFPC() { return strType.equals("fpc"); }
	
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
		File pfile = new File(dir,"params");
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
		}
	}
	public void updateParameters(UpdatePool db, ProgressDialog progress) 
	{
		try
		{
			String dir = "data/" + (isPseudo() ? "pseudo/" : "fpc/") + strDBName ;
			if (progress != null)
			{
				progress.msg("Read params file from " + dir + "\n");	
			}
			File pfile = new File(dir,"params");
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
				System.out.println("Loaded " + n + " properties for " + strDBName);
			}
			else
			{
				System.out.println("Can't open params file in " + dir);	
				if (progress != null)
				{
					progress.msg("Can't open params file in " + dir);	
				}
				
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.out.println("\n\nWarning: failed to update parameters!!\n\n");
		}
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
			grpIdxList.add(rs.getInt("idx"));
		}
	}
	public String toString() { return strDBName; }

}
