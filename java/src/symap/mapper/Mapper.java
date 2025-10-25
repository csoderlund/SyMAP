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

import props.PropsDB;
import props.PropertiesReader;
import database.DBconn2;
import symap.Globals;
import symap.drawingpanel.DrawingPanel;
import symap.drawingpanel.FilterHandler;
import symap.frame.HelpBar; 		
import symap.frame.HelpListener;
import symap.sequence.Sequence;
import symap.sequence.TrackHolder;
import symapQuery.TableMainPanel;
import util.ErrorReport;

/**
 * The Mapper that holds two tracks (overlaying them when drawn) and all of the hits.
 * Called from DrawingPanel
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Mapper extends JComponent 
	implements  MouseMotionListener, MouseListener, MouseWheelListener, HelpListener 		
{
	// ColorDialog colors at bottom
	private DrawingPanel drawingPanel; 
	private TrackHolder trackHolders[];
	private FilterHandler fh;
	private HfilterData hitFilData;
	private TableMainPanel theTablePanel;
	private SeqHits seqHitObj; 
	private MapperPool mapPool;
	
	private volatile boolean initing;
	private String helpText; 
	
	// Created in DrawingPanel for display
	public Mapper(DrawingPanel drawingPanel, TrackHolder th1, TrackHolder th2,
			FilterHandler fh, DBconn2 dbc2, PropsDB projPool, HelpBar hb, TableMainPanel listPanel) 
	{
		super();
		this.mapPool = new MapperPool(dbc2, projPool); 
		this.drawingPanel = drawingPanel; 
		this.fh = fh;
		this.theTablePanel = listPanel;
		initing = true;
		
		hitFilData = new HfilterData(); 
		fh.setHfilter(this); 			// creates Hfilter with this hitFilData
		
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
	public boolean initHits() { // DrawingPanel.make and above
		Sequence t1 = trackHolders[0].getTrack();
		Sequence t2 = trackHolders[1].getTrack();
		
		if (t1 == null || t2 == null) return false;

		if (seqHitObj!=null) return true;
		
		initing = true;
		seqHitObj = mapPool.setData(this, t1, t2); 
		seqHitObj.setMinMax(hitFilData);	
		initing = false;
		
		if (seqHitObj==null) ErrorReport.print("SyMAP internal error getting hits");
		return (seqHitObj!=null);
	}
	public void setVisible(boolean visible) {
		fh.getFilterButton().setEnabled(visible);
		super.setVisible(visible);
	}

	public void update() {} // HitFilter; 
	
	public void setMapperData(MapperData md) { // DrawingPanel.setMaps for history
		if (md != null) {
			md.setMapper(this);
			initHits();
		}
	}
	
	public MapperData getMapperData() {return new MapperData(this);}
	public DrawingPanel getDrawingPanel() { return drawingPanel;}
	public HfilterData getHitFilter() {return hitFilData;}
	
	// For HitData.getCoordsForGenePopup
	public String getGeneNum1(int annoIdx) { return seqHitObj.getSeqObj1().getGeneNumFromIdx(annoIdx);}
	public String getGeneNum2(int annoIdx) { return seqHitObj.getSeqObj2().getGeneNumFromIdx(annoIdx);}
	
	public JButton getFilterButton() {return fh.getFilterButton();}
	public Sequence getTrack1() {return trackHolders[0].getTrack();}
	public Sequence getTrack2() {return trackHolders[1].getTrack();}
	public Sequence getTrack(int trackNum) {return trackHolders[trackNum].getTrack();}
	
	protected int getTrackPosition(Sequence t) {
		if (trackHolders[0].getTrack() == t)	return Globals.LEFT_ORIENT;
		else									return Globals.RIGHT_ORIENT;
	}
	// closeup align
	public Vector <HitData> getHitsInRange(Sequence src, int start, int end) {
		Vector <HitData> retHits = new Vector<HitData>();
		seqHitObj.getHitsInRange(retHits, start, end, isQueryTrack(src));  
		return retHits;
	}

	public int[] getMinMax(Sequence src, int start, int end) {
		int[] minMax = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE };
		
		seqHitObj.getMinMax(minMax, start, end, isQueryTrack(src));
		
		if (minMax[0] == Integer.MAX_VALUE || minMax[1] == Integer.MIN_VALUE)
			return null; // no hits within range
		
		return minMax;
	}
	
	/**********************************************
	 * hasPair is (query,target); query<target alphanumeric; 
	 */
	public boolean isQueryTrack(Sequence src) {
		Sequence t1 = trackHolders[0].getTrack(); 
		Sequence t2 = trackHolders[1].getTrack();
		
		if (t2==null) System.out.println("Mapper Error: NULL TRACK 2");
		
		if (t1.getProjIdx() == t2.getProjIdx()) { // isSelf
			if (t1==src && getTrackPosition(t1)==Globals.LEFT_ORIENT) return true;
			else return false;
		}
		if (t1 == src && mapPool.hasPair(t1, t2)) return true; // query on left
		if (t2 == src && mapPool.hasPair(t2, t1)) return true; // query on right 
		
		return false; // target
	}
	
	public boolean isQuerySelHit(int idx, boolean bHit1) {
		if(theTablePanel != null) return theTablePanel.isHitSelected(idx, bHit1);
		return false;
	}
	/********************************************************************/
	public void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		// this makes hit lines thicker, but drawing is slower on Linux 
		// g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (!initing && seqHitObj!=null) 
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
		int length = trackHolders[0].getTrack().getTrackSize();
		for (int i = 0;  i < trackHolders.length;  i++)
			length = Math.min(length,trackHolders[i].getTrack().getEnd()-trackHolders[i].getTrack().getStart()+1);
		for (int i = 0;  i < trackHolders.length;  i++)
			trackHolders[i].getTrack().mouseWheelMoved(e, length);
	}
	public void mousePressed(MouseEvent e) {// popup hit wire description; see SeqHits.DrawHits.doPopupDesc
		if (e.isPopupTrigger()) {
			if (seqHitObj.doPopupDesc(e)) return;
			fh.showPopup(e);
		}
	} 
	public boolean isActive() { // drawingPanel can check
		return (trackHolders[0].getTrack()!=null && trackHolders[1].getTrack()!=null);
	}
	/******************************************************************/
	// called by HelpBar
	public String getHelpText(MouseEvent e) { 
		if (helpText == null) return hitFilData.getFilterText() + "    \n" + seqHitObj.getInfo();
		
		return helpText; 
	}
	protected void setHelpText(String text) {helpText = text;}// set in SeqHits.DrawHits
		
	////////////////////////////////////////////////
	public String toString() {
		return "[Mapper (Track1: " + trackHolders[0].toString() + " "+ trackHolders[0].getTrack() + 
				     ") (Track2: " +  trackHolders[1].toString() + " "+ trackHolders[1].getTrack() + ")]";
	}
	public void clearData() {
		if (seqHitObj!=null) {
			seqHitObj.clearData();
			seqHitObj = null;
		}
	}
	public void closeFilter() {fh.hide();} // closes this mappers filter window @see FilterHandler#hide() 
	
	public void setTrackBuild() {
		if (trackHolders[0].getTrack() != null) trackHolders[0].getTrack().setTrackBuild();
		if (trackHolders[1].getTrack() != null) trackHolders[1].getTrack().setTrackBuild();
	}
	/******************************************************************/
	// drawing methods directly access these, and ColorDialog can change them
	public static Color negOrientLineColor;
	public static Color posOrientLineColor;
	public static Color pseudoLineColorPP; 
	public static Color pseudoLineColorPN;
	public static Color pseudoLineColorNP;
	public static Color pseudoLineColorNN;
	public static Color pseudoLineHoverColor;			
	public static Color pseudoLineGroupColor;		// Query group and Gene popup	
	public static Color pseudoLineHighlightColor1;
	public static Color pseudoLineHighlightColor2;
	public static Color pseudoLineHighlightColor3;  // Block 3rd color;
	public static Color pseudoLineHighlightColor4;  // Block 4th color
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
		pseudoLineGroupColor = 		props.getColor("pseudoLineGroupColor");
		pseudoLineHighlightColor1 = props.getColor("pseudoLineHighlightColor1"); 
		pseudoLineHighlightColor2 = props.getColor("pseudoLineHighlightColor2"); 
		hitRibbonBackgroundColor = 	props.getColor("hitRibbonBackgroundColor"); 
		
		props = new PropertiesReader(Globals.class.getResource("/properties/annotation.properties"));// CAS571 added for 2 extra colors
		pseudoLineHighlightColor3 = props.getColor("exonColorP"); 
		pseudoLineHighlightColor4 = props.getColor("exonColorN"); 
	}
}
