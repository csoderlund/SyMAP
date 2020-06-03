package symap.sequence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;
import number.GenomicsNumber;
import symap.SyMAP;
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
		"SELECT name,type,start,end,strand FROM pseudo_annot WHERE grp_idx=? ORDER BY type DESC"; 
	
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
	 * Sets <code>size</code> and <code>annotations</code> objects according to 
	 * the information in <code>sequence</code> (i.e. project and group) by 
	 * looking in the cache and then the database if needed.
	 * 
	 * @param sequence The requesting Sequence
	 * @param size The GenomicsNumber to be set
	 * @param annotations a <code>Vector</code> to put Annotation objects. 
	 *        The last one will be the centromere if it exists
	 * @return a <code>String</code> value of the group name
	 * @exception SQLException
	 */
	public synchronized String setSequence(Sequence sequence, GenomicsNumber size, Vector<Annotation> annotations) throws SQLException {
		int project = sequence.getProject();
		int group =   sequence.getGroup();
		PseudoData data = null;
		if (pseudoCache != null)
			data = (PseudoData)(pseudoCache.get(new PseudoData(project,group)));
		if (data == null) {
			if (SyMAP.DEBUG)System.out.println("Looking in database for sequence data for group " + group);

			data = new PseudoData(project, group);
			if (pseudoCache != null) pseudoCache.add(data);

			Statement statement = null;
			ResultSet rs = null;
			Vector<AnnotationData> ad = new Vector<AnnotationData>();
			try {
				statement = createStatement();
				rs = statement.executeQuery(setInt(setInt(SIZE_QUERY,group),group));
				rs.next(); // a row no matter what
				data.setSize(rs.getLong(1));
				if (!rs.wasNull()) {
					data.setName(rs.getString(2));
					closeResultSet(rs);
					rs = statement.executeQuery(setInt(ANNOT_QUERY,group));
					while (rs.next()) {
						if (rs.getString(2).equals("hit")) continue;
						AnnotationData annot = new AnnotationData(
								rs.getString(1),	/* String name 		*/
								rs.getString(2),	/* String type 		*/
								rs.getInt(3),		/* int start		*/
								rs.getInt(4),		/* int end			*/
								rs.getString(5));	/* String strand	*/
						
						ad.add(annot);
					}
				}
				else
					System.err.println("No information in db found for Sequence with group id "+group);
				
				closeResultSet(rs);
				closeStatement(statement);

				data.setAnnotationData((AnnotationData[])ad.toArray(new AnnotationData[ad.size()]));
			} catch (SQLException sql) {
				closeResultSet(rs);
				closeStatement(statement);
				close();
				System.err.println("SQL exception acquiring sequence data.");
				System.err.println("Message: " + sql.getMessage());
				throw sql;
			} finally {
				rs = null;
				statement = null;
				ad.clear();
				ad = null;
			}
		}

		size.setBPValue(data.getSize());
		data.setAnnotations(annotations);

		return data.getName();
	}

	/**
	 * Method <code>getGroupID</code> returns the group id for the group name.
	 *
	 * @param group value of group name or group name with prefix, or prefix+0+group.
	 * @param project an int value of the project id
	 * @return an int value of the group id, 0 if not found, or -1 on error
	 */
	public int getGroupID(String group, int project) {
		int id = 0;
		Statement stat = null;
		ResultSet rs = null;
		try {
			stat = createStatement();
			String query = setInt(
					setString("SELECT idx FROM xgroups WHERE (name=? AND proj_idx=?)",group),project);	
			rs = stat.executeQuery(query);
			if (rs.next()) id = rs.getInt(1);
		}
		catch (SQLException e) {
			id = -1;
			System.err.println("Failure to obtain id for group="+group+" project="+project);
			e.printStackTrace();
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
