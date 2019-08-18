package symap3D;

import java.applet.Applet;
import java.awt.Color;
import java.awt.Component;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;
import java.util.HashMap;
import java.util.Observer;
import java.util.Observable;

import javax.vecmath.Color3f;
import symap.projectmanager.common.Project;
import symap.projectmanager.common.TrackCom;
import symap.projectmanager.common.Mapper;
import com.sun.j3d.utils.universe.SimpleUniverse;

import backend.UpdatePool;
import symap.projectmanager.common.Block;
import util.DatabaseReader;

// Mimicking structure of SyMAP 2D with this class
public class SyMAP3D implements Observer {
	public static final Color3f green  = new Color3f(Color.green);
	public static final Color3f blue   = new Color3f(Color.blue);
	public static final Color3f red    = new Color3f(Color.red);
	public static final Color3f yellow = new Color3f(Color.yellow);
	public static final Color3f cyan   = new Color3f(Color.cyan);
	public static final Color3f orange = new Color3f(Color.orange);
	public static final Color3f purple = new Color3f(0.8f, 0.5f, 1.0f);
	public static final Color3f white  = new Color3f(Color.white);
	public static final Color3f black  = new Color3f(Color.black);
	public static final Color3f[] projectColors = { cyan, green, purple, yellow, orange };
	
	private static final int REQUIRED_JAVA3D_MAJOR_VERSION = 1;
	private static final int REQUIRED_JAVA3D_MINOR_VERSION = 3;
	private static final int PREFERRED_JAVA3D_MINOR_VERSION = 5;

	private SyMAPFrame3D frame;
	private DatabaseReader databaseReader;
	private Vector<Project> projects;
	Vector<TrackCom> tracks;
	Mapper mapper;
	
	public SyMAP3D(DatabaseReader dr) throws SQLException {
		this(null,dr);
	}

	public SyMAP3D(Applet applet, DatabaseReader dr) throws SQLException {
		databaseReader = dr;
		
		projects = new Vector<Project>();
		tracks = new Vector<TrackCom>();
		
		mapper = new Mapper3D();
		
		frame = new SyMAPFrame3D(applet, databaseReader, (Mapper3D)mapper);
	}
	
	public void update(Observable o, Object arg) {
		if (frame != null) frame.repaint();
	}
	
	public boolean addProject(String strName, String strType) {
		try {
			Project p = loadProject(strName, strType);
			if ( p != null ) {
				p.setColor( projectColors[projects.size() % (projectColors.length)].get() );
				
				tracks.addAll( loadProjectTracks(p) );
				projects.add( p );
				return true;
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}
	
	public boolean hasProject(String strName, String strType) {
		for (Project p : projects)
			if (p.getDBName().equals(strName) && p.getType().equals(strType))
				return true;
		return false;
	}
	
	public boolean build() {
		try {
			Vector<Block> blocks = loadAllBlocks(tracks);
			
			//mapper.setFrame(frame); // mdb removed 2/1/10
			mapper.setProjects(projects.toArray(new Project[0]));
			mapper.setTracks(tracks.toArray(new TrackCom[0]));
			mapper.setBlocks(blocks.toArray(new Block[0]));
			projects = null;
			tracks = null;
			return true;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public SyMAPFrame3D getFrame() {
		return frame;
	}
	
	private Project loadProject(String strProjName, String strTypeName) throws SQLException
	{
        int nProjIdx = -1;
        String strDisplayName = null;

        UpdatePool pool = new UpdatePool(databaseReader);
        ResultSet rs = pool.executeQuery("SELECT p.idx, pp.value " +
        		"FROM projects AS p " +
        		"JOIN proj_props AS pp ON (p.idx=pp.proj_idx) " +
        		"WHERE pp.name='display_name' " +
        		"AND p.name='"+strProjName+"' AND p.type='"+strTypeName+"'");
        
        if ( rs.next() ) {
        	nProjIdx = rs.getInt("p.idx");
        	strDisplayName = rs.getString("pp.value");
        }

        rs.close();
        
        if (nProjIdx < 0) {
        	System.err.println("Couldn't find project '"+strProjName+"' in database.");
        	return null;
        }
        
        return new Project(nProjIdx, strProjName, strTypeName, strDisplayName);
	}
	
	private Vector<TrackCom> loadProjectTracks(Project p) throws SQLException
	{
		Vector<TrackCom> projTracks = new Vector<TrackCom>();
        
        // Get group(s) and create track(s)
        UpdatePool pool = new UpdatePool(databaseReader);
        String qry = "SELECT idx,name FROM groups " +
        				"WHERE proj_idx=" + p.getID() +
        				" AND sort_order > 0 " + // mdb added 1/19/10 #207 - make consistent with full dotplot for FPC projects
        				"ORDER BY sort_order";
        ResultSet rs = pool.executeQuery(qry);
        while( rs.next() ) {
        	int nGroupIdx = rs.getInt("idx");
        	String strGroupName = rs.getString("name");
        	
       		projTracks.add(new TrackCom(p, strGroupName, nGroupIdx));
       		//System.out.println("found group: " + p.getName() + " " + strGroupName + " idx=" + nGroupIdx);
        }
        rs.close();
        
        if (projTracks.isEmpty()) {
        	System.err.println("No groups found for project " + p.getID() + " in database.");
        	return projTracks; // empty
        }
        
        // Initialize tracks
        for (TrackCom t : projTracks) {
        	// PSEUDO
        	if (t.isPseudo()) {
		        rs = pool.executeQuery("SELECT length FROM pseudos "+
		        						"WHERE (grp_idx="+t.getGroupIdx()+")");
		        while( rs.next() ) {
		        	t.setSizeBP( rs.getLong("length") );
		        	//System.out.println("found pseudo: " + p.getName() + " " + t.getSizeBP());
		        }
        	}
	        // FPC
        	else if (t.isFPC()) {
        		int ccb;
        		int size;
        		int cbsize;
        		
		        rs = pool.executeQuery("SELECT ccb,size FROM contigs "+
		        						"WHERE (grp_idx="+t.getGroupIdx()+") ORDER BY ccb");
		        rs.last();
		        ccb = rs.getInt("ccb");
		        size = rs.getInt("size");
		        	
				rs = pool.executeQuery("SELECT VALUE FROM proj_props "+
										"WHERE (name='cbsize')");
				rs.next();
				cbsize = rs.getInt("value");
		        	
				t.setBpPerUnit( cbsize ); // save for loading blocks
		        t.setSizeBP((ccb + size) * cbsize);
		        //System.out.println("found fpc: " + project + " " + t.groupIdx + " " + t.sizeBP);
        	}
        	
            rs.close();
        }
        
        return projTracks;
	}
	
	private static String contigIdxArraytoNumArray(String ctgList, HashMap<String,String> ctgIdxToNum) {
    	String[] ctgIdx = ctgList.split(",");
    	if (ctgIdx.length == 0)
    		return null;
    	
    	String[] ctgNum = new String[ctgIdx.length];
    	for (int i = 0;  i < ctgIdx.length;  i++)
    		ctgNum[i] = ctgIdxToNum.get(ctgIdx[i]);
    	
    	String out = "";
    	for (String s : ctgNum)
    		out += s + ",";
    		
    	return out;
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
		HashMap<String,String> contigIdxToNum = new HashMap<String,String>();
    	
		// Load contig numbers
		UpdatePool pool = new UpdatePool(databaseReader);
		String strQ = "SELECT idx,number FROM contigs";
		//System.out.println("loadAllBlocks: "+strQ);
        ResultSet rs = pool.executeQuery(strQ);
        while( rs.next() )
        	contigIdxToNum.put(rs.getString("idx"), rs.getString("number"));
        rs.close();
        
        // See if we have the corr field
      	boolean haveCorr = true;
        try
        {
        	pool.executeQuery("select corr from blocks limit 1");	
        }
        catch (Exception e)
        {
  			haveCorr = false;
        }
        
        String corrStr = (haveCorr ? ",corr " : "");
		
        // Load blocks for each track
        String strGroupList = "(" + getGroupList(tracks) + ")";
        strQ = "SELECT idx,grp1_idx,grp2_idx,start1,end1,start2,end2,ctgs1,ctgs2" + corrStr + " FROM blocks " + 
					"WHERE (grp1_idx IN "+strGroupList+" AND grp2_idx IN "+strGroupList+")";
        //System.out.println("loadBlocks: "+strQ);
        rs = pool.executeQuery(strQ);
        while( rs.next() ) {
        	//int blockNum = rs.getInt("blockNum");
        	int blockIdx = rs.getInt("idx");
        	int grp1_idx = rs.getInt("grp1_idx");
        	int grp2_idx = rs.getInt("grp2_idx");
        	long start1  = rs.getLong("start1");
        	long end1    = rs.getLong("end1");
        	long start2  = rs.getLong("start2");
        	long end2    = rs.getLong("end2");
        	String ctgs1 = rs.getString("ctgs1");
        	String ctgs2 = rs.getString("ctgs2");
        	float corr = (haveCorr ? rs.getFloat("corr") : 0.01F); // if no corr field, make sure they are positive
        	
        	TrackCom t1 = TrackCom.getTrackByGroupIdx(tracks, grp1_idx);
        	TrackCom t2 = TrackCom.getTrackByGroupIdx(tracks, grp2_idx);
        	
        	if (t1.isFPC()) {
        		start1 = start1 * t1.getBpPerUnit();
        		end1 = end1 * t1.getBpPerUnit();
        	}
        	if (t2.isFPC()) {
        		start2 = start2 * t2.getBpPerUnit();
        		end2 = end2 * t2.getBpPerUnit();
        	}
        	
        	ctgs1 = contigIdxArraytoNumArray(ctgs1, contigIdxToNum);
        	ctgs2 = contigIdxArraytoNumArray(ctgs2, contigIdxToNum);

        	Block b = new Block(blockIdx, t1.getProjIdx(), t2.getProjIdx(), 
        			t1.getGroupIdx(), t2.getGroupIdx(), 
        			start1, end1, start2, end2, ctgs1, ctgs2, corr);
        	if (!blocks.contains(b)) {
        		//System.out.println("found block: blockIdx="+blockIdx+" proj1="+t1.getFullName()+" proj2="+t2.getFullName()+" start1="+start1+" end1="+end1+" start2="+start2+" end2="+end2+" ctgs1="+ctgs1+" ctgs2="+ctgs2);
        		blocks.add(b);
        	}
        }
        rs.close();
        
        System.out.println("Loaded " + blocks.size() + " blocks");
        return blocks;
	}
	
	public static boolean checkJava3DSupported(Component frame) {	
		String version = getInstalledJava3DVersionStr();
		if (version != null) {
			String[] subs = version.split("\\.");
			int major = 0;
			int minor = 0;

			if (subs.length > 0) major  = Integer.valueOf(subs[0]);
			if (subs.length > 1) minor  = Integer.valueOf(subs[1]);
			
			if (major < REQUIRED_JAVA3D_MAJOR_VERSION
				|| (major >= REQUIRED_JAVA3D_MAJOR_VERSION && minor < REQUIRED_JAVA3D_MINOR_VERSION))
			{
				System.err.println("Java3D version " 
						+ getRequiredJava3DVersionStr()
						+ " or later is required.");
				javax.swing.JOptionPane.showMessageDialog(frame,
					    "The installed Java3D version is "
					    + getInstalledJava3DVersionStr() + ".  \n"
					    + "SyMAP requires version "
					    + getRequiredJava3DVersionStr() + " or later.  \n"
					    + "Please visit http://java.com/download/ to upgrade.",
					    "Java Incompatibility",
					    javax.swing.JOptionPane.ERROR_MESSAGE);
				return false;
			}
			else
				return true;
		}
		else 
			System.err.println("Could not determine Java3D version.");
		
		return false;
	}
	
	public static boolean checkJava3DPreferred() {
		String version = getInstalledJava3DVersionStr();
		if (version != null) {
			String[] subs = version.split("\\.");
			int major = 0;
			int minor = 0;

			if (subs.length > 0) major  = Integer.valueOf(subs[0]);
			if (subs.length > 1) minor  = Integer.valueOf(subs[1]);
			
			if (major >= REQUIRED_JAVA3D_MAJOR_VERSION 
					&& minor >= PREFERRED_JAVA3D_MINOR_VERSION)
			{
				return true;
			}
		}
		
		return false;
	}
	
	private static String getRequiredJava3DVersionStr() {
		return REQUIRED_JAVA3D_MAJOR_VERSION
				+ "." + REQUIRED_JAVA3D_MINOR_VERSION;
	}
	
	public static String getInstalledJava3DVersionStr() {
		return (String)SimpleUniverse.getProperties().get("j3d.version");
	}
}
