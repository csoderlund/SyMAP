package circview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Component;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

import java.applet.Applet;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JComboBox;
import javax.swing.DefaultBoundedRangeModel;

import java.util.Observer;
import java.util.Observable;

import symap.SyMAP;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Utilities;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanelCirc extends JPanel implements HelpListener 
{
     CircPanel cp;
    HelpBar hb;
    JButton plusButton, minusButton, rotateButton, helpButton, saveButton;
    JCheckBox scaleCheckbox, selfCheckbox, rotCheckbox;
    JComboBox invChooser;
    Applet applet;
    
    public ControlPanelCirc(CircPanel _cp, HelpBar _hb, final Applet _applet) 
    {

    	hb = _hb;
    	cp = _cp;
    	applet = _applet;

		minusButton      = (JButton)  Utilities.createButton(null,"/images/minus.gif","Shrink: Decrease the scale.",hb,buttonListener,false);
		plusButton       = (JButton)  Utilities.createButton(null,"/images/plus.gif","Grow: Increase the scale.",hb,buttonListener,false);
		rotateButton       = (JButton)  Utilities.createButton(null,"/images/rotate.gif","Rotate the image.",hb,buttonListener,false);
		scaleCheckbox = (JCheckBox)  Utilities.createButton(null,"Scale to genome size","Scale to genome and chromosome sizes in bp",hb,buttonListener,true); // mdb added 7/8/09 #164
		selfCheckbox = (JCheckBox)  Utilities.createButton(null,"Self-align","Show self-alignment synteny blocks",hb,buttonListener,true); // mdb added 7/8/09 #164
		selfCheckbox.setSelected(true);
		rotCheckbox = (JCheckBox)  Utilities.createButton(null,"Rotated text","Draw with labels rotated",hb,buttonListener,true); // mdb added 7/8/09 #164
		rotCheckbox.setSelected(false);

		String[] invOptions = {"Show all blocks","Show inverted blocks","Show non-inverted blocks","Two-color scheme"};
		invChooser = new JComboBox(invOptions);
		invChooser.setSelectedIndex(0);
		invChooser.addActionListener(buttonListener);
		invChooser.setName("Select how to show inverted and non-inverted synteny blocks");
		hb.addHelpListener(invChooser,this);
		//Component[] comps = invChooser.getComponents();
		//for(int i = 0; i < comps.length; i++)
		//{			
		//	hb.addHelpListener(comps[i],this);
		//}

		saveButton = (JButton) Utilities.createButton(this,"/images/print.gif","Save image" ,hb,buttonListener,false);
		helpButton = (JButton) Utilities.createButton(this,"/images/help.gif","Help: Online documentation." ,hb,null,false);
		helpButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String url = SyMAP.USER_GUIDE_URL;
				if ( !Utilities.tryOpenURL(applet, url) )
					System.err.println("Error opening URL: " + url);
			}
		});
	
		
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
		addToGrid(gbl,gbc,rotCheckbox,1,1);
		addToGrid(gbl,gbc,saveButton,1,1);
		addToGrid(gbl,gbc,helpButton,GridBagConstraints.REMAINDER,0);
	
    }
    
    private ActionListener buttonListener = new ActionListener() 
    {
	    public void actionPerformed(ActionEvent evt) 
	    {
			Object src = evt.getSource();
			if (src == minusButton)      
			{
				cp.zoom(0.95);
			}
			else if (src == plusButton)       
			{
				cp.zoom(1/0.95);
			}
			else if (src == rotateButton)
			{
				cp.rotate(-10);
			}
			else if (src == scaleCheckbox)
			{
				cp.toggleScaled(((JCheckBox)src).isSelected());
			}
			else if (src == selfCheckbox)
			{
				cp.showSelf = ((JCheckBox)src).isSelected();
				cp.makeRepaint();
			}
			else if (src == rotCheckbox)
			{
				cp.bRotateText = ((JCheckBox)src).isSelected();
				cp.makeRepaint();
			}
			else if (src == invChooser)
			{
				cp.invChoice = ((JComboBox)src).getSelectedIndex();
				cp.makeRepaint();
			}
			else if (src == saveButton)
			{
				ImageViewer.showImage(cp);
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
		    //addToGrid(gbl,gbc,new JLabel(),1,0); // mdb removed 5/20/09
		}
    }
    
    // mdb added 7/6/09
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	

}
