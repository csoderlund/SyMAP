package circview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import colordialog.ColorDialogHandler;
import props.PersistentProps;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Jcomp;
import util.Popup;
import util.Utilities;

/*******************************************************************
 * Control Panel for Circle View
 */
public class ControlPanelCirc extends JPanel implements HelpListener  {
	private static final long serialVersionUID = 1L;
	private CircPanel circPanel;
    private HelpBar helpPanel;
  
    private JButton homeButton, plusButton, minusButton, rotateLButton, rotateRButton; 
    private JButton helpButton, saveButton, infoButton;		
    private JButton scaleToggle, rotateToggle, revToggle, selfToggle; 
    
    private JButton viewPopupButton;  						
    private String[] actOptions = {"All blocks","Inverted only","Non-inverted only","Two-color all blocks"};
    private JRadioButtonMenuItem view0Radio	= new JRadioButtonMenuItem(actOptions[0]);
    private JRadioButtonMenuItem view1Radio	= new JRadioButtonMenuItem(actOptions[1]);
    private JRadioButtonMenuItem view2Radio	= new JRadioButtonMenuItem(actOptions[2]);
    private JRadioButtonMenuItem view3Radio	= new JRadioButtonMenuItem(actOptions[3]);
    
    private JButton colorButton;  						
    private ColorDialogHandler cdh;
	private PersistentProps    persistentProps;
	
   
    public ControlPanelCirc(CircPanel cp, HelpBar hb, boolean bIsWG, 
    		boolean isSelf, boolean hasSelf) { // isSelf - only self align; hasSelf - at least one project has self align
    	helpPanel = hb;
    	circPanel = cp;
    	circPanel.bShowSelf = isSelf; // turn on showing self since isSelf
    	
    	persistentProps = new PersistentProps(); // does not work unless this is global
		cdh = new ColorDialogHandler(persistentProps); // needs to be called on creation to init non-default colors
		
    	homeButton   =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/home.gif",
							"Home: Reset to original size settings.");
    	homeButton.setEnabled(false);
    	
		minusButton   =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/minus.gif",
							"Decrease the circle size.");
		plusButton    =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/plus.gif",
							"Increase the circle size.");
		String msg = (bIsWG) ? "genome size." : "chromosome size.";
		scaleToggle =  Jcomp.createBorderIconButton(null, helpPanel, buttonListener, "/images/scale.gif",
							"Toggle: Scale to the " + msg); 
		
		rotateRButton  =  Jcomp.createIconButton(null, helpPanel, buttonListener,"/images/rotate-right.png",
							"Rotate the image clock-wise.");
		rotateLButton  =  Jcomp.createIconButton(null, helpPanel, buttonListener,"/images/rotate-left.png",
							"Rotate the image counter-clock-wise.");
		
		rotateToggle =    Jcomp.createBorderButton(null, helpPanel, buttonListener,"Rotate", 
							"Toggle: Rotate the text."); 
		
		colorButton  =  Jcomp.createIconButton(null, helpPanel, buttonListener,"/images/colorPalette.png",
				"Menu of ways to alter block colors.");
		colorButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                new ChgColor();
            }
        });
		
		viewPopupButton = Jcomp.createButtonGray(this,helpPanel, null /*has own listener*/,"View",
				"Click button for menu of options");
		createViewPopup();
		
		selfToggle = Jcomp.createBorderButton(null, helpPanel, buttonListener, "Self-align",
							"Toggle: Show self-alignment synteny blocks"); 
		if (isSelf) {
			circPanel.bShowSelf = isSelf;
			selfToggle.setBackground(Jcomp.getBorderColor(isSelf));
		}
		revToggle =  Jcomp.createBorderButton(null,helpPanel,buttonListener,"Reverse",
							"Toggle: Reverse reference, which re-assigns reference colors"); 
		
		saveButton = Jcomp.createIconButton(this,helpPanel,buttonListener,"/images/print.gif",
							"Save image" );
		infoButton = Jcomp.createIconButton(this,helpPanel,buttonListener,"/images/info.png",
							"Quick Circle Help" );
		helpButton = util.Jhtml.createHelpIconUserLg(util.Jhtml.circle);
		
		//// build row ///////////
		JPanel row = Jcomp.createRowPanelGray(); 
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
		addToGrid(row, gbl,gbc,scaleToggle,  sp2); // needs extra space between image
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp1); // End home functions
		
		addToGrid(row, gbl,gbc,viewPopupButton, sp1); 		
		if (hasSelf)          addToGrid(row, gbl,gbc,selfToggle,sp1); 		// only if there is self
		if (bIsWG && !isSelf) addToGrid(row, gbl,gbc,revToggle, sp2);     	// only for whole genome 
		addToGrid(row, gbl,gbc,colorButton,  sp2); 
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
		
		viewPopupButton.setText(actOptions[0]);
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
			viewPopupButton.setText(actOptions[idx]);
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
			else if (src == minusButton)   circPanel.zoom(0.95); 
			else if (src == plusButton)    circPanel.zoom(1.05);  
			else if (src == rotateRButton) circPanel.rotate(-circPanel.ARC_INC); 
			else if (src == rotateLButton) circPanel.rotate(circPanel.ARC_INC);  
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
		//		revToggle.setBackground(Jcomp.getBorderColor(false));
		//		selfToggle.setBackground(Jcomp.getBorderColor(bIsSelf));
		//		viewPopupButton.setText(invOptions[0]);
	}
    
    /////////////////////////////////////////////////////////////////
	protected void setLastParams(int inv, boolean self, boolean rotate, boolean scale) { 
		viewPopupButton.setText(actOptions[inv]);
		selfToggle.setSelected(self); selfToggle.setBackground(Jcomp.getBorderColor(self));
		scaleToggle.setSelected(scale); scaleToggle.setBackground(Jcomp.getBorderColor(scale));
		rotateToggle.setSelected(rotate); rotateToggle.setBackground(Jcomp.getBorderColor(rotate));
		
		homeButton.setEnabled(!circPanel.isHome());
	}
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	private void popupHelp() {
		String msg = "Move the mouse cursor over project name and click "
				 + "\n   to color the blocks by the project's chromosome colors."
			     + "\n\nClick on an arc to bring its blocks to the top. "
				 + "\nDouble-click an arc to only show its blocks. To undo, "
			     + "\n   click an arc or a project name."
				 + "\n\nSee ? for details.\n";
				
		Popup.displayInfoMonoSpace(this, "Quick Circle Help", msg, false);
	}
	private class ChgColor extends JDialog implements ActionListener {
		private static final long serialVersionUID = 1L;
		private JButton okButton, defButton, cancelButton, info2Button;
		private JRadioButton set1Radio, set2Radio;
		private JRadioButton orderRadio, reverseRadio, shuffleRadio, noneRadio;
		private JCheckBox scaleBox;
		private JTextField txtScale, txtShuffle;
		private JButton editColorButton;
		
		private ChgColor() {
			super();
			setModal(false);
			setTitle("Circle Colors"); 
			setResizable(true);
			
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
			addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {}
			});
			JPanel optionPanel = Jcomp.createPagePanel();
			
			JPanel row0 = Jcomp.createRowPanel();
			cdh.setCircle();
			editColorButton = Jcomp.createBorderIconButton("/images/colorchooser.gif", "Edit the color settings");
			editColorButton.addActionListener(this);
			row0.add(new JLabel("Two-color all blocks: "));
			row0.add(editColorButton);
			optionPanel.add(row0);
			optionPanel.add(new JSeparator());
			
			int h=5;
			JPanel row1 = Jcomp.createRowPanel();
			set1Radio = Jcomp.createRadio("Color Set 1", "Lighter colors"); 
			set2Radio = Jcomp.createRadio("Color Set 2", "Darker colors");
			ButtonGroup grp = new ButtonGroup ();
			grp.add(set1Radio); grp.add(set2Radio); 
			set1Radio.setSelected(circPanel.colorSet==1);
			set2Radio.setSelected(circPanel.colorSet==2);
			row1.add(set1Radio); row1.add(Box.createHorizontalStrut(h)); row1.add(set2Radio); 
			optionPanel.add(row1); optionPanel.add(Box.createVerticalStrut(3));
			
			JPanel row2 = Jcomp.createRowPanel();
			scaleBox = Jcomp.createCheckBox("Scale", "< 1 darker, >1 lighter", circPanel.bScaleColors);
			txtScale = Jcomp.createTextField(circPanel.scaleColors+"", 3);
			row2.add(scaleBox); row2.add(txtScale);
			optionPanel.add(row2); optionPanel.add(Box.createVerticalStrut(3));
			
			JPanel row3 = Jcomp.createRowPanel();
			orderRadio = Jcomp.createRadio("Order", "Sorts the colors so the blue-green colors are shown first");
			reverseRadio = Jcomp.createRadio("Reverse", "Sorts the colors so the yellow-red colors are shown first");
			
			txtShuffle = Jcomp.createTextField(circPanel.seed+"", 2);
			shuffleRadio = Jcomp.createRadio("Shuffle", "Randomize the colors");
			noneRadio = Jcomp.createRadio("None", "No action");
			
			row3.add(orderRadio);    row3.add(Box.createHorizontalStrut(h));
			row3.add(reverseRadio);  row3.add(Box.createHorizontalStrut(h));
			row3.add(shuffleRadio);  row3.add(txtShuffle);  row3.add(Box.createHorizontalStrut(h));
			row3.add(noneRadio);	 row3.add(Box.createHorizontalStrut(h));
			optionPanel.add(row3); optionPanel.add(Box.createVerticalStrut(8));
			
			ButtonGroup grp3 = new ButtonGroup ();
			grp3.add(orderRadio);grp3.add(reverseRadio);grp3.add(shuffleRadio);grp3.add(noneRadio);
			if (circPanel.bOrder) orderRadio.setSelected(true);
			else if (circPanel.bRevOrder) reverseRadio.setSelected(true);
			else if (circPanel.bShuffle) shuffleRadio.setSelected(true);
			else noneRadio.setSelected(true);
			
			// Save, cancel, Default
			okButton = Jcomp.createButton("Save", "Save and redisplay"); 
			okButton.addActionListener(this);
			
			cancelButton = Jcomp.createButton("Cancel", "Cancel"); 
			cancelButton.addActionListener(this);
			
			defButton = Jcomp.createButton("Defaults", "Set defaults"); 
			defButton.addActionListener(this);
			
			info2Button = Jcomp.createIconButton(null,helpPanel,buttonListener,"/images/info.png",
					"Quick Circle Color Help" );
			info2Button.addMouseListener(new MouseAdapter() {
	            public void mousePressed(MouseEvent e) {
	                popupHelp2();
	            }
	        });
			JPanel row = new JPanel(); row.setBackground(Color.white);
			row.add(okButton);
			row.add(cancelButton); 
			row.add(defButton); 
			row.add(info2Button);
			
			JPanel buttonPanel = new JPanel(new BorderLayout()); buttonPanel.setBackground(Color.white);
			buttonPanel.add(new JSeparator(), "North");
			buttonPanel.add(row, "Center");
			
			Container cp = getContentPane();
			GridBagLayout gbl = new GridBagLayout();
			GridBagConstraints gbc = new GridBagConstraints();
			cp.setLayout(gbl);
			gbc.fill = 2;
			gbc.gridheight = 1;
			gbc.ipadx = 5;
			gbc.ipady = 8;
			int rem = GridBagConstraints.REMAINDER;
			
			addToGrid(cp, gbl, gbc, optionPanel, rem);
			addToGrid(cp, gbl, gbc, buttonPanel, rem);
			pack();
			
			setModal(true);
			setBackground(Color.white);
			setResizable(false);
			setAlwaysOnTop(true); // doesn't work on Ubuntu
			setLocationRelativeTo(null);	
			setVisible(true);
		}
		private void addToGrid(Container c, GridBagLayout gbl, GridBagConstraints gbc, Component comp, int i) {
			gbc.gridwidth = i;
			gbl.setConstraints(comp,gbc);
			c.add(comp);
		}
		public void actionPerformed(ActionEvent e) {
			if (e.getSource() == editColorButton) {
				cdh.showX();
			}
			else if (e.getSource() == okButton) {
				circPanel.colorSet = (set1Radio.isSelected()) ? 1 : 2;
				
				circPanel.bOrder = orderRadio.isSelected();
				circPanel.bRevOrder = reverseRadio.isSelected();
				
				circPanel.bScaleColors = scaleBox.isSelected();
				if (circPanel.bScaleColors) {
					double d = -1.0;
					try {
						d = Double.parseDouble(txtScale.getText());
					} catch (Exception ex) {d= -1;}
					if (d<0) {
						Popup.showErrorMessage("Invalid 'Scale' value; must be a real positive number.");
						return;
					}
					circPanel.scaleColors = d;
				}
				circPanel.bShuffle = shuffleRadio.isSelected();
				if (circPanel.bShuffle) {
					int s = 0;
					try {
						s = Integer.parseInt(txtShuffle.getText());
					} catch (Exception ex) {s = 0;}
					if (s<=0) {
						Popup.showErrorMessage("Invalid 'Shuffle' value; must be positive integer.");
						return;
					}
					circPanel.seed = s;
				}
				circPanel.makeNewColorVec();
				circPanel.saveColors();
				circPanel.makeRepaint();
				setVisible(false); 
			}
			else if (e.getSource() == defButton) { // same defaults as set in CircPanel
				set1Radio.setSelected(true);
				
				noneRadio.setSelected(true); scaleBox.setSelected(false);
				
				txtScale.setText("0.8"); txtShuffle.setText("1");
			}
			else if (e.getSource() == cancelButton) {
				if (circPanel.colorSet==1) set1Radio.isSelected();
				else                       set2Radio.isSelected();
				
				orderRadio.setSelected(circPanel.bOrder);
				reverseRadio.setSelected(circPanel.bShuffle);
				
				shuffleRadio.setSelected(circPanel.bShuffle);
				txtShuffle.setText(circPanel.seed+"");
				
				scaleBox.setSelected(circPanel.bScaleColors);
				txtScale.setText(circPanel.scaleColors+"");
				
				setVisible(false); 
			}	
		}
		private void popupHelp2() {
			String msg = "The 'Two-color all blocks' pull-down option:\n"
					+ "   colors the inverted and non-inverted blocks different colors.\n"
					+ "   Set the colors with the Color Wheel; this uses the Color Wheel 'Save' and\n"
					+ "   the Circle Color 'Save'. The Wheel must be closed before the Circle Color.\n\n"
					
					+ "Set 1 and Set 2 are two sets of 100 colors each.\n\n"
					
					+ "Scale < 1 makes the colors darker, >1 makes them lighter.\n\n"
					
					+ "Order   sorts the colors so the blue-green colors are shown first.\n"
					+ "Reverse sorts the colors so the yellow-red colors are shown first.\n\n"
					
					+ "Shuffle randomizes the 100 colors, where a different constant\n"
					+ "   produces a different set.\n\n"
					
					+ "The color settings are saved between sessions.\n"
					;
					
			Popup.displayInfoMonoSpace(this, "Quick Circle Help", msg, false);
		}
	}
}
