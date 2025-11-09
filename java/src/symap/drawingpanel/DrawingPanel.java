package symap.drawingpanel;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.util.Vector;

import database.DBconn2;
import colordialog.ColorListener;
import props.PropsDB;
import symap.Globals;
import symap.frame.HelpBar;

import symap.mapper.HitData;
import symap.mapper.HfilterData;
import symap.mapper.Mapper;
import symap.mapper.MapperData;
import symap.sequence.Sequence;
import symap.sequence.TrackData;
import symap.sequence.TrackHolder;
import symap.sequence.TrackLayout;
import symap.closeup.CloseUp;

import symapQuery.TableMainPanel;

import util.ErrorReport;
import util.Popup;

/**
 * The DrawingPanel is the area that the maps are drawn onto. 
 * Used by Explorer for 2D right hand side, and for DotPlot, Query and Blocks for popup 2D
 * 
 * 1. Create frame. 2. Assign trackholders. 3. Assign Sequence. 
 */
public class DrawingPanel extends JPanel implements ColorListener, HistoryListener { 
	private static final long serialVersionUID = 1L;

	private static Color backgroundColor = Color.white;
	
	public double trackDistance = 100; // distance between tracks, used in TrackLayout; can be changed (was PADDING)
	public boolean isToScale=false; // Tells TrackLayout tracks should be same size. 
	public String selectMouseFunction = null; // Set by ControlPanel for Selected Track Region
	
	private Mapper[] mappers;
	private TrackHolder[] trackHolders;
	private HistoryControl historyControl;
	private Frame2d frame2dListener;
	private PropsDB propDB = null;
	private CloseUp closeup;
	private ControlPanel cntlPanel; // for reset

	private DBconn2 dbc2;

	private JPanel buttonPanel = null; 			// Filter buttons added in TrackLayout
	private JScrollPane scrollPane = null;
	private HelpBar helpbar = null;				// if non-explorer, helpbar goes at bottom
	private TableMainPanel queryPanel = null;	// special for Query
	
	private int numTracks = 0, numMaps=0;
	
	private boolean bUpdateHistory = true, bReplaceHistory = false;

	private void dprt(String msg) {symap.Globals.dprt("DP: " + msg);}
	
	// Called in frame.SyMAP2d; the explorer reuses the drawing panel, non-explorers do not.
	public DrawingPanel(TableMainPanel listPanel, DBconn2 dbc2, HistoryControl hc, HelpBar bar) { 
		super();
		setBackground(backgroundColor);
		
		this.dbc2 = dbc2; // made copy in SyMAP2d, close in SyMAP2d
		propDB = new PropsDB(dbc2);
		historyControl = hc;
		helpbar = bar;			
		queryPanel = listPanel;	

		buttonPanel = new JPanel(null); // Filter buttons are added in TrackLayout
		buttonPanel.setOpaque(false);

		scrollPane = new JScrollPane(this);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED); 
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED); 
		scrollPane.getVerticalScrollBar().setUnitIncrement(50);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(50);
		scrollPane.getViewport().setBackground(backgroundColor);

		scrollPane.setColumnHeaderView(buttonPanel);
		scrollPane.getColumnHeader().setBackground(backgroundColor);
	}
	// Initialized tracks; called from all 2d inits
	public void setTracks(int num) {
		numTracks = num; ; 
		if (num>3) numTracks = num + (num-3); // after 3, ref is duplicated,e.g 1R3R4 num=4+1
		numMaps=numTracks-1;
		
		trackHolders = new TrackHolder[numTracks]; // assign seqObj in setSequenceTrack below
		for (int i = 0; i < trackHolders.length; i++) {
			int o =  (i == 0) ? Globals.LEFT_ORIENT : Globals.RIGHT_ORIENT ;
			trackHolders[i] = new TrackHolder(this, helpbar, (i+1), o); 
			add(trackHolders[i]);
		}
		
		mappers = new Mapper[numMaps];
		for (int i = 0; i < mappers.length; i++) {
			FilterHandler fh = new FilterHandler(this);
			mappers[i] = new Mapper(this,trackHolders[i],trackHolders[i+1], fh, dbc2, propDB, helpbar, queryPanel); 
			add(mappers[i]);
			mappers[i].setLocation(0,0);
		}
		
		setLayout(new TrackLayout(this, trackHolders,mappers,buttonPanel));
	}
	
	// Called by all functions that display 2d; assigns sequence to a track
	public Sequence setSequenceTrack(int position, int projIdx, int grpIdx, Color color) {// position starts at 1
		TrackHolder holder = trackHolders[position-1];	
		Sequence track = new Sequence(this,holder); 
		
		track.setup(projIdx, grpIdx); // sets all necessary in Sequence, read DB for anno
		
		track.setBackground(color); 
		track.setPosition(position);
		
		trackHolders[position-1].setTrack(track);
		if (position>1) { 
			Sequence otrack = trackHolders[position-2].getTrack();
			track.setOtherProject(otrack.getProjIdx());
			otrack.setOtherProject(track.getProjIdx());
		}
		
		bUpdateHistory = true;  // 1st history of stack
		
		return (Sequence) track; // for Query.TableDataPanel to set show defaults
	}
	
	public void paint(Graphics g) {
		super.paint(g);
		for (Mapper m : mappers) {
			if (m.isActive()) 		
				m.paintComponent(g); 
			else dprt("mapper timing problem");
		}
	}

	protected void setListener(Frame2d dpl) { this.frame2dListener = dpl;} // Frame2d
	protected void setCntl(ControlPanel cp) { this.cntlPanel = cp; } // Frame2d reset for History

	protected Frame getFrame() { // FilterHandler
		if (frame2dListener != null) return frame2dListener.getFrame();
		else return null;
	}
	public void setFrameEnabled(boolean enabled) {
		if (frame2dListener != null) frame2dListener.setFrameEnabled(enabled);
	}

	public PropsDB getPropDB() 		{return propDB;} 
	public DBconn2 getDBC() 		{return dbc2; }		// CloseUpDialog, Sequence, TextShowSeq; 
	protected JComponent getView() 	{return scrollPane;}
	protected void setCloseUp(CloseUp closeup) {this.closeup = closeup;}
	
	public CloseUp getCloseUp() {return closeup;}

	public int getViewHeight() { return scrollPane.getViewport().getHeight();}

	public void resetColors() {// ColorDialogHandler
		setBackground(backgroundColor);	
		
		scrollPane.getViewport().setBackground(backgroundColor);
			
		scrollPane.getColumnHeader().setBackground(backgroundColor);
		 
		buttonPanel.setBackground(backgroundColor);
		setTrackBuild();
		
		amake();
	}
	
	/////////////////////////////////////
	public Vector <HitData> getHitsInRange(Sequence seqObj, int start, int end) {//closeup.TextShowSeq
		Vector <HitData> list = new Vector <HitData>();
		for (Mapper m : mappers) {
			if (m.isActive()) {
				Sequence t1 = m.getTrack1(); // left
				Sequence t2 = m.getTrack2(); // right
				if (t1 == seqObj || t2 == seqObj)  
					list.addAll(m.getHitsInRange(seqObj,start,end));
			}
		}
		return list;
	}
	
	/* Called by all functions that display 2d
	 * The ends of the map are locked till the next build if the track at pos is a Sequence.*/
	public boolean setTrackEnds(int pos, double startBP, double endBP) throws IllegalArgumentException {
		Sequence track = trackHolders[pos-1].getTrack();
		if (track != null) {
			track.setStartBP((int)Math.round(startBP),true);
			track.setEndBP((int)Math.round(endBP),true);
			setUpdateHistory();
			return true;
		}
		return false;
	}
	// set defaults for: symapQuery Show Synteny from table and dotplot.Data 
	public void setHitFilter(int map, HfilterData hf) {
		mappers[map-1].getHitFilter().setChanged(hf, "DP setHitFilter");
	}

	private Sequence getOpposingTrack(Sequence src, int orientation) {
		for (int i = 0; i < numMaps; i++) {
			Sequence t1 = mappers[i].getTrack1(); // left
			Sequence t2 = mappers[i].getTrack2(); // right

			if (t1 == src && orientation == Globals.RIGHT_ORIENT) 		return t2;
			else if (t2 == src && orientation == Globals.LEFT_ORIENT) 	return t1;
		}
		return null;
	}
	///// Zoom ////////////////////
	private void zoomTracks(Sequence src, int start, int end, int orientation ) {
		Sequence t = getOpposingTrack(src, orientation);
		if (t==null) return; 
		
		int[] minMax = null;
		for (Mapper m : mappers) {
			Sequence t1 = m.getTrack1(); // left
			Sequence t2 = m.getTrack2(); // right
			if ((t1 == src && t2 == t) || (t1 == t && t2 == src))
				minMax = m.getMinMax(src, start, end);
		}
		
		if (minMax != null) {
			int pad = (minMax[1] - minMax[0]) / 20; // 5%
			minMax[0] = Math.max(0, minMax[0]-pad);
			minMax[1] = Math.min((int)t.getTrackSize()-1, minMax[1]+pad);
			t.setEnd(minMax[1]);
			t.setStart(minMax[0]);
			zoomTracks(t, minMax[0], minMax[1], orientation); // recursive
		}
	}
	
	public void zoomAllTracks(Sequence src, int start, int end) {
		zoomTracks(src, start, end, Globals.LEFT_ORIENT);
		zoomTracks(src, start, end, Globals.RIGHT_ORIENT);
	}
	
	public void setMouseFunction(String s) { selectMouseFunction = s; }
	public String getMouseFunction() { return (selectMouseFunction == null ? "" : selectMouseFunction); }
	public boolean isMouseFunction() {
		return ControlPanel.MOUSE_FUNCTION_ZOOM_ALL.equals(selectMouseFunction) ||
			   ControlPanel.MOUSE_FUNCTION_ZOOM_SINGLE.equals(selectMouseFunction) ||
			   ControlPanel.MOUSE_FUNCTION_CLOSEUP.equals(selectMouseFunction) ||
			   ControlPanel.MOUSE_FUNCTION_SEQ.equals(selectMouseFunction);
	}
	public boolean isMouseFunctionZoom() {
		return ControlPanel.MOUSE_FUNCTION_ZOOM_ALL.equals(selectMouseFunction) ||
			   ControlPanel.MOUSE_FUNCTION_ZOOM_SINGLE.equals(selectMouseFunction);
	}
	public boolean isMouseFunctionPop() {
		return ControlPanel.MOUSE_FUNCTION_CLOSEUP.equals(selectMouseFunction) ||
			   ControlPanel.MOUSE_FUNCTION_SEQ.equals(selectMouseFunction);
	}
	public boolean isMouseFunctionSeq() { return ControlPanel.MOUSE_FUNCTION_SEQ.equals(selectMouseFunction); }
	public boolean isMouseFunctionCloseup() { return ControlPanel.MOUSE_FUNCTION_CLOSEUP.equals(selectMouseFunction); }
	public boolean isMouseFunctionZoomSingle() { return ControlPanel.MOUSE_FUNCTION_ZOOM_SINGLE.equals(selectMouseFunction); }
	public boolean isMouseFunctionZoomAll() { return ControlPanel.MOUSE_FUNCTION_ZOOM_ALL.equals(selectMouseFunction); }

	// ControlPanel: + up 2, - down 0.5 
	protected boolean changeShowRegion(double factor) { 
		setUpdateHistory();
		
		if (factor > 1) {
			boolean fullyExpanded = true;
	
			for (int i = 0; i < numTracks; i++) {
				if (!trackHolders[i].getTrack().fullyExpanded()) {
					fullyExpanded = false;
					break;
				}
			}
			if (fullyExpanded) return true;
		}
		for (int i = 0; i < numTracks; i++) trackHolders[i].getTrack().changeAlignRegion(factor);
		
		smake("dp: changedShowRegion");
		return true;
	}
	protected void shrinkSpace() { 
		if (trackDistance>10) trackDistance-=5;
		setUpdateHistory();
		smake("dp: shrinkspace");
	}
	protected void drawToScale() {	// ControlPanel
		setUpdateHistory();
		if (isToScale) {			// isToScale is changed from ControlPanel
			double bpPerPixel = 0;
			Sequence track = null;
			for (int i = 0; i < numTracks; i++) {
				track = trackHolders[i].getTrack();
				bpPerPixel = Math.max(bpPerPixel,track.getBpPerPixel()); 
			}
			if (bpPerPixel == 0 && track != null) bpPerPixel = track.getBpPerPixel();
			for (int i = 0; i < numTracks; i++)
				trackHolders[i].getTrack().setBpPerPixel(bpPerPixel);
		}
		else {
			for (int i = 0; i < numTracks; i++) 
				trackHolders[i].getTrack().setDefPixel();
		}
		smake("dp: drawtoscale");
	}
	
	public void setVisible(boolean visible) {
		for (int i = 0; i < numMaps; i++) mappers[i].setVisible(visible);
		for (int i = 0; i < numTracks; i++) trackHolders[i].setVisible(visible);
		
		if (visible) doLayout();
		
		super.setVisible(visible); 

		getView().repaint();
	}

	public int getNumMaps() {return numMaps;}// Frame2d and Sequence 

	public int getNumAnnots() {// Frame2d; for query, the annotation filter is turned on; make room
		int ret = 0;
		for (int i = 0; i < trackHolders.length; i++) {
			TrackHolder th = trackHolders[i];
			if (th != null) {
				Sequence t = th.getTrack();
				if (t.getShowAnnot()) ret++;
			}
		}
		return ret;
	}
	
	public String toString() {
		return "[ Drawing Panel: {" + java.util.Arrays.asList(mappers).toString() + "} ]";
	}
	
	//////////Clears ////////////////// 
	public void setTrackBuild() { // resetColor, Sequence.clearAllBuild
		for (int i = 0; i < numTracks; i++)
			if (trackHolders[i].getTrack() != null)
				trackHolders[i].getTrack().setTrackBuild(); // hasBuild=false
	}
	protected void clearData() {
		if (numMaps==0) return;
		
		closeFilters();
		
		for (int i = 0; i < trackHolders.length; i++) trackHolders[i].getTrack().clearData();
		trackHolders = null;
		for (int i=0; i<mappers.length; i++)  mappers[i].clearData();
		mappers = null;
		numMaps=numTracks=0;
	}
	protected void closeFilters() {
		if (numMaps==0) return;
		
		for (int i = 0; i < mappers.length; i++) mappers[i].closeFilter();
		for (int i = 0; i < trackHolders.length; i++)trackHolders[i].closeFilter();
	}
	
	///////////////////////////////////////////////////////////////	
	public boolean amake() { // called on 1st view, or resetColors; 
		return make(); // if fails, quit trying to display
	}	
	public boolean smake(String msg) { // called when graphics changes: Sfilter, Sequence.mousePress, DrawingPanel.changeRegions
		return make();
	}
	private synchronized boolean make() { 
		if (trackHolders==null || trackHolders.length==0) {
			dprt("no tracks");
			return false;
		}
		boolean status = false;
		
		setFrameEnabled(false);
		try {
			for (int i = 0; i < numMaps; i++)
				mappers[i].initHits(); 			// only init the first time called
			
			for (int i = 0; i < numTracks; i++)
				if (!trackHolders[i].getTrack().buildGraphics()) return false; // build graphics for painting in repaint
			
			if (bUpdateHistory) {
				updateHistory();
				bUpdateHistory = bReplaceHistory = false;
			}
			else if (bReplaceHistory) { // filters are not part of history
				replaceHistory();
				bReplaceHistory = false;
			}
			
			setFrameEnabled(true);
			repaint();
			status = true;
		} 
		catch (IllegalArgumentException iae) {ErrorReport.print(iae, "Drawing panel make: illegal argument");} 
		catch (IllegalStateException ise) {ErrorReport.print(ise, "Drawing panel make: illegal state");} 
		catch (Exception exc) {ErrorReport.print(exc, "Drawing panel make: exception");} 
		catch (OutOfMemoryError me) {
			ErrorReport.print(me, "Drawing panel make: " +me.getCause());
			Popup.showErrorMessage("SyMAP is out of memory. Please restart your browser.", -1);
			frame2dListener.setFrameEnabled(false);
			throw me;
		}
		return status;
	}
	
	/****************************************************************
	 * History
	 */
	public void setHistory(Object obj) { // HistoryControl.actionPerformed because button clicked 
		setFrameEnabled(false);
		new Thread(new MapMaker((DrawingPanelData)obj)).start();
	}
	public void setUpdateHistory() {bUpdateHistory = true;} // Filters, changeAlignRegion, setSequencTrack, setTrackEnds
	
	public void updateHistory() { // make when doUpdateHistory; Sequence.mouseRelease changes map
		DrawingPanelData dpd = new DrawingPanelData(mappers,trackHolders, trackDistance);
		historyControl.add(dpd);
	}
	
	public void setReplaceHistory() {bReplaceHistory = true; }
	
	public void replaceHistory() { 
		DrawingPanelData dpd = new DrawingPanelData(mappers,trackHolders, trackDistance);
		historyControl.replace(dpd);
	}
	// Show History: setMaps in MapMaker sets the drawing panel to correspond to the DrawingPanelData
	private synchronized boolean setMaps(DrawingPanelData dpd) {
		boolean good = false;
		MapperData[] mapperData = dpd.getMapperData();
		TrackData[] trackData  = dpd.getTrackData();
		trackDistance = dpd.trackDistance;
		cntlPanel.setHistory(dpd.isScale, dpd.mouseFunction);
		
		try {
			for (int i = 0; i < numTracks; i++) trackHolders[i].setTrackData(trackData[i]);
			for (int i = 0; i < numMaps; i++)   mappers[i].setMapperData(mapperData[i]);
			good = true;
		} 
		catch (IllegalArgumentException iae) {ErrorReport.print(iae, "Drawing panel setmaps: illegal argument");} 
		catch (IllegalStateException ise) {ErrorReport.print(ise, "Drawing panel setmaps: illegal state");} 
		catch (Exception exc) {ErrorReport.print(exc, "Drawing panel setmaps: Unable to make map");} 
		catch (OutOfMemoryError me) {
			System.out.println("Caught OutOfMemoryError in SyMAP::setMaps() - "+me.getCause());
			ErrorReport.print(me, "out of memory");
			Popup.showErrorMessage("SyMAP is out of memory. Please restart your browser.", -1); 
			frame2dListener.setFrameEnabled(false);
			throw me;
		}
		return good;
	}
	// setHistory called by history.HistoryControl when button click; show data
	private class MapMaker implements Runnable {
		DrawingPanelData data = null;

		public MapMaker(DrawingPanelData data) { this.data = data;}
		
		public void run() {
			if (data == null || setMaps(data)) {
				make(); 
			}
		}
	}
	// Create History: 
	private class DrawingPanelData {
		private MapperData[] mapperData;
		private TrackData[]  trackData;
		private double trackDistance;
		private String mouseFunction;
		private boolean isScale;

		protected DrawingPanelData(Mapper[] mappers, TrackHolder[] trackHolders, double trackDistance) {
			mapperData = new MapperData[mappers.length];
			trackData = new TrackData[trackHolders.length];
			for (int i = 0; i < mapperData.length; i++) mapperData[i] = mappers[i].getMapperData();
			for (int i = 0; i < trackData.length; i++)  trackData[i] = trackHolders[i].getTrackData();
			this.trackDistance = trackDistance;
			this.isScale = isToScale;
			this.mouseFunction = selectMouseFunction;
		}

		protected MapperData[] getMapperData() {return mapperData;}

		protected TrackData[] getTrackData() {return trackData;}
	}
}

