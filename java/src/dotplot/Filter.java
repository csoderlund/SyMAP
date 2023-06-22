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
import java.awt.Insets;
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

/*********************************************
 * Filter popup - see FilterData
 * CAS533 removed 'extends Observable' and SBObserable; made Filter object in ControlPanel once
 * CAS541 change filter to add Color Wheel and gene filters
 */

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Filter extends JDialog  {
	private final int manyGrps=20; // show empty
	private final String hitLabel = " Hit %Identity ";
	private final String dotLabel = " Dot Size      ";
	
	private Data data;
	private FilterData cpFiltData;	// CAS533 add
	private ControlPanel cntl;		// CAS533 add

	private JSlider pctidSlider, dotSizeSlider; 
	private JLabel  pctidLabel, dotSizeLabel; 
	
	private JRadioButton bPctScale,  bLenScale,  bNoScale;
	private JRadioButton bBlockHits, bMixHits, bAllHits;
	private JRadioButton bGeneHits, b2GeneHits, b1GeneHits, b0GeneHits;
	
	private JCheckBox showBlocksBox,  showBlkNumBox;
	private JCheckBox showEmptyBox; 
	
	private JPanel buttonPanel;
	private JButton cancelButton, defaultButton, okButton;

	public Filter(Data d, ControlPanel c) {
		data = d;
		cntl = c;
		
		FilterListener listener = new FilterListener();
		
		okButton = createButton("Apply","Save changes and close");
		okButton.addActionListener(listener);

		cancelButton = createButton("Cancel", "Discard changes and close");
		cancelButton.addActionListener(listener);

		defaultButton = createButton("Defaults", "Reset to defaults and close");
		defaultButton.addActionListener(listener);

		JButton helpButton = util.Jhtml.createHelpIconUserSm(util.Jhtml.dotfilter); // CAS533 add
		
		buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(new JSeparator(), "North");
		JPanel jpanel = new JPanel();
		jpanel.add(okButton);
		jpanel.add(cancelButton);
		jpanel.add(defaultButton);
		jpanel.add(helpButton);
		buttonPanel.add(jpanel, "Center");

		int id = (int) data.getPctid();
		pctidSlider = new JSlider(id, 100, id);
		pctidSlider.setMajorTickSpacing(10);
		pctidSlider.setMinorTickSpacing(5);
		pctidSlider.setPaintTicks(true);
		pctidLabel = new JLabel(hitLabel + id); // %identity
		pctidSlider.addChangeListener(listener);
		listener.stateChanged(new ChangeEvent(pctidSlider));
		
		dotSizeSlider = new JSlider(1, 5, 1); // min, max, init
		dotSizeSlider.setMajorTickSpacing(1);
		dotSizeSlider.setPaintTicks(true);
		dotSizeLabel = new JLabel (dotLabel + "1");
		dotSizeSlider.addChangeListener(listener);
		listener.stateChanged(new ChangeEvent(dotSizeSlider));
		
		bPctScale = new JRadioButton("%Id");
		bPctScale.addItemListener(listener);
		
		bLenScale = new JRadioButton("Length");
		bLenScale.addItemListener(listener);
		
		bNoScale = new JRadioButton("None");
		bNoScale.addItemListener(listener);

		ButtonGroup sgroup = new ButtonGroup();
		sgroup.add(bPctScale);
		sgroup.add(bLenScale);
		sgroup.add(bNoScale);
		bLenScale.setSelected(true);
		
		bAllHits = new JRadioButton("All");
		bAllHits.addItemListener(listener);
		
		bMixHits = new JRadioButton("Mix");
		bMixHits.addItemListener(listener);

		bBlockHits = new JRadioButton("Block");
		bBlockHits.addItemListener(listener);

		ButtonGroup group = new ButtonGroup();
		group.add(bAllHits);
		group.add(bMixHits);
		group.add(bBlockHits);
		bAllHits.setSelected(true);
		
		bGeneHits = new JRadioButton("Ignore");
		bGeneHits.addItemListener(listener);
		
		b2GeneHits = new JRadioButton("Both");
		b2GeneHits.addItemListener(listener);
		
		b1GeneHits = new JRadioButton("One");
		b1GeneHits.addItemListener(listener);
		
		b0GeneHits = new JRadioButton("None");
		b0GeneHits.addItemListener(listener);


		ButtonGroup group2 = new ButtonGroup();
		group2.add(bGeneHits);
		group2.add(b2GeneHits);
		group2.add(b1GeneHits);
		group2.add(b0GeneHits);
		bGeneHits.setSelected(true);
		
		showBlkNumBox = new JCheckBox("Show Block Numbers");
		showBlkNumBox.addItemListener(listener);

		showBlocksBox = new JCheckBox("Show Blocks");
		showBlocksBox.addItemListener(listener);

		showEmptyBox = new JCheckBox("Show Empty Regions");
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
		
		addToGrid(cp,gbl,gbc,new JLabel(" Scale dot by hit"),1);
		addToGrid(cp,gbl,gbc,bLenScale,1);
		addToGrid(cp,gbl,gbc,bPctScale,1);
		addToGrid(cp,gbl,gbc,bNoScale,rem);
		
		addToGrid(cp,gbl,gbc,new JLabel(" Show Hits"),1);
		addToGrid(cp,gbl,gbc,bAllHits,1);
		addToGrid(cp,gbl,gbc,bMixHits,1);
		addToGrid(cp,gbl,gbc,bBlockHits,rem);
		
		addToGrid(cp,gbl,gbc,new JLabel(" Only Genes"),1);
		addToGrid(cp,gbl,gbc,bGeneHits,1);
		addToGrid(cp,gbl,gbc,b2GeneHits,1);
		addToGrid(cp,gbl,gbc,b1GeneHits,1);
		addToGrid(cp,gbl,gbc,b0GeneHits,rem);
		
		addToGrid(cp,gbl,gbc,new JSeparator(),rem);
		
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
	public void showX() {
		cpFiltData = data.getFilterData().copy();
		setFromFD();
		setVisible(true);
	}
	public void setFromFD() {
		FilterData fd = data.getFilterData();
		
		int id = (int) fd.getPctid();
		if (id>100) id=100;
		pctidSlider.setValue(id);
		String x = (id < 100) ? ((id < 10) ? "  " : " ") : ""; // blanks
		pctidLabel.setText(hitLabel + x + id);
		
		dotSizeSlider.setValue((int)fd.getDotSize());
		
		bPctScale.setSelected(fd.isPctScale());
		bLenScale.setSelected(fd.isLenScale());
		
		bAllHits.setSelected(fd.isShowAllHits());
		bMixHits.setSelected(fd.isShowMixHits());
		bBlockHits.setSelected(fd.isShowBlockHits());
		
		bGeneHits.setSelected(fd.isShowGeneIgn()); 
		b2GeneHits.setSelected(fd.isShowGene2());  
		b1GeneHits.setSelected(fd.isShowGene1()); 
		b0GeneHits.setSelected(fd.isShowGene0()); 
		
		showBlocksBox.setSelected(fd.isShowBlocks());
		showBlkNumBox.setEnabled(fd.isShowBlocks());
		showBlkNumBox.setSelected(fd.isShowBlkNum());
		
		showEmptyBox.setSelected(fd.isShowEmpty()); 
	}
	private JButton createButton(String s, String t) {
		JButton jbutton = new JButton(s);
		jbutton.setMargin(new Insets(1,3,1,3));
		jbutton.setToolTipText(t);
		return jbutton;
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
				setVisible(false); 
			}
		}
		public void stateChanged(ChangeEvent e) {
			Object src = e.getSource();
			FilterData fd = data.getFilterData();
			
			if (src == pctidSlider) {
				int s = pctidSlider.getValue();
				String x = (s < 100) ? ((s < 10) ? "  " : " ") : ""; // blanks
				pctidLabel.setText(hitLabel + x + s);
			  
				boolean b = fd.setPctid(s);
				if (b) cntl.update();
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
				
				if (src == showBlocksBox)	{
					b = fd.setShowBlocks(isChg);
					showBlkNumBox.setEnabled(showBlocksBox.isSelected());
				}
				else if (src == showBlkNumBox)	b = fd.setShowBlkNum(isChg);
				else if (src == showEmptyBox) 	b = fd.setShowEmpty(isChg);
			}
			if (b) cntl.update();
		}
	} // end listener
}
