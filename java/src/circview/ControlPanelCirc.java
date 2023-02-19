package circview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Component;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JComboBox;

import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Jcomp;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanelCirc extends JPanel implements HelpListener  {
    private CircPanel circPanel;
    private HelpBar helpPanel;
    private JButton plusButton, minusButton, rotateButton, helpButton, saveButton;
    private JCheckBox scaleCheckbox, rotCheckbox, revCheckbox;
    private JCheckBox selfCheckbox;
    private JComboBox <String> invChooser;
   
    public ControlPanelCirc(CircPanel _cp, HelpBar _hb, boolean bIsWG, boolean isSelf) {
    	helpPanel = _hb;
    	circPanel = _cp;
    	circPanel.bShowSelf = isSelf;
    	
    	// createButton: HelpListener parent, String path, String tip, HelpBar bar, ActionListener listener, boolean isCheckbox
		minusButton   = (JButton)  Jcomp.createButton(null,"/images/minus.gif",
							"Shrink: Decrease the scale.", helpPanel, buttonListener, false, false);
		plusButton    = (JButton)  Jcomp.createButton(null,"/images/plus.gif",
							"Grow: Increase the scale.", helpPanel, buttonListener, false, false);
		rotateButton  = (JButton)  Jcomp.createButton(null,"/images/rotate.gif",
							"Rotate the image.", helpPanel, buttonListener, false, false);
		scaleCheckbox = (JCheckBox)Jcomp.createButton(null,"Scale",
							"Scale to genome and chromosome sizes in bp", helpPanel, buttonListener, true, false); 
		selfCheckbox = (JCheckBox)  Jcomp.createButton(null,"Self-align",
				"Show self-alignment synteny blocks", helpPanel, buttonListener, true, isSelf); 
		rotCheckbox = (JCheckBox)  Jcomp.createButton(null,"Rotate text", 
				"Draw with labels rotated", helpPanel,buttonListener,true, false); 
		revCheckbox = (JCheckBox)  Jcomp.createButton(null,"Reverse",
				"Reverse reference",helpPanel,buttonListener,true, false); 
		
		String[] invOptions = {"All blocks","Inverted","Non-inverted","Two-color"};
		invChooser = new JComboBox <String> (invOptions);
		invChooser.setPreferredSize(invChooser.getMinimumSize());
		invChooser.setSelectedIndex(0);
		invChooser.addActionListener(buttonListener);
		invChooser.setName("Show inverted or non-inverted blocks; or green=inverted, red=non-inverted.");
		invChooser.addActionListener(buttonListener);
		

		saveButton = (JButton) Jcomp.createButton(this,"/images/print.gif",
				"Save image" ,helpPanel,buttonListener,false, false);
		helpButton = (JButton) Jcomp.createButton(this,"/images/help.gif",
				"Help: Online documentation." ,helpPanel,null,false, false);
		
		helpButton = util.Jhtml.createHelpIconUserLg(util.Jhtml.circle);
		

		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		setLayout(gbl);
		gbc.fill = 2;
		gbc.gridheight = 1;
		gbc.ipadx = 5;
		gbc.ipady = 8;
		addToGrid(gbl,gbc,plusButton,1,1);
		addToGrid(gbl,gbc,minusButton,1,1);
		addToGrid(gbl,gbc,rotateButton,1,1);
		addToGrid(gbl,gbc,scaleCheckbox,1,1); 		
		addToGrid(gbl,gbc,invChooser,1,1); 		
		addToGrid(gbl,gbc,selfCheckbox,1,1);
		if (bIsWG && !isSelf) addToGrid(gbl,gbc,revCheckbox,1,1); // whole genome only
		addToGrid(gbl,gbc,rotCheckbox,1,1);
		addToGrid(gbl,gbc,saveButton,1,1);
		addToGrid(gbl,gbc,helpButton,GridBagConstraints.REMAINDER,0);
    }
    
    private ActionListener buttonListener = new ActionListener() {
	    public void actionPerformed(ActionEvent evt) {
			Object src = evt.getSource();
			if (src == minusButton)   		circPanel.zoom(0.95);
			else if (src == plusButton) 	circPanel.zoom(1/0.95);
			else if (src == rotateButton)	circPanel.rotate(-10);
			else if (src == scaleCheckbox)	circPanel.toggleScaled(((JCheckBox)src).isSelected());
			else if (src == revCheckbox) {
				circPanel.bRevRef = ((JCheckBox)src).isSelected();
				circPanel.reverse();
				circPanel.makeRepaint();
			}
			else if (src == selfCheckbox){
				circPanel.bShowSelf = ((JCheckBox)src).isSelected();
				circPanel.makeRepaint();
			}
			else if (src == rotCheckbox){
				circPanel.bRotateText = ((JCheckBox)src).isSelected();
				circPanel.makeRepaint();
			}
			else if (src == invChooser){
				circPanel.invChoice = ((JComboBox)src).getSelectedIndex();
				circPanel.makeRepaint();
			}
			else if (src == saveButton) {
				ImageViewer.showImage("Circle", circPanel);
			}
	    }
	};

    private void addToGrid(GridBagLayout gbl, GridBagConstraints gbc, Component comp, int i, int sep) {
		gbc.gridwidth = i;
		gbl.setConstraints(comp,gbc);
		add(comp);
		if (sep > 0) addToGrid(gbl,gbc,new JLabel(),1,0);
		if (sep > 1) {
		    addToGrid(gbl,gbc,new JSeparator(JSeparator.VERTICAL),1,0);
		}
    }
    
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
}
