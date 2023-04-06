package backend;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import database.DBconn;
import database.DBAbsUser;

/*********************************************************************
 * This is used everywhere to get a connection (even when it does not need mBulk).
 * Bulk methods are only used with backend.
 * 
 * CAS506 remove hasTables, getMaxID; a little rearrangement
 * CAS522 removed FPC
 * CAS534 moved createProject, deleteProject, getProjIdx, etc  to Mproject 
 * CAS535 saveAnnoHits used methods in multiple files including here; now all in AnchorsMain with PreparedStatement
 *        old way is faster than PreparedStatement if innodb_flush_log_at_trx_commit=1;
 */

public class UpdatePool extends DBAbsUser {
	private static TreeMap<String,Vector<String>> mBulkData;
	private static TreeMap<String,String> mBulkStmt;
	private static int mBulkSize = 100;
	static Pattern mpQUOT = Pattern.compile("[\'\"]");;
	public String mBadStrMsg = "Quotes not permitted";

	public UpdatePool(DBconn dr) {
		super(dr);
		
		mBulkData = new TreeMap<String,Vector<String>>();
		mBulkStmt = new TreeMap<String,String>();
		initBulk();
	}
	
	// Eliminate all quotes in strings before DB insertion, a better option is PreparedStatement.
	public String sqlSanitize(String in) {
		Matcher m = mpQUOT.matcher(in);
		return m.replaceAll("");
	}	
	
	public void bulkInsertAdd(String key, Vector<String> vals) throws SQLException {
		String valStr = "('" + Utils.join(vals,"','") + "')";
		mBulkData.get(key).add(valStr);
		if (mBulkData.get(key).size() == mBulkSize)
			bulkInsert(key);
	}

	public void finishBulkInserts() throws SQLException {
		for (String key : mBulkStmt.keySet())
			bulkInsert(key);
	}
	
	private void bulkSetup(String key, String stmt) {
		mBulkStmt.put(key,stmt);
		mBulkData.put(key, new Vector<String>());
	}
	
	private void bulkInsert(String key) throws SQLException {
		if (mBulkData.get(key).isEmpty()) return;
		
		String stmt = mBulkStmt.get(key);
		stmt += " VALUES\n" + Utils.join(mBulkData.get(key), ",\n");
		
		executeUpdate(stmt);
		mBulkData.get(key).clear();
	}
	
	// For these methods, all strings coming from user input must be sanitized first
	// (use function sqlSanitize, or use the batch mode of PreparedStatement instead -
	// but note that PreparedStatement is slower)

	private void initBulk(){
		String stmt;

		// AnchorsMain.uploadHit - may use alternative prepareStatements
		stmt = "insert into pseudo_hits (pair_idx,proj1_idx,proj2_idx," +
			"grp1_idx,grp2_idx,evalue,pctid,score,strand,start1,end1,start2,end2,query_seq," +
			"target_seq,gene_overlap,countpct,cvgpct,annot1_idx,annot2_idx)";
		bulkSetup("pseudo",stmt);
		// AnchorsMain.uploadHit - 
		stmt = "insert ignore into pseudo_hits_annot (hit_idx,annot_idx,olap)"; // ignore is to prevent double insertions
		bulkSetup("pseudo_hits_annot",stmt);									// for self alignments

		// may be dead
		stmt = "insert into pseudo_block_hits (hit_idx,block_idx)"; 
		bulkSetup("pseudo_block_hits",stmt);								
		
		stmt = "insert into pseudo_annot (grp_idx,type,name,start,end,strand,text)";
		bulkSetup("pseudo_annot",stmt);	
	}
	
	public PreparedStatement prepareStatement(String st) throws SQLException{
		return getDBconn().getConnection().prepareStatement(st);
	}
}
