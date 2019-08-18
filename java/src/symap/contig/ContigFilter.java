package symap.contig;

import java.awt.*;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.PopupMenuEvent;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import symap.SyMAPConstants;
import symap.drawingpanel.DrawingPanel;
import symap.marker.MarkerFilter;
import symap.marker.MarkerTrack;
import number.GenomicsNumber;

/**
 * The filter window for a contig view.
 * 
 * @author Austin Shoemaker
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ContigFilter extends MarkerFilter {	
	private static final int VISIBLE_CLONE_REMARKS = 4;
	private static final int MAX_WIDTH = 3500;

	private JTextField cloneNameText;
	private JTextField startText;
	private JTextField endText;
	private JTextField contigText;
	private JComboBox cloneNameCombo;

	private JSlider widthSlider;
	private JCheckBox showCloneNamesBox;
	private JCheckBox showOnly2BESHitClonesBox, showOnly2BESCurrentHitClonesBox;

	private JComboBox cloneRemarksCombo;
	private JList cloneRemarksList;
	
	private JCheckBoxMenuItem showCloneNamesPopupBox; 				// mdb added 3/15/07 #104
	private JCheckBoxMenuItem showOnly2BESHitClonesPopupBox; 		// mdb added 3/15/07 #104
	private JCheckBoxMenuItem showOnly2BESCurrentHitClonesPopupBox; // mdb added 3/15/07 #104

	private Contig contig;
	private String cloneFilter;
	private boolean showCloneNames;
	private boolean showOnly2BESHitClones, showOnly2BESCurrentHitClones;

	private String startStr, endStr;
	private int width, contigNum, cloneFilterInd;
	private int[] selectedRemarkIndices;
	private int cloneRemarksInd;

	private boolean noChange = false;

	public ContigFilter(Frame owner, DrawingPanel dp, AbstractButton helpButton, Contig contig) {
		super(owner,dp,"Contig Filter",helpButton,(MarkerTrack)contig);
		this.contig = contig;

		String[] showHideHighlight = new String[3];
		showHideHighlight[Contig.CLONE_SHOW]      = "Show";
		showHideHighlight[Contig.CLONE_HIDE]      = "Hide";
		showHideHighlight[Contig.CLONE_HIGHLIGHT] = "Highlight";

		Container contentPane = getContentPane();
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints constraints = new GridBagConstraints();

		cloneRemarksCombo = new JComboBox(showHideHighlight);
		cloneRemarksCombo.setSelectedIndex(Contig.CLONE_HIGHLIGHT);
		cloneRemarksCombo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				if (!noChange) {
					Object[] objs = cloneRemarksList.getSelectedValues();
					if (objs != null && objs.length > 0) {
						int[] remarks = new int[objs.length];
						for (int i = 0; i < objs.length; ++i) remarks[i] = ((CloneRemarks.CloneRemark)objs[i]).getID();
						ContigFilter.this.contig.setSelectedRemarks(remarks,cloneRemarksCombo.getSelectedIndex());
						refresh();
					}
				}
			}
		});

		cloneRemarksList = new JList();
		cloneRemarksList.setLayoutOrientation(JList.VERTICAL);
		cloneRemarksList.setVisibleRowCount(VISIBLE_CLONE_REMARKS);
		cloneRemarksList.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (!noChange) { //&& !e.getValueIsAdjusting()) {
					Object[] objs = cloneRemarksList.getSelectedValues();
					int[] remarks = new int[objs.length];
					for (int i = 0; i < objs.length; ++i) remarks[i] = ((CloneRemarks.CloneRemark)objs[i]).getID();
					ContigFilter.this.contig.setSelectedRemarks(remarks,cloneRemarksCombo.getSelectedIndex());
					refresh();
				}
			}
		});
		JScrollPane scrollPane = new JScrollPane(cloneRemarksList);

		cloneNameText                   = new JTextField("", 10);
		startText                       = new JTextField("", 4);
		endText                         = new JTextField("", 4);
		contigText                      = new JTextField(new Integer(contig.getContig()).toString(), 3);
		cloneNameCombo                  = new JComboBox(showHideHighlight);
		widthSlider                     = new JSlider(JSlider.HORIZONTAL, 0, MAX_WIDTH, width);
		showCloneNamesBox               = new JCheckBox("Show Clone Names");
		showOnly2BESHitClonesBox        = new JCheckBox("Show Only Clones With BES Paired Hits");
		showOnly2BESCurrentHitClonesBox = new JCheckBox("Show Only Clones With Visible BES Paired Hits");

		widthSlider.addChangeListener(this);
		showCloneNamesBox.addChangeListener(this);
		showOnly2BESHitClonesBox.addChangeListener(this);
		showOnly2BESCurrentHitClonesBox.addChangeListener(this);

		contentPane.setLayout(gridbag);
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(1,1,1,1);
		constraints.gridheight = 1;
		constraints.ipadx = 5;
		constraints.ipady = 8;

		addToGrid(contentPane,gridbag,constraints,new JLabel("Change Contig:"),1);
		addToGrid(contentPane,gridbag,constraints,contigText,1);
		addToGrid(contentPane,gridbag,constraints,new JLabel(),GridBagConstraints.REMAINDER);
		addToGrid(contentPane,gridbag,constraints,new JSeparator(),GridBagConstraints.REMAINDER);

		addToGrid(contentPane,gridbag,constraints,new JLabel("Clone Name:"),1);
		addToGrid(contentPane,gridbag,constraints,cloneNameText,1);
		addToGrid(contentPane,gridbag,constraints,cloneNameCombo,1);
		addToGrid(contentPane,gridbag,constraints,new JLabel(),GridBagConstraints.REMAINDER);

		addMarkerNameFilterToGrid(contentPane,gridbag,constraints);

		addToGrid(contentPane,gridbag,constraints,cloneRemarksCombo,1);
		addToGrid(contentPane,gridbag,constraints,new JLabel(" Clones with Remarks:"),GridBagConstraints.REMAINDER);
		addToGrid(contentPane,gridbag,constraints,scrollPane,GridBagConstraints.REMAINDER);

		addToGrid(contentPane,gridbag,constraints,new JSeparator(),GridBagConstraints.REMAINDER);

		addToGrid(contentPane,gridbag,constraints,new JLabel("Start:"),1);
		addToGrid(contentPane,gridbag,constraints,startText,1);
		addToGrid(contentPane,gridbag,constraints,new JLabel(GenomicsNumber.CB),1);
		addToGrid(contentPane,gridbag,constraints,new JLabel(),GridBagConstraints.REMAINDER);
		addToGrid(contentPane,gridbag,constraints,new JLabel("End:"),1);
		addToGrid(contentPane,gridbag,constraints,endText,1);
		addToGrid(contentPane,gridbag,constraints,new JLabel(GenomicsNumber.CB),1);
		addToGrid(contentPane,gridbag,constraints,new JLabel(),GridBagConstraints.REMAINDER);

		addToGrid(contentPane,gridbag,constraints,new JLabel("Width of Contig:"),1);
		addToGrid(contentPane,gridbag,constraints,widthSlider,GridBagConstraints.REMAINDER);

		addToGrid(contentPane,gridbag,constraints,showCloneNamesBox,2);
		addToGrid(contentPane,gridbag,constraints,getFlippedBox(),GridBagConstraints.REMAINDER);

		addToGrid(contentPane,gridbag,constraints,new JSeparator(),GridBagConstraints.REMAINDER);

		constraints.anchor = GridBagConstraints.CENTER;
		addMarkerFilterToGrid(contentPane,gridbag,constraints);

		addToGrid(contentPane,gridbag,constraints,new JSeparator(),GridBagConstraints.REMAINDER);	

		constraints.anchor = GridBagConstraints.CENTER;
		addToGrid(contentPane,gridbag,constraints,showOnly2BESHitClonesBox,GridBagConstraints.REMAINDER);			
		
		constraints.anchor = GridBagConstraints.CENTER;
		addToGrid(contentPane,gridbag,constraints,showOnly2BESCurrentHitClonesBox,GridBagConstraints.REMAINDER);

		addToGrid(contentPane,gridbag,constraints,buttonPanel,GridBagConstraints.REMAINDER);

		pack();
		setResizable(false);
		
		// mdb added 3/15/07 #104 -- BEGIN
		//popupTitle.setLabel("Contig Options:"); // mdb removed 7/2/07 #118
		popupTitle.setText("Contig Options:"); // mdb added 7/2/07 #118
		//SyMAP.enableHelpOnButton(showNavigationHelp,"contigcontrols"); // mdb removed 4/30/09 #162
		//SyMAP.enableHelpOnButton(showTrackHelp,"contigtrack"); // mdb removed 4/30/09 #162
		showCloneNamesPopupBox = new JCheckBoxMenuItem("Show Clone Names");
		showOnly2BESHitClonesPopupBox = new JCheckBoxMenuItem("Show Only Clones With BES Paired Hits");
		showOnly2BESCurrentHitClonesPopupBox = new JCheckBoxMenuItem("Show Only Clones With Visible BES Paired Hits");
		popup.add(showCloneNamesPopupBox);
		popup.add(showOnly2BESHitClonesPopupBox);
		popup.add(showOnly2BESCurrentHitClonesPopupBox);
		ActionListener pmnl = new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				setCursor(SyMAPConstants.WAIT_CURSOR);	
				if (event.getSource() == showCloneNamesPopupBox) {
					showCloneNamesBox.setSelected(showCloneNamesPopupBox.getState());
					stateChanged(new ChangeEvent(showCloneNamesBox));
				}
				if (event.getSource() == showOnly2BESHitClonesPopupBox) {
					showOnly2BESHitClonesBox.setSelected(showOnly2BESHitClonesPopupBox.getState());
					stateChanged(new ChangeEvent(showOnly2BESHitClonesBox));
				}
				if (event.getSource() == showOnly2BESCurrentHitClonesPopupBox) {
					showOnly2BESCurrentHitClonesBox.setSelected(showOnly2BESCurrentHitClonesPopupBox.getState());
					stateChanged(new ChangeEvent(showOnly2BESCurrentHitClonesBox));
				}
				try { okAction(); }
				catch (Exception e) { }
				drawingPanel.smake();
				setCursor(SyMAPConstants.DEFAULT_CURSOR);	
			}
		};
		showCloneNamesPopupBox.addActionListener(pmnl);
		showOnly2BESHitClonesPopupBox.addActionListener(pmnl);
		showOnly2BESCurrentHitClonesPopupBox.addActionListener(pmnl);
//		 mdb added 3/15/07 #104 -- END
	}

	public String getHelpID() {
		return "contigfilter";//Filter.CONTIG_FILTER_ID;
	}

	protected void setDefault() {
		contigText.setText(new Integer(contig.contig).toString());
		widthSlider.setValue(contig.getBaseWidth());

		showOnly2BESHitClonesBox.setSelected(false);
		showOnly2BESCurrentHitClonesBox.setSelected(false);
		showCloneNamesBox.setSelected(Contig.DEFAULT_SHOW_CLONE_NAMES);
		cloneNameCombo.setSelectedIndex(Contig.CLONE_SHOW);
		cloneNameText.setText("");

		startText.setText("0");
		endText.setText(new Long(contig.getTrackSize()).toString());

		super.setDefault();
	}

	// mdb moved out of show() 3/15/07 #104
	private void setupShow() {
		noChange = true;

		setStart();
		setEnd();
		startStr = startText.getText();
		endStr = endText.getText();

		contigNum = contig.contig;
		width = contig.getContigWidth();
		showCloneNames = contig.showCloneNames;
		cloneFilter = contig.cloneFilterPattern;
		cloneFilterInd = contig.cloneFilterShow;
		showOnly2BESHitClones = contig.showBothBESFilter == Clone.BOTH_BES_COND_FILTERED;
		showOnly2BESCurrentHitClones = contig.showBothBESFilter == Clone.BOTH_BES_CURRENT_COND_FILTERED;

		contigText.setText(new Integer(contigNum).toString());
		widthSlider.setValue(width);
		showCloneNamesBox.setSelected(showCloneNames);
		showOnly2BESHitClonesBox.setSelected(showOnly2BESHitClones);
		showOnly2BESCurrentHitClonesBox.setSelected(showOnly2BESCurrentHitClones);

		cloneNameCombo.setSelectedIndex(cloneFilterInd);
		cloneNameText.setText(cloneFilter);

		cloneRemarksInd = contig.cloneRemarkShow;
		cloneRemarksCombo.setSelectedIndex(cloneRemarksInd);
		cloneRemarksList.setListData(contig.getCloneRemarks());
		selectedRemarkIndices = contig.getSelectedRemarkIndices();
		Arrays.sort(selectedRemarkIndices);
		cloneRemarksList.setSelectedIndices(contig.getSelectedRemarkIndices());
		cloneRemarksList.setVisibleRowCount(VISIBLE_CLONE_REMARKS);

		pack();

		noChange = false;	
	}
	
	public void show() {
		if (!isShowing()) setupShow();
		super.show();
	}

	/**
	 * Method <code>canShow</code> returns true if the Contig has been initialized.
	 *
	 * @return a <code>boolean</code> value
	 */
	public boolean canShow() {
		if (contig == null) return false;
		return contig.hasInit();
	}

	private void setStart() {
		if (contig != null)
			startText.setText(new Long(contig.getStart()).toString());
	}

	private void setEnd() {
		if (contig != null)
			endText.setText(new Long(contig.getEnd()).toString());
	}

	protected void didFlip() {
		setStart();
		setEnd();
	}

	protected boolean okAction() throws Exception {
		if (contig == null) return false;

		long number;
		long tstart, tend;
		boolean changed = false;

		if (!contigText.getText().equals(new Integer(contigNum).toString())) {
			int tContigNum;
			try {
				tContigNum = new Integer(contigText.getText()).intValue();
			} catch (NumberFormatException nfe) {
				throw new Exception("Contig value entered is not valid.");
			}
			if (tContigNum < 0) throw new Exception("Contig number entered is negative.");
			if (!contig.isContig(tContigNum)) throw new Exception("Contig "+tContigNum+" does not exist.");
			contigNum = tContigNum;
			if (contigNum != contig.getContig()) {
				contig.clearWidth();
				contig.setContig(contigNum);
				if (!contig.hasInit()) throw new Exception("Problem initializing Contig");
				contig.resetStart();
				contig.resetEnd();
				setStart();
				setEnd();
				cloneNameText.setText("");
				cloneNameCombo.setSelectedIndex(Contig.CLONE_SHOW);
				widthSlider.setValue(contig.getBaseWidth());
			}
		}

		if (widthSlider.getValue() != width) {
			width = widthSlider.getValue();
			changed = true;
		}

		if (!cloneFilter.equals(cloneNameText.getText()) || cloneFilterInd != cloneNameCombo.getSelectedIndex()) {
			cloneFilter = cloneNameText.getText();
			cloneFilterInd = cloneNameCombo.getSelectedIndex();
			contig.filterCloneNames(cloneFilter,cloneFilterInd);
		}

		tstart = contig.getStart();
		tend = contig.getEnd();

		if (startText.getText().trim().length() > 0) {
			try {
				number = (long)Math.round((new Double(startText.getText())).doubleValue());
			} catch (NumberFormatException nfe) {
				throw new Exception("Start value entered is not a number.");
			}
			try {
				contig.setStart(number);
			} catch (IllegalArgumentException e) {
				setStart();
				throw new Exception("Invalid start value entered.");
			}
		} else {
			contig.resetStart();
			startText.setText(new Long(contig.getStart()).toString());
		}

		if (endText.getText().trim().length() > 0) {
			try {
				number = (long)Math.round((new Double(endText.getText())).doubleValue());
			} catch (NumberFormatException nfe) {
				throw new Exception("End value entered is not a number.");
			}
			long newNum;
			try {
				newNum = contig.setEnd(number);
			} catch (IllegalArgumentException e) {
				setEnd();
				throw new Exception("Invalid end value entered.");
			}
			if (newNum != number) endText.setText(new Long(newNum).toString());
		} else {
			contig.resetEnd();
			endText.setText((new Long(contig.getEnd())).toString());
		}

		if (contig.getStart() == contig.getEnd()) {
			contig.setStart(tstart);
			contig.setEnd(tend);
			throw new Exception("The start and end value must be different.");
		}

		startStr = startText.getText();
		endStr = endText.getText();

		if (super.okAction()) changed = true;

		if (showCloneNames != showCloneNamesBox.isSelected()) {
			showCloneNames = !showCloneNames;
			changed = true;
		}
		if (showOnly2BESHitClones != showOnly2BESHitClonesBox.isSelected()) {
			showOnly2BESHitClones = !showOnly2BESHitClones;
			//if (showOnly2BESHitClones) drawingPanel.downloadAllHits(contig);
			changed = true;
		}
		if (showOnly2BESCurrentHitClones != showOnly2BESCurrentHitClonesBox.isSelected()) {
			showOnly2BESCurrentHitClones = !showOnly2BESCurrentHitClones;
			changed = true;
		}

		if (cloneRemarksInd != cloneRemarksCombo.getSelectedIndex() && cloneRemarksList.getSelectedIndices() != null && 
				cloneRemarksList.getSelectedIndices().length > 0) changed = true;

		if (!Arrays.equals(selectedRemarkIndices,cloneRemarksList.getSelectedIndices())) changed = true;

		return (changed || !contig.hasBuilt());
	}

	/**
	 * Handles the width slider, showing clone names, showing relevant marker names, showing all marker names 
	 * (super.stateChanged(ChangeEvent)), and refreshing the view.
	 * 
	 * @see javax.swing.event.ChangeListener#stateChanged(javax.swing.event.ChangeEvent)
	 */
	public void stateChanged(ChangeEvent event) {
		if (contig != null && !noChange) {
			if (event.getSource() == widthSlider) {
				contig.setWidth(widthSlider.getValue());
			} 
			else if (event.getSource() == showCloneNamesBox) {
				contig.showCloneNames(showCloneNamesBox.isSelected());
			} 
			else if (event.getSource() == showOnly2BESCurrentHitClonesBox) {
				if (showOnly2BESCurrentHitClonesBox.isSelected()) {
					contig.showBothBESFilter(Clone.BOTH_BES_CURRENT_COND_FILTERED);
					showOnly2BESHitClonesBox.setSelected(false);
				}
				else if (showOnly2BESHitClonesBox.isSelected()) {
					contig.showBothBESFilter(Clone.BOTH_BES_COND_FILTERED);
				}
				else {
					contig.showBothBESFilter(Contig.NO_BOTH_BES_FILTER);
				}
			}
			else if (event.getSource() == showOnly2BESHitClonesBox) {
				if (showOnly2BESHitClonesBox.isSelected()) {
					contig.showBothBESFilter(Clone.BOTH_BES_COND_FILTERED);
					showOnly2BESCurrentHitClonesBox.setSelected(false);
				}
				else if (showOnly2BESCurrentHitClonesBox.isSelected()) {
					contig.showBothBESFilter(Clone.BOTH_BES_CURRENT_COND_FILTERED);
				}
				else {
					contig.showBothBESFilter(Contig.NO_BOTH_BES_FILTER);
				}
			}
			else {
				super.stateChanged(event);
				return ; /* !!!!!! refresh() is handled in MarkerFilter !!!!!! */
			}
			refresh();
		}
	}
	
	// mdb added 3/15/07 #104
	public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
		setupShow();		
		showCloneNamesPopupBox.setState(showCloneNames);
		showOnly2BESHitClonesPopupBox.setState(showOnly2BESHitClones);
		showOnly2BESCurrentHitClonesPopupBox.setState(showOnly2BESCurrentHitClones);
		super.popupMenuWillBecomeVisible(event);
	}

	protected void cancelAction() {
		super.cancelAction();
		cloneNameText.setText(cloneFilter);
		cloneNameCombo.setSelectedIndex(cloneFilterInd);
		startText.setText(startStr);
		endText.setText(endStr);
		widthSlider.setValue(width);
		showCloneNamesBox.setSelected(showCloneNames);
		contigText.setText(new Integer(contigNum).toString());
	}
}

