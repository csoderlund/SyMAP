package backend;

import java.sql.SQLException;
import java.sql.ResultSet;

/*
 *  Lightweight group class, for setting up and loading initial groups
 */

public class GroupInt 
{
	String grpName = "";
	String grpPrefix = "";
	int grpIdx = -1;
	int projIdx;
	ProjType type;

	public GroupInt(String name, String prefix, int pidx, ProjType pt)
	{
		grpName = name;
		grpPrefix = prefix;
		projIdx = pidx;
		type = pt;
	}
	
	public String getName() { return grpName; }
	public String getLongName() { return grpPrefix + grpName; }
	public int getGrpIdx() { return grpIdx; }
	
	public void addGrpToDB(UpdatePool pool) throws SQLException
	{
		String st = "SELECT count(*) AS count FROM groups WHERE proj_idx='" + projIdx + "'";
		ResultSet rs = pool.executeQuery(st);
		int sort_order = 0;
		if (rs.next())
		{
			// count from 1 in pseudo case, 0 in fpc case (0=unanchored)
			// this is not good, should be changed
			sort_order = (type == ProjType.pseudo ? 1 + rs.getInt("count") : rs.getInt("count"));
		}
		rs.close();
		
		String grpFullName = grpPrefix + grpName;
		
		st = "INSERT INTO groups VALUES('0','" + projIdx + "','" + grpName + "','" + grpFullName + "','" 
			+ sort_order + "',0)"; 
		pool.executeUpdate(st);
		
		//mGrpIdx = pool.lastID(); // mdb removed 7/1/09
		// mdb added 7/1/09
		st = "SELECT idx FROM groups WHERE proj_idx=" + projIdx + " AND name='" + grpName + "'";
		grpIdx = pool.getIdx(st);
	}
}
