package symap.sequence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;
import number.GenomicsNumber;
import symap.SyMAP;
import symap.pool.DatabaseUser;
import util.DatabaseReader;
import util.ListCache;

/**
 * The PseudoPool handles the cache of data for the Sequence Track.
 * 
 * @author Austin Shoemaker
 */
public class PseudoPool extends DatabaseUser {	
//	private static final boolean TIME_TRACE = false;
	
// mdb removed 8/29/07 #120
//	protected static final String ID_QUERY = 
//		"SELECT g.idx "+
//		"FROM "+GROUPS_TABLE+" AS g,"+PROJECT_PROPS_TABLE+" AS p "+
//		"WHERE g.proj_idx=? AND p.proj_idx=? AND p.name='"+GRP_PREFIX_PROP+"' AND "+
//		"      (g.name=? OR CONCAT(p.value,g.name)=? OR CONCAT(p.value,'0',g.name)=?)";

	protected static final String SIZE_QUERY = 
		"SELECT (SELECT length FROM pseudos WHERE grp_idx=?) as size, "+
		"       (SELECT name FROM groups WHERE idx=?) as name; ";

	protected static final String ANNOT_QUERY =
		// mdb 3/8/07 #102: added "ORDER BY" to force exons be drawn before genes
		"SELECT name,type,start,end,strand FROM pseudo_annot WHERE grp_idx=? ORDER BY type DESC"; // mdb removed "text" column 4/8/08 #156 // mdb added strand 1/8/09
		//" AND "+ // mdb removed 3/8/07 #102
		//"       (type='"+Annotation.GENE+"' OR type='"+Annotation.FRAMEWORK+"' OR "+ // mdb removed 3/8/07 #102
		//"        type='"+Annotation.GAP+"' OR type='"+Annotation.CENTROMERE+"')"; // mdb removed 3/8/07 #102

	private ListCache pseudoCache;
	//private Map idMap; // mdb unused 8/29/07

	public PseudoPool(DatabaseReader dr, ListCache cache) { 
		super(dr);
		pseudoCache = cache;
		//idMap = new HashMap(); // mdb unused 8/29/07
	}

	public synchronized void close() {
		super.close();
	}

	public synchronized void clear() {
		super.close();
		if (pseudoCache != null) pseudoCache.clear();
		//idMap.clear(); // mdb unused 8/29/07
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
		int group = sequence.getGroup();
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
					//long cStart = System.currentTimeMillis();
					rs = statement.executeQuery(setInt(ANNOT_QUERY,group));
					//if (TIME_TRACE) System.out.println("PseudoPool: annotation query time = "+(System.currentTimeMillis()-cStart)+" ms");
					while (rs.next()) {
						if (rs.getString(2).equals("hit")) continue;
						AnnotationData annot = new AnnotationData(
								rs.getString(1),	/* String name 		*/
								rs.getString(2),	/* String type 		*/
								rs.getInt(3),		/* int start		*/
								rs.getInt(4),		/* int end			*/
								rs.getString(5));	/* String strand	*/
						//if (!ad.contains(annot)) // mdb test 12/19/08 - too slow, need to enforce uniqueness at DB level
						ad.add(annot);
					}
					//if (TIME_TRACE) System.out.println("PseudoPool: annotation query+build time = "+(System.currentTimeMillis()-cStart)+" ms");
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
	 * @param group a <code>String</code> value of group name or group name 
	 * with prefix, or prefix+0+group.
	 * @param project an <code>int</code> value of the project id
	 * @return an <code>int</code> value of the group id, 0 if not found, or -1 on error
	 */
	public int getGroupID(String group, int project) {
		//Integer iObj = (Integer)idMap.get(group);	// mdb removed 8/29/07 #120
		//if (iObj != null) return iObj.intValue();	// mdb removed 8/29/07 #120

		int id = 0;
		Statement stat = null;
		ResultSet rs = null;
		try {
			stat = createStatement();
			//String query = setString(setString(setString(setInt(setInt(ID_QUERY,project),project),group),group),group); // mdb removed 8/29/07 #120
			String query = setInt(setString("SELECT idx FROM groups WHERE (name=? AND proj_idx=?)",group),project);	// mdb added 8/29/07 #120
			rs = stat.executeQuery(query);
			if (rs.next()) id = rs.getInt(1);
			//idMap.put(group,new Integer(id));	// mdb removed 8/29/07 #120
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
