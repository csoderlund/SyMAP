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
 * ChrExpFrame does the actual drawing of the icons on left using the mapper created here.
 * 
 * CAS521 remove FPC, CAS534 symapCE.SyMAPExp=> frame.ChrExpInit, CAS541 called by ManagerFrame instead of SyMAPmanager
 */

public class ChrExpInit implements PropertyChangeListener { 
	private ChrExpFrame expFrame;
	private DBconn2 tdbc2;
	
	private Vector<Mproject> projects  = new Vector<Mproject>();
	private Vector<ChrInfo> allChrs = new Vector<ChrInfo>();
	private MapLeft mapper =   new MapLeft();
	
	private static final Color[] projectColors = { Color.cyan, Color.green, new Color(0.8f, 0.5f, 1.0f),
		Color.yellow, Color.orange };

	public ChrExpInit(String title, DBconn2 dbc2, Vector<Mproject> selectedProjVec) throws SQLException { // Called by ManagerFrame
		tdbc2 = new DBconn2("ChrExp-" + DBconn2.getNumConn(), dbc2); // closed in ChrExpFrame
		
		for (Mproject p : selectedProjVec) addProject( p.getDBName() ); // CAS550 mv from ManagerFrame
		makeDS();
		
		expFrame = new ChrExpFrame(title, tdbc2, mapper); 	// CAS550 finish mapper before this call, so it can be directly used
	}
	public ChrExpFrame getExpFrame() {return expFrame;} // managerFrame setVisible
	
	public void propertyChange(PropertyChangeEvent evt) {if (expFrame != null) expFrame.repaint();}
	
	private boolean addProject(String strName) {
		try {
			Mproject p = loadProject(strName);
			if ( p != null ) {
				p.setColor( projectColors[projects.size() % (projectColors.length)] );				
				allChrs.addAll( loadProjectTracks(p) );
				projects.add( p );
				return true;
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Add project for Explorer");}
		return false;
	}
	
	private boolean makeDS() { // called after all projects are added
		try {
			Vector<Block> blocks = loadAllBlocks(allChrs);
			
			mapper.setProjects(projects.toArray(new Mproject[0]));
			mapper.setChrs(allChrs.toArray(new ChrInfo[0]));
			mapper.setBlocks(blocks.toArray(new Block[0]));
			
			projects = null;
			allChrs = null;
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Build Explorer");}
		return false;
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
	
	private Vector<ChrInfo> loadProjectTracks(Mproject p)  {
	try {
		Vector<ChrInfo> projTracks = new Vector<ChrInfo>();
     
	     // Get group(s) and create track(s)
	     String qry = "SELECT idx,name FROM xgroups WHERE proj_idx=" + p.getID() +
	     				" AND sort_order > 0 " + // make consistent with full dotplot for FPC projects
	     				"ORDER BY sort_order";
	     ResultSet rs = tdbc2.executeQuery(qry);
	     while( rs.next() ) {
	     	int nGroupIdx = rs.getInt(1);
	     	String strGroupName = rs.getString(2);
	     	projTracks.add(new ChrInfo(p, strGroupName, nGroupIdx));
	     }
	     rs.close();
	     
	     if (projTracks.isEmpty()) {
	     	System.err.println("No groups found for project " + p.getID() + " in database.");
	     	return projTracks; // empty
	     }
	     
	     // Initialize tracks
	     for (ChrInfo t : projTracks) {
	        rs = tdbc2.executeQuery("SELECT length FROM pseudos WHERE (grp_idx="+t.getGroupIdx()+")");
	        while( rs.next() ) {
	        	t.setSizeBP( rs.getLong(1) );
	        }
	        rs.close();
	     }
	     return projTracks;
	} catch (Exception e) {ErrorReport.print(e, "Load tracks"); return null;}
	}
	
	private Vector<Block> loadAllBlocks(Vector<ChrInfo> tracks) {
		Vector<Block> blocks = new Vector<Block>();
		
		try {
	     // Load blocks for each track
		String s = "";
		for (ChrInfo t : tracks)
				s += t.getGroupIdx() + (t == tracks.lastElement() ? "" : ",");
	     String strGroupList = "(" + s + ")";
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
	     	
	     	ChrInfo t1 = ChrInfo.getChrByGroupIdx(tracks, grp1_idx);
	     	ChrInfo t2 = ChrInfo.getChrByGroupIdx(tracks, grp2_idx);
	     	
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
}

