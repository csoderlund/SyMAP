package symap;

import java.awt.Color;
import javax.swing.JApplet;
import javax.swing.SwingUtilities;
import java.sql.SQLException;

import symap.SyMAP;
import symap.frame.SyMAPFrame;
import symap.pool.DatabaseUser;
import util.DatabaseReader;
import util.Utilities;
import symap.drawingpanel.DrawingPanel;

/**
 * Class <code>SyMAPApplet</code> creates a new SyMAP on init() and provides
 * an API for calls from javascript.
 *
 * @see JApplet
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SyMAPApplet extends JApplet {
	private static final boolean METHOD_TRACE = false;
	
	public static final String BLOCK_TYPE    = "block";
	public static final String SEQUENCE_TYPE = "sequence";
	public static final String CONTIG_TYPE   = "contig";
	public static final String SHOW_NAME     = "show";
	public static final String PROJECT_NAME  = "project";
	public static final String TYPE_NAME     = "type";
	public static final String CONTENT_NAME  = "content";
	public static final String DATABASE_URL  = "database";
	public static final String USERNAME      = "username";
	public static final String PASSWORD      = "password";

	private SyMAP symapObj;
	private SyMAPFrame frame = null;
	private Object lock = new Integer(0);
	private boolean show = true;
	private boolean ready= false;

	public SyMAPApplet() {
		super();
	}

	/**
	 * Method <code>init</code> initializes SyMAP by creating a new instance, 
	 * if it hasn't already been, sending this applet instance and a database 
	 * reader with the values set based off of parameters of the applet and 
	 * default values (parameters have precedence), and acquires the SyMAPFrame.
	 */
	public void init() {
		SyMAP.printVersion();
		
		if (!SyMAP.checkJavaSupported(frame)) {
			show = false;
			return;
		}
		
		String param = getParameter("filtered");
		if (param != null) try { 
			symap.mapper.HitFilter.setFilteredDefault(Boolean.valueOf(param).booleanValue());
		} catch (Exception e) { e.printStackTrace(); }
		
		// provide interface to enable annotation descriptions
		param = getParameter("annot");
		if (param != null) try { 
			symap.sequence.Sequence.setDefaultShowAnnotation(Boolean.valueOf(param).booleanValue());
		} catch (Exception e) { e.printStackTrace(); }
		
		param = null;
		
		Utilities.setResClass(this.getClass());

		if (frame == null) {
			symapObj = null;
			try {
				symapObj = new SyMAP(this,getDatabaseReader(), null);
				symapObj.clear(); 
				frame = symapObj.getFrame();
				Utilities.setHelpParentFrame(frame);
				
				String project, type, content;
				param = getParameter(SHOW_NAME);
				if (param != null && Boolean.valueOf(param).booleanValue() != show)
					show = !show;

				for (int i = 1; i <= DrawingPanel.MAX_TRACKS; i++) {
					project = getParameter(PROJECT_NAME + i);
					type    = getParameter(TYPE_NAME    + i);
					content = getParameter(CONTENT_NAME + i);
					if (project != null && type != null && content != null) {
						int p = -1;
						try {
							p = Integer.parseInt(project);
						} catch (Exception e) { }

						if (p < 0) setTrack(i,project,type,content);
						else       setTrackByProjectNumber(i,p,type,content);
					}
				}
				ready = true;
				System.out.println("Initialization done, applet is ready.");
			} catch (SQLException se) {
				se.printStackTrace();
			}
		}
		
		if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.init()");
	}	

	/**
	 * Method <code>isReady</code> returns true if the applet has completed initialization
	 * without exceptions.
	 *
	 * @return a <code>boolean</code> value
	 */
	public boolean isReady() {
		return ready;
	}

	/**
	 * Method <code>start</code> calls <code>showFrame()</code> if the
	 * applet is to be shown (i.e. show parameter was not set to false).
	 *
	 * @see #showFrame()
	 */
	public void start() {
		if (METHOD_TRACE) System.out.println("Entering SyMAPApplet.start() show=" + show);
		if (show) showFrame();
		if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.start()");
	}

	/**
	 * Method <code>stop</code> hides the frame.
	 *
	 * @see SyMAPFrame#hide()
	 */
	public void stop() {
		if (METHOD_TRACE) System.out.println("Entering SyMAPApplet.stop()");
		if (frame != null) frame.hide();
		if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.stop()");
	}

	public void destroy() {
		if (METHOD_TRACE) System.out.println("Entering SyMAPApplet.destroy()");
		if (frame != null) {
			frame.hide();
			frame.dispose();
		}
		frame = null;
		
		symapObj.clear(); 
		symapObj = null;
		super.destroy();

		if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.destroy()");
	}

	/**
	 * Sets the ends of a track at position <code>pos</code>.  The track at that
	 * position should already be set.
	 *
	 * @param pos
	 * @param startBP
	 * @param endBP
	 * @return true on success, false otherwise (i.e. track at position not set)
	 */
	public boolean setTrackEnds(int pos, double startBP, double endBP) {
		if (METHOD_TRACE) System.out.println("Entering SyMAPApplet.setTrackEnds("+pos+","+startBP+","+endBP+")");
		synchronized (lock) {
			boolean success = false;
			if (frame != null) {
				frame.getDrawingPanel().closeFilters();
				try {
					success = frame.getDrawingPanel().setTrackEnds(pos,startBP,endBP);
				} catch (Exception exc) {
					exc.printStackTrace();
				}
			}
			else
				System.err.println("SyMAPApplet.setTrackEnds: applet not ready"); 
			if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.setTrackEnds(int,double,double)");
			return success;
		}
	}

	/**
	 * Sets the track at position <code>position</code> to correspond to the 
	 * paramaters project, type, and content.
	 * <br><br>
	 * Group id is assumed over group name as long as content can parse to an 
	 * int value.
	 * <br>
	 * @param position The track position starting at 1
	 * @param projectName  The project name of the new track
	 * @param type     The type of the track (values defined in 
	 *                 /properties/applet.properties file)
	 * @param content  The content (i.e. block => '1,2,6-9', 
	 *                 sequence => &lt;group id or group name&gt;,
	 *                 contig => &lt;contig number&gt;).
	 * @see SyMAPFrame#getDrawingPanel()
	 * @see symap.drawingpanel.DrawingPanel#getPools()
	 * @see symap.pool.Pools#getProjectProperties()
	 * @see symap.pool.ProjectProperties#getID(String)
	 */
	public void setTrack(int position, String projectName, String type, String content) {
		if (frame != null) {
			int project = frame.getDrawingPanel().getPools().getProjectProperties().getID(projectName);
			if (project >= 0) 
				setTrackByProjectNumber(position,project,type,content);
			else
				System.err.println("SyMAPApplet.setTrack: unknown project '" + projectName + "'"); 
		}
		else
			System.err.println("SyMAPApplet.setTrack: applet not ready"); 
	}

	/**
	 * Sets the track at position <code>position</code> to correspond to the 
	 * paramaters project, type, and content.
	 * <br><br>
	 * Group id is assumed over group name as long as content can parse to an 
	 * int value.
	 * <br>
	 * @param position The track position starting at 1
	 * @param project  The project id of the new track
	 * @param type     The type of the track (values defined in 
	 *                 /properties/applet.properties file)
	 * @param content  The content (i.e. block => '1,2,6-9', 
	 *                 sequence => &lt;group id or group name&gt;, 
	 *                 contig => &lt;contig number&gt;).
	 *             
	 * @see SyMAPFrame#getDrawingPanel()
	 * @see symap.drawingpanel.DrawingPanel#setBlockTrack(int,int,String)
	 * @see symap.drawingpanel.DrawingPanel#setContigTrack(int,int,int)
	 * @see symap.drawingpanel.DrawingPanel#setSequenceTrack(int,int,int)
	 * @see symap.sequence.PseudoPool#getGroupID(String,int)
	 */
	public void setTrackByProjectNumber(int position, int project, String type, String content) {
		if (METHOD_TRACE) System.out.println("Entering SyMAPApplet.setTrackByProjectNumber() position=" + position + " project=" + project + " type=" + type + " content=" + content);
		if (type == null || content == null || position < 1) {
			System.err.println("SyMAPApplet.setTrackByProjectNumber: null params"); 
			return;
		}
		type = type.intern();
		if (type == null || (type != CONTIG_TYPE && type != BLOCK_TYPE && type != SEQUENCE_TYPE)) {
			System.err.println("SyMAPApplet.setTrackByProjectNumber: bad type"); 
			return;
		}
		synchronized (lock) {
			if (frame != null) {
				Color bgColor = SyMAP.projectColors[(position-1) % (SyMAP.projectColors.length)]; 
				if (type == BLOCK_TYPE) {
					frame.getDrawingPanel().setBlockTrack(position,project,content,bgColor);
				}
				else if (type == CONTIG_TYPE) {
					frame.getDrawingPanel().setContigTrack(position,project,Integer.parseInt(content));
				}
				else { // type == SEQUENCE_TYPE
					int gid = frame.getDrawingPanel().getPools().getPseudoPool().getGroupID(content,project);
					if (gid <= 0) System.err.println("SyMAPApplet.setTrackByProjectNumber: unable to acquire the group id for "+content);
					else frame.getDrawingPanel().setSequenceTrack(position,project,gid,bgColor);
				}
			}
			else
				System.err.println("SyMAPAppletsetTrackByProjectNumber: applet not ready"); 
		}
		if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.setTrack(int,int,String,String)");
	}

	/**
	 * Method <code>showFrame</code> should be used to show the frame.
	 * @see SyMAPFrame#show()
	 */
	public void showFrame() {
		if (METHOD_TRACE) System.out.println("Entering SyMAPApplet.showFrame()");
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (frame != null) frame.show();
				else System.err.println("SyMAPApplet.showFrame: applet not ready"); 
			}
		});
		show = true;
		if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.showFrame()");
	}

	/**
	 * Method <code>hideFrame</code> should be used to hide the frame.
	 * @see SyMAPFrame#hide()
	 */
	public void hideFrame() {
		if (METHOD_TRACE) System.out.println("Entering SyMAPApplet.hideFrame()");
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (frame != null) frame.hide();
				else System.err.println("SyMAPApplet.hideFrame: applet not ready"); 
			}
		});
		show = false;
		if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.hideFrame()");
	}

	/**
	 * Method <code>setMaps</code> sets the number of maps desired.
	 *
	 * @param numberOfMaps an <code>int</code> value
	 * @see SyMAPFrame#getDrawingPanel()
	 * @see symap.drawingpanel.DrawingPanel#setMaps(int)
	 */
	public void setMaps(int numberOfMaps) {
		if (METHOD_TRACE) System.out.println("Entering SyMAPApplet.setMaps(int)");
		synchronized (lock) {
			if (frame != null) {
				System.out.println("Clearing the history."); 	
				symapObj.getHistory().clear(); 					
				frame.getDrawingPanel().setMaps(numberOfMaps);
			}
			else System.err.println("SyMAPApplet.setMaps: applet not ready"); 
		}
		if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.setMaps(int)");
	}

	public void setHighlightClones(String listOfRemarkIDs) {
		if (METHOD_TRACE) System.out.println("Entering SyMAPApplet.setHighlightClones(String)");
		if (listOfRemarkIDs != null && listOfRemarkIDs.length() > 0) {
			synchronized (lock) {
				if (frame != null) frame.getDrawingPanel().setHighlightedClones(Utilities.getIntArray(listOfRemarkIDs));
				else System.err.println("SyMAPApplet.setHighlightedClones: applet not ready"); 
			}
		}
		else 
			System.err.println("SyMAPApplet.setHighlightClones: bad params"); 
		if (METHOD_TRACE) System.out.println("Exiting SyMAPApplet.setHighlightClones(String)");
	}

	private DatabaseReader getDatabaseReader() {
		return DatabaseUser.getDatabaseReader(SyMAPConstants.DB_CONNECTION_SYMAP_APPLET,
				getParameter(DATABASE_URL),
				getParameter(USERNAME),
				getParameter(PASSWORD),
				Utilities.getHost(this));
	}
}
