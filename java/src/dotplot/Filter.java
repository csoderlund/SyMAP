package dotplot;

/*********************************************
 * Filter popup - see FilterData
 */
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
import java.util.Observer;
import java.util.Observable;
import java.util.Vector;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Filter extends JDialog implements DotPlotConstants {
	private Data data;
	
	private FilterData myFilterData;

	private JSlider pctidSliders  = new JSlider();
	private JLabel pctidLabels  = new JLabel ();
	
	private JPanel buttonPanel;
	private JButton cancelButton, defaultButton, okButton;
	
	private JRadioButton showBlockHitsBtn, showAllHitsBtn;
	private JCheckBox showBlocksBox,  highBlockHitsBox;
	private JCheckBox showEmptyBox; 

	public static void showFilter(Data d) {
		SBObserver.show(new Filter(d));
	}

	public static void hideFilter(Data d) {
		SBObserver.hide();
	}

	private Filter(Data d) {
		data = d;
		
		FilterListener listener = new FilterListener();
		myFilterData = new FilterData(data.getFilterData());
		
		pctidSliders.addChangeListener(listener);
		listener.stateChanged(new ChangeEvent(pctidSliders));
		
		setTitle("Filters");
		setModal(false);

		okButton = createButton("Apply","Save changes and close");
		okButton.addActionListener(listener);

		cancelButton = createButton("Cancel", "Discard changes and close");
		cancelButton.addActionListener(listener);

		defaultButton = createButton("Defaults", "Reset to defaults and close");
		defaultButton.addActionListener(listener);

		buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(new JSeparator(), "North");
		JPanel jpanel = new JPanel();
		jpanel.add(okButton);
		jpanel.add(cancelButton);
		jpanel.add(defaultButton);
		buttonPanel.add(jpanel, "Center");

		showBlockHitsBtn = new JRadioButton("Show Only Block Hits");
		showBlockHitsBtn.addItemListener(listener);

		showAllHitsBtn = new JRadioButton("Show All Hits");
		showAllHitsBtn.addItemListener(listener);

		ButtonGroup group = new ButtonGroup();
		group.add(showBlockHitsBtn);
		group.add(showAllHitsBtn);
		
		highBlockHitsBox = new JCheckBox("Highlight Block Hits");
		highBlockHitsBox.addItemListener(listener);

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
		
		addToGrid(cp,gbl,gbc,new JLabel(" Hits"),1); 
		addToGrid(cp,gbl,gbc,new JLabel(),GridBagConstraints.REMAINDER);
		
		addToGrid(cp,gbl,gbc,new JLabel(" Identity:"),1);
		addToGrid(cp,gbl,gbc,pctidLabels, 1);
		addToGrid(cp,gbl,gbc,pctidSliders, GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,new JSeparator(),GridBagConstraints.REMAINDER);
		
		addToGrid(cp,gbl,gbc,showBlockHitsBtn,GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,showAllHitsBtn,GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,new JSeparator(),GridBagConstraints.REMAINDER);

		addToGrid(cp,gbl,gbc,highBlockHitsBox,GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,showBlocksBox,GridBagConstraints.REMAINDER);
		addToGrid(cp,gbl,gbc,showEmptyBox,GridBagConstraints.REMAINDER); 
		
		addToGrid(cp,gbl,gbc,buttonPanel,GridBagConstraints.REMAINDER);

		init();

		pack();
		setResizable(false);

		Dimension dim = getToolkit().getScreenSize();
		setLocation(dim.width / 4,dim.height / 4);
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
		pctidSliders.setValue((int)myFilterData.getPctid());
		
		highBlockHitsBox.setSelected(myFilterData.isHighlightBlockHits());
		showBlocksBox.setSelected(myFilterData.isShowBlocks());
		showEmptyBox.setSelected(myFilterData.isShowEmpty()); 
		
		if (myFilterData.isShowAllHits())        showAllHitsBtn.setSelected(true);
		else if (myFilterData.isShowBlockHits()) showBlockHitsBtn.setSelected(true);
	}

	private static class SBObserver implements Observer {
		private Filter filter;

		private static Vector<SBObserver> observers = new Vector<SBObserver>(1,1);

		private SBObserver(Filter filter) { 
			this.filter = filter;
		}

		private static void hide() {
			SBObserver o = new SBObserver(null);
			int i = observers.indexOf(o);
			if (i >= 0) {
				o = observers.get(i);
				o.filter.setVisible(false); 
				o.filter.dispose();
				observers.remove(i);
			}
		}

		private static void show(Filter filter) {
			SBObserver o = new SBObserver(filter);
			int i = observers.indexOf(o);
			if (i >= 0) {
				o = observers.get(i);
				if (!o.filter.isShowing()) 
				{
					o.filter.setVisible(false); 
					o.filter.dispose();
					o.filter = filter;
				}
			}
			else {
				observers.add(o);
				//o.sb.addObserver(o);
				//o.filter.setMinMaxValues(o.sb);
			}
			o.filter.setVisible(true); 
		}

		public void update(Observable o, Object arg) {
			
		}
	}

	private class FilterListener implements ActionListener, ChangeListener, ItemListener {
		private FilterListener() { }

		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			if      (src == okButton) setVisible(false); 
			else if (src == cancelButton) {
				data.getFilterData().set(myFilterData);
				setVisible(false); 
			}
			else if (src == defaultButton) {
				data.getFilterData().setDefaults();
				setVisible(false); 
			}
		}

		public void stateChanged(ChangeEvent e) {
			Object src = e.getSource();
			FilterData fd = data.getFilterData();
			if (src == pctidSliders) {
				int s = pctidSliders.getValue();
				String x = (s < 100 ? (s < 10 ? "  " : " ") : "");
				pctidLabels.setText(x + s + "%");
				fd.setPctid(s);
			}
		}

		public void itemStateChanged(ItemEvent evt) {
			Object src = evt.getSource();
			FilterData fd = data.getFilterData();
			
			if (src == showBlockHitsBtn || src == showAllHitsBtn)
				fd.setShowHits(showBlockHitsBtn.isSelected(), showAllHitsBtn.isSelected());
			else if (src == highBlockHitsBox)
				fd.setHighBlockHits(evt.getStateChange() == ItemEvent.SELECTED);
			else if (src == showEmptyBox) 
				fd.setShowEmpty(evt.getStateChange() == ItemEvent.SELECTED);
			else if (src == showBlocksBox)
				fd.setShowBlocks(evt.getStateChange() == ItemEvent.SELECTED);
			else if (src == showEmptyBox) 
				fd.setShowEmpty(evt.getStateChange() == ItemEvent.SELECTED);
		}
	}
}
