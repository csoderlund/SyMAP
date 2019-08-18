package backend;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import symap.SyMAPConstants;
import symap.pool.DatabaseUser;
import util.DatabaseReader;
import util.Utilities;
import backend.Project;

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
	
//	// WMN not thread safe!! 
//	// (And doesn't work with pools which we shouldn't be using anyway. )
//	public Integer lastID() throws SQLException
//	{
//		int id = -1;
//		
//		String st = "SELECT last_insert_id() AS id";
//		ResultSet rs = executeQuery(st);
//		if (rs.next())
//			id = rs.getInt("id");
//		else
//			throw(new SQLException("Couldn't get last ID"));
//			
//		rs.close();
//		return id;
//	}
	public int getMaxID(String table, String field) throws Exception
	{
		String sql = "select max(" + field + ") as maxidx from " + table;
		ResultSet rs = executeQuery(sql);
		rs.first();
		return rs.getInt("maxidx");
	}
	public void bulkSetup(String key, String stmt)
	{
		mBulkStmt.put(key,stmt);
		mBulkData.put(key, new Vector<String>());
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
	
	private void bulkInsert(String key) throws SQLException
	{
		if (mBulkData.get(key).isEmpty()) return;
		
		String stmt = mBulkStmt.get(key);
		stmt += " VALUES\n" + Utils.join(mBulkData.get(key), ",\n");
		//System.out.println("bulkInsert: " + stmt);
		executeUpdate(stmt);
		mBulkData.get(key).clear();
	}
	
	public void finishBulkInserts() throws SQLException
	{
		for (String key : mBulkStmt.keySet())
			bulkInsert(key);
	}
	
	// For these methods, all strings coming from user input must be sanitized first
	// (use function sqlSanitize, or use the batch mode of PreparedStatement instead -
	// but note that PreparedStatement is slower)

	private void initBulk()
	{
		String stmt = "insert into bes_hits (pair_idx,proj1_idx,proj2_idx,clone," +
			"bes_type," + "grp2_idx,evalue,pctid,score,strand,start1,end1,start2," +
			"end2,query_seq,target_seq,gene_overlap)";
		bulkSetup("bes",stmt);

		stmt = "insert into mrk_hits (pair_idx,proj1_idx,marker," +
			"grp2_idx,evalue,pctid,score,strand,start1,end1,start2,end2,query_seq," +
			"target_seq,gene_overlap)";
		bulkSetup("mrk",stmt);

		stmt = "insert into pseudo_hits (pair_idx,proj1_idx,proj2_idx," +
			"grp1_idx,grp2_idx,evalue,pctid,score,strand,start1,end1,start2,end2,query_seq," +
			"target_seq,gene_overlap,countpct,cvgpct,annot1_idx,annot2_idx)";
		bulkSetup("pseudo",stmt);

		stmt = "insert into bes_block_hits (hit_idx,ctghit_idx)";
		bulkSetup("bes_block_hits",stmt);
		
		stmt = "insert into mrk_block_hits (hit_idx,ctghit_idx)";
		bulkSetup("mrk_block_hits",stmt);
		
		stmt = "insert into pseudo_block_hits (hit_idx,block_idx)"; 
		bulkSetup("pseudo_block_hits",stmt);								
		
		stmt = "insert ignore into pseudo_hits_annot (hit_idx,annot_idx,olap)"; // ignore is to prevent double insertions
		bulkSetup("pseudo_hits_annot",stmt);									// for self alignments

		stmt = "insert into clone_remarks_byctg (ctg_idx,remark_idx,count)";
		bulkSetup("clone_remarks_byctg",stmt);

		stmt = "insert into bes_seq (proj_idx,clone,type,seq,rep)";
		bulkSetup("bes_seq",stmt);
		
		stmt = "insert into clones (proj_idx,name,ctg_idx,cb1,cb2,bes1,bes2,remarks)";
		bulkSetup("clones",stmt);

		stmt = "insert into markers (proj_idx,name,type,remarks)";
		bulkSetup("markers",stmt);

		stmt = "insert into mrk_clone (mrk_idx,clone_idx)";
		bulkSetup("mrk_clone",stmt);

		stmt = "insert into mrk_ctg (mrk_idx,ctg_idx,pos,nhits)";
		bulkSetup("mrk_ctg",stmt);

		stmt = "insert into mrk_seq (proj_idx,marker,seq)";
		bulkSetup("mrk_seq",stmt);	

		stmt = "insert into pseudo_annot (grp_idx,type,name,start,end,strand,text)";
		bulkSetup("pseudo_annot",stmt);	
	}
	
	public PreparedStatement prepareStatement(String st) throws SQLException
	{
		return getDatabaseReader().getConnection().prepareStatement(st);
	}
	
	public void createProject(String name, ProjType type) throws SQLException
	{
		executeUpdate("INSERT INTO projects (name,type,loaddate) " +
							"VALUES('" + name + "','" + 
							type.toString().toLowerCase() + "',NOW())");
	}
	
	public void deleteProject(int idx) throws SQLException
	{
		executeUpdate("DELETE FROM projects WHERE idx='" + idx + "' LIMIT 1");			
	}
	
	public int getProjIdx(String name, ProjType type) throws SQLException
	{
		int idx = -1;
	
		ResultSet rs = executeQuery(
				"SELECT idx FROM projects WHERE name='" + name + 
				"' AND type='" + type.toString().toLowerCase() + "'");
		
		if (rs.next())
			idx = rs.getInt("idx");
	
		rs.close();
		
		return idx;
	}
	
	public ProjType getProjType(String name) throws SQLException
	{
		ResultSet rs = executeQuery("SELECT type FROM projects WHERE name='" + name + "'");
		if (rs.next()) {
			String strType = rs.getString("type");
			for (ProjType t : ProjType.values())
				if (t.toString().equals(strType)) {
					rs.close();
					return t;
				}
		}
		rs.close();
	
		return ProjType.unknown;
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
	public boolean hasTables() throws Exception
	{
		ResultSet rs = executeQuery("show tables");
		return rs.first();
	}
	public boolean tableHasColumn(String table, String column) throws Exception
	{
		boolean ret = false;
		ResultSet rs = executeQuery("show columns from " + table + " where field='" + column + "'");
		if (rs.first()) ret = true;
		rs.close();
		return ret;
	}
	public void updateSchemaTo40() throws Exception
	{
		updateSchemaTo35();
		if (!tableHasColumn("groups","fullname"))
		{
			executeUpdate("alter table groups add fullname varchar(40) after name");
			executeUpdate("update groups set fullname=name");
			executeUpdate("update groups,proj_props set fullname=concat(proj_props.value,groups.name) where " +
					" proj_props.proj_idx=groups.proj_idx and proj_props.name='grp_prefix'");
		}
		if (!tableHasColumn("pseudo_hits", "runsize"))
		{
			System.err.println("Adding runsize column to database");
			executeUpdate("alter table pseudo_hits add runsize int default 0");
		}

		if (!tableHasColumn("blocks", "genef1"))
		{
			System.err.println("Adding gene fraction columns to blocks table");
			executeUpdate("alter table blocks add score integer default 0 after corr");
			executeUpdate("alter table blocks add ngene1 integer default 0 after score");
			executeUpdate("alter table blocks add ngene2 integer default 0 after ngene1");
			executeUpdate("alter table blocks add genef1 float default 0 after ngene2");
			executeUpdate("alter table blocks add genef2 float default 0 after genef1");
			Utils.updateGeneFractions(this);
		}
	}
	public void updateSchemaTo35() throws Exception
	{
		ResultSet rs = executeQuery("show tables like 'annot_key'");
		if (!rs.first())
		{
			executeUpdate("CREATE Table annot_key ( " +
				" idx 				INTEGER AUTO_INCREMENT PRIMARY KEY,	 " +
				" proj_idx			INTEGER NOT NULL, " +
				" keyname				TEXT, " +
				" count				BIGINT DEFAULT 0, " +
				" FOREIGN KEY (proj_idx) REFERENCES projects (idx) ON DELETE CASCADE " +
				" ) ENGINE = InnoDB; ");
		}
	}
}
