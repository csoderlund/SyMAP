package symap.sequence;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import database.DBconn2;
import number.GenomicsNumber;
import util.ErrorReport;

/**
 * The PseudoPool handles the cache of data for the Sequence Track.
 * CAS531 removed dead cache
 * CAS541 removed DBAbsUser
 * CAS543 moved stuff immediately to Annotation; by-pass AnnotationData and PseudoData; CAS544 add seqObj
 */
public class SeqPool {	
	private DBconn2 dbc2;
	
	public SeqPool(DBconn2 dbc2) { this.dbc2 = dbc2;}

	public synchronized void close() {}

	/**
	 * Called by Sequence object in init
	 * gnsize - enter setBPsize; Annotation Vector = enter annotation objects 
	 */
	public synchronized String setSequence(Sequence seqObj, GenomicsNumber gnsize, Vector<Annotation> annoVec) {
		int group =   seqObj.getGroup();		
		
		String name=null, type, desc;
		int start, end; 
		String strand, tag; // CAS512 new variables
		int annot_idx, genenum, gene_idx, numhits;

		ResultSet rs = null;
		try {
			String size_query = 
					"SELECT (SELECT length FROM pseudos WHERE grp_idx=" + group + ") as size, "+
					"       (SELECT name   FROM xgroups WHERE idx="     + group + ") as name; ";
			
			rs = dbc2.executeQuery(size_query);
			rs.next(); // a row no matter what
			
			if (rs.wasNull()) {
				System.err.println("No information in db found for Sequence with group id "+group);
				return null;
			}
			gnsize.setBPValue(rs.getLong(1));
			name = rs.getString(2);
			
			String annot_query =
					"SELECT idx, type,name,start,end,strand, genenum, gene_idx, tag, numhits FROM pseudo_annot " // CAS520 add numhits
					+ " WHERE grp_idx=" + group + " ORDER BY type DESC"; 
			
			rs = dbc2.executeQuery(annot_query);
			while (rs.next()) {
				if (rs.getString(2).equals("hit")) continue; // ???
				
				annot_idx = rs.getInt(1);		
				type = rs.getString(2);	
				desc = rs.getString(3);	
				start = rs.getInt(4);		
				end = rs.getInt(5);		
				strand = rs.getString(6);	
				genenum = rs.getInt(7); 	
				gene_idx = rs.getInt(8);		
				tag = rs.getString(9); 		
				numhits = rs.getInt(10);
					
				tag = (tag==null || tag.contentEquals("")) ? type : tag; 
				
				Annotation aObj = new Annotation(seqObj, desc,type,start,end,strand, tag, gene_idx, annot_idx, genenum, numhits);
				annoVec.add(aObj);
			}
			rs.close();
			
			// Sort the annotations by type (ascending order) so that 'exons' are drawn on top of 'genes'.
			Collections.sort(annoVec,
				new Comparator<Annotation>() {
					public int compare(Annotation a1, Annotation a2) { 
						if (a1.isGene() && a2.isGene()) {
							if (a1.getGeneNum()==a2.getGeneNum()) return a1.getStart()-a2.getStart();
								return a1.getGeneNum()-a2.getGeneNum(); // CAS517 sort on gene#
						}
						
						return (a1.getType() - a2.getType()); 			  
					}
				}
			);
			
		} catch (Exception sql) {ErrorReport.print(sql, "SQL exception acquiring sequence data.");}
		
		/**
		try {
			AnnotationData [] annoData = annoVec.toArray(new AnnotationData[annoVec.size()]); // CAS512 simplified
			pdata.setAnnotationData(annoData);
		}
		catch (Exception e) {ErrorReport.print(e, "Process sequence data");}
		
		annoVec.clear();
		annoVec = null;
		
		gnsize.setBPValue(pdata.getSize());
		pdata.setAnnotations(annotations); // transfers annoData to annotations vector
		**/
		return name;
	}
}
