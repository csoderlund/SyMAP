package symap.sequence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;

import number.GenomicsNumber;
import symap.pool.DatabaseUser;
import util.DatabaseReader;
import util.ErrorReport;
import util.ListCache;

/**
 * The PseudoPool handles the cache of data for the Sequence Track.
 */
public class PseudoPool extends DatabaseUser {	
	protected static final String SIZE_QUERY = 
		"SELECT (SELECT length FROM pseudos WHERE grp_idx=?) as size, "+
		"       (SELECT name FROM xgroups WHERE idx=?) as name; ";

	protected static final String ANNOT_QUERY =
		"SELECT idx, type,name,start,end,strand, genenum, gene_idx, tag FROM pseudo_annot "
		+ " WHERE grp_idx=? ORDER BY type DESC"; 
	
	private ListCache pseudoCache;

	public PseudoPool(DatabaseReader dr, ListCache cache) { 
		super(dr);
		pseudoCache = cache;
	}

	public synchronized void close() {
		super.close();
	}

	public synchronized void clear() {
		super.close();
		if (pseudoCache != null) pseudoCache.clear();
	}

	/**
	 * Called by Sequence object
	 * gnsize - enter setBPsize
	 * Annotation Vector = enter annotation objects 
	 */
	public synchronized String setSequence(Sequence seqObj, GenomicsNumber gnsize, Vector<Annotation> annotations) throws SQLException {
		int project = seqObj.getProject();
		int group =   seqObj.getGroup();
		PseudoData pdata = null;
		if (pseudoCache != null)
			pdata = (PseudoData)(pseudoCache.get(new PseudoData(project,group)));
		
		if (pdata != null) {
			gnsize.setBPValue(pdata.getSize());
			pdata.setAnnotations(annotations); // transfers annoData to annotations vector
			return pdata.getName();
		}
		
		pdata = new PseudoData(project, group);
		if (pseudoCache != null) pseudoCache.add(pdata);

		Statement statement = null;
		ResultSet rs = null;
		Vector<AnnotationData> annoVec = new Vector<AnnotationData>();
		try {
			statement = createStatement();
			rs = statement.executeQuery(setInt(setInt(SIZE_QUERY,group),group));
			rs.next(); // a row no matter what
			pdata.setSize(rs.getLong(1));
			if (rs.wasNull()) {
				System.err.println("No information in db found for Sequence with group id "+group);
			}
			else {
				pdata.setName(rs.getString(2));
				closeResultSet(rs);
				
				rs = statement.executeQuery(setInt(ANNOT_QUERY,group));
				while (rs.next()) {
					if (rs.getString(2).equals("hit")) continue; // ???
					
					AnnotationData annot = new AnnotationData(
							rs.getInt(1),		/* idx */
							rs.getString(2),	/* type  */
							rs.getString(3),	/* name - description 		*/
							rs.getInt(4),		/* start		*/
							rs.getInt(5),		/* end			*/
							rs.getString(6),	/* strand	*/
							rs.getInt(7),		/* genenum	*/
							rs.getInt(8),		/* gene_idx */
							rs.getString(9));	/* tag */
					
					annoVec.add(annot);
				}
			}
			closeResultSet(rs);
			closeStatement(statement);
		} catch (SQLException sql) {
			closeResultSet(rs);
			closeStatement(statement);
			close();
			ErrorReport.print(sql, "SQL exception acquiring sequence data.");
			throw sql;
		} finally {
			rs = null;
			statement = null;
		}
		
		try {
			AnnotationData [] annoData = annoVec.toArray(new AnnotationData[annoVec.size()]); // CAS512 simplified
			pdata.setAnnotationData(annoData);
		}
		catch (Exception e) {ErrorReport.print(e, "Process sequence data");}
		
		annoVec.clear();
		annoVec = null;
		
		gnsize.setBPValue(pdata.getSize());
		pdata.setAnnotations(annotations); // transfers annoData to annotations vector

		return pdata.getName();
	}

	/**
	 * returns the group id for the group name, -1 on error
	 * group = group name or group name with prefix, or prefix+0+group.
	 */
	public int getGroupID(String group, int projID) {
		int id = 0;
		Statement stat = null;
		ResultSet rs = null;
		try {
			stat = createStatement();
			String query = setInt(
					setString("SELECT idx FROM xgroups WHERE (name=? AND proj_idx=?)",group),projID);	
			rs = stat.executeQuery(query);
			if (rs.next()) id = rs.getInt(1);
		}
		catch (SQLException e) {
			id = -1;
			ErrorReport.print(e, "Failure to obtain id for group="+group+" project="+projID);
			close();
		}
		finally {
			closeStatement(stat);
			closeResultSet(rs);
			stat = null;
			rs = null;
		}
		return id;
	}
}
