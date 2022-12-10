package backend;

/*********************************************************************
 * This is used everywhere to get a connection.
 * Methods are only used with backend.
 * 
 * CAS506 remove hasTables, getMaxID; a little rearrangement
 * CAS522 removed FPC
 */
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.pool.DatabaseUser;
import util.DatabaseReader;

public class UpdatePool extends DatabaseUser
{
	private static TreeMap<String,Vector<String>> mBulkData;
	private static TreeMap<String,String> mBulkStmt;
	private static int mBulkSize = 100;
	static Pattern mpQUOT = Pattern.compile("[\'\"]");;
	public String mBadStrMsg = "Quotes not permitted";

	public UpdatePool(DatabaseReader dr)
	{
		super(dr);
		
		mBulkData = new TreeMap<String,Vector<String>>();
		mBulkStmt = new TreeMap<String,String>();
		initBulk();
	}
	
	public UpdatePool(String dbstr, String user, String pass) 
	{
		this( getDatabaseReader(SyMAPConstants.DB_CONNECTION_BACKEND, dbstr, user, pass, null) );
	}
	
	public void singleInsert(String tbl, Vector<String> vals) throws SQLException
	{
		String valStr = "('" + Utils.join(vals,"','") + "')";
		String stmt = "INSERT INTO " + tbl + " VALUES\n" + valStr;
		executeUpdate(stmt);
	}
	
	// Eliminate all quotes in strings before DB insertion,
	// a better option is PreparedStatement.
	public String sqlSanitize(String in)
	{
		Matcher m = mpQUOT.matcher(in);
		return m.replaceAll("");
	}
	
	public boolean isSQLSafe(String in)
	{
		Matcher m = mpQUOT.matcher(in);
		return !m.matches();
	}	
	
	public void bulkInsertAdd(String key, Vector<String> vals) throws SQLException
	{
		String valStr = "('" + Utils.join(vals,"','") + "')";
		mBulkData.get(key).add(valStr);
		if (mBulkData.get(key).size() == mBulkSize)
			bulkInsert(key);
	}


	public void finishBulkInserts() throws SQLException
	{
		for (String key : mBulkStmt.keySet())
			bulkInsert(key);
	}
	
	private void bulkSetup(String key, String stmt)
	{
		mBulkStmt.put(key,stmt);
		mBulkData.put(key, new Vector<String>());
	}
	
	private void bulkInsert(String key) throws SQLException
	{
		if (mBulkData.get(key).isEmpty()) return;
		
		String stmt = mBulkStmt.get(key);
		stmt += " VALUES\n" + Utils.join(mBulkData.get(key), ",\n");
		
		executeUpdate(stmt);
		mBulkData.get(key).clear();
	}
	
	// For these methods, all strings coming from user input must be sanitized first
	// (use function sqlSanitize, or use the batch mode of PreparedStatement instead -
	// but note that PreparedStatement is slower)

	private void initBulk()
	{
		String stmt;

		// AnchorsMain.uploadHit
		stmt = "insert into pseudo_hits (pair_idx,proj1_idx,proj2_idx," +
			"grp1_idx,grp2_idx,evalue,pctid,score,strand,start1,end1,start2,end2,query_seq," +
			"target_seq,gene_overlap,countpct,cvgpct,annot1_idx,annot2_idx)";
		bulkSetup("pseudo",stmt);

		stmt = "insert into pseudo_block_hits (hit_idx,block_idx)"; 
		bulkSetup("pseudo_block_hits",stmt);								
		
		stmt = "insert ignore into pseudo_hits_annot (hit_idx,annot_idx,olap)"; // ignore is to prevent double insertions
		bulkSetup("pseudo_hits_annot",stmt);									// for self alignments

		stmt = "insert into pseudo_annot (grp_idx,type,name,start,end,strand,text)";
		bulkSetup("pseudo_annot",stmt);	
	}
	
	public PreparedStatement prepareStatement(String st) throws SQLException
	{
		return getDatabaseReader().getConnection().prepareStatement(st);
	}
	
	public void createProject(String name) throws SQLException
	{
		executeUpdate("INSERT INTO projects (name,type,loaddate, syver) " + // CAS520 add version
				"VALUES('" + name + "','pseudo',NOW(),'" + SyMAP.VERSION + "')");
	}
	
	public void deleteProject(int idx) throws SQLException
	{
		executeUpdate("DELETE FROM projects WHERE idx='" + idx + "' LIMIT 1");	
		resetIdx("idx", "projects"); // CAS520 add
	}
	
	public int getProjIdx(String name) throws SQLException
	{
		int idx = -1;
	
		ResultSet rs = executeQuery(
				"SELECT idx FROM projects WHERE name='" + name + "'");
		
		if (rs.next())
			idx = rs.getInt("idx");
	
		rs.close();
		
		return idx;
	}
	
	public boolean projectExists(String name) throws SQLException
	{
		ResultSet rs = executeQuery("SELECT idx FROM projects WHERE name='" + name + "'");
		boolean exists = rs.next();
		rs.close();
		return exists;
	}
	
	public String getProjProp(int nProjIdx, String name) throws SQLException
	{
		String val = "";
		
		ResultSet rs = executeQuery(
				"SELECT value FROM proj_props WHERE proj_idx='" + nProjIdx + "' " +
				"AND name='" + name + "'");
		if (rs.next())
			val = rs.getString("value");
		rs.close();

		return val;
	}
	
	public boolean tableHasColumn(String table, String column) throws Exception
	{
		boolean ret = false;
		ResultSet rs = executeQuery("show columns from " + table + " where field='" + column + "'");
		if (rs.first()) ret = true;
		rs.close();
		return ret;
	}
}
