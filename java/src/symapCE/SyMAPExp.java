package symapCE;

/*********************************************************
 * 2D for Chromosome Explorer
 * CAS521 remove FPC
 */
import java.beans.PropertyChangeEvent;  // CAS521 replaced Depreciated Observer with this
import java.beans.PropertyChangeListener;

import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import util.DatabaseReader;
import util.ErrorReport;
import backend.UpdatePool;
import symap.projectmanager.common.Block;
import symap.projectmanager.common.Project;
import symap.projectmanager.common.SyMAPFrameCommon;
import symap.projectmanager.common.TrackCom;
import symap.projectmanager.common.Mapper;

public class SyMAPExp implements PropertyChangeListener { 
	
	private SyMAPFrameCommon frame;
	private DatabaseReader databaseReader;
	private Vector<Project> projects;
	Vector<TrackCom> tracks;
	Mapper mapper;
	public static final Color[] projectColors = { Color.cyan, Color.green, new Color(0.8f, 0.5f, 1.0f),
		Color.yellow, Color.orange };

	public SyMAPExp(DatabaseReader dr) throws SQLException {
		databaseReader = dr;
		
		projects = new Vector<Project>();
		tracks =   new Vector<TrackCom>();
		
		mapper =   new Mapper();
		
		frame =    new SyMAPFrameCommon(databaseReader, mapper);
	}
	public void propertyChange(PropertyChangeEvent evt) {
		if (frame != null) frame.repaint();
	}
	
	// symapCE.SyMAPmanager.showExplorer
	public boolean addProject(String strName, String strType) {
		try {
			Project p = loadProject(strName, strType);
			if ( p != null ) {
				p.setColor( projectColors[projects.size() % (projectColors.length)] );				
				tracks.addAll( loadProjectTracks(p) );
				projects.add( p );
				return true;
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Add project for Explorer");}
		return false;
	}
	// symapCE.SyMAPmanager.showExplorer
	public boolean build() {
		try {
			Vector<Block> blocks = loadAllBlocks(tracks);
			
			mapper.setProjects(projects.toArray(new Project[0]));
			mapper.setTracks(tracks.toArray(new TrackCom[0]));
			mapper.setBlocks(blocks.toArray(new Block[0]));
			projects = null;
			tracks = null;
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Build Explorer");}
		return false;
	}
	// symapCE.SyMAPmanager.showExplorer
	public SyMAPFrameCommon getFrame() {
		return frame;
	}
	
	private Project loadProject(String strProjName, String strTypeName) throws SQLException{
	     int nProjIdx = -1;
	     String strDisplayName = null;
	     String loaddate=""; // CAS513 to put on left side by name
	
	     UpdatePool pool = new UpdatePool(databaseReader);
	     ResultSet rs = pool.executeQuery("SELECT p.idx, p.loaddate, pp.value " +
	     		"FROM projects AS p " +
	     		"JOIN proj_props AS pp ON (p.idx=pp.proj_idx) " +
	     		"WHERE pp.name='display_name' " +
	     		"AND p.name='"+strProjName+"' AND p.type='"+strTypeName+"'");
	     
	     if ( rs.next() ) {
	    	loaddate = rs.getString("p.loaddate");
	     	nProjIdx = rs.getInt("p.idx");
	     	strDisplayName = rs.getString("pp.value");
	     }
	     rs.close();
	     
	     if (nProjIdx < 0) {
	     	System.err.println("Project '"+strProjName+"' not loaded.");
	     	return null;
	     }
	     
	     return new Project(nProjIdx, strProjName, strTypeName, strDisplayName, loaddate);
	}
	
	private Vector<TrackCom> loadProjectTracks(Project p) throws SQLException
	{
		Vector<TrackCom> projTracks = new Vector<TrackCom>();
     
	     // Get group(s) and create track(s)
	     UpdatePool pool = new UpdatePool(databaseReader);
	     String qry = "SELECT idx,name FROM xgroups WHERE proj_idx=" + p.getID() +
	     				" AND sort_order > 0 " + // make consistent with full dotplot for FPC projects
	     				"ORDER BY sort_order";
	     ResultSet rs = pool.executeQuery(qry);
	     while( rs.next() ) {
	     	int nGroupIdx = rs.getInt(1);
	     	String strGroupName = rs.getString(2);
	     	projTracks.add(new TrackCom(p, strGroupName, nGroupIdx));
	     }
	     rs.close();
	     
	     if (projTracks.isEmpty()) {
	     	System.err.println("No groups found for project " + p.getID() + " in database.");
	     	return projTracks; // empty
	     }
	     
	     // Initialize tracks
	     for (TrackCom t : projTracks) {
	        rs = pool.executeQuery("SELECT length FROM pseudos WHERE (grp_idx="+t.getGroupIdx()+")");
	        while( rs.next() ) {
	        	t.setSizeBP( rs.getLong(1) );
	        }
	        rs.close();
	     }
	     return projTracks;
	}
	
	private static String getGroupList(Vector<TrackCom> tracks) {
		String s = "";
		for (TrackCom t : tracks)
			s += t.getGroupIdx() + (t == tracks.lastElement() ? "" : ",");
		return s;
	}
	
	private Vector<Block> loadAllBlocks(Vector<TrackCom> tracks) 
	throws SQLException
	{
		Vector<Block> blocks = new Vector<Block>();
		
		UpdatePool pool = new UpdatePool(databaseReader);
		
		/* CAS512 is added, See if we have the corr field - CAS was probably added to schema long ago
		 boolean haveCorr = true;
	     try {pool.executeQuery("select corr from blocks limit 1");}
	     catch (Exception e) {haveCorr = false;}
	     String corrStr = (haveCorr ? ",corr " : "");
		*/
	     // Load blocks for each track
	     String strGroupList = "(" + getGroupList(tracks) + ")";
	     String strQ = "SELECT idx,grp1_idx,grp2_idx,start1,end1,start2,end2,ctgs1,ctgs2, corr FROM blocks " + 
						"WHERE (grp1_idx IN "+strGroupList+" AND grp2_idx IN "+strGroupList+")";
     
	     ResultSet rs = pool.executeQuery(strQ);
	     while( rs.next() ) {
	     	int blockIdx = rs.getInt(1); // CAS512 replace fields with numbers
	     	int grp1_idx = rs.getInt(2);
	     	int grp2_idx = rs.getInt(3);
	     	long start1  = rs.getLong(4);
	     	long end1    = rs.getLong(5);
	     	long start2  = rs.getLong(6);
	     	long end2    = rs.getLong(7);
	     	String ctgs1 = rs.getString(8);
	     	String ctgs2 = rs.getString(9);
	     	// CAS512 float corr = (haveCorr ? rs.getFloat("corr") : 0.01F); // if no corr field, make sure they are positive
	     	float corr = rs.getFloat(10);
	     	
	     	TrackCom t1 = TrackCom.getTrackByGroupIdx(tracks, grp1_idx);
	     	TrackCom t2 = TrackCom.getTrackByGroupIdx(tracks, grp2_idx);
	     	
	     	Block b = new Block(blockIdx, t1.getProjIdx(), t2.getProjIdx(), 
	     			t1.getGroupIdx(), t2.getGroupIdx(), 
	     			start1, end1, start2, end2, ctgs1, ctgs2, corr);
	     	if (!blocks.contains(b)) {
	     		blocks.add(b);
	     	}
	     }
	     rs.close();
	     return blocks;
	}
}

