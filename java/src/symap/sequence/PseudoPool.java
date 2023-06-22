package symap.sequence;

import java.sql.ResultSet;
import java.util.Vector;

import database.DBconn2;
import number.GenomicsNumber;
import util.ErrorReport;

/**
 * The PseudoPool handles the cache of data for the Sequence Track.
 * CAS531 removed dead cache
 * CAS541 removed DBAbsUser
 */
public class PseudoPool {	
	private DBconn2 dbc2;
	
	public PseudoPool(DBconn2 dbc2) { this.dbc2 = dbc2;}

	public synchronized void close() {}

	/**
	 * Called by Sequence object
	 * gnsize - enter setBPsize; Annotation Vector = enter annotation objects 
	 */
	public synchronized String setSequence(Sequence seqObj, GenomicsNumber gnsize, Vector<Annotation> annotations) {
		int project = seqObj.getProject();
		int group =   seqObj.getGroup();		
		PseudoData pdata  = new PseudoData(project, group);

		ResultSet rs = null;
		Vector<AnnotationData> annoVec = new Vector<AnnotationData>();
		try {
			String size_query = 
					"SELECT (SELECT length FROM pseudos WHERE grp_idx=" + group + ") as size, "+
					"       (SELECT name   FROM xgroups WHERE idx="     + group + ") as name; ";
			
			rs = dbc2.executeQuery(size_query);
			rs.next(); // a row no matter what
			pdata.setSize(rs.getLong(1));
			if (rs.wasNull()) {
				System.err.println("No information in db found for Sequence with group id "+group);
			}
			else {
				pdata.setName(rs.getString(2));
				
				String annot_query =
						"SELECT idx, type,name,start,end,strand, genenum, gene_idx, tag, numhits FROM pseudo_annot " // CAS520 add numhits
						+ " WHERE grp_idx=" + group + " ORDER BY type DESC"; 
				
				rs = dbc2.executeQuery(annot_query);
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
							rs.getString(9),		/* tag */
							rs.getInt(10)
							);	/* tag */
					
					annoVec.add(annot);
				}
			}
			rs.close();
			
		} catch (Exception sql) {ErrorReport.print(sql, "SQL exception acquiring sequence data.");}
		
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
		int id = -1;
		ResultSet rs = null;
		try {
			String query = "SELECT idx FROM xgroups WHERE (name='"+ group + "' AND proj_idx=" + projID;
			rs = dbc2.executeQuery(query);
			if (rs.next()) id = rs.getInt(1);
			rs.close();
		}
		catch (Exception e) {ErrorReport.print(e, "Failure to obtain id for group="+group+" project="+projID);}
		return id;
	}
}
