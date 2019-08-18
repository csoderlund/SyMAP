package symap.drawingpanel;

import java.util.Vector;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

import java.awt.*;
import javax.swing.*;

import java.sql.SQLException;

import symap.SyMAPConstants;
import symap.marker.MarkerList;
import symap.frame.ControlPanel;
import symap.frame.HelpBar;
import symap.pool.Pools;
import symap.mapper.AbstractHitData;
import symap.mapper.Mapper;
import symap.mapper.MapperData;
import symap.filter.FilterHandler;
import symap.track.*;
import symap.marker.Marker;
import symap.marker.MarkerTrack;
import colordialog.ColorListener;
import history.HistoryControl;
import history.HistoryListener;
import symap.contig.Contig;
import symap.contig.ContigTrackData;
import symap.block.Block;
import symap.block.BlockTrackData;
import symap.sequence.Sequence;
import symap.closeup.CloseUp;
import symap.mapper.HitFilter;
import symapQuery.ListDataPanel;
import dotplot.FilterData;
import util.Utilities;

/**
 * The DrawingPanel is the area that the maps are drawn onto.
 * 
 * @author Austin Shoemaker
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class DrawingPanel extends JPanel 
	implements ColorListener, HistoryListener, SyMAPConstants
{ 
	private static final boolean METHOD_TRACE = false;
	
// mdb removed 1/28/09 #159 simplify properties	
//	public static Color backgroundColor;
//	public static final boolean SCROLLED;
//	public static final boolean FILTERED;
//	public static final int MAX_TRACKS;
//	static {
//		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/drawingpanel.properties"));
//		backgroundColor = props.getColor("backgroundColor");
//		SCROLLED   = props.getBoolean("scrolled");
//		FILTERED   = props.getBoolean("filtered");
//		MAX_TRACKS = Math.max(props.getInt("maxTracks"),100/*2*/); // mdb changed maximum on 12/2/08 for 3D
//	}
	
	// mdb added 1/28/09 #159 simplify properties	
	public static Color backgroundColor = Color.white;
	public static final int MAX_TRACKS = 100;

	private Mapper[] mappers;
	private TrackHolder[] trackHolders;
	private HistoryControl historyControl;
	private DrawingPanelListener drawingPanelListener;
	private ListDataPanel theParentPanel = null;
	
	private CloseUp closeup;

	private boolean doUpdateHistory = true;
	private boolean resetResetIndex = true;

	private Pools pools;

	private boolean firstView = true;

	private JPanel buttonPanel = null;
	private JScrollPane scrollPane = null;

	private int numMaps = 1;
	
	private String mouseFunction = null; // mdb added 4/21/09 #161
	
	/**
	 * Creates a new <code>DrawingPanel</code> instance.
	 *
	 * @param pools a <code>Pools</code> value
	 * @param hc a <code>HistoryControl</code> value
	 * @param bar a <code>HelpBar</code> value
	 */
	public DrawingPanel(ListDataPanel listPanel, Pools pools, HistoryControl hc, HelpBar bar) {
		super();
		setBackground(backgroundColor);
		this.pools = pools;
		historyControl = hc;
		theParentPanel = listPanel;

		// Create new pools

		//if (FILTERED) {
			buttonPanel = new JPanel(null);
			buttonPanel.setOpaque(false);
		//}

		//if (SCROLLED) {
			scrollPane = new JScrollPane(this);
			scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED/*HORIZONTAL_SCROLLBAR_ALWAYS*/); // mdb changed 1/28/09
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED/*VERTICAL_SCROLLBAR_ALWAYS*/); // mdb changed 1/28/09
			scrollPane.getVerticalScrollBar().setUnitIncrement(50);
			scrollPane.getHorizontalScrollBar().setUnitIncrement(50);
			scrollPane.getViewport().setBackground(backgroundColor);

			//if (FILTERED) {
				scrollPane.setColumnHeaderView(buttonPanel);
				scrollPane.getColumnHeader().setBackground(backgroundColor);
			//}
		//}
				
		trackHolders = new TrackHolder[MAX_TRACKS];
		for (int i = 0; i < trackHolders.length; i++) {
			trackHolders[i] = new TrackHolder(this,bar);
			trackHolders[i].setOrientation( i == 0 ? LEFT_ORIENT : RIGHT_ORIENT );
			add(trackHolders[i]);
		}

		mappers = new Mapper[MAX_TRACKS - 1];
		for (int i = 0; i < mappers.length; i++) {
			mappers[i] = new Mapper(this,trackHolders[i],trackHolders[i+1],
					new FilterHandler(this,bar),pools.getMapperPool(),
					bar, listPanel); // mdb added bar 3/21/07 #105
			add(mappers[i]);
			mappers[i].setLocation(0,0);
		}

		setLayout(new TrackLayout(trackHolders,mappers,buttonPanel)); 
	}
	
	// mdb added 7/21/09 #165 - override paint to draw hit bars on top of sequences
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

// mdb replaced with util.Utilities.showErrorMessage() 5/25/07 #119
//	public void displayError(String message) {
//		if (drawingPanelListener != null) drawingPanelListener.displayError(message);
//	}

// mdb replaced with util.Utilities.showWarningMessage() 5/25/07 #119
//	public void displayWarning(String message) {
//		if (drawingPanelListener != null) drawingPanelListener.displayWarning(message);
//	}

	public void setFrameEnabled(boolean enabled) {
		if (drawingPanelListener != null) drawingPanelListener.setFrameEnabled(enabled);
	}

	public Pools getPools() {
		return pools;
	}

	public JComponent getView() {
		//if (SCROLLED) 
			return scrollPane;
		//return this;
	}

// mdb unused 6/26/09	
//	public void setScrollPaneSize(Dimension d) {
//		//if (SCROLLED) 
//			scrollPane.setPreferredSize(d);
//	}

	public void resetColors() {
		setBackground(backgroundColor);	
		//if (SCROLLED) {
			scrollPane.getViewport().setBackground(backgroundColor);
			//if (FILTERED) 
				scrollPane.getColumnHeader().setBackground(backgroundColor);
		//}
		//else if (FILTERED) 
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
	 * Method <code>getMapType</code> returns the first map in which
	 * mapMember is a member and returns the Map type.
	 *
	 * @param mapMember a <code>Track</code> value
	 * @return an <code>int</code> value
	 */
	public int getMapType(Track mapMember) {
		for (int i = 0; i < numMaps; i++)
			if (mappers[i].getTrack1() == mapMember 
					|| mappers[i].getTrack2() == mapMember)
				return mappers[i].getMapType();
		throw new IllegalArgumentException("Mapper with Track doesn't exist!");
	}

	/**
	 * Returns the data needed to recreate the drawing panel (all of the maps 
	 * visible).  This information is all of the filter information and any 
	 * information needed to acquire the data from the database not the actual 
	 * data.
	 * 
	 * @return the DrawingPanelData
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

	/**
	 * Method <code>getViewHeight</code> returns the actual height
	 * of the viewable area.
	 *
	 * @return an <code>int</code> value
	 */
	public int getViewHeight() {
		//if (SCROLLED) 
			return scrollPane.getViewport().getHeight();
		//return getHeight();
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
		//smake(); // mdb removed 8/27/09 for 3D
	}
	
	// mdb added 2/3/10 - clear caches/data but don't re-init tracks
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
		if (METHOD_TRACE) System.out.println("getHitData: " + list.size() + " hits");
		
		return list;
	}
	
	/**
	 * Sets the tracks start and end (in base pair) to start/end.  Returns true 
	 * if a track exists at pos.  If the track is not initialized before setting 
	 * the start/end, it is initialized.
	 * 
	 * The ends of the map are locked till the next build if the track at pos 
	 * is a Sequence.
	 * 
	 * @param pos
	 * @param startBP
	 * @param endBP
	 * @return true on success
	 * @throws IllegalArgumentException
	 * @throws TrackException
	 */
	public boolean setTrackEnds(int pos, double startBP, double endBP) throws IllegalArgumentException {
		Track track = trackHolders[pos-1].getTrack();
		if (track != null) {
			//if (!track.hasInit()) track.init();
			track.setStartBP((long)Math.round(startBP),true);
			track.setEndBP((long)Math.round(endBP),true);
			firstView = false;
			setUpdateHistory();
			return true;
		}
		return false;
	}

	public void setHitFilter(int map, HitFilter hf) {
		mappers[map-1].getHitFilter().set(hf);
	}

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
	
	// mdb added 4/23/09 #161
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
	
	// mdb added 4/23/09 #161
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

	// mdb added 4/23/09 #161
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
	
	// mdb added 4/23/09 #161
	public void zoomAllTracks(Track src, int start, int end) {
		zoomTracks(src, start, end, LEFT_ORIENT);
		zoomTracks(src, start, end, RIGHT_ORIENT);
	}
	
	// mdb added 4/21/09 #161 - "zoom all tracks" mouse function
	public void setMouseFunction(String s) { mouseFunction = s; }
	public String getMouseFunction() { return (mouseFunction == null ? "" : mouseFunction); }
	public boolean isMouseFunctionCloseup() { return ControlPanel.MOUSE_FUNCTION_CLOSEUP.equals(mouseFunction); }
	public boolean isMouseFunctionZoomSingle() { return ControlPanel.MOUSE_FUNCTION_ZOOM_SINGLE.equals(mouseFunction); }
	public boolean isMouseFunctionZoomAll() { return ControlPanel.MOUSE_FUNCTION_ZOOM_ALL.equals(mouseFunction); }

	public boolean setSequenceTrack(int position, int project, int group, Color color) {
		closeFilters();

		TrackHolder holder = trackHolders[position-1];
		Track track = holder.getTrack();

		if ( !(track instanceof Sequence) ) track = new Sequence(this,holder);
		((Sequence)track).setup(project,group,getOpposingTrackProject(position-1));
		
		track.setBackground(color); // mdb added 12/3/08 for 3D
		
		return initTrack(track,position);
	}
	
	public boolean setContigTrack(int position, int project, int contig) {
		closeFilters();

		TrackHolder holder = trackHolders[position-1];
		Track track = holder.getTrack();
		if ( !(track instanceof Contig) ) track = new Contig(this,holder);
		((Contig)track).setup(project,contig,getOpposingTrackProject(position-1),null);

		return initTrack(track,position);
	}
	
	// mdb added 7/13/09 for 3D - show FPC in 2D view
	public boolean setBlockTrack(int position, int project, String contigs, Color color) 
	throws IllegalArgumentException 
	{
		return setBlockTrack(position, project, null, contigs, color);
	}

	public boolean setBlockTrack(int position, int project, String group, String contigs, Color color) 
	throws IllegalArgumentException 
	{
		closeFilters();

		TrackHolder holder = trackHolders[position-1];
		Track track = holder.getTrack();

		if ( !(track instanceof Block) ) track = new Block(this,holder);
		((Block)track).setup(project,contigs,getOpposingTrackProject(position-1),null);
		
		track.setBackground(color); // mdb added 6/25/09 for 3D
		if (group != null) ((Block)track).setBlock(group); // mdb added 7/13/09 for 3D

		return initTrack(track,position);
	}

	private boolean initTrack(Track track, int position) {
		track.setPosition(position); // mdb added 3/18/08
		setTrack(track, position);
		setFirstView();
		setResetIndex();
		setUpdateHistory();
		return track.hasInit();
	}

	/**
	 * Method <code>update</code> makes the updates based on the
	 * parameters passed.
	 *
	 * If track is an instance of a Block, than a contig is put in
	 * that track's place with a contig number of ((Integer)arg).intValue().
	 * The from block list for the contig is also acquired from the block track.
	 *
	 * If track is an instance of a Contig, than a block track is put in it's 
	 * place with a contig list of (Collection)arg.
	 *
	 * Finally the reset index is set, the history is updated, and the view is 
	 * updated.
	 *
	 * @param track a <code>Track</code> value
	 * @param arg an <code>Object</code> value
	 */
	public void update(final Track track, final Object arg) {
		setFrameEnabled(false);
		final DrawingPanel dp = this;
		new Thread(new Runnable() {
			public void run() {
				try {
					if (track instanceof Block) {
						Contig c = new Contig(dp,track.getHolder());
						c.setup(track.getProject(),
								((Integer)arg).intValue(),
								track.getOtherProject(),
								(BlockTrackData)track.getData());
						c.setFromBlockList(((Block)track).getContigList());
						replaceTrack(track, c);
					} else if (track instanceof Contig) {
						Block block = new Block(dp,track.getHolder());
						block.setup(track.getProject(),
								Block.getContigs((Collection)arg),
								track.getOtherProject(),
								(ContigTrackData)track.getData());
						replaceTrack(track, block);
					}
				} catch (IllegalArgumentException iae) {
					System.out.println(iae.getMessage());
					//displayError(iae.getMessage()); 						// mdb removed 5/25/07 #119
					Utilities.showErrorMessage(iae.getMessage(), -1); 		// mdb added 5/25/07 #119
				} catch (IllegalStateException ise) {
					System.out.println(ise.getMessage());
					//displayError(ise.getMessage()); 						// mdb removed 5/25/07 #119
					Utilities.showErrorMessage(ise.getMessage(), -1); 		// mdb added 5/25/07 #119
				} catch (Exception exc) {
					//System.out.println("Exception: " + exc); 				// mdb removed 5/25/07
					exc.printStackTrace();
					//displayError("Internal Error: Unable to make map"); 	// mdb removed 5/25/07 #119
					System.err.println("Unable to make map");//Utilities.showErrorMessage("Unable to make map", -1); 	// mdb added 5/25/07 #119 // mdb changed 2/8/10
				} catch (OutOfMemoryError me) {
					System.out.println("Caught OutOfMemoryError in SyMAP::update() - "+me);
					System.out.println("     Cause: "+me.getCause());
					me.printStackTrace();
					//displayError("SyMAP is out of memory. Please restart your browser."); 				// mdb removed 5/25/07 #119
					Utilities.showErrorMessage("SyMAP is out of memory. Please restart your browser.", -1); // mdb added 5/25/07 #119
					drawingPanelListener.setFrameEnabled(false);
					throw me;
				}
				setResetIndex();
				setUpdateHistory();
				smake();
			}
		}).start();
	}

	/**
	 * Method <code>changeZoomFactor</code> changes the zooms by a factor.  The factor should be non negative and
	 * a zero factor does not change the zooms.
	 *
	 * @param factor a <code>double</code> value factor by which to change the zooms of all of the tracks
	 * @return a <code>boolean</code> value of true if all changes are successful
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
		
		if (factor > 1)
		{
			boolean fullyExpanded = true;
	
			for (int i = 0; i <= numMaps; i++)
			{
				if (trackHolders[i].getTrack() != null)
				{
					if (!trackHolders[i].getTrack().fullyExpanded())
					{
						fullyExpanded = false;
						break;
					}
				}
			}
			if (fullyExpanded) return true;
		}
		
		for (int i = 0; i <= numMaps; i++)
		{
			if (trackHolders[i].getTrack() != null)
			{
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
					bpPerPixel = Math.max(bpPerPixel,track.getBpPerPixel()); // mdb changed 7/20/09 - scale to largest
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
			if (METHOD_TRACE) System.out.println("DrawingPanel.setTrack: increasing numMaps " + position);
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
	 * Method <code>closeFilters</code> goes through each Mapper and track calling
	 * there respective closeFilter methods
	 *
	 * @see TrackHolder#closeFilter()
	 * @see Mapper#closeFilter()
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
	 * Method <code>setMaps</code> sets the drawing panel to correspond to the DrawingPanelData
	 *
	 * @param dpd a <code>DrawingPanelData</code> value
	 * @return a <code>boolean</code> value of true if no exception occurs.
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
			//System.out.println(iae.getMessage()); 			// mdb removed 5/25/07 #119
			//displayError(iae.getMessage()); 					// mdb removed 5/25/07 #119
			Utilities.showErrorMessage(iae.getMessage(), -1); 	// mdb added 5/25/07 #119
		} catch (IllegalStateException ise) {
			//System.out.println(ise.getMessage()); 			// mdb removed 5/25/07 #119
			//displayError(ise.getMessage()); 					// mdb removed 5/25/07 #119
			Utilities.showErrorMessage(ise.getMessage(), -1); 	// mdb added 5/25/07 #119
		} catch (Exception exc) {
			//System.out.println("Exception: " + exc); 			// mdb removed 5/25/07
			exc.printStackTrace();
			//displayError("Internal Error: Unable to make map"); // mdb removed 5/25/07 #119
			Utilities.showErrorMessage("Unable to make map", -1); // mdb added 5/25/07 #119
		} catch (OutOfMemoryError me) {
			System.out.println("Caught OutOfMemoryError in SyMAP::setMaps() - "+me);
			System.out.println("     Cause: "+me.getCause());
			me.printStackTrace();
			//displayError("SyMAP is out of memory. Please restart your browser."); 				// mdb removed 5/25/07 #119
			Utilities.showErrorMessage("SyMAP is out of memory. Please restart your browser.", -1); // mdb added 5/25/07 #119
			drawingPanelListener.setFrameEnabled(false);
			throw me;
		}
		return good;
	}

	public void amake() { // asynchronous call to make()
		//System.out.println("DrawingPanel.amake");
		new Thread(new MapMaker()).start();
	}

	public boolean smake() { // synchronizes private calls to make()
		//System.out.println("DrawingPanel.smake");
		return make();
	}

	private synchronized boolean make() {
		//System.out.println("DrawingPanel.make BEGIN");
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
				System.out.println(iae.getMessage());
				//displayError(iae.getMessage()); 					// mdb removed 5/25/07 #119
				Utilities.showErrorMessage(iae.getMessage(), -1); 	// mdb added 5/25/07 #119
			} catch (IllegalStateException ise) {
				System.out.println(ise.getMessage());
				//displayError(ise.getMessage()); 					// mdb removed 5/25/07 #119
				Utilities.showErrorMessage(ise.getMessage(), -1); 	// mdb added 5/25/07 #119
			} catch (Exception exc) {
				//System.out.println("Exception: " + exc); 			// mdb removed 5/25/07
				exc.printStackTrace();
				//displayError("Internal Error: Unable to make map"); // mdb removed 5/25/07 #119
				Utilities.showErrorMessage("Unable to make map", -1); // mdb added 5/25/07 #119
			} catch (OutOfMemoryError me) {
				System.out.println("Caught OutOfMemoryError in SyMAP::make() - "+me);
				System.out.println("     Cause: "+me.getCause());
				me.printStackTrace();
				//displayError("SyMAP is out of memory. Please restart your browser."); 				// mdb removed 5/25/07 #119
				Utilities.showErrorMessage("SyMAP is out of memory. Please restart your browser.", -1); // mdb added 5/25/07 #119
				drawingPanelListener.setFrameEnabled(false);
				throw me;
			}
		}
		
		//System.out.println("DrawingPanel.make END");
		return status;
	}

	private class MapMaker implements Runnable {
		int status = 0;
		DrawingPanelData data = null;

		public MapMaker() {
			data = null;
		}

		public MapMaker(DrawingPanelData data) {
			this.data = data;
		}

		public void run() {
			status = 0;
			if (data == null || setMaps(data)) {
				if (make()) status = 1;
				else status = -1;
			}
			else status = -1;
		}
	}

	/**
	 * Sets this drawing panel to first view.  This is used in setting the size of the Sequences to
	 * fit to the hits shown by the Mapper's.
	 */
	public void setFirstView() {
		firstView = true;
	}

	/**
	 * Method <code>setClickedMarker</code> sets all the markers that are equal to <code>marker</code> to
	 * be click highlighted if clicked is true and not clicked highlighted if clicked is false.  All other markers
	 * are set to not be click highlighted.
	 *
	 * @param marker a <code>Marker</code> value
	 * @param clicked a <code>boolean</code> value
	 */
	public void setClickedMarker(Marker marker, boolean clicked) {
		MarkerList list;
		Track t;
		for (int i = 0; i <= numMaps; i++) {
			t = trackHolders[i].getTrack();
			if (t != null && t instanceof MarkerTrack) {
				list = ((MarkerTrack)t).getMarkerList();
				if (list != null) {
					for (Marker cmarker : list.getMarkers())
						cmarker.setClickHighlighted(clicked && marker.equals(cmarker));
				}
			}
		}
		repaint();
	}

	/**
	 * Method <code>setHoveredMarker</code> sets all the markers that are equal to <code>marker</code> to
	 * be hovered if hovered is true and not hovered if hovered is false.  All other markers
	 * are set to not be hovered.
	 *
	 * @param marker a <code>Marker</code> value
	 * @param hovered a <code>boolean</code> value
	 */
	public void setHoveredMarker(Marker marker, boolean hovered) {
		MarkerList list;	
		Track t;
		for (int i = 0; i <= numMaps; i++) {
			t = trackHolders[i].getTrack();
			if (t != null && t instanceof MarkerTrack) {
				list = ((MarkerTrack)t).getMarkerList();
				if (list != null) {
					for (Marker cmarker : list.getMarkers())
						cmarker.setHover(hovered && cmarker.equals(marker));
				}
			}
		}
		repaint();
	}

	private void initAll() { //throws IllegalStateException {
		int i;
		for (i = 0; i <= numMaps; i++) {
			if (trackHolders[i].getTrack() == null) 
				//throw new IllegalStateException("Track at Position "+(i+1)+" is not set.");
				//displayWarning("Track at position "+(i+1)+" is not set."); 			 // mdb removed 5/25/07 #119
				Utilities.showWarningMessage("Track at position "+(i+1)+" is not set."); // mdb added 5/25/07 #119
			if (!trackHolders[i].getTrack().hasInit()) 
				//throw new IllegalStateException("Track at Position "+(i+1)+" is unable to initialize.");
				//displayWarning("Track at Position "+(i+1)+" is unable to initialize."); 			  // mdb removed 5/25/07 #119
				Utilities.showWarningMessage("Track at Position "+(i+1)+" is unable to initialize."); // mdb added 5/25/07 #119
		}
		for (i = 0; i < numMaps; i++)
			if (!mappers[i].init()) 
				//throw new IllegalStateException("The "+(i+1)+" map is unable to initialize.");
				//displayWarning("The "+(i+1)+" map is unable to initialize."); 			// mdb removed 5/25/07 #119
				Utilities.showWarningMessage("The "+(i+1)+" map is unable to initialize."); // mdb added 5/25/07 #119
	}

	private void firstViewBuild() {
		if (METHOD_TRACE) System.out.println("DrawingPanel.firstViewBuild");
		
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
			mappers[i].getSequenceMinMax(mm);
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
		Iterator<Marker> iter, titer;
		Vector<Marker> sharedMarkers = null, trackMarkers = new Vector<Marker>(), allMarkers = new Vector<Marker>();
		boolean sharing = false;
		MarkerList mlist;
		List<Marker> tempList;
		Marker marker, clickedMarker = null, hoveredMarker = null;
		Track t;
		for (int i = 0; i <= numMaps; i++) {
			t = trackHolders[i].getTrack();
			if (t instanceof MarkerTrack && (mlist = ((MarkerTrack)t).getMarkerList()) != null) {
				tempList = mlist.getMarkers();
				titer = tempList.iterator();
				while (titer.hasNext()) {
					marker = (Marker)titer.next();
					marker.setShared(false);
					if (marker.isClickHighlighted()) clickedMarker = marker;
					if (marker.isHover()) hoveredMarker = marker;
					allMarkers.add(marker);
				}
				trackMarkers.clear();
				trackMarkers.addAll(tempList);
				if (sharedMarkers == null) {
					sharedMarkers = new Vector<Marker>();
				}
				else {
					sharing = true;
					sharedMarkers.retainAll(trackMarkers);
					trackMarkers.retainAll(sharedMarkers);
				}
				sharedMarkers.addAll(trackMarkers);
			}
		}

		iter = allMarkers.iterator();
		while (iter.hasNext()) {
			marker = (Marker)iter.next();
			if (marker.equals(clickedMarker)) marker.setClickHighlighted(true);
			if (marker.equals(hoveredMarker)) marker.setHover(true);
		}
		if (sharing && sharedMarkers != null) {
			iter = sharedMarkers.iterator();
			while (iter.hasNext())
				((Marker)iter.next()).setShared(true);
		}
		
		if (firstView) {
			firstViewBuild();
			firstView = false;
		}
		
		for (int i = 0; i <= numMaps; i++)
			trackHolders[i].getTrack().build();
	}

	public void setVisible(boolean visible) {
		//if (!visible) setCursor(WAIT_CURSOR); // mdb removed 7/16/09 - not needed
		//else if (getCursor() == WAIT_CURSOR) setCursor(null); // mdb removed 7/16/09 - not needed

		//super.setVisible(visible); // mdb removed below 7/16/09 - moved below, fixes redrawing problem

		int i;
		for (i = 0; i < numMaps; i++) {
			mappers[i].setVisible(visible);
			trackHolders[i].setVisible(visible);
		}
		trackHolders[i].setVisible(visible);

		if (visible) doLayout();
		
		super.setVisible(visible); // mdb added 7/16/09 - moved from above

		getView().repaint();
	}

	/**
	 * Returns false if any of the tracks are not set up to the number of maps specified
	 * 
	 * @return true if the all the tracks are set
	 * @see #setMaps(int)
	 */
	public boolean tracksSet() {
		if (METHOD_TRACE) System.out.println("DrawingPanel.tracksSet: numMaps = " + numMaps);
		for (int i = 0; i <= numMaps; i++) {
			if (METHOD_TRACE) System.out.println("DrawingPanel.tracksSet: track " + i + " is " + trackHolders[i].getTrack());
			if (trackHolders[i].getTrack() == null) return false;
		} 
		return true;
	}

	public int getNumMaps() {
		return numMaps;
	}

	public void setMaps(int numberOfMaps) {
		if (METHOD_TRACE) System.out.println("DrawingPanel.setMaps("+numberOfMaps+")");
		
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
	public int getNumAnnots()
	{
		int ret = 0;
		for (int i = 0; i < trackHolders.length; i++)
		{
			TrackHolder th = trackHolders[i];
			if (th != null)
			{
				Track t = th.getTrack();
				if (t instanceof symap.sequence.Sequence)
				{
					if (((Sequence)t).getShowAnnot())
					{
						ret++;
					}
				}
			}
		}
		return ret;
	}
	public void setHighlightedClones(int[] remarkIDs) {
		closeFilters();
		for (int i = 0; i <= numMaps; i++) {
			if (trackHolders[i].getTrack() instanceof Contig)
				((Contig)trackHolders[i].getTrack()).setSelectedRemarks(remarkIDs,Contig.CLONE_HIGHLIGHT);
		}
	}

	public void setHistory(Object obj) {
		setFrameEnabled(false);
		new Thread(new MapMaker((DrawingPanelData)obj)).start();
	}

	public void setUpdateHistory() {
		if (METHOD_TRACE) System.out.println("DrawingPanel.setUpdateHistory()");
		doUpdateHistory = true;
	}

	public void setImmediateUpdateHistory() {
		if (METHOD_TRACE) System.out.println("DrawingPanel.setImmediateUpdateHistory()");
		updateHistory();
	}

	public void doConditionalUpdateHistory() {
		if (METHOD_TRACE) System.out.println("DrawingPanel.doConditionalUpdateHistory()");
		if (doUpdateHistory) {
			updateHistory();
			doUpdateHistory = false;
		}
	}

	public void setResetIndex() {
		resetResetIndex = true;
	}

	private void updateHistory() {
		if (METHOD_TRACE) System.out.println("DrawingPanel.updateHistory()");
		historyControl.add(getData(),resetResetIndex);
		resetResetIndex = false;
	}
}

