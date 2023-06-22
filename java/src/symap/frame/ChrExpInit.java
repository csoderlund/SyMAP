package symap.frame;

import java.beans.PropertyChangeEvent;  // CAS521 replaced Depreciated Observer with this
import java.beans.PropertyChangeListener;

import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import util.ErrorReport;
import database.DBconn2;
import symap.manager.Mproject;

/*********************************************************
 * Chromosome Explorer setup; loads projects, tracks, blocks and initializes track
 * CAS521 remove FPC
 * CAS534 symapCE.SyMAPExp=> frame.ChrExpInit
 * CAS541 called by ManagerFrame instead of SyMAPmanager
 */

public class ChrExpInit implements PropertyChangeListener { 
	private ChrExpFrame frame;
	private DBconn2 tdbc2;
	private Vector<Mproject> projects;
	private Vector<TrackCom> tracks;
	private Mapper mapper;
	private static final Color[] projectColors = { Color.cyan, Color.green, new Color(0.8f, 0.5f, 1.0f),
		Color.yellow, Color.orange };

	public ChrExpInit(DBconn2 dbc2) throws SQLException {
		tdbc2 = new DBconn2("ChrExp-" + DBconn2.getNumConn(), dbc2); // closed in ChrExpFrame
		
		projects = new Vector<Mproject>();
		tracks =   new Vector<TrackCom>();
		
		mapper =   new Mapper();
		
		frame =    new ChrExpFrame(tdbc2, mapper);
	}
	public void propertyChange(PropertyChangeEvent evt) {
		if (frame != null) frame.repaint();
	}
	
	public boolean addProject(String strName) {
		try {
			Mproject p = loadProject(strName);
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
	
	public boolean build() {
		try {
			Vector<Block> blocks = loadAllBlocks(tracks);
			
			mapper.setProjects(projects.toArray(new Mproject[0]));
			mapper.setTracks(tracks.toArray(new TrackCom[0]));
			mapper.setBlocks(blocks.toArray(new Block[0]));
			projects = null;
			tracks = null;
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Build Explorer");}
		return false;
	}
	
	public ChrExpFrame getFrame() {
		return frame;
	}
	
	private Mproject loadProject(String strProjName) throws Exception{
	     int nProjIdx = -1;
	     String loaddate=""; // CAS513 to put on left side by name
	
	     ResultSet rs = tdbc2.executeQuery("SELECT p.idx, p.loaddate " +
	     		"FROM projects AS p  where p.name='"+strProjName+"'");
	     
	     if ( rs.next() ) {
	    	nProjIdx = rs.getInt("p.idx");
	    	loaddate = rs.getString("p.loaddate");
	     }
	     rs.close();
	     
	     if (nProjIdx < 0) {
	     	System.err.println("Project '"+strProjName+"' not loaded.");
	     	return null;
	     }
	     
	     Mproject p = new Mproject(tdbc2, nProjIdx, strProjName,  loaddate);
	     p.loadDataFromDB();
	     p.loadParamsFromDB();
	     return p;
	}
	
	private Vector<TrackCom> loadProjectTracks(Mproject p)  {
	try {
		Vector<TrackCom> projTracks = new Vector<TrackCom>();
     
	     // Get group(s) and create track(s)
	     String qry = "SELECT idx,name FROM xgroups WHERE proj_idx=" + p.getID() +
	     				" AND sort_order > 0 " + // make consistent with full dotplot for FPC projects
	     				"ORDER BY sort_order";
	     ResultSet rs = tdbc2.executeQuery(qry);
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
	        rs = tdbc2.executeQuery("SELECT length FROM pseudos WHERE (grp_idx="+t.getGroupIdx()+")");
	        while( rs.next() ) {
	        	t.setSizeBP( rs.getLong(1) );
	        }
	        rs.close();
	     }
	     return projTracks;
	} catch (Exception e) {ErrorReport.print(e, "Load tracks"); return null;}
	}
	
	private Vector<Block> loadAllBlocks(Vector<TrackCom> tracks) {
		Vector<Block> blocks = new Vector<Block>();
		
		/* CAS512 is added, See if we have the corr field - CAS was probably added to schema long ago
		 boolean haveCorr = true;
	     try {pool.executeQuery("select corr from blocks limit 1");}
	     catch (Exception e) {haveCorr = false;}
	     String corrStr = (haveCorr ? ",corr " : "");
		*/
		try {
	     // Load blocks for each track
	     String strGroupList = "(" + getGroupList(tracks) + ")";
	     String strQ = "SELECT idx,grp1_idx,grp2_idx,start1,end1,start2,end2, corr FROM blocks " + 
						"WHERE (grp1_idx IN "+strGroupList+" AND grp2_idx IN "+strGroupList+")";
     
	     ResultSet rs = tdbc2.executeQuery(strQ);
	     while( rs.next() ) {
	     	int blockIdx = rs.getInt(1); // CAS512 replace fields with numbers
	     	int grp1_idx = rs.getInt(2);
	     	int grp2_idx = rs.getInt(3);
	     	long start1  = rs.getLong(4);
	     	long end1    = rs.getLong(5);
	     	long start2  = rs.getLong(6);
	     	long end2    = rs.getLong(7);
	     	// CAS512 float corr = (haveCorr ? rs.getFloat("corr") : 0.01F); // if no corr field, make sure they are positive
	     	float corr = rs.getFloat(8);
	     	
	     	TrackCom t1 = TrackCom.getTrackByGroupIdx(tracks, grp1_idx);
	     	TrackCom t2 = TrackCom.getTrackByGroupIdx(tracks, grp2_idx);
	     	
	     	Block b = new Block(blockIdx, t1.getProjIdx(), t2.getProjIdx(), 
	     			t1.getGroupIdx(), t2.getGroupIdx(), start1, end1, start2, end2,  corr);
	     	if (!blocks.contains(b)) {
	     		blocks.add(b);
	     	}
	     }
	     rs.close();
	     return blocks;
	} catch (Exception e) {ErrorReport.print(e, "Load blocks"); return null;}   
	}
	private static String getGroupList(Vector<TrackCom> tracks) {
		String s = "";
		for (TrackCom t : tracks)
			s += t.getGroupIdx() + (t == tracks.lastElement() ? "" : ",");
		return s;
	}
}

