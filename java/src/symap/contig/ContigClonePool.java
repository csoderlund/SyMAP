package symap.contig;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Vector;
import java.util.ArrayList;
import java.util.Collection;

import number.GenomicsNumber;
import symap.SyMAP;
import symap.marker.MarkerList;
import symap.marker.MarkerData;
import symap.block.ContigData;
import symap.pool.DatabaseUser;
import util.DatabaseReader;
import util.ListCache;

/**
 * Handles the cache of data for the Contig Track.
 * 
 * @author Austin Shoemaker
 */
public class ContigClonePool extends DatabaseUser {	
	protected static final String CONTIGS_QUERY = 
		"SELECT idx,size FROM contigs "+
		"WHERE proj_idx=? AND number=?";

	protected static final String MARKER_QUERY =
		"SELECT m.name,m.type,mc.pos "+
		"FROM markers as m, mrk_ctg as mc "+
		"WHERE m.proj_idx=? AND mc.ctg_idx=? AND m.idx=mc.mrk_idx ORDER BY mc.pos; ";

	protected static final String CLONE_QUERY = 
		"SELECT cl.name,cl.cb1,cl.cb2,cl.bes1,cl.bes2, "+
		"       EXISTS (SELECT b1.idx "+
		"               FROM bes_hits as b1 "+
		"               WHERE b1.proj1_idx=? AND b1.proj2_idx=? AND b1.clone=cl.name AND b1.bes_type=cl.bes1 "+
		"               LIMIT 1) as hit1,"+
		"       EXISTS (SELECT a1.idx "+
		"               FROM bes_hits as a1,bes_block_hits as bb1 "+
		"               WHERE a1.proj1_idx=? AND a1.proj2_idx=? AND a1.clone=cl.name AND a1.bes_type=cl.bes1 AND a1.idx=bb1.hit_idx "+
		"               LIMIT 1) as block1,"+
		"       f1.filter_code,"+
		"       EXISTS (SELECT b2.idx "+
		"               FROM bes_hits as b2 "+
		"               WHERE b2.proj1_idx=? AND b2.proj2_idx=? AND b2.clone=cl.name AND b2.bes_type=cl.bes2 "+
		"               LIMIT 1) as hit2,"+
		"       EXISTS (SELECT a2.idx "+
		"               FROM bes_hits as a2,bes_block_hits as bb2 "+
		"               WHERE a2.proj1_idx=? AND a2.proj2_idx=? AND a2.clone=cl.name AND a2.bes_type=cl.bes2 AND a2.idx=bb2.hit_idx "+
		"               LIMIT 1) as block2,"+
		"       f2.filter_code,cl.remarks "+
		"FROM clones as cl "+
		"LEFT JOIN bes_filter as f1 ON (f1.clone_idx=cl.idx AND f1.bes_type=cl.bes1 AND f1.proj2_idx=?) "+
		"LEFT JOIN bes_filter as f2 ON (f2.clone_idx=cl.idx AND f2.bes_type=cl.bes2 AND f2.proj2_idx=?) "+
		"WHERE cl.proj_idx=? AND cl.ctg_idx=? "+
		"ORDER BY cl.cb1,cl.cb2-cl.cb1; ";

	protected static final String MRK2CLONE_QUERY = 
		"SELECT m.name as marker, c.name as clone "+
		"FROM mrk_clone as mc, clones as c, markers as m "+
		"WHERE c.proj_idx=? AND c.ctg_idx=? AND mc.clone_idx=c.idx AND mc.mrk_idx=m.idx ORDER BY m.name; ";

	protected static final String CLONE_REMARKS_QUERY = 
		"SELECT idx,remark FROM clone_remarks WHERE proj_idx=? AND count >= "+Clone.MIN_CLONE_REMARKS;

	private ListCache cpData, crData;

	public ContigClonePool(DatabaseReader dr, ListCache cpCache, ListCache cloneRemarkCache) {
		super(dr);
		cpData = cpCache;
		crData = cloneRemarkCache;
	}

	public synchronized void close() {
		super.close();
	}

	public synchronized void clear() {
		super.close();
		if (cpData != null) cpData.clear();
		if (crData != null) crData.clear();
	}

	public synchronized CloneRemarks getCloneRemarks(int project) throws SQLException {
		CloneRemarks crs = null;
		if (crData != null)
			crs = (CloneRemarks)crData.get(new CloneRemarks(project));
		if (crs == null) {
			List<CloneRemarks.CloneRemark> remarks = new LinkedList<CloneRemarks.CloneRemark>();
			Statement stat = null;
			ResultSet rs = null;
			try {
				stat = createStatement();
				rs = stat.executeQuery(setInt(CLONE_REMARKS_QUERY,project));
				while (rs.next())
					remarks.add(new CloneRemarks.CloneRemark(rs.getInt(1),rs.getString(2)));
				closeResultSet(rs);
				rs = null;
				closeStatement(stat);
				stat = null;

				crs = new CloneRemarks(project,remarks);

				if (crData != null) crData.add(crs);

			} catch (SQLException sql) {
				closeResultSet(rs);
				closeStatement(stat);
				close();
				System.out.println("SQL exception acquiring clone remark data.");
				System.out.println("Message: " + sql.getMessage());
				throw sql;
			} 
		}
		return crs;
	}

	/**
	 * Acquire contig data, getting it from the database if its not in the cache.
	 * 
	 * @param c The requesting Contig
	 * @param size The size that will be set
	 * @param cloneList The list of clones that will be set
	 * @param cloneCondFilters The vector to used when creating clones.
	 * @param markerList The list of markers that will be set
	 * @throws SQLException
	 */
	public synchronized void setContig(Contig c, GenomicsNumber size, List<Clone> cloneList, 
			Vector<Object> cloneCondFilters, MarkerList markerList) throws SQLException 
	{
		ContigCloneData data;
		ContigCloneData backup = new ContigCloneData(c.getProject(),c.getContig(),c.getOtherProject());
		ContigData cd = null;
		if (cpData != null)
			cd = (ContigData)cpData.get(backup);
		if (cd == null)  {
			data = setData(backup);
			if (cpData != null) cpData.add(data);
		}
		else if (cd instanceof ContigCloneData) {
			data = (ContigCloneData)cd;
		}
		else {
			data = setData(cd);
			if (cpData != null) cpData.add(data);
		}
		data.setContig(c,size,cloneList,cloneCondFilters,markerList);
	}

	private ContigCloneData setData(ContigCloneData data) throws SQLException {
		int project = data.getProject();
		int project2 = data.getProject2();
		int contig = data.getContig();

		if (SyMAP.DEBUG) System.out.println("Looking in database for contig data " + contig);

		Statement statement = null;
		ResultSet rs = null;
		int contigID;
		String name, pname;
		List<String> m2cList;
		HashMap<String,List<String>> marker2CloneMap = new HashMap<String,List<String>>();
		List dataList = new ArrayList();

		try {
			statement = createStatement();
			rs = statement.executeQuery(setInt(setInt(CONTIGS_QUERY,project),contig));
			rs.next();
			contigID = rs.getInt(1);
			data.setID(contigID);
			data.setSize(rs.getLong(2));
			closeResultSet(rs);

			if (data.getSize() != 0) {
				statement = createStatement();
				String query = CLONE_QUERY;
				query = setInt(query,project);
				query = setInt(query,project2);
				query = setInt(query,project);
				query = setInt(query,project2);
				query = setInt(query,project);
				query = setInt(query,project2);
				query = setInt(query,project);
				query = setInt(query,project2);
				query = setInt(query,project2);
				query = setInt(query,project2);
				query = setInt(query,project);
				query = setInt(query,contigID);
				rs = statement.executeQuery(query);

				while (rs.next()) {
					dataList.add(
						ContigCloneData.getCloneData(
							rs.getString(1),
							rs.getLong(2),
							rs.getLong(3),
							getBESValue(rs.getString(4)),
							getBESValue(rs.getString(5)),
							rs.getBoolean(6),
							rs.getBoolean(7),
							rs.getInt(8)>0,
							rs.getBoolean(9),
							rs.getBoolean(10),
							rs.getInt(11)>0,
							rs.getString(12)));
				}
				data.setCloneData(dataList);
				dataList.clear();

				closeResultSet(rs);
				rs = statement.executeQuery(setInt(setInt(MRK2CLONE_QUERY,project),contigID));
				pname = null;
				m2cList = null;
				while (rs.next()) {
					name = rs.getString(1);//.intern(); // mdb removed intern() 2/2/10 - can cause memory leaks in this case
					if (pname == null || !pname.equals(name)) {
						m2cList = new LinkedList();
						marker2CloneMap.put(name, m2cList);
						pname = name;
					}
					m2cList.add(rs.getString(2));
				}

				closeResultSet(rs);
				rs = statement.executeQuery(setInt(setInt(MARKER_QUERY,project),contigID));
				while (rs.next()) {
					name = rs.getString(1);
					dataList.add(
						ContigCloneData.getMarkerData(
								name,
								rs.getString(2),
								rs.getLong(3),
								((Collection)marker2CloneMap.get(name))));
				}
				closeResultSet(rs);
				closeStatement(statement);
				data.setMarkerData(dataList);
				dataList.clear();
				marker2CloneMap.clear();
			}
		} catch (SQLException sql) {
			closeResultSet(rs);
			closeStatement(statement);
			close();
			System.out.println("SQL exception acquiring clone data.");
			System.out.println("Message: " + sql.getMessage());
			throw sql;
		} finally {
			rs = null;
			statement = null;
			dataList = null;
			marker2CloneMap = null;
		}
		return data;
	}

	private ContigCloneData setData(ContigData contigData) throws SQLException {
		ContigCloneData data = new ContigCloneData(contigData);

		int project = data.getProject();
		int project2 = data.getProject2();
		int contig = data.getContig();
		int contigID = data.getID();

		if (SyMAP.DEBUG) System.out.println("Looking in database for clone data for contig " + contig);

		Statement statement = null;
		ResultSet rs = null;
		String name, pname;
		List<String> m2cList;
		HashMap<String,List<String>> marker2CloneMap = new HashMap<String,List<String>>();
		List<CloneData> dataList = new ArrayList<CloneData>();
		MarkerData[] md;
		MarkerCloneData[] mcd;
		try {
			if (data.getSize() != 0) {
				statement = createStatement();
				String query = CLONE_QUERY;
				query = setInt(query,project);
				query = setInt(query,project2);
				query = setInt(query,project);
				query = setInt(query,project2);
				query = setInt(query,project);
				query = setInt(query,project2);
				query = setInt(query,project);
				query = setInt(query,project2);
				query = setInt(query,project2);
				query = setInt(query,project2);
				query = setInt(query,project);
				query = setInt(query,contigID);

				//long cStart = System.currentTimeMillis();
				rs = statement.executeQuery(query);
				//System.out.println("CloneQuery for contig id "+contigID+" took "+(System.currentTimeMillis()-cStart)+" millis.");

				while (rs.next()) {
					dataList.add(ContigCloneData.getCloneData(rs.getString(1),rs.getLong(2),rs.getLong(3),
							getBESValue(rs.getString(4)),getBESValue(rs.getString(5)),
							rs.getBoolean(6),rs.getBoolean(7),rs.getInt(8)>0,
							rs.getBoolean(9),rs.getBoolean(10),rs.getInt(11)>0,rs.getString(12)));
				}
				closeResultSet(rs);

				data.setCloneData(dataList);
				dataList.clear();

				rs = statement.executeQuery(setInt(setInt(MRK2CLONE_QUERY,project),contigID));
				pname = null;
				m2cList = null;
				while (rs.next()) {
					name = rs.getString(1);
					if (pname == null || !pname.equals(name)) {
						m2cList = new LinkedList<String>();
						marker2CloneMap.put(name, m2cList);
						pname = name;
					}
					m2cList.add(rs.getString(2));
				}
				closeResultSet(rs);
				closeStatement(statement);
				md = contigData.getMarkerData();
				mcd = new MarkerCloneData[md.length];
				for (int i = 0; i < md.length; i++) {
					mcd[i] = ContigCloneData.getMarkerData(md[i],((Collection)marker2CloneMap.get(md[i].getName())));
				}
				data.setMarkerData(mcd);
				marker2CloneMap.clear();
			}
		} catch (SQLException sql) {
			closeResultSet(rs);
			closeStatement(statement);
			close();
			System.out.println("SQL exception acquiring clone data.");
			System.out.println("Message: " + sql.getMessage());
			throw sql;
		} finally {
			rs = null;
			statement = null;
			dataList = null;
			marker2CloneMap = null;
			contigData = null;
			md = null;
			mcd = null;
		}
		return data;
	}

}
