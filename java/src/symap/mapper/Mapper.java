package symap.mapper;

import java.util.Vector;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;	
import java.awt.event.MouseListener; 	
import java.awt.event.MouseWheelListener; 	
import javax.swing.JComponent;
import javax.swing.JButton;

import props.ProjectPool;
import props.PropertiesReader;
import database.DBconn2;
import symap.Globals;
import symap.drawingpanel.ControlPanel;
import symap.drawingpanel.DrawingPanel;
import symap.drawingpanel.FilterHandler;
import symap.frame.HelpBar; 		
import symap.frame.HelpListener;
import symap.sequence.Track;
import symap.sequence.TrackHolder;
import symapQuery.TableDataPanel;
import util.ErrorReport;

/**
 * The Mapper that holds two tracks (overlaying them when drawn) and all of the hits.
 * Called from DrawingPanel
 * CAS531 major changes as there was a List that was actually only one Object, major red-herring code
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Mapper extends JComponent 
	implements   // CAS521 remove Filtered Interface CAS542 remove HfilterData.HitFilterListener, 
		MouseMotionListener, MouseListener, MouseWheelListener, HelpListener 		
{
	// ColorDialog colors at bottom
	private DrawingPanel drawingPanel; 
	private TrackHolder trackHolders[];
	private FilterHandler fh;
	private HfilterData hitFilData;
	private TableDataPanel theTablePanel;
	private SeqHits seqHitObj; // CAS531 was List, but actually only one object
	private MapperPool mapPool;
	
	private volatile boolean initing;
	private String helpText; // CAS520 add hover
	
	private static final String HOVER_MESSAGE = 
			"Hit-wire Information:"
			+ "\n-Hover on hit-wire for information."
			+ "\n-Right-click on hit-wire for popup of full information."
			+ "\n-Right-click in white space of hit area for subset filter popup."
			+ "\n-Filters are retained between displays of a session.";
	
	// Created in DrawingPanel; it starts out making 100 mappers with trackholders but no tracks
	public Mapper(DrawingPanel drawingPanel, TrackHolder th1, TrackHolder th2,
			FilterHandler fh, DBconn2 dbc2, ProjectPool projPool, HelpBar hb, TableDataPanel listPanel) 
	{
		super();
	
		this.mapPool = new MapperPool(dbc2, projPool); // CAS541 create here instead of passing as arg
		this.drawingPanel = drawingPanel; 
		this.fh = fh;
		this.theTablePanel = listPanel;
		initing = true;
		
		hitFilData = new HfilterData(); // CAS542 hitFilter = new HitFilter(this); was a listener
		fh.setHfilter(this); // creates Hfilter with this hitFilData
		
		trackHolders = new TrackHolder[2];
		trackHolders[0] = th1;
		trackHolders[1] = th2;

		setOpaque(false);
		setVisible(false);
		addMouseListener(this); 		
		addMouseWheelListener(this);	
		addMouseMotionListener(this);
		
		if (hb != null) hb.addHelpListener(this,this); 
	}
	
	public void clearData() {
		seqHitObj = null;
	}
	// called from HitFilter
	public void update() {
		// CAS541 dead code; if (!mapinfo.hasHitContent(hf.isBlock(), false)) // CAS531 hf.isRepeat which was always false
		init();
	}
	public String getFilterText() {return hitFilData.getFilterText();} // Called from SeqHits 

	/** Method closeFilter closes this mappers filter window @see FilterHandler#hide() */
	public void closeFilter() {fh.hide();}
	
	public void clearTrackBuild() {
		if (trackHolders[0].getTrack() != null) trackHolders[0].getTrack().clearTrackBuild();
		if (trackHolders[1].getTrack() != null) trackHolders[1].getTrack().clearTrackBuild();
	}

	/* initializes the mapper downloading all hits. Was called from dead code in drawingPanel
	public boolean initAllHits() {
		HfilterData hf = hitFilData.copy("Mapper initAllHits");
		//hf.setBlock(false); // CAS542 remove
		return myInit(hf);
	}*/
	public boolean init() {
		return myInit(hitFilData);
	}

	private boolean myInit(HfilterData hf) {
		Track t1 = trackHolders[0].getTrack();
		Track t2 = trackHolders[1].getTrack();
		if (t1 == null || t2 == null) return false;

		if (seqHitObj!=null) return true;
		initing = true;
		seqHitObj = mapPool.setData(this, t1, t2, hf); 
		seqHitObj.setMinMax(hitFilData);	
		initing = false;
		
		if (seqHitObj==null) ErrorReport.print("SyMAP internal error getting hits");
		return (seqHitObj!=null);
	}
	public void setMapperData(MapperData md) {
		if (md != null) {
			md.setMapper(this);
			init();
		}
	}
	public void setVisible(boolean visible) {
		fh.getFilterButton().setEnabled(visible);
		super.setVisible(visible);
	}

	public MapperData getMapperData() {return new MapperData(this);}
	
	public DrawingPanel getDrawingPanel() { return drawingPanel;}
	
	public HfilterData getHitFilter() {return hitFilData;}
	
	// CAS548 add for HitData.getCoordsForGenePopup
	public String getGeneNum1(int annoIdx) { return seqHitObj.getSeqObj1().getGeneNumFromIdx(annoIdx);}
	public String getGeneNum2(int annoIdx) { return seqHitObj.getSeqObj2().getGeneNumFromIdx(annoIdx);}
	
	public JButton getFilterButton() {return fh.getFilterButton();}
	
	public Track getTrack1() {return trackHolders[0].getTrack();}
	
	public Track getTrack2() {return trackHolders[1].getTrack();}
	
	public Track getTrack(int trackNum) {return trackHolders[trackNum].getTrack();}
	
	protected int getTrackPosition(Track t) {
		if (trackHolders[0].getTrack() == t)	return Globals.LEFT_ORIENT;
		else									return Globals.RIGHT_ORIENT;
	}
	// closeup align
	public Vector <HitData> getHitsInRange(Track src, int start, int end) {
		Vector <HitData> retHits = new Vector<HitData>();
		seqHitObj.getHitsInRange(retHits, start, end, isQueryTrack(src));  
		return retHits;
	}

	public int[] getMinMax(Track src, int start, int end) {
		int[] minMax = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE };
		
		seqHitObj.getMinMax(minMax, start, end, isQueryTrack(src));
		
		if (minMax[0] == Integer.MAX_VALUE || minMax[1] == Integer.MIN_VALUE)
			return null; // no hits within range
		
		return minMax;
	}
	
	/**********************************************
	 * hasPair is always (query,target);  CAS517 rearranged and renamed from isSwapped 
	 * return (t1 == src && !pool.hasPair(t2, t1)) || (t2 == src && !pool.hasPair(t1, t2))|| (isSelf() && src == t1); 
	 */
	public boolean isQueryTrack(Track src) {
		Track t1 = getTrack1(); 
		Track t2 = getTrack2();
		
		if (t2==null) System.out.println("Mapper Error: NULL TRACK 2");
		
		if (t1.getProject() == t2.getProject()) { // isSelf
			if (t1==src && getTrackPosition(t1)==Globals.LEFT_ORIENT) return true;
			else return false;
		}
		if (t1 == src && mapPool.hasPair(t1, t2)) return true; 
		if (t2 == src && mapPool.hasPair(t2, t1)) return true;
		
		return false;
	}
	
	// CAS516 change to one call instead of 4
	public boolean isQuerySelHit(int s1, int e1, int s2, int e2) {
		if(theTablePanel != null)
			return theTablePanel.isHitSelected(s1, e1, s2, e2);
		return false;
	}
	/********************************************************************/
	public void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		// CAS533 this makes hit lines thicker, but drawing is slower on Linux 
		// g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		if (!initing) 
			seqHitObj.paintComponent(g2);
	}
	
	public void mouseDragged(MouseEvent e) { }
	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) { }
	
	public void mouseExited(MouseEvent e) {
		if (!initing)
			seqHitObj.mouseExited(e);
	}
	public void mouseMoved(MouseEvent e) {
		if (!initing)
			seqHitObj.mouseMoved(e);
	}
	public void mouseWheelMoved(MouseWheelEvent e) {
		long length = trackHolders[0].getTrack().getTrackSize();
		for (int i = 0;  i < trackHolders.length;  i++)
			length = Math.min(length,trackHolders[i].getTrack().getEnd()-trackHolders[i].getTrack().getStart()+1);
		for (int i = 0;  i < trackHolders.length;  i++)
			trackHolders[i].getTrack().mouseWheelMoved(e, length);
	}
	// CAS517 add for popup hit wire description; see SeqHits.PseudoHits.doPopupDesc
	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger()) {
			if (seqHitObj.doPopupDesc(e)) return;
			fh.showPopup(e);
		}
	} 
	public boolean isActive() { // CAS531 add so drawingPanel can check
		return (trackHolders[0].getTrack()!=null && trackHolders[1].getTrack()!=null);
	}
	/******************************************************************/
	public String toString() {
		return "[Mapper (Track1: " + trackHolders[0].toString() + " "+ trackHolders[0].getTrack() + 
				     ") (Track2: " +  trackHolders[1].toString() + " "+ trackHolders[1].getTrack() + ")]";
	}
	// called by HelpBar
	public String getHelpText(MouseEvent e) { 
		int n = drawingPanel.getStatOpts();
		if (n==ControlPanel.pHELP) return HOVER_MESSAGE;
		
		if (helpText == null) return hitFilData.getFilterText() + "    \n" + seqHitObj.getInfo();
		
		return helpText; 
	}
	// set in SeqHits.PseudoHits
	public void setHelpText(String text) {
		helpText = text;
	}
	/******************************************************************/
	// drawing methods directly access these, and ColorDialog can change them
	public static Color negOrientLineColor;
	public static Color posOrientLineColor;
	public static Color pseudoLineColorPP; 
	public static Color pseudoLineColorPN;
	public static Color pseudoLineColorNP;
	public static Color pseudoLineColorNN;
	public static Color pseudoLineHoverColor;		// CAS520 renamed to Hover and use Highlight for Highlight	
	public static Color pseudoLineHighlightColor1;
	public static Color pseudoLineHighlightColor2;
	public static Color hitRibbonBackgroundColor;	
	public static int 	hitRibbonWidth=3; 			
	static {
		PropertiesReader props = new PropertiesReader(Globals.class.getResource("/properties/mapper.properties"));
		posOrientLineColor = 		props.getColor("posOrientLineColor");
		negOrientLineColor = 		props.getColor("negOrientLineColor");
		pseudoLineColorPP = 		props.getColor("pseudoLineColorPP"); 
		pseudoLineColorPN = 		props.getColor("pseudoLineColorPN"); 
		pseudoLineColorNP = 		props.getColor("pseudoLineColorNP"); 
		pseudoLineColorNN = 		props.getColor("pseudoLineColorNN"); 
		pseudoLineHoverColor = 		props.getColor("pseudoLineHoverColor");
		pseudoLineHighlightColor1 = props.getColor("pseudoLineHighlightColor1"); 
		pseudoLineHighlightColor2 = props.getColor("pseudoLineHighlightColor2"); 
		hitRibbonBackgroundColor = 	props.getColor("hitRibbonBackgroundColor"); 
	}
}
