package symap.drawingpanel;

import java.util.List;
import java.util.ArrayList;
import java.awt.*;
import javax.swing.*;
import java.sql.SQLException;

import symap.SyMAPConstants;
import symap.frame.ControlPanel;
import symap.frame.HelpBar;
import symap.pool.Pools;
import symap.mapper.AbstractHitData;
import symap.mapper.Mapper;
import symap.mapper.MapperData;
import symap.filter.FilterHandler;
import symap.track.*;
import colordialog.ColorListener;
import history.HistoryControl;
import history.HistoryListener;
import symap.sequence.Sequence;
import symap.closeup.CloseUp;
import symap.mapper.HitFilter;
import symapQuery.TableDataPanel;
import dotplot.FilterData;
import util.ErrorReport;
import util.Utilities;

/**
 * The DrawingPanel is the area that the maps are drawn onto.
 * CAS521 totally removed all FPC (CAS517 had added stuff to not show FPC if no /fpc)
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class DrawingPanel extends JPanel 
	implements ColorListener, HistoryListener, SyMAPConstants
{ 
	private static boolean TRACE = symap.SyMAP.TRACE;
		
	public static Color backgroundColor = Color.white;
	public static final int MAX_TRACKS = 100;

	private Mapper[] mappers;
	private TrackHolder[] trackHolders;
	private HistoryControl historyControl;
	private DrawingPanelListener drawingPanelListener;
	
	private CloseUp closeup;

	private boolean doUpdateHistory = true;
	private boolean resetResetIndex = true;

	private Pools pools;

	private boolean firstView = true;

	private JPanel buttonPanel = null;
	private JScrollPane scrollPane = null;

	private int numMaps = 1;
	
	private String mouseFunction = null; 
	
	public DrawingPanel(TableDataPanel listPanel /*notused*/, 
			Pools pools, HistoryControl hc, HelpBar bar) {
		super();
		setBackground(backgroundColor);
		this.pools = pools;
		historyControl = hc;

		buttonPanel = new JPanel(null);
		buttonPanel.setOpaque(false);

		scrollPane = new JScrollPane(this);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED); 
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED); 
		scrollPane.getVerticalScrollBar().setUnitIncrement(50);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(50);
		scrollPane.getViewport().setBackground(backgroundColor);

		scrollPane.setColumnHeaderView(buttonPanel);
		scrollPane.getColumnHeader().setBackground(backgroundColor);
			
		trackHolders = new TrackHolder[MAX_TRACKS];
		for (int i = 0; i < trackHolders.length; i++) {
			trackHolders[i] = new TrackHolder(this,bar, (i+1)); // CAS517 add trackNum
			trackHolders[i].setOrientation( i == 0 ? LEFT_ORIENT : RIGHT_ORIENT );
			add(trackHolders[i]);
		}

		mappers = new Mapper[MAX_TRACKS - 1];
		for (int i = 0; i < mappers.length; i++) {
			mappers[i] = new Mapper(this,trackHolders[i],trackHolders[i+1],
					new FilterHandler(this,bar),pools.getMapperPool(),
					bar, listPanel); 
			add(mappers[i]);
			mappers[i].setLocation(0,0);
		}
		setLayout(new TrackLayout(trackHolders,mappers,buttonPanel)); 
	}
	
	public void paint(Graphics g) {
		super.paint(g);
		
		for (Mapper m : mappers)
			m.paint(g);
	}

	public void setListener(DrawingPanelListener dpl) {
		this.drawingPanelListener = dpl;
	}

	public Frame getFrame() {
		if (drawingPanelListener != null) return drawingPanelListener.getFrame();
		else return null;
	}

	public void setFrameEnabled(boolean enabled) {
		if (drawingPanelListener != null) drawingPanelListener.setFrameEnabled(enabled);
	}

	public Pools getPools() {
		return pools;
	}

	public JComponent getView() {
		return scrollPane;
	}

	public void resetColors() {
		setBackground(backgroundColor);	
		
		scrollPane.getViewport().setBackground(backgroundColor);
			
		scrollPane.getColumnHeader().setBackground(backgroundColor);
		 
		buttonPanel.setBackground(backgroundColor);
		clearTrackBuild();
		amake();
	}

	public void clearTrackBuild() {
		for (int i = 0; i <= numMaps; i++)
			if (trackHolders[i].getTrack() != null)
				trackHolders[i].getTrack().clearTrackBuild();
	}

	/**
	 * Returns the data needed to recreate the drawing panel (all of the maps 
	 * visible).  This information is all of the filter information and any 
	 * information needed to acquire the data from the database not the actual  data.
	 */
	public DrawingPanelData getData() {
		return new DrawingPanelData(mappers,trackHolders,numMaps);
	}
	
	public void setCloseUp(CloseUp closeup) {
		this.closeup = closeup;
	}

	public CloseUp getCloseUp() {
		return closeup;
	}

	public int getViewHeight() { // height of viewable area
		return scrollPane.getViewport().getHeight();
	}

	public String toString() {
		return "[ Drawing Panel: {" + java.util.Arrays.asList(mappers).toString() + "} ]";
	}

	public void resetData() {
		setFrameEnabled(false);
		pools.clearPools();
		try {
			pools.getProjectProperties().reset();
		} catch (SQLException e) { }
		for (int i = 0; i <  numMaps; ++i) mappers[i].clearData();
		for (int i = 0; i <= numMaps; ++i) {
			if (trackHolders[i].getTrack() != null)
				trackHolders[i].getTrack().resetData();
		}
	}
	
	// clear caches/data but don't re-init tracks
	public void clearData() {
		pools.clearPools();
		try {
			pools.getProjectProperties().reset();
		} catch (SQLException e) { }
		
		for (int i = 0; i <  numMaps; ++i) mappers[i].clearData();
		for (int i = 0; i <= numMaps; ++i) {
			if (trackHolders[i].getTrack() != null)
				trackHolders[i].getTrack().clearData();
		}
	}

	public List<AbstractHitData> getHitsInRange(Track src, int start, int end) {
		List<AbstractHitData> list = new ArrayList<AbstractHitData>();
		for (Mapper m : mappers) {
			Track t1 = m.getTrack1(); // left
			Track t2 = m.getTrack2(); // right
			if (t1 == src || t2 == src) {
				list.addAll(m.getHitsInRange(src,start,end));
			}
		}
		if (TRACE) System.out.println("getHitData: " + list.size() + " hits");
		
		return list;
	}
	
	/**
	 * Sets the tracks start and end (in base pair) to start/end.  Returns true 
	 * if a track exists at pos.  If the track is not initialized before setting 
	 * the start/end, it is initialized.
	 * 
	 * The ends of the map are locked till the next build if the track at pos is a Sequence.
	 */
	public boolean setTrackEnds(int pos, double startBP, double endBP) throws IllegalArgumentException {
		Track track = trackHolders[pos-1].getTrack();
		if (track != null) {
			track.setStartBP((long)Math.round(startBP),true);
			track.setEndBP((long)Math.round(endBP),true);
			firstView = false;
			setUpdateHistory();
			return true;
		}
		return false;
	}
	// symapQuery Show Synteny from table
	public void setHitFilter(int map, HitFilter hf) {
		mappers[map-1].getHitFilter().set(hf);
	}
	// Dotplot.Data zoomArea and zoomBlock
	public void setHitFilter(int map, FilterData fd) {
		mappers[map-1].getHitFilter().set(fd);
	}

	public void downloadAllHits(int map) {
		mappers[map-1].initAllHits();
	}

	public void downloadAllHits(Track track) {
		for (int i = 0; i < numMaps; ++i)
			if (mappers[i].getTrack1() == track || mappers[i].getTrack2() == track) 
				mappers[i].initAllHits();
	}

	private int getOpposingTrackProject(int position) {
		Track opposite = null;
		if (position-1 >= 0) opposite = trackHolders[position-1].getTrack();
		if (opposite == null && position+1 < trackHolders.length) opposite = trackHolders[position+1].getTrack();
		return opposite == null ? NO_VALUE : opposite.getProject();
	}
	
	public int[] getMinMax(Track src, Track dest, int start, int end) {
		int[] minMax = null;
		
		for (Mapper m : mappers) {
			Track t1 = m.getTrack1(); // left
			Track t2 = m.getTrack2(); // right
			if ((t1 == src && t2 == dest) || (t1 == dest && t2 == src))
				minMax = m.getMinMax(src, start, end);
		}

		return minMax;
	}
	
	private Track getOpposingTrack(Track src, int orientation) {
		for (int i = 0; i < numMaps; i++) {
			Track t1 = mappers[i].getTrack1(); // left
			Track t2 = mappers[i].getTrack2(); // right
			if (t1 == src && orientation == RIGHT_ORIENT)
				return t2;
			else if (t2 == src && orientation == LEFT_ORIENT)
				return t1;
		}
		return null;
	}

	private void zoomTracks(Track src, int start, int end, int orientation ) {
		Track t = getOpposingTrack(src, orientation);
		int[] minMax = getMinMax(src, t, start, end);
		if (minMax != null) {
			int pad = (minMax[1] - minMax[0]) / 20; // 5%
			minMax[0] = Math.max(0,minMax[0]-pad);
			minMax[1] = Math.min((int)t.getTrackSize()-1,minMax[1]+pad);
			t.setEnd(minMax[1]);
			t.setStart(minMax[0]);
			zoomTracks(t, minMax[0], minMax[1], orientation);
		}
	}
	
	public void zoomAllTracks(Track src, int start, int end) {
		zoomTracks(src, start, end, LEFT_ORIENT);
		zoomTracks(src, start, end, RIGHT_ORIENT);
	}
	
	// "zoom all tracks" mouse function
	public void setMouseFunction(String s) { mouseFunction = s; }
	public String getMouseFunction() { return (mouseFunction == null ? "" : mouseFunction); }
	public boolean isMouseFunction() { // CAS504
		return 
			   ControlPanel.MOUSE_FUNCTION_ZOOM_ALL.equals(mouseFunction) ||
			   ControlPanel.MOUSE_FUNCTION_ZOOM_SINGLE.equals(mouseFunction) ||
			   ControlPanel.MOUSE_FUNCTION_CLOSEUP.equals(mouseFunction) ||
			   ControlPanel.MOUSE_FUNCTION_SEQ.equals(mouseFunction)
			   ;
	}
	public boolean isMouseFunctionZoom() {// CAS504
		return 
				   ControlPanel.MOUSE_FUNCTION_ZOOM_ALL.equals(mouseFunction) ||
				   ControlPanel.MOUSE_FUNCTION_ZOOM_SINGLE.equals(mouseFunction);
	}
	public boolean isMouseFunctionPop() {// CAS504
		return 
				   ControlPanel.MOUSE_FUNCTION_CLOSEUP.equals(mouseFunction) ||
				   ControlPanel.MOUSE_FUNCTION_SEQ.equals(mouseFunction);
	}
	public boolean isMouseFunctionSeq() { return ControlPanel.MOUSE_FUNCTION_SEQ.equals(mouseFunction); }
	public boolean isMouseFunctionCloseup() { return ControlPanel.MOUSE_FUNCTION_CLOSEUP.equals(mouseFunction); }
	public boolean isMouseFunctionZoomSingle() { return ControlPanel.MOUSE_FUNCTION_ZOOM_SINGLE.equals(mouseFunction); }
	public boolean isMouseFunctionZoomAll() { return ControlPanel.MOUSE_FUNCTION_ZOOM_ALL.equals(mouseFunction); }

	public boolean setSequenceTrack(int position, int project, int group, Color color) {
		closeFilters();

		TrackHolder holder = trackHolders[position-1];
		Track track = holder.getTrack();

		if ( !(track instanceof Sequence) ) track = new Sequence(this,holder);
		
		((Sequence)track).setup(project,group,getOpposingTrackProject(position-1));
		
		track.setBackground(color); 
		
		return initTrack(track,position);
	}
	
	private boolean initTrack(Track track, int position) {
		track.setPosition(position); 
		setTrack(track, position);
		setFirstView();
		setResetIndex();
		setUpdateHistory();
		return track.hasInit();
	}

	/**
	 * update - makes the updates based on the parameters passed.
	 * Finally the reset index is set, the history is updated, and the view is updated
	 */
	public void update(final Track track, final Object arg) {
		setFrameEnabled(false);
		new Thread(new Runnable() {
			public void run() {
				setResetIndex();
				setUpdateHistory();
				smake();
			}
		}).start();
	}

	/**
	 * changeZoomFactor: The factor should be non negative and a zero factor does not change the zooms.
	 *
	 * @param factor a double value factor by which to change the zooms of all of the tracks
	 * @return a boolean value of true if all changes are successful
	 */
	public boolean changeZoomFactor(double factor) {
		setUpdateHistory();
		boolean success = true;
		for (int i = 0; i <= numMaps; i++)
			if (trackHolders[i].getTrack() != null)
				if (!trackHolders[i].getTrack().changeZoomFactor(factor))
					success = false;
		smake();
		return success;
	}
	
	public boolean changeAlignRegion(double factor) {
		setUpdateHistory();
		
		if (factor > 1) {
			boolean fullyExpanded = true;
	
			for (int i = 0; i <= numMaps; i++) {
				if (trackHolders[i].getTrack() != null) {
					if (!trackHolders[i].getTrack().fullyExpanded()) {
						fullyExpanded = false;
						break;
					}
				}
			}
			if (fullyExpanded) return true;
		}
		for (int i = 0; i <= numMaps; i++) {
			if (trackHolders[i].getTrack() != null) {
				trackHolders[i].getTrack().changeAlignRegion(factor);
			}
		}
	
		smake();
		return true;
	}

	/**
	 * Sets the bp/pixel of the tracks in every map to the bp/pixel of the 
	 * last found Sequence track of all of the maps.
	 */
	public void drawToScale() {
		setUpdateHistory();
		
		double bpPerPixel = 0;
		Track track = null;
		for (int i = 0; i <= numMaps; i++) {
			if (trackHolders[i].getTrack() != null) {
				track = trackHolders[i].getTrack();
				if (track instanceof Sequence)
					bpPerPixel = Math.max(bpPerPixel,track.getBpPerPixel()); 
			}
		}
		if (bpPerPixel == 0 && track != null) bpPerPixel = track.getBpPerPixel();
		for (int i = 0; i <= numMaps; i++)
			if (trackHolders[i].getTrack() != null) 
				trackHolders[i].getTrack().setBpPerPixel(bpPerPixel);
		
		smake();
	}
	
	private void setOtherTracksOtherProject(Track track, int position) {
		if (position-1 >= 0 && trackHolders[position-1].getTrack() != null)
			trackHolders[position-1].getTrack().setOtherProject(track.getProject());
		else if (position+1 < trackHolders.length && trackHolders[position+1].getTrack() != null)
			trackHolders[position+1].getTrack().setOtherProject(track.getProject());
	}

	protected void setTrack(Track track, int position) throws IndexOutOfBoundsException {
		position--; 
		trackHolders[position].setTrack(track);
		setOtherTracksOtherProject(track,position);
		if (position > numMaps) {
			if (TRACE) System.out.println("DrawingPanel.setTrack: increasing numMaps " + position);
			numMaps = position; 
		}
	}

	protected void replaceTrack(Track oldTrack, Track newTrack) {
		for (int i = 0; i <= numMaps; i++) {
			if (trackHolders[i].getTrack() == oldTrack) {
				trackHolders[i].setTrack(newTrack);
				break;
			}
		}
	}

	/**
	 * Method closeFilters goes through each Mapper and track calling there respective closeFilter methods
	 * @see TrackHolder#closeFilter() @see Mapper#closeFilter()
	 */
	public void closeFilters() {
		int i;
		for (i = 0; i < MAX_TRACKS-1; i++) {
			mappers[i].closeFilter();
			trackHolders[i].closeFilter();
		}
		trackHolders[i].closeFilter();
	}

	/**
	 * setMaps - sets the drawing panel to correspond to the DrawingPanelData
	 */
	public synchronized boolean setMaps(DrawingPanelData dpd) {
		boolean good = false;
		int i;
		MapperData[] mapperData = dpd.getMapperData();
		TrackData[] trackData  = dpd.getTrackData();
		try {
			setMaps(mapperData.length);
			for (i = 0; i <= numMaps; i++)
				trackHolders[i].setTrackData(trackData[i]);
			for (i = 0; i < numMaps; i++)
				mappers[i].setMapperData(mapperData[i]);
			firstView = false;
			good = true;
		} catch (IllegalArgumentException iae) {
			ErrorReport.print(iae, "Drawing panel setmaps: illegal argument");
			Utilities.showErrorMessage(iae.getMessage(), -1); 	
		} catch (IllegalStateException ise) {
			ErrorReport.print(ise, "Drawing panel setmaps: illegal state");
			Utilities.showErrorMessage(ise.getMessage(), -1); 	
		} catch (Exception exc) {
			ErrorReport.print(exc, "Drawing panel setmaps: Unable to make map");
			Utilities.showErrorMessage("Unable to make map", -1); 
		} catch (OutOfMemoryError me) {
			System.out.println("Caught OutOfMemoryError in SyMAP::setMaps() - "+me);
			System.out.println("     Cause: "+me.getCause());
			ErrorReport.print(me, "out of memory");
			Utilities.showErrorMessage("SyMAP is out of memory. Please restart your browser.", -1); 
			drawingPanelListener.setFrameEnabled(false);
			throw me;
		}
		return good;
	}

	public void amake() { // asynchronous call to make()
		new Thread(new MapMaker()).start();
	}

	public boolean smake() { // synchronizes private calls to make()
		return make();
	}

	private synchronized boolean make() {
		boolean status = false;
		if (tracksSet()) {
			setFrameEnabled(false);
			try {
				initAll();
				buildAll();
				doConditionalUpdateHistory();
				setFrameEnabled(true);
				repaint();
				status = true;
			} catch (IllegalArgumentException iae) {
				ErrorReport.print(iae, "Drawing panel make: illegal argument");
				Utilities.showErrorMessage(iae.getMessage(), -1); 	
			} catch (IllegalStateException ise) {
				ErrorReport.print(ise, "Drawing panel make: illegal state");
				Utilities.showErrorMessage(ise.getMessage(), -1); 	
			} catch (Exception exc) {
				ErrorReport.print(exc, "Drawing panel make: exception");
				Utilities.showErrorMessage("Unable to make map", -1); 
			} catch (OutOfMemoryError me) {
				ErrorReport.print(me, "Drawing panel make: " +me.getCause());
				Utilities.showErrorMessage("SyMAP is out of memory. Please restart your browser.", -1);
				drawingPanelListener.setFrameEnabled(false);
				throw me;
			}
		}
		return status;
	}

	private class MapMaker implements Runnable {
		DrawingPanelData data = null;

		public MapMaker() {
			data = null;
		}
		public MapMaker(DrawingPanelData data) {
			this.data = data;
		}
		public void run() {
			if (data == null || setMaps(data)) {
				make(); 
			}
		}
	}

	/**
	 * Sets this drawing panel to first view.  This is used in setting the size of the Sequences to
	 * fit to the hits shown by the Mapper's.
	 */
	public void setFirstView() {
		firstView = true;
	}

	private void initAll() { 
		int i;
		for (i = 0; i <= numMaps; i++) {
			if (trackHolders[i].getTrack() == null) 
				Utilities.showWarningMessage("Track at position "+(i+1)+" is not set."); 
			if (!trackHolders[i].getTrack().hasInit()) 
				Utilities.showWarningMessage("Track at Position "+(i+1)+" is unable to initialize."); 
		}
		for (i = 0; i < numMaps; i++)
			if (!mappers[i].init()) 
				Utilities.showWarningMessage("The "+(i+1)+" map is unable to initialize."); 
	}

	private void firstViewBuild() {
		if (TRACE) System.out.println("DrawingPanel.firstViewBuild");
		
		int i;
		for (i = 0; i <= numMaps; i++) {
			if (trackHolders[i].getTrack() instanceof Sequence) {
				trackHolders[i].getTrack().resetStart();
				trackHolders[i].getTrack().resetEnd();
			}
		}
		for (i = 0; i <= numMaps; i++)
			trackHolders[i].getTrack().build();

		Sequence seq = null;
		int[] mm = {Integer.MAX_VALUE,Integer.MIN_VALUE};
		for (i = 0; i < numMaps; i++) {
			if (seq != null) {
				for (int j = 0; j < 2; j++) {
					if (seq == mappers[i].getTrack(j)) {
						if (mm[0] != Integer.MAX_VALUE) seq.setStartBP(mm[0],true);
						if (mm[1] != Integer.MIN_VALUE) seq.setEndBP(mm[1],true);
						break;
					}
				}
				mm[0] = Integer.MAX_VALUE;
				mm[1] = Integer.MIN_VALUE;
			}
			if (mappers[i].getTrack1() instanceof Sequence && mappers[i].getTrack1() != seq) {
				seq = (Sequence)mappers[i].getTrack1();
			}
			else if (mappers[i].getTrack2() instanceof Sequence && mappers[i].getTrack2() != seq) {
				seq = (Sequence)mappers[i].getTrack2();
			}
			else {
				seq = null;
				mm[0] = Integer.MAX_VALUE;
				mm[1] = Integer.MIN_VALUE;
			}
		}

		if (seq != null) {
			if (mm[0] != Integer.MAX_VALUE) seq.setStartBP(mm[0],true);
			if (mm[1] != Integer.MIN_VALUE) seq.setEndBP(mm[1],true);
		}
	}

	private void buildAll() {
		if (firstView) {
			firstViewBuild();
			firstView = false;
		}
		
		for (int i = 0; i <= numMaps; i++)
			trackHolders[i].getTrack().build();
	}

	public void setVisible(boolean visible) {
		int i;
		for (i = 0; i < numMaps; i++) {
			mappers[i].setVisible(visible);
			trackHolders[i].setVisible(visible);
		}
		trackHolders[i].setVisible(visible);

		if (visible) doLayout();
		
		super.setVisible(visible); 

		getView().repaint();
	}

	/**
	 * Returns false if any of the tracks are not set up to the number of maps specified
	 * @return true if the all the tracks are set
	 * @see #setMaps(int)
	 */
	public boolean tracksSet() {
		if (TRACE) System.out.println("DrawingPanel.tracksSet: numMaps = " + numMaps);
		for (int i = 0; i <= numMaps; i++) {
			if (TRACE) System.out.println("DrawingPanel.tracksSet: track " + i + " is " + trackHolders[i].getTrack());
			if (trackHolders[i].getTrack() == null) return false;
		} 
		return true;
	}

	public int getNumMaps() {
		return numMaps;
	}

	public void setMaps(int numberOfMaps) {
		if (TRACE) System.out.println("DrawingPanel.setMaps("+numberOfMaps+")");
		
		if (numberOfMaps < 1) numberOfMaps = 1;
		else if (numberOfMaps > MAX_TRACKS - 1) numberOfMaps = MAX_TRACKS - 1;

		if (numMaps != numberOfMaps) {
			closeFilters();
			setFrameEnabled(false);

			numMaps = numberOfMaps;
			for (int i = numMaps + 1; i < trackHolders.length; i++) {
				trackHolders[i].setTrack(null);
			}
		}
	}
	public int getNumAnnots() {
		int ret = 0;
		for (int i = 0; i < trackHolders.length; i++) {
			TrackHolder th = trackHolders[i];
			if (th != null) {
				Track t = th.getTrack();
				if (t instanceof symap.sequence.Sequence) {
					if (((Sequence)t).getShowAnnot()) {
						ret++;
					}
				}
			}
		}
		return ret;
	}
	
	public void setHistory(Object obj) {
		setFrameEnabled(false);
		new Thread(new MapMaker((DrawingPanelData)obj)).start();
	}
	public void setUpdateHistory() {
		if (TRACE) System.out.println("DrawingPanel.setUpdateHistory()");
		doUpdateHistory = true;
	}
	public void setImmediateUpdateHistory() {
		if (TRACE) System.out.println("DrawingPanel.setImmediateUpdateHistory()");
		updateHistory();
	}
	public void doConditionalUpdateHistory() {
		if (TRACE) System.out.println("DrawingPanel.doConditionalUpdateHistory()");
		if (doUpdateHistory) {
			updateHistory();
			doUpdateHistory = false;
		}
	}
	public void setResetIndex() {
		resetResetIndex = true;
	}
	private void updateHistory() {
		if (TRACE) System.out.println("DrawingPanel.updateHistory()");
		historyControl.add(getData(),resetResetIndex);
		resetResetIndex = false;
	}
}

