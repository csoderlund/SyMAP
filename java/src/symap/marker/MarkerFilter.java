package symap.marker;

import javax.swing.event.ChangeEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.ButtonModel;
import javax.swing.ButtonGroup;
import javax.swing.AbstractButton;

import java.awt.Frame;
import java.awt.Container;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import symap.SyMAPConstants;
import symap.drawingpanel.DrawingPanel;
import symap.mapper.Mapper;
import symap.filter.Filter;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public abstract class MarkerFilter extends Filter {
	private JLabel markerNameLabel;
	private JTextField markerNameText;
	private JComboBox markerNameCombo;
	private JCheckBox showOnlySharedMarkersBox;
	private JRadioButton showNoMarkerNamesRadio;
	private JRadioButton showOnlyMarkersWithHitsRadio;
	private JRadioButton showOnlyMarkersWithCurrentHitsRadio;
	private JRadioButton showMarkerNamesRadio;
	private ButtonGroup markerNameGroup;

	private JCheckBox flippedBox;
	
	// mdb added 3/14/07 #104 -- BEGIN
	private JCheckBoxMenuItem flippedPopupBox;
	private JCheckBoxMenuItem showOnlySharedMarkersPopupBox;
	private JRadioButtonMenuItem showNoMarkerNamesPopupRadio;
	private JRadioButtonMenuItem showOnlyMarkersWithHitsPopupRadio;
	private JRadioButtonMenuItem showOnlyMarkersWithCurrentHitsPopupRadio;
	private JRadioButtonMenuItem showMarkerNamesPopupRadio;
	// mdb added 3/14/07 #104 -- END

	private MarkerTrack track;

	private String markerFilter;
	private boolean markerFilterShow;
	private int showMarkerNames;
	private boolean noChange = false;

	private boolean flipped;

	public MarkerFilter(Frame owner, DrawingPanel dp, String title,
			AbstractButton helpButton, MarkerTrack track) {
		super(owner,dp,title,helpButton);
		this.track = track;

		markerNameLabel = new JLabel("Marker Name:");
		markerNameText  = new JTextField("", 10);
		markerNameCombo = new JComboBox(new String[] {"Show","Hide"});

		showOnlySharedMarkersBox            = new JCheckBox("Hide Marker Names That Are Not Shared");
		showOnlyMarkersWithHitsRadio        = new JRadioButton("Show Only Marker Names With Hits");
		showOnlyMarkersWithCurrentHitsRadio = new JRadioButton("Show Only Marker Names With Visible Hits");
		showMarkerNamesRadio                = new JRadioButton("Show All Marker Names");
		showNoMarkerNamesRadio              = new JRadioButton("Show No Marker Names");

		markerNameGroup = new ButtonGroup();
		markerNameGroup.add(showMarkerNamesRadio);
		markerNameGroup.add(showOnlyMarkersWithHitsRadio);
		markerNameGroup.add(showOnlyMarkersWithCurrentHitsRadio);
		markerNameGroup.add(showNoMarkerNamesRadio);

		ActionListener mnl = new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				if (MarkerFilter.this.track != null && !noChange) {
					MarkerFilter.this.track.setShowMarkerNames(getMarkerNameShow());
					refresh();
				}
			}
		};

		showOnlySharedMarkersBox.addActionListener(mnl);
		showMarkerNamesRadio.addActionListener(mnl);
		showNoMarkerNamesRadio.addActionListener(mnl);
		showOnlyMarkersWithHitsRadio.addActionListener(mnl);
		showOnlyMarkersWithCurrentHitsRadio.addActionListener(mnl);
		
		flippedBox = new JCheckBox("Flip");
		flippedBox.setSelected(MarkerTrack.DEFAULT_FLIPPED);
		flippedBox.addChangeListener(this);

		// mdb added 3/14/07 #104 -- BEGIN		
		flippedPopupBox = new JCheckBoxMenuItem("Flip");
		showOnlySharedMarkersPopupBox = new JCheckBoxMenuItem("Hide Non-Shared Markers");
		showNoMarkerNamesPopupRadio = new JRadioButtonMenuItem("Show No Marker Names");
		showOnlyMarkersWithHitsPopupRadio = new JRadioButtonMenuItem("Show Only Marker Names With Hits");
		showOnlyMarkersWithCurrentHitsPopupRadio = new JRadioButtonMenuItem("Show Only Marker Names With Visible Hits");
		showMarkerNamesPopupRadio = new JRadioButtonMenuItem("Show All Marker Names");
		popup.add(flippedPopupBox); 
		popup.add(showOnlySharedMarkersPopupBox); 
		popup.add(showNoMarkerNamesPopupRadio); 
		popup.add(showOnlyMarkersWithHitsPopupRadio); 
		popup.add(showOnlyMarkersWithCurrentHitsPopupRadio); 
		popup.add(showMarkerNamesPopupRadio);
		ActionListener pmnl = new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				setCursor(SyMAPConstants.WAIT_CURSOR);			
				if (event.getSource() == flippedPopupBox)
					flippedBox.setSelected(flippedPopupBox.getState());
				if (event.getSource() == showOnlySharedMarkersPopupBox)
					showOnlySharedMarkersBox.setSelected(showOnlySharedMarkersPopupBox.getState());            				
				if (event.getSource() == showOnlyMarkersWithHitsPopupRadio)
					showOnlyMarkersWithHitsRadio.setSelected(showOnlyMarkersWithHitsPopupRadio.isSelected());        				
				if (event.getSource() == showOnlyMarkersWithCurrentHitsPopupRadio)
					showOnlyMarkersWithCurrentHitsRadio.setSelected(showOnlyMarkersWithCurrentHitsPopupRadio.isSelected()); 			
				if (event.getSource() == showMarkerNamesPopupRadio)
					showMarkerNamesRadio.setSelected(showMarkerNamesPopupRadio.isSelected());                
				if (event.getSource() == showNoMarkerNamesPopupRadio)
					showNoMarkerNamesRadio.setSelected(showNoMarkerNamesPopupRadio.isSelected());   
				
				MarkerFilter.this.track.setShowMarkerNames(getMarkerNameShow());
				try { okAction(); }
				catch(Exception e) { }	
				drawingPanel.smake();
				setCursor(SyMAPConstants.DEFAULT_CURSOR);
			}
		};
		flippedPopupBox.addActionListener(pmnl);
		showOnlySharedMarkersPopupBox.addActionListener(pmnl);
		showNoMarkerNamesPopupRadio.addActionListener(pmnl);
		showOnlyMarkersWithHitsPopupRadio.addActionListener(pmnl);
		showOnlyMarkersWithCurrentHitsPopupRadio.addActionListener(pmnl);
		showMarkerNamesPopupRadio.addActionListener(pmnl);
		// mdb added 3/14/07 #104 -- END
	}

	private int getMarkerNameShow() {
		ButtonModel m = markerNameGroup.getSelection();

		if (showNoMarkerNamesRadio.getModel() != m && showOnlyMarkersWithCurrentHitsRadio.getModel() != m)
			drawingPanel.downloadAllHits(track);

		if (showNoMarkerNamesRadio.getModel() == m) return MarkerList.NO_NAMES;
		if (showMarkerNamesRadio.getModel() == m) 
			return showOnlySharedMarkersBox.isSelected() ? MarkerList.SHARED_NAMES : MarkerList.ALL_NAMES;
		if (showOnlyMarkersWithCurrentHitsRadio.getModel() == m)
			return showOnlySharedMarkersBox.isSelected() ? MarkerList.SHARED_NAMES_WITH_CURRENT_HITS : MarkerList.NAMES_WITH_CURRENT_HITS;
		// showOnlyMarkersWithHitsRadio.getModel() == m
		return showOnlySharedMarkersBox.isSelected() ? MarkerList.SHARED_NAMES_WITH_HITS : MarkerList.NAMES_WITH_HITS;
	}

	private void setMarkerNameShow(int showNames) {
		showOnlySharedMarkersBox.setEnabled(showNames != MarkerList.NO_NAMES);
		switch (showNames) {
		case MarkerList.NO_NAMES:
			showNoMarkerNamesRadio.setSelected(true);
			break;
		case MarkerList.ALL_NAMES:
		case MarkerList.SHARED_NAMES:
			showOnlySharedMarkersBox.setSelected(showNames == MarkerList.SHARED_NAMES);
			showMarkerNamesRadio.setSelected(true);
			break;
		case MarkerList.SHARED_NAMES_WITH_CURRENT_HITS:
		case MarkerList.NAMES_WITH_CURRENT_HITS:
			showOnlySharedMarkersBox.setSelected(showNames == MarkerList.SHARED_NAMES_WITH_CURRENT_HITS);
			showOnlyMarkersWithCurrentHitsRadio.setSelected(true);
			break;
		default: // SHARED_NAMES_WITH_HITS or NAMES_WITH_HITS
			showOnlySharedMarkersBox.setSelected(showNames == MarkerList.SHARED_NAMES_WITH_HITS);
			showOnlyMarkersWithHitsRadio.setSelected(true);
		}
	}

	protected void addMarkerNameFilterToGrid(Container contentPane, GridBagLayout gridbag, GridBagConstraints constraints) {
		addToGrid(contentPane,gridbag,constraints,markerNameLabel,1);
		addToGrid(contentPane,gridbag,constraints,markerNameText,1);
		addToGrid(contentPane,gridbag,constraints,markerNameCombo,1);
		addToGrid(contentPane,gridbag,constraints,new JLabel() ,GridBagConstraints.REMAINDER);
	}

	protected void addMarkerFilterToGrid(Container contentPane, GridBagLayout gridbag, GridBagConstraints constraints) {
		addToGrid(contentPane,gridbag,constraints,showOnlySharedMarkersBox,GridBagConstraints.REMAINDER);
		addToGrid(contentPane,gridbag,constraints,showNoMarkerNamesRadio,GridBagConstraints.REMAINDER);
		addToGrid(contentPane,gridbag,constraints,showOnlyMarkersWithHitsRadio,GridBagConstraints.REMAINDER);
		addToGrid(contentPane,gridbag,constraints,showOnlyMarkersWithCurrentHitsRadio,GridBagConstraints.REMAINDER);	
		addToGrid(contentPane,gridbag,constraints,showMarkerNamesRadio,GridBagConstraints.REMAINDER);
	}

	protected JCheckBox getFlippedBox() {
		return flippedBox;
	}
	
	// mdb moved out of show() 3/15/07 #104
	private void setupShow() {
		noChange = true;

		markerFilter = track.pattern;
		markerFilterShow = track.show;

		showMarkerNames = track.getMarkerList().getShowNames();
		setMarkerNameShow(showMarkerNames);

		markerNameCombo.setSelectedIndex(markerFilterShow ? 0 : 1);
		markerNameText.setText(markerFilter);

		showOnlySharedMarkersBox.setVisible(drawingPanel.getNumMaps() > 1 || drawingPanel.getMapType(track) == Mapper.FPC2FPC);
		showOnlySharedMarkersPopupBox.setVisible(drawingPanel.getNumMaps() > 1 || drawingPanel.getMapType(track) == Mapper.FPC2FPC); // mdb added 3/15/07
		
		flipped = track.flipped;
		flippedBox.setSelected(flipped);
		flippedPopupBox.setSelected(flipped); 

		noChange = false;		
	}

	public void show() {
		if (!isShowing()) setupShow();
		super.show();
	}

	protected void setDefault() {
		setMarkerNameShow(MarkerList.DEFAULT_SHOW);

		markerNameCombo.setSelectedIndex(0);
		markerNameText.setText("");

		flippedBox.setSelected(MarkerTrack.DEFAULT_FLIPPED);
	}

	protected boolean okAction() throws Exception {
		if (track == null) return false;

		boolean changed = false;

		if (flipped != flippedBox.isSelected()) {
			flipped = !flipped;
			changed = true;
		}

		if (markerFilter == null
				|| !markerFilter.equals(markerNameText.getText())
				|| markerFilterShow != (markerNameCombo.getSelectedIndex() == 0)) 
		{
			markerFilter = markerNameText.getText();
			markerFilterShow = (markerNameCombo.getSelectedIndex() == 0);
			track.filterMarkerNames(markerFilter,markerFilterShow);
		}

		int newShow = getMarkerNameShow();
		if (newShow != showMarkerNames) {
			showMarkerNames = newShow;
			changed = true;
		}

		return (changed || !track.hasBuilt());
	}

	protected void cancelAction() {
		markerNameText.setText(markerFilter);
		markerNameCombo.setSelectedIndex(markerFilterShow ? 0 : 1);
		flippedBox.setSelected(flipped);
		setMarkerNameShow(showMarkerNames);
	}

	/**
	 * Method <code>stateChanged</code> handles flipping and calls refresh().
	 *
	 * @param event a <code>ChangeEvent</code> value
	 */
	public void stateChanged(ChangeEvent event) {
		if (track != null && !noChange) {
			if (event.getSource() == flippedBox) {
				if (track.flip(flippedBox.isSelected(),true))
					didFlip();
			}
			else super.stateChanged(event);
			refresh();
		}
	}
	
	// mdb added 3/14/07 #104
	public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
		setupShow();
		flippedPopupBox.setState(flippedBox.isSelected());
		showOnlySharedMarkersPopupBox.setState(showOnlySharedMarkersBox.isSelected());
		showOnlyMarkersWithHitsPopupRadio.setSelected(showOnlyMarkersWithHitsRadio.isSelected());        				
		showOnlyMarkersWithCurrentHitsPopupRadio.setSelected(showOnlyMarkersWithCurrentHitsRadio.isSelected());
		showMarkerNamesPopupRadio.setSelected(showMarkerNamesRadio.isSelected());
		showNoMarkerNamesPopupRadio.setSelected(showNoMarkerNamesRadio.isSelected());
	}

	/**
	 * Method <code>didFlip</code> is called when a flip of the track
	 * has occured.  Refresh will be called by MarkerFilter after this method is 
	 * invoked.
	 */
	protected abstract void didFlip();
}

