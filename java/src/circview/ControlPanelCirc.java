package circview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Jcomp;

/*******************************************************************
 * Control Panel for Circle View; CAS552 made more consistent with other interface
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanelCirc extends JPanel implements HelpListener  {
    private CircPanel circPanel;
    private HelpBar helpPanel;
  
    private JButton homeButton, plusButton, minusButton, rotateLButton, rotateRButton; 
    private JButton helpButton, saveButton, infoButton;		// CAS552 add home, 2nd rotate, infoButton
    private JButton scaleToggle, rotateToggle, revToggle, selfToggle; // CAS552 change to from checkbox to toggle
    
    private JButton viewPopupButton;  						// CAS552 replace JComboBox with popup
    private String[] invOptions = {"All blocks","Inverted only","Non-inverted only","Two-color all blocks"};
    private JRadioButtonMenuItem view0Radio	= new JRadioButtonMenuItem(invOptions[0]);
    private JRadioButtonMenuItem view1Radio	= new JRadioButtonMenuItem(invOptions[1]);
    private JRadioButtonMenuItem view2Radio	= new JRadioButtonMenuItem(invOptions[2]);
    private JRadioButtonMenuItem view3Radio	= new JRadioButtonMenuItem(invOptions[3]);
    
    public ControlPanelCirc(CircPanel cp, HelpBar hb, boolean bIsWG, 
    		boolean isSelf, boolean hasSelf) { // isSelf - only self align; hasSelf - at least one project has self aling
    	helpPanel = hb;
    	circPanel = cp;
    	circPanel.bShowSelf = isSelf; // turn on by default since only self align
    	
    	homeButton   =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/home.gif",
							"Reset to original size settings.");
    	homeButton.setEnabled(false);
    	
		minusButton   =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/minus.gif",
							"Decrease the circle size.");
		plusButton    =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/plus.gif",
							"Increase the circle size.");
		scaleToggle =  Jcomp.createBorderIcon(null, helpPanel, buttonListener, "/images/scale.gif",
							"Toggle: Scale to genome and chromosome sizes in bp."); 
		
		rotateRButton  =  Jcomp.createIconButton(null, helpPanel, buttonListener,"/images/rotate-right.png",
							"Rotate the image clock-wise.");
		rotateLButton  =  Jcomp.createIconButton(null, helpPanel, buttonListener,"/images/rotate-left.png",
							"Rotate the image counter-clock-wise.");
		
		rotateToggle =    Jcomp.createBorderText(null, helpPanel, buttonListener,"Rotate", 
							"Toggle: Rotate the text."); 
			
		viewPopupButton = Jcomp.createButton(this,helpPanel, null /*has own listener*/,"View",
				"Click button for menu of options");
		createViewPopup();
		
		selfToggle =   Jcomp.createBorderText(null, helpPanel, buttonListener, "Self-align",
							"Toggle: Show self-alignment synteny blocks"); 
		if (isSelf) {
			circPanel.bShowSelf = isSelf;
			selfToggle.setBackground(Jcomp.getBorderColor(isSelf));
		}
		revToggle =    Jcomp.createBorderText(null,helpPanel,buttonListener,"Reverse",
							"Toggle: Reverse reference, which re-assigns reference colors"); 
		
		saveButton = Jcomp.createIconButton(this,helpPanel,buttonListener,"/images/print.gif",
							"Save image" );
		infoButton = Jcomp.createIconButton(this,helpPanel,buttonListener,"/images/info.png",
							"Quick Circle Help" );
		helpButton = util.Jhtml.createHelpIconUserLg(util.Jhtml.circle);
		
		//// build row ///////////
		JPanel row = Jcomp.createGrayRowPanel(); // CAS552 added row; changed spacing
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		setLayout(gbl);							// If row.setLayout(gbl), cannot add Separators, but ipadx, etc work
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridheight = 1;
		gbc.ipadx = 0;
		gbc.ipady = 0;
		
		String sp1=" ", sp2="  ";
		addToGrid(row, gbl,gbc,homeButton,	 sp2); 
		addToGrid(row, gbl,gbc,plusButton,	 sp1);					
		addToGrid(row, gbl,gbc,minusButton,	 sp2);
		addToGrid(row, gbl,gbc,rotateRButton,sp1);
		addToGrid(row, gbl,gbc,rotateLButton,sp1);
		addToGrid(row, gbl,gbc,rotateToggle, sp2);
		addToGrid(row, gbl,gbc,scaleToggle,  sp2); // needs extra space between image and spe
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp1); // End home functions
		
		addToGrid(row, gbl,gbc,viewPopupButton, sp1); 		
		if (hasSelf)          addToGrid(row, gbl,gbc,selfToggle,sp1); 		// only if there is self; CAS552 add check
		if (bIsWG && !isSelf) addToGrid(row, gbl,gbc,revToggle, sp1);     	// only for whole genome 
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp2); // end alter display
		
		addToGrid(row, gbl,gbc,saveButton,sp1);
		addToGrid(row, gbl,gbc,helpButton,sp1);
		addToGrid(row, gbl,gbc,infoButton,sp1);
		
		add(row);
    }
    private void addToGrid(JPanel cp, GridBagLayout gbl, GridBagConstraints gbc, Component comp, String blank) {
		gbl.setConstraints(comp,gbc);
		cp.add(comp);
		if (blank.length()>0) addToGrid(cp, gbl,gbc,new JLabel(blank), "");
    }
    private void createViewPopup() {
    	PopupListener listener = new PopupListener();
		view0Radio.addActionListener(listener);
		view1Radio.addActionListener(listener);
		view2Radio.addActionListener(listener);
		view3Radio.addActionListener(listener);
		
    	JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBackground(symap.Globals.white);
		
		JMenuItem popupTitle = new JMenuItem();
		popupTitle.setText("Select View");
		popupTitle.setEnabled(false);
		popupMenu.add(popupTitle);
		popupMenu.addSeparator();
		
		popupMenu.add(view0Radio);
		popupMenu.add(view1Radio);
		popupMenu.add(view2Radio);
		popupMenu.add(view3Radio);
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(view0Radio); grp.add(view1Radio); grp.add(view2Radio); grp.add(view3Radio);
		
		viewPopupButton.setComponentPopupMenu(popupMenu); // zoomPopupButton create in main
		viewPopupButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
		
		Dimension d = new Dimension(100, 25); // w,d
		viewPopupButton.setPreferredSize(d); 
		viewPopupButton.setMaximumSize(d);
		viewPopupButton.setMinimumSize(d);
		
		viewPopupButton.setText(invOptions[0]);
		view0Radio.setSelected(true);
    }
    /**********************************************************************
     * Action
     */
    private class PopupListener implements ActionListener {
		private PopupListener() { }
		public void actionPerformed(ActionEvent event) { 
			Object src = event.getSource();
			int idx=0;
			if (src==view0Radio) idx=0;
			else if (src==view1Radio) idx=1;
			else if (src==view2Radio) idx=2;
			else if (src==view3Radio) idx=3;
			else return;
			
			circPanel.invChoice = idx;
			viewPopupButton.setText(invOptions[idx]);
			circPanel.makeRepaint();
		}
    }
    private ActionListener buttonListener = new ActionListener() {
	    public void actionPerformed(ActionEvent evt) {
			Object src = evt.getSource();
			if (src == homeButton)   		{
				resetToHome();
				circPanel.makeRepaint();
			}
			else if (src == minusButton)   	circPanel.zoom(0.95);
			else if (src == plusButton) 	circPanel.zoom(1/0.95);
			else if (src == rotateRButton)	circPanel.rotate(-10);
			else if (src == rotateLButton)	circPanel.rotate(10);
			else if (src == scaleToggle)	{	
				circPanel.bToScale = !circPanel.bToScale;
				scaleToggle.setBackground(Jcomp.getBorderColor(circPanel.bToScale));
				circPanel.makeRepaint();
			}
			else if (src == rotateToggle) {
				circPanel.bRotateText = !circPanel.bRotateText;
				rotateToggle.setBackground(Jcomp.getBorderColor(circPanel.bRotateText));
				circPanel.makeRepaint();
			}
			else if (src == revToggle) {
				circPanel.bRevRef = !circPanel.bRevRef;
				revToggle.setBackground(Jcomp.getBorderColor(circPanel.bRevRef));
				
				circPanel.reverse();
				circPanel.makeRepaint();
			}
			else if (src == selfToggle){
				circPanel.bShowSelf = !circPanel.bShowSelf;
				selfToggle.setBackground(Jcomp.getBorderColor(circPanel.bShowSelf));
				circPanel.makeRepaint();
			}
			else if (src == saveButton) {
				ImageViewer.showImage("Circle", circPanel);
			}
			else if (src == infoButton) {
				popupHelp();
			}
			setEnables();
	    }
	};
	private void setEnables() {
		homeButton.setEnabled(!circPanel.isHome());
	}
	private void resetToHome() {
		rotateToggle.setBackground(Jcomp.getBorderColor(false));
		scaleToggle.setBackground(Jcomp.getBorderColor(false));
		circPanel.resetToHome(); // arc, zoom, rotate, scale
		// The following are not part of Home:
		//revToggle.setBackground(Jcomp.getBorderColor(false));
		//selfToggle.setBackground(Jcomp.getBorderColor(bIsSelf));
		//viewPopupButton.setText(invOptions[0]);
	}
    
    /////////////////////////////////////////////////////////////////
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	private void popupHelp() {
		String msg = "Move the mouse cursor near project name until it changes to a finger, "
				 + "\n   and click to color the blocks by the project's chromosome colors."
				 + "\n   Note: When the text is not Rotated, the cursor may only change to a"
				 + "\n         finger in white space between the name and arc."
			     + "\n\nClick on an arc to bring its blocks to the top. "
				 + "\nDouble-click an arc to only show its blocks."
				 + "\n\nSee ? for details.\n";
				
		util.Utilities.displayInfoMonoSpace(this, "Quick Circle Help", msg, false);
	}
}
