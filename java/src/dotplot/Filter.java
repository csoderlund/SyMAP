package dotplot;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSlider;

import util.Jcomp;
/*********************************************
 * Filter popup - see FilterData
 */
public class Filter extends JDialog  {
	private static final long serialVersionUID = 1L;
	private final int manyGrps=20; // show empty
	private final String hitLabel = "   Hit %Identity ";
	private final String dotLabel = "   Dot Size      ";
	
	private Data data;
	private FilterData cpFiltData;	
	private ControlPanel cntl;		

	private JSlider pctidSlider, dotSizeSlider; 
	private JLabel  pctidLabel, dotSizeLabel; 
	
	private JRadioButton bPctScale,  bLenScale,  bNoScale;
	private JRadioButton bBlockHits, bMixHits, bAllHits;
	private JRadioButton bGeneHits, b2GeneHits, b1GeneHits, b0GeneHits;
	
	private JCheckBox showBlocksBox,  showBlkNumBox;
	private JCheckBox showEmptyBox; 
	
	private JPanel buttonPanel;
	private JButton cancelButton, defaultButton, okButton;
	
	private boolean bIsFirst=true;

	protected Filter(Data d, ControlPanel c) { // Created in ControlPanel when session starts
		data = d;
		cntl = c;
		
		FilterListener listener = new FilterListener();
		
		okButton = Jcomp.createButtonGray("Save","Save changes and close");
		okButton.addActionListener(listener);

		cancelButton = Jcomp.createButtonGray("Cancel", "Discard changes and close");
		cancelButton.addActionListener(listener);

		defaultButton = Jcomp.createButtonGray("Defaults", "Reset to defaults");
		defaultButton.addActionListener(listener);

		JButton helpButton = util.Jhtml.createHelpIconUserSm(util.Jhtml.dotfilter); 
		
		buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(new JSeparator(), "North");
		JPanel jpanel = new JPanel();
		jpanel.add(okButton);
		jpanel.add(cancelButton);
		jpanel.add(defaultButton);
		jpanel.add(helpButton);
		buttonPanel.add(jpanel, "Center");

		int id = data.getInitPctid();	
		pctidLabel = Jcomp.createLabelGray(hitLabel + id, "Show dots > %Identity"); // %identity
		pctidSlider = new JSlider(id, 100, id);
		pctidSlider.setMajorTickSpacing(10);
		pctidSlider.setMinorTickSpacing(5);
		pctidSlider.setPaintTicks(true);
		pctidSlider.addChangeListener(listener);
		listener.stateChanged(new ChangeEvent(pctidSlider));
		
		dotSizeSlider = new JSlider(1, 5, 1); // min, max, init
		dotSizeSlider.setMajorTickSpacing(1);
		dotSizeSlider.setPaintTicks(true);
		dotSizeLabel = Jcomp.createLabelGray(dotLabel + "1", "Scale dots");
		dotSizeSlider.addChangeListener(listener);
		listener.stateChanged(new ChangeEvent(dotSizeSlider));
		
		bPctScale = Jcomp.createRadioGray("%Id", "Scale hits by %Identity");
		bPctScale.addItemListener(listener);
		
		bLenScale = Jcomp.createRadioGray("Length", "Scale hits by length (shown as rectangles)");
		bLenScale.addItemListener(listener);
		
		bNoScale = Jcomp.createRadioGray("None", "All hits the same size");
		bNoScale.addItemListener(listener);

		ButtonGroup sgroup = new ButtonGroup();
		sgroup.add(bPctScale);
		sgroup.add(bLenScale);
		sgroup.add(bNoScale);
		bLenScale.setSelected(true);
		
		bAllHits = Jcomp.createRadioGray("All", "Show all hits the same size");
	
		bAllHits.addItemListener(listener);
		
		bMixHits = Jcomp.createRadioGray("Mix", "Block hits are scale, all others are dots");
		bMixHits.addItemListener(listener);

		bBlockHits = Jcomp.createRadioGray("Block", "Only show block hits");
		bBlockHits.addItemListener(listener);

		ButtonGroup group = new ButtonGroup();
		group.add(bAllHits);
		group.add(bMixHits);
		group.add(bBlockHits);
		bAllHits.setSelected(true);
		
		bGeneHits = Jcomp.createRadioGray("Ignore", "Show all hits");
		bGeneHits.addItemListener(listener);
		
		b2GeneHits = Jcomp.createRadioGray("Both", "Only show hits to two genes");
		b2GeneHits.addItemListener(listener);
		
		b1GeneHits = Jcomp.createRadioGray("One", "Only show hits to one gene");
		b1GeneHits.addItemListener(listener);
		
		b0GeneHits = Jcomp.createRadioGray("None", "Only show hits to no genes");
		b0GeneHits.addItemListener(listener);

		ButtonGroup group2 = new ButtonGroup();
		group2.add(bGeneHits);
		group2.add(b2GeneHits);
		group2.add(b1GeneHits);
		group2.add(b0GeneHits);
		bGeneHits.setSelected(true);
		
		showBlkNumBox = Jcomp.createCheckBoxGray("Number", "Show block number");
		showBlkNumBox.addItemListener(listener);

		showBlocksBox = Jcomp.createCheckBoxGray("Boundary", "Show boundary of block");
		showBlocksBox.addItemListener(listener);

		showEmptyBox = Jcomp.createCheckBoxGray("Show Empty Regions", "Show regions with no hits");
		showEmptyBox.addItemListener(listener);
		
		Container cp = getContentPane();
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		cp.setLayout(gbl);
		gbc.fill = 2;
		gbc.gridheight = 1;
		gbc.ipadx = 5;
		gbc.ipady = 8;
		int rem = GridBagConstraints.REMAINDER;
		
		addToGrid(cp,gbl,gbc,pctidLabel, 1);
		addToGrid(cp,gbl,gbc,pctidSlider, rem);
		
		addToGrid(cp,gbl,gbc,dotSizeLabel, 1);
		addToGrid(cp,gbl,gbc,dotSizeSlider, rem);
		
		addToGrid(cp,gbl,gbc,new JLabel("   Scale dot by hit"),1);
		addToGrid(cp,gbl,gbc,bLenScale,1);
		addToGrid(cp,gbl,gbc,bPctScale,1);
		addToGrid(cp,gbl,gbc,bNoScale,rem);
		
		addToGrid(cp,gbl,gbc,new JLabel("   Show Hits"),1);
		addToGrid(cp,gbl,gbc,bAllHits,1);
		addToGrid(cp,gbl,gbc,bMixHits,1);
		addToGrid(cp,gbl,gbc,bBlockHits,rem);
		
		addToGrid(cp,gbl,gbc,new JLabel("   Only Genes"),1);
		addToGrid(cp,gbl,gbc,bGeneHits,1);
		addToGrid(cp,gbl,gbc,b2GeneHits,1);
		addToGrid(cp,gbl,gbc,b1GeneHits,1);
		addToGrid(cp,gbl,gbc,b0GeneHits,rem);
		
		addToGrid(cp,gbl,gbc,new JSeparator(),rem);
		addToGrid(cp,gbl,gbc,new JLabel("   Show Block"),1);
		addToGrid(cp,gbl,gbc,showBlocksBox,1);
		addToGrid(cp,gbl,gbc,showBlkNumBox,rem);
		
		if (data.getMaxGrps()>manyGrps) {
			addToGrid(cp,gbl,gbc,new JSeparator(),rem);
			addToGrid(cp,gbl,gbc,showEmptyBox, rem); 
		}
		
		addToGrid(cp,gbl,gbc,buttonPanel, rem);

		setFromFD();

		pack();
		setResizable(false);

		setTitle("DotPlot Filter");
		Dimension dim = getToolkit().getScreenSize();
		setLocation(dim.width / 4,dim.height / 4);
		toFront();
		requestFocus();
		setModal(true); 
	}
	protected void showX() {
		cpFiltData = data.getFilterData().copy();
		setFromFD();
		setVisible(true);
	}
	protected void setFromFD() {
		FilterData fd = data.getFilterData();
		
		int id = fd.getPctid();	
		if (id>100) id=100;
		pctidSlider.setValue(id);
		String x = (id < 100) ? ((id < 10) ? "  " : " ") : ""; // blanks
		pctidLabel.setText(hitLabel + x + id);
		
		dotSizeSlider.setValue((int)fd.getDotSize());
		
		bPctScale.setSelected(fd.isPctScale());
		bLenScale.setSelected(fd.isLenScale());
		bNoScale.setSelected(fd.isNoScale());
		
		bAllHits.setSelected(fd.isShowAllHits());
		bMixHits.setSelected(fd.isShowMixHits());
		bBlockHits.setSelected(fd.isShowBlockHits());
		
		bGeneHits.setSelected(fd.isShowGeneIgn()); 
		b2GeneHits.setSelected(fd.isShowGene2());  
		b1GeneHits.setSelected(fd.isShowGene1()); 
		b0GeneHits.setSelected(fd.isShowGene0()); 
		
		showBlocksBox.setSelected(fd.isShowBlocks());
		showBlkNumBox.setSelected(fd.isShowBlkNum());
		
		showEmptyBox.setSelected(fd.isShowEmpty()); 
	}
	
	private void addToGrid(Container c, GridBagLayout gbl, GridBagConstraints gbc, Component comp, int i) {
		gbc.gridwidth = i;
		gbl.setConstraints(comp,gbc);
		c.add(comp);
	}
	
	/************************************************************************************/
	private class FilterListener implements ActionListener, ChangeListener, ItemListener {
		private FilterListener() { }

		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			if (src == okButton) {
				setVisible(false); 
			}
			else if (src == cancelButton) {
				data.getFilterData().setFromFD(cpFiltData);
				setFromFD();
				cntl.update();
				setVisible(false); 
			}
			else if (src == defaultButton) {
				data.getFilterData().setDefaults();
				setFromFD();
				cntl.update();
			}
		}
		public void stateChanged(ChangeEvent e) {
			Object src = e.getSource();
			FilterData fd = data.getFilterData();
			
			if (src == pctidSlider) {
				int s = pctidSlider.getValue();
				String x = (s < 100) ? ((s < 10) ? "  " : " ") : ""; // blanks
				pctidLabel.setText(hitLabel + x + s);
				if (bIsFirst) bIsFirst=false; // otherwise, sets to initMinPctid
				else {
					boolean b = fd.setPctid(s);
					if (b) cntl.update();
				}
			}
			else if (src == dotSizeSlider) {
				int s = dotSizeSlider.getValue();
				String x = (s<10) ? "  " : " "; // blanks
				dotSizeLabel.setText(dotLabel + x + s);
				boolean b = fd.setDotSize(s);
				if (b) cntl.update();
			}
		}
		public void itemStateChanged(ItemEvent evt) {
			Object src = evt.getSource();	
			FilterData fd = data.getFilterData();
		
			boolean b=false;
			if (src == bBlockHits || src == bAllHits || src == bMixHits) {
				b = fd.setShowHits(bBlockHits.isSelected(), bAllHits.isSelected());
			}
			else if (src == bGeneHits || src == b1GeneHits || src == b2GeneHits || src == b0GeneHits) {
				b = fd.setGeneHits(b2GeneHits.isSelected(), b1GeneHits.isSelected(), b0GeneHits.isSelected());
			}
			else if (src == bPctScale || src==bLenScale || src==bNoScale) {
				b = fd.setDotScale(bLenScale.isSelected(), bPctScale.isSelected());
			}
			else {
				boolean isChg = (evt.getStateChange() == ItemEvent.SELECTED);
				
				if (src == showBlocksBox)		b = fd.setShowBlocks(isChg);
				else if (src == showBlkNumBox)	b = fd.setShowBlkNum(isChg);
				else if (src == showEmptyBox) 	b = fd.setShowEmpty(isChg);
			}
			if (b) cntl.update();
		}
	} // end listener
}
