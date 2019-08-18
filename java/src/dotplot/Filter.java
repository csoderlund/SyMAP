/**
 * @author austinps@email.arizona.edu
 *
 */

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
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.util.Observer;
import java.util.Observable;
import java.util.Vector;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Filter extends JDialog implements DotPlotConstants {
	private static final boolean SHOW_NONREPETITIVE = false;
	private static final boolean SHOW_HIGHLIGHT_BLOCK_HITS_CHECKBOX = true;

	/* sort of got to be a mess stemming from the static showFilter(Data), but it works */
	private static final ButtonModel highlightBlockHitsModel = new JToggleButton.ToggleButtonModel();
	private static final ButtonModel highlightSubChainsModel = new JToggleButton.ToggleButtonModel();

	private static final HighlightCheckBoxListener highlightListener = 
		new HighlightCheckBoxListener(highlightBlockHitsModel, highlightSubChainsModel);

	private Data data;
	//private boolean type; 			  // mdb removed 12/3/09 #202
	private boolean isPseudoX, isPseudoY; // mdb added 12/3/09 #202
	private FilterData myFilterData;

	private JSlider[] evalueSliders = new JSlider[NUM_HIT_TYPES];
	private JSlider[] pctidSliders  = new JSlider[NUM_HIT_TYPES];
	private JLabel[] evalueLabels = new JLabel[NUM_HIT_TYPES];
	private JLabel[] pctidLabels  = new JLabel[NUM_HIT_TYPES];
	private JCheckBox[] hideBoxes = new JCheckBox[NUM_HIT_TYPES];
	private JPanel buttonPanel;
	private JButton cancelButton, defaultButton, okButton;
	//private JButton helpButton; // mdb removed 5/20/09
	private JRadioButton showBlockHitsBtn, showNonRepetitiveHitsBtn, showAllHitsBtn;
	private JCheckBox showBlocksBox, showContigsBox, highlightBox, highlightSubChains;
	private JCheckBox showEmptyBox; // mdb added 12/3/09 #203

	public static void showFilter(Data d) {
		SBObserver.show(d.getScoreBounds(),new Filter(d));
	}

	public static void hideFilter(Data d) {
		SBObserver.hide(d.getScoreBounds());
	}

	private Filter(Data d) {
		data = d;
		
		// mdb added 12/3/09 #202
		isPseudoX/*type*/ = data.getProject(X).isPseudo();
		isPseudoY = data.getProject(Y).isPseudo();

		FilterListener listener = new FilterListener();
		myFilterData = new FilterData(data.getFilterData());
		setMinMaxValues(data.getScoreBounds());
		
		for (int i = 0; i < NUM_HIT_TYPES; ++i) {
			hideBoxes[i]     = new JCheckBox("Hide");
			hideBoxes[i].addItemListener(listener);
			evalueSliders[i].addChangeListener(listener);
			listener.stateChanged(new ChangeEvent(evalueSliders[i]));
			pctidSliders[i].addChangeListener(listener);
			listener.stateChanged(new ChangeEvent(pctidSliders[i]));
		}
		setTitle("Filters");
		setModal(false);

		okButton = createButton("Apply","Save changes and close");
		okButton.addActionListener(listener);

		cancelButton = createButton("Cancel", "Discard changes and close");
		cancelButton.addActionListener(listener);

		defaultButton = createButton("Defaults", "Reset to defaults and close");
		defaultButton.addActionListener(listener);

		//helpButton = new JButton("Help"); // mdb removed 5/20/09
		//data.enableHelpOnButton(helpButton,"dpfilter"); // mdb removed 4/30/09 #162

		buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(new JSeparator(), "North");
		JPanel jpanel = new JPanel();
		jpanel.add(okButton);
		jpanel.add(cancelButton);
		jpanel.add(defaultButton);
		//jpanel.add(helpButton); // mdb removed 5/20/09
		buttonPanel.add(jpanel, "Center");

		highlightBox = new JCheckBox("Highlight Block Hits");
		highlightBox.setModel(getHighlightCheckBoxModel(data));

		highlightSubChains = new JCheckBox("Highlight Sub-Chains On Last Selected Block");
		highlightSubChains.setModel(getHighlightSubChainsBoxModel(data));

		showBlockHitsBtn = new JRadioButton("Show Only Block Hits");
		showBlockHitsBtn.addItemListener(listener);

		showNonRepetitiveHitsBtn = new JRadioButton("Show Only Nonrepetitive Hits");
		showNonRepetitiveHitsBtn.addItemListener(listener);

		if (SHOW_NONREPETITIVE)
			showAllHitsBtn = new JRadioButton("Show All Hits (Region Only)");
		else
			showAllHitsBtn = new JRadioButton("Show All Hits");

		showAllHitsBtn.addItemListener(listener);

		ButtonGroup group = new ButtonGroup();
		group.add(showBlockHitsBtn);
		group.add(showNonRepetitiveHitsBtn);
		group.add(showAllHitsBtn);
		showBlocksBox = new JCheckBox("Show Blocks");
		showBlocksBox.addItemListener(listener);

		showContigsBox = new JCheckBox("Show Contigs (Region Only)");
		showContigsBox.addItemListener(listener);
		
		// mdb added 12/3/09 #203
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

		if (!isPseudoY) { // FPC
			addToGrid(cp,gbl,gbc,new JLabel(isPseudoX ? "Marker Hits" : "Shared Markers"),1);
			addToGrid(cp,gbl,gbc,new JLabel(),1);
			addToGrid(cp,gbl,gbc,hideBoxes[MRK],GridBagConstraints.REMAINDER);
		
			addToGrid(cp,gbl,gbc,getLabel("E-Value:",isPseudoX),1);
			addToGrid(cp,gbl,gbc,evalueSliders[MRK],1);
			addToGrid(cp,gbl,gbc,evalueLabels[MRK],GridBagConstraints.REMAINDER);
			addToGrid(cp,gbl,gbc,getLabel("% Identity:",isPseudoX),1);
			addToGrid(cp,gbl,gbc,pctidSliders[MRK],1);
			addToGrid(cp,gbl,gbc,pctidLabels[MRK],GridBagConstraints.REMAINDER);
		
			addToGrid(cp,gbl,gbc,new JSeparator(),GridBagConstraints.REMAINDER);
			addToGrid(cp,gbl,gbc,getLabel(isPseudoX ? "BES Hits" : "FP Hits",true),1);
			addToGrid(cp,gbl,gbc,new JLabel(),1);
			addToGrid(cp,gbl,gbc,hideBoxes[isPseudoX ? BES : FP],GridBagConstraints.REMAINDER);
			addToGrid(cp,gbl,gbc,getLabel(isPseudoX ? "E-Value:" : "Score:",true),1);//maxEvalue-minEvalue > 2),1);
			addToGrid(cp,gbl,gbc,evalueSliders[isPseudoX ? BES : FP],1);
			addToGrid(cp,gbl,gbc,evalueLabels[isPseudoX ? BES : FP],GridBagConstraints.REMAINDER);
			if (!isPseudoY && isPseudoX) { // FPC to PSEUDO
				addToGrid(cp,gbl,gbc,getLabel("% Identity:",isPseudoX),1);
				addToGrid(cp,gbl,gbc,pctidSliders[BES],1);
				addToGrid(cp,gbl,gbc,pctidLabels[BES],GridBagConstraints.REMAINDER);
			}
		}
		else if (isPseudoX) { // PSEUDO to PSEUDO - mdb added 12/3/09 #202
			addToGrid(cp,gbl,gbc,new JLabel("Pseudo Hits"),1);
			addToGrid(cp,gbl,gbc,new JLabel(),GridBagConstraints.REMAINDER);
			addToGrid(cp,gbl,gbc,new JLabel("% Identity:"),1);
			addToGrid(cp,gbl,gbc,pctidSliders[BES],1);
			addToGrid(cp,gbl,gbc,pctidLabels[BES],GridBagConstraints.REMAINDER);
		}
		
		addToGrid(cp,gbl,gbc,new JSeparator(),GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,showBlockHitsBtn,GridBagConstraints.REMAINDER);
		if (SHOW_NONREPETITIVE)
			addToGrid(cp,gbl,gbc,showNonRepetitiveHitsBtn,GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,showAllHitsBtn,GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,new JSeparator(),GridBagConstraints.REMAINDER);

		if (SHOW_HIGHLIGHT_BLOCK_HITS_CHECKBOX)
			addToGrid(cp,gbl,gbc,highlightBox,GridBagConstraints.REMAINDER);
		if (DotPlot.RUN_SUBCHAIN_FINDER)
			addToGrid(cp,gbl,gbc,highlightSubChains,GridBagConstraints.REMAINDER);

		if (!isPseudoY) // FPC - mdb added condition 12/3/09 #202
			addToGrid(cp,gbl,gbc,showContigsBox,GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,showBlocksBox,GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,showEmptyBox,GridBagConstraints.REMAINDER); // mdb added 12/3/09 #203
		addToGrid(cp,gbl,gbc,buttonPanel,GridBagConstraints.REMAINDER);

		init();

		pack();
		setResizable(false);

		Dimension dim = getToolkit().getScreenSize();
		setLocation(dim.width / 4,dim.height / 4);
	}

	private JLabel getLabel(String text, boolean enabled) {
		JLabel label = new JLabel(text);
		label.setEnabled(enabled);
		return label;
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

	public void init() {
		for (int i = 0; i < NUM_HIT_TYPES; i++) {
			evalueSliders[i].setValue((int)(Math.floor((Math.log(myFilterData.getEvalue(i)) / Math.log(10)) * -1)));
			pctidSliders[i].setValue((int)myFilterData.getPctid(i));
			hideBoxes[i].setSelected(myFilterData.getHide(i));
		}
		highlightBox.setSelected(myFilterData.isHighlightBlockHits());
		showBlocksBox.setSelected(myFilterData.isShowBlocks());
		showContigsBox.setSelected(myFilterData.isShowContigs());
		showEmptyBox.setSelected(myFilterData.isShowEmpty()); // mdb added 12/3/09 #203
		if (myFilterData.isShowAllHits())        showAllHitsBtn.setSelected(true);
		else if (myFilterData.isShowBlockHits()) showBlockHitsBtn.setSelected(true);
		else	                                 showNonRepetitiveHitsBtn.setSelected(true);
	}

	private void setMinMaxValues(ScoreBounds sb) {
		for (int i = 0; i < NUM_HIT_TYPES; ++i) {
			if (evalueSliders[i] == null) {
				evalueSliders[i] = new JSlider(JSlider.HORIZONTAL,0,200,0);
				evalueLabels[i] = new JLabel();
			}
			evalueSliders[i].setMinimum((int)Math.floor((Math.log(sb.getEvalue(MAX,i))/Math.log(10)) * -1));
			evalueSliders[i].setMaximum((int)Math.floor((Math.log(sb.getEvalue(MIN,i))/Math.log(10)) * -1));

			if (pctidSliders[i] == null) {
				pctidSliders[i] = new JSlider(JSlider.HORIZONTAL,0,100,0);
				pctidLabels[i] = new JLabel();
			}
			pctidSliders[i].setMinimum((int)sb.getPctid(MIN,i));
			pctidSliders[i].setMaximum((int)sb.getPctid(MAX,i));
		}
	}

	private static class SBObserver implements Observer {
		private ScoreBounds sb;
		private Filter filter;

		private static Vector<SBObserver> observers = new Vector<SBObserver>(1,1);

		private SBObserver(ScoreBounds sb, Filter filter) { 
			this.sb = sb;
			this.filter = filter;
		}

		public boolean equals(Object obj) {
			return obj instanceof SBObserver && ((SBObserver)obj).sb == sb;
		}

		private static void hide(ScoreBounds sb) {
			SBObserver o = new SBObserver(sb,null);
			int i = observers.indexOf(o);
			if (i >= 0) {
				o = observers.get(i);
				//o.filter.hide(); // mdb removed 6/29/07 #118
				o.filter.setVisible(false); // mdb added 6/29/07 #118
				o.filter.dispose();
				observers.remove(i);
			}
		}

		private static void show(ScoreBounds sb, Filter filter) {
			SBObserver o = new SBObserver(sb,filter);
			int i = observers.indexOf(o);
			if (i >= 0) {
				o = observers.get(i);
				if (!o.filter.isShowing() 
						|| o.filter.isPseudoX != filter.isPseudoX 
						|| o.filter.isPseudoY != filter.isPseudoY) 
				{
					//o.filter.hide(); // mdb removed 6/29/07 #118
					o.filter.setVisible(false); // mdb added 6/29/07 #118
					o.filter.dispose();
					o.filter = filter;
				}
			}
			else {
				observers.add(o);
				o.sb.addObserver(o);
				o.filter.setMinMaxValues(o.sb);
			}
			//o.filter.show(); // mdb removed 7/2/07 #118
			o.filter.setVisible(true); // mdb added 7/2/07 #118
		}

		public void update(Observable o, Object arg) {
			if (SwingUtilities.isEventDispatchThread())
				filter.setMinMaxValues(sb);
			else try {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						filter.setMinMaxValues(sb);
					}
				});
			} catch (Exception e) { }
		}
	}

	private class FilterListener implements ActionListener, ChangeListener, ItemListener {
		private FilterListener() { }

		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			if      (src == okButton) /*hide();*/setVisible(false); // mdb changed 6/29/07 #118
			else if (src == cancelButton) {
				data.getFilterData().set(myFilterData);
				//hide(); // mdb removed 6/29/07 #118
				setVisible(false); // mdb added 6/29/07 #118
			}
			else if (src == defaultButton) {
				data.getFilterData().setDefaults();
				//hide(); // mdb removed 6/29/07 #118
				setVisible(false); // mdb added 6/29/07 #118
			}
		}

		public void stateChanged(ChangeEvent e) {
			Object src = e.getSource();
			FilterData fd = data.getFilterData();
			int s;
			for (int i = 0; i < NUM_HIT_TYPES; i++) {
				if (src == evalueSliders[i]) {
					s = evalueSliders[i].getValue();
					evalueLabels[i].setText("1 E-" + s + (s < 100 ? (s < 10 ? "  " : " ") : ""));
					fd.setEvalue(i,Math.pow(10,s*-1));
				}
				else if (src == pctidSliders[i]) {
					s = pctidSliders[i].getValue();
					pctidLabels[i].setText((s < 100 ? (s < 10 ? "  " : " ") : "") + s + "%");
					fd.setPctid(i,s);
				}
			}
		}

		public void itemStateChanged(ItemEvent evt) {
			Object src = evt.getSource();
			FilterData fd = data.getFilterData();
			for (int i = 0; i < hideBoxes.length; i++) {
				if (src == hideBoxes[i]) {
					fd.setHide(i,evt.getStateChange() == ItemEvent.SELECTED);
					return ;
				}
			}
			if (src == showBlockHitsBtn || src == showNonRepetitiveHitsBtn || src == showAllHitsBtn)
				fd.setShowHits(showBlockHitsBtn.isSelected(),showNonRepetitiveHitsBtn.isSelected(),showAllHitsBtn.isSelected());
			else if (src == showBlocksBox)
				fd.setShowBlocks(evt.getStateChange() == ItemEvent.SELECTED);
			else if (src == showContigsBox)
				fd.setShowContigs(evt.getStateChange() == ItemEvent.SELECTED);
			else if (src == showEmptyBox) // mdb added 12/3/09 #203
				fd.setShowEmpty(evt.getStateChange() == ItemEvent.SELECTED);
		}
	}

	private static class HighlightCheckBoxListener implements ItemListener {
		private Vector<Data> datas;

		public HighlightCheckBoxListener(ButtonModel model, ButtonModel subChainsModel) {
			datas = new Vector<Data>(1);
			model.addItemListener(this);
			subChainsModel.addItemListener(this);
		}

		public void addData(Data data) {
			if (!datas.contains(data))
				datas.add(data);
		}

		public void itemStateChanged(ItemEvent evt) {
			int sc = evt.getStateChange();
			if (sc == ItemEvent.SELECTED || sc == ItemEvent.DESELECTED) {
				if (evt.getSource() == highlightBlockHitsModel)
					for (int i = 0; i < datas.size(); i++)
						((Data)datas.get(i)).getFilterData().setHighlightBlockHits(evt.getStateChange() == ItemEvent.SELECTED);
				else
					for (int i = 0; i < datas.size(); i++)
						((Data)datas.get(i)).getFilterData().setHighlightSubChains(evt.getStateChange() == ItemEvent.SELECTED);    
			}
		}
	}

	public static ButtonModel getHighlightCheckBoxModel(Data d) {
		highlightListener.addData(d);
		return highlightBlockHitsModel;
	}

	public static ButtonModel getHighlightSubChainsBoxModel(Data d) {
		highlightListener.addData(d);
		return highlightSubChainsModel;
	}
}
