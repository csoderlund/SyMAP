package circflatview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
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
import symap.Ext;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Jcomp;
import util.Jhtml;
import util.Popup;

/*******************************************************************
 * Control Panel for Circle View
 * CAS573 add linear
 * CAS574 add Display
 * CAS575 add swap colors, and all chromosome labels, and blocks outline
 */
public class ControlPanelFlat extends JPanel implements HelpListener  {
	private static final long serialVersionUID = 1L;
	private FlatPanel flatPanel;
    private HelpBar helpPanel;
  
    // On control bar
    private JButton homeButton, plusButton, minusButton, scaleToggle; 
    private JButton helpButton, saveButton, infoButton;		
    
    static protected final int [] lenCuts = {0,100000, 200000, 500000, 750000, 1000000,2000000}; // For FlatPanel; 
    private JButton lenPopupButton;   // if change, change FlatPanel.paintGroupBlk						
    private String[] lenOptions = {"\u2265 0bp", "\u2265 100k", "\u2265 250k", "\u2265 500k", 
    		                      "\u2265 750k", "\u2265 1M","\u2265 2M"}; 
    private JRadioButtonMenuItem [] lenRadio = new JRadioButtonMenuItem [lenOptions.length];
    
    static protected final int [] numHits = {0,15,25,50,100,200};		// For FlatPanel
    private JButton hitPopupButton;   // if change, change FlatPanel.paintGroupBlk						
    private String[] hitOptions = {"\u2265 0 hits", "\u2265 15 hits", "\u2265 25 hits", 
    		                      "\u2265 50 hits", "\u2265 100 hits","\u2265 200 hits"}; // >= as one char
    private JRadioButtonMenuItem [] hitRadio = new JRadioButtonMenuItem [lenOptions.length];
    
    // Color
    private JButton colorButton;  						
    private ColorDialogHandler cdh;
	private PersistentProps    persistentProps;
	
	// Display
	private JButton displayButton;
	private ChgDisplay disObj = null;

	
	
    public ControlPanelFlat(FlatPanel cp, HelpBar hb) { 
    	helpPanel = hb;
    	flatPanel = cp;
    	
    	persistentProps = new PersistentProps(); // does not work unless this is global
		cdh = new ColorDialogHandler(persistentProps); // needs to be called on creation to init non-default colors
		
    	homeButton   =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/home.gif",
							"Home: Reset to original size settings.");
    	homeButton.setEnabled(false);
    	
		minusButton   =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/minus.gif",
							"Decrease the linear size.");
		plusButton    =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/plus.gif",
							"Increase the linear size.");
		scaleToggle =  Jcomp.createBorderIconButton(null, helpPanel, buttonListener, "/images/scale.gif",
							"Toggle: Scale to the genome size."); 
		
		colorButton  =  Jcomp.createIconButton(null, helpPanel, buttonListener,"/images/colorPalette.png",
				"Menu of ways to alter block colors.");
		colorButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                new ChgColor();
            }
        });
		
		displayButton = Jcomp.createButton(this,helpPanel, null /*has own listener*/,"Display",
				"Option menu for altering the display");
		displayButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (disObj==null) disObj = new ChgDisplay();
                else disObj.setVisible(true); 
            }
        });
		
		lenPopupButton = Jcomp.createButton(this,helpPanel, null /*has own listener*/,"Length",
				"Minimal length of a block to be shown; if either end is < N, it will be filtered");
		createLenPopup();
		
		hitPopupButton = Jcomp.createButton(this,helpPanel, null /*has own listener*/,"Hits",
				"Minimal number of hits for a block to be shown");
		createHitsPopup();
		
		saveButton = Jcomp.createIconButton(this,helpPanel,buttonListener,"/images/print.gif",
							"Save image" );
		infoButton = Jcomp.createIconButton(this,helpPanel,buttonListener,"/images/info.png",
							"Quick Linear Help" );
		
		helpButton = (Ext.isLinux()) ? Jhtml.createHelpIconUserSm(Jhtml.linear)
				   					 : Jhtml.createHelpIconUserLg(Jhtml.linear);
		
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
		if (Ext.isLinux()) {sp1="  "; sp2="   ";}
		addToGrid(row, gbl,gbc,homeButton,	 sp2); 
		addToGrid(row, gbl,gbc,plusButton,	 sp1);					
		addToGrid(row, gbl,gbc,minusButton,	 sp2);
		addToGrid(row, gbl,gbc,scaleToggle,  sp2); // needs extra space between image for Linux
		addToGrid(row, gbl,gbc,lenPopupButton, sp1); 
		addToGrid(row, gbl,gbc,hitPopupButton, sp1); 
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp1); // End home functions
		
		addToGrid(row, gbl,gbc,displayButton, sp1); 
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
  
    private void createLenPopup() {
    	JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBackground(symap.Globals.white);
		
		JMenuItem popupTitle = new JMenuItem("Block Length");
		popupTitle.setFont(new Font(popupTitle.getFont().getName(),Font.PLAIN,11));
		
		popupTitle.setEnabled(false);
		popupMenu.add(popupTitle);
		popupMenu.addSeparator();
		ButtonGroup grp = new ButtonGroup();
		
    	PopupListener listener = new PopupListener();
    	
    	for (int i=0; i<lenOptions.length; i++) {
    		lenRadio[i] = new JRadioButtonMenuItem(lenOptions[i]);
    		lenRadio[i].addActionListener(listener);
    		popupMenu.add(lenRadio[i]);
    		grp.add(lenRadio[i]);
    	}
		lenPopupButton.setComponentPopupMenu(popupMenu);  
		lenPopupButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
		
		lenPopupButton.setComponentPopupMenu(popupMenu);  
		lenPopupButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
		
		Dimension d = new Dimension(90, 25); // w,d
		lenPopupButton.setPreferredSize(d); 
		lenPopupButton.setMaximumSize(d);
		lenPopupButton.setMinimumSize(d);
		
		lenPopupButton.setText(lenOptions[0]);
		lenRadio[0].setSelected(true);
    }
    private void createHitsPopup() {
    	JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBackground(symap.Globals.white);
		
		JMenuItem popupTitle = new JMenuItem("Number hits");
		popupTitle.setFont(new Font(popupTitle.getFont().getName(),Font.PLAIN,11));
		
		popupTitle.setEnabled(false);
		popupMenu.add(popupTitle);
		popupMenu.addSeparator();
		
		ButtonGroup grp = new ButtonGroup();
		PopupListener listener = new PopupListener();
		
		for (int i=0; i<hitOptions.length; i++) {
    		hitRadio[i] = new JRadioButtonMenuItem(hitOptions[i]);
    		hitRadio[i].addActionListener(listener);
    		popupMenu.add(hitRadio[i]);
    		grp.add(hitRadio[i]);
    	}
		
		hitPopupButton.setComponentPopupMenu(popupMenu); 
		hitPopupButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
		
		Dimension d = new Dimension(90, 25); // w,d
		hitPopupButton.setPreferredSize(d); 
		hitPopupButton.setMaximumSize(d);
		hitPopupButton.setMinimumSize(d);
		
		hitPopupButton.setText(hitOptions[0]);
		hitRadio[0].setSelected(true);
    }
    /**********************************************************************
     * Action
     */
    private class PopupListener implements ActionListener {
		private PopupListener() { }
		public void actionPerformed(ActionEvent event) { 
			Object src = event.getSource();
			int idx=-1;
			
			// View minimal size blocks
			idx = -1;
			// View minimal size blocks
			for (int i=0; i<lenRadio.length && idx==-1; i++) {
				if (src==lenRadio[i]) idx=i;
			}
			if (idx!=-1) {
				flatPanel.lenChoice = idx;
				lenPopupButton.setText(lenOptions[idx]);
				setEnables();
				flatPanel.makeRepaint();
				return;
			}
			// View minimal hits
			idx = -1;
			// View minimal hits
			for (int i=0; i<hitRadio.length && idx==-1; i++) {
				if (src==hitRadio[i]) idx=i;
			}
			
			if (idx!=-1) {
				flatPanel.hitChoice = idx;
				hitPopupButton.setText(hitOptions[idx]);
				setEnables();
				flatPanel.makeRepaint();
			}	
		}
    }
    private ActionListener buttonListener = new ActionListener() {
	    public void actionPerformed(ActionEvent evt) {
			Object src = evt.getSource();
			if (src == homeButton)   		{
				resetToHome();
				flatPanel.makeRepaint();
			}
			else if (src == minusButton)   flatPanel.zoom(0.95); 
			else if (src == plusButton)    flatPanel.zoom(1.05);  
			else if (src == scaleToggle)	{	
				flatPanel.bToScale = !flatPanel.bToScale;
				scaleToggle.setBackground(Jcomp.getBorderColor(flatPanel.bToScale));
				flatPanel.makeRepaint();
			}
			else if (src == saveButton) {
				ImageViewer.showImage("Linear", flatPanel);
			}
			else if (src == infoButton) {
				popupHelp();
			}
			setEnables();
	    }
	};
	private void setEnables() {
		homeButton.setEnabled(!flatPanel.isHome());
	}
	private void resetToHome() {
		scaleToggle.setBackground(Jcomp.getBorderColor(false));
		lenPopupButton.setText(lenOptions[0]); lenPopupButton.setSelected(true); 
		hitPopupButton.setText(hitOptions[0]); hitPopupButton.setSelected(true);
		flatPanel.resetToHome(); // arc, zoom, rotate, scale
	}
    
    /////////////////////////////////////////////////////////////////
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	private void popupHelp() {
		String msg = "Move the mouse cursor over a project name and click "
				 + "\n   to color the blocks by the project's chromosome colors."
			     + "\n\nClick on a bar to bring its blocks to the top. "
				 + "\nDouble-click a bar to only show its blocks. To undo, "
			     + "\n   click a bar or a project name."
				 + "\n\nThe first set of controls can be set to their defaults "
				 + "\n   by clicking the Home Icon. "
				 + "\n\nDisplay: Pops up a window with more ways to adjust the display."
				 + "\n   These controls are not saved between sessions."
				 + "\n\nColor palette: Pops up a window with ways to change the colors."
				 + "\n   The color settings are the only settings saved between sessions."
				 + "\n\nSee ? for details.\n";
				
		Popup.displayInfoMonoSpace(this, "Quick Linear Help", msg, false);
	}
	/*********************************************************************
	 * XXX Copied from ControlPanelCirc
	 * Setting are saved in .symap_saved_props under SyMAPlinear (FlatPanel.propName) 
	 * plus ColorDialog write properties/linear.properties 
	 * so the object is recreated every time
	 */
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
			setTitle("Linear Colors"); 
			setResizable(true);
			
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
			addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {}
			});
			JPanel optionPanel = Jcomp.createPagePanel();
			
			/*************************************************************************/
			JPanel row0 = Jcomp.createRowPanel();
			cdh.setLinear();
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
			set1Radio.setSelected(flatPanel.colorSet==1);
			set2Radio.setSelected(flatPanel.colorSet==2);
			row1.add(set1Radio); row1.add(Box.createHorizontalStrut(h)); row1.add(set2Radio); 
			optionPanel.add(row1); optionPanel.add(Box.createVerticalStrut(3));
			
			JPanel row2 = Jcomp.createRowPanel();
			scaleBox = Jcomp.createCheckBox("Scale", "< 1 darker, >1 lighter", flatPanel.bScaleColors);
			txtScale = Jcomp.createTextField(flatPanel.scaleColors+"", 3);
			row2.add(scaleBox); row2.add(txtScale);
			optionPanel.add(row2); optionPanel.add(Box.createVerticalStrut(3));
			
			JPanel row3 = Jcomp.createRowPanel();
			orderRadio = Jcomp.createRadio("Order", "Sorts the colors so the blue-green colors are shown first");
			reverseRadio = Jcomp.createRadio("Reverse", "Sorts the colors so the yellow-red colors are shown first");
			
			txtShuffle = Jcomp.createTextField(flatPanel.seed+"", 2);
			shuffleRadio = Jcomp.createRadio("Shuffle", "Randomize the colors");
			noneRadio = Jcomp.createRadio("None", "No action");
			
			row3.add(orderRadio);    row3.add(Box.createHorizontalStrut(h));
			row3.add(reverseRadio);  row3.add(Box.createHorizontalStrut(h));
			row3.add(shuffleRadio);  row3.add(txtShuffle);  row3.add(Box.createHorizontalStrut(h));
			row3.add(noneRadio);	 row3.add(Box.createHorizontalStrut(h));
			optionPanel.add(row3); optionPanel.add(Box.createVerticalStrut(8));
			
			ButtonGroup grp3 = new ButtonGroup ();
			grp3.add(orderRadio);grp3.add(reverseRadio);grp3.add(shuffleRadio);grp3.add(noneRadio);
			if (flatPanel.bOrder) orderRadio.setSelected(true);
			else if (flatPanel.bRevOrder) reverseRadio.setSelected(true);
			else if (flatPanel.bShuffle) shuffleRadio.setSelected(true);
			else noneRadio.setSelected(true);
			
			// Save, cancel, Default
			okButton = Jcomp.createButton("Save", "Save and close"); 
			okButton.addActionListener(this);
			
			cancelButton = Jcomp.createButton("Cancel", "Cancel and close"); 
			cancelButton.addActionListener(this);
			
			defButton = Jcomp.createButton("Defaults", "Set defaults"); 
			defButton.addActionListener(this);
			
			info2Button = Jcomp.createIconButton(null,helpPanel,buttonListener,"/images/info.png",
					"Quick Linear Color Help" );
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
		
		public void actionPerformed(ActionEvent e) {
			if (e.getSource() == editColorButton) {
				cdh.showX();
			}
			else if (e.getSource() == okButton) {
				flatPanel.colorSet = (set1Radio.isSelected()) ? 1 : 2;
				
				flatPanel.bOrder = orderRadio.isSelected();
				flatPanel.bRevOrder = reverseRadio.isSelected();
				
				flatPanel.bScaleColors = scaleBox.isSelected();
				if (flatPanel.bScaleColors) {
					double d = -1.0;
					try {
						d = Double.parseDouble(txtScale.getText());
					} catch (Exception ex) {d= -1;}
					if (d<0) {
						Popup.showErrorMessage("Invalid 'Scale' value; must be a real positive number.");
						return;
					}
					flatPanel.scaleColors = d;
				}
				flatPanel.bShuffle = shuffleRadio.isSelected();
				if (flatPanel.bShuffle) {
					int s = 0;
					try {
						s = Integer.parseInt(txtShuffle.getText());
					} catch (Exception ex) {s = 0;}
					if (s<=0) {
						Popup.showErrorMessage("Invalid 'Shuffle' value; must be positive integer.");
						return;
					}
					flatPanel.seed = s;
				}
				flatPanel.makeNewColorVec();
				flatPanel.saveColors();
				flatPanel.makeRepaint();
				setVisible(false); 
			}
			else if (e.getSource() == defButton) { // same defaults as set in CircPanel
				set1Radio.setSelected(true);
				
				noneRadio.setSelected(true); scaleBox.setSelected(false);
				
				txtScale.setText("0.8"); txtShuffle.setText("1");
			}
			else if (e.getSource() == cancelButton) {
				if (flatPanel.colorSet==1) set1Radio.isSelected();
				else                       set2Radio.isSelected();
				
				orderRadio.setSelected(flatPanel.bOrder);
				reverseRadio.setSelected(flatPanel.bShuffle);
				
				shuffleRadio.setSelected(flatPanel.bShuffle);
				txtShuffle.setText(flatPanel.seed+"");
				
				scaleBox.setSelected(flatPanel.bScaleColors);
				txtScale.setText(flatPanel.scaleColors+"");
				
				setVisible(false); 
			}	
		}
		private void popupHelp2() {
			String msg = "The 'Two-color all blocks' pull-down option:\n"
					+ "   colors the inverted and non-inverted blocks different colors.\n"
					+ "   Set the colors with the Color Wheel; this uses the Color Wheel 'Save' and\n"
					+ "   the Linear Color 'Save'. The Wheel must be closed before the Linear Color.\n\n"
					
					+ "Set 1 and Set 2 are two sets of 100 colors each.\n\n"
					
					+ "Scale < 1 makes the colors darker, >1 makes them lighter.\n\n"
					
					+ "Order   sorts the colors so the blue-green colors are shown first.\n"
					+ "Reverse sorts the colors so the yellow-red colors are shown first.\n\n"
					
					+ "Shuffle randomizes the 100 colors, where a different constant\n"
					+ "   produces a different set.\n\n"
					
					+ "The color settings are saved between sessions.\n"
					;
					
			Popup.displayInfoMonoSpace(this, "Quick Linear Help", msg, false);
		}
	}
	/********************************************************************
	 * XXX Display of blocks
	 * Settings are not saved between sessions, so this is created once, and made visible after that
	 */
	private class ChgDisplay extends JDialog implements ActionListener {
		private static final long serialVersionUID = 1L;
		// same defaults as in FlatPanel
		private final int fBlkChoice=0;
		private final boolean fbInvBlk=true, fbInvStroke=false;
		private final int fChrChoice=0;
		private final boolean fbChr0Label=true, fbChr1Label=true; // set in Project.bShowChrLabel
		private final int fSlantChoice=0;
		private final int fSwapChoice=0;

		private JButton okButton, defButton, cancelButton, info3Button;
		
		private JCheckBox invBlkCheck, invStrokeCheck; 
		private JRadioButton allBlkRadio, invBlkRadio, notInvBlkRadio, twoBlkRadio;
	
		private int [] slantVals = {0, 25, 45, 65, 90}; 
		private JRadioButton [] slantRadio = new JRadioButton [slantVals.length];
		private JCheckBox chr0LabelCheck, chr1LabelCheck;
		
		private String [] showChrVals = {"All", "With blocks", "With displayed blocks"};
		private String [] showChrTips = {"Show all chromosomes", "Only show chromosome that have blocks", "Only display chromosomes that have displayed blocks"};
		private JRadioButton [] chrRadio = new JRadioButton [showChrVals.length];
		
		// FlatPanel depends on the exact index to work!! 
		private String [] swapVals = {"None", "Species", "Colors", "Both"};
		private String [] swapTips = {"Leave as default", "Put lower species on top", "Put lower colors on top",
				"Put lower species and colors on top"};
		private JRadioButton [] swapRadio = new JRadioButton [swapVals.length];
		
		private ChgDisplay () {
			super();
			setModal(false);
			setTitle("Linear Display"); 
			setResizable(true);
			
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
			addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {}
			});
			
			/***********************************************************/
			JPanel optionPanel = Jcomp.createPagePanel();
			
			JLabel xxx = Jcomp.createLabel("xxxxxxxxxxxxxxx");
			int hw=5, head=xxx.getPreferredSize().width;
			int vw=3, vwb=6;
			JPanel row;
		
		// Blocks
			row = Jcomp.createRowPanel();
			JLabel blkLab = Jcomp.createLabel("Blocks", "Change the display of the blocks");
			row.add(blkLab); 			row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, blkLab)));
			optionPanel.add(row); 		optionPanel.add(Box.createVerticalStrut(vw));
			
			// Display Blocks
			row = Jcomp.createRowPanel();
			JLabel disBlkLab = Jcomp.createLabel("   Display", "Change the display of the blocks");
			row = Jcomp.createRowPanel();
			row.add(disBlkLab); 			row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, disBlkLab)));
			
			allBlkRadio = 		Jcomp.createRadio("All", 		"Show all blocks"); 
			notInvBlkRadio = 	Jcomp.createRadio("Non-Invert", 	"Show non-inverted blocks only");
			invBlkRadio = 		Jcomp.createRadio("Invert", 	"Show inverted blocks only"); 
			twoBlkRadio = 		Jcomp.createRadio("Two-color", 	"Show inverted/non-inverted in two colors");
			allBlkRadio.addActionListener(this); notInvBlkRadio.addActionListener(this);
			invBlkRadio.addActionListener(this); twoBlkRadio.addActionListener(this);
			
			ButtonGroup grp = new ButtonGroup ();
			grp.add(allBlkRadio); grp.add(notInvBlkRadio); grp.add(invBlkRadio); grp.add(twoBlkRadio); 
			allBlkRadio.setSelected(true); blkChoiceSave=fBlkChoice;
				
			row.add(allBlkRadio); 		row.add(Box.createHorizontalStrut(hw)); 
			row.add(twoBlkRadio); 		row.add(Box.createHorizontalStrut(hw));
			row.add(notInvBlkRadio); 	row.add(Box.createHorizontalStrut(hw));
			row.add(invBlkRadio); 		row.add(Box.createHorizontalStrut(hw));
			
			optionPanel.add(row); 		optionPanel.add(Box.createVerticalStrut(vw));
	
			// Inverted blocks
			row = Jcomp.createRowPanel();
			JLabel invLab = Jcomp.createLabel("   Inverted", "Reverse lower coordinates for inverted blocks");
			row.add(invLab);       row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, invLab)));
			
			invBlkCheck = Jcomp.createCheckBox("Reverse orientation", "Reverse orientation of the lower species", fbInvBlk); 
			invBlkCheck.addActionListener(this);
			row.add(invBlkCheck);  row.add(Box.createHorizontalStrut(hw)); 
			
			invStrokeCheck = Jcomp.createCheckBox("Outline", "Inverted blocks are outlined", fbInvStroke); 
			invStrokeCheck.addActionListener(this);
			row.add(invStrokeCheck); 
			
			optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vwb));
						
		// Chromsomes
			row = Jcomp.createRowPanel();
			JLabel chrLab = Jcomp.createLabel("Chromosomes", "Change chromosomes display");
			row.add(chrLab);       row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, chrLab)));
			optionPanel.add(row); 		optionPanel.add(Box.createVerticalStrut(vw));
			
			// Display
			row = Jcomp.createRowPanel();
			JLabel disChrLab = Jcomp.createLabel("   Display", "Show chromosomes based on filter");
			row.add(disChrLab);       row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, disChrLab)));
			ButtonGroup grp2 = new ButtonGroup ();
			for (int i=0; i<showChrVals.length; i++) {
				chrRadio[i] = Jcomp.createRadio(showChrVals[i]+"", showChrTips[i]);
				chrRadio[i].addActionListener(this);
				grp2.add(chrRadio[i]);
				row.add(chrRadio[i]); row.add(Box.createHorizontalStrut(hw));
			}
			chrRadio[0].setSelected(true);
			optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));
						
			// Labels Display
			row = Jcomp.createRowPanel();
			JLabel txtLab = Jcomp.createLabel("   Labels", "Change chromosome labels");
			row.add(txtLab);       row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, txtLab)));
			
			row.add(Jcomp.createMonoLabel(" Display:", 10));
			chr0LabelCheck = Jcomp.createCheckBox(flatPanel.getProj(0), "Display chromosome labels for " + flatPanel.getProj(0), fbChr0Label); 
			chr0LabelCheck.addActionListener(this);
			row.add(chr0LabelCheck);  row.add(Box.createHorizontalStrut(2)); 
			
			chr1LabelCheck = Jcomp.createCheckBox(flatPanel.getProj(1), "Display chromosome labels for " + flatPanel.getProj(1), fbChr1Label); 
			chr1LabelCheck.addActionListener(this);
			row.add(chr1LabelCheck);  row.add(Box.createHorizontalStrut(hw)); 
			optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));
					
			// Labels Slant
			row = Jcomp.createRowPanel();
			JLabel noLab = Jcomp.createLabel("", "");
			row.add(noLab);   row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, noLab)));
			
			row.add(Jcomp.createMonoLabel(" Slant:  ", 10));
			ButtonGroup grp1 = new ButtonGroup ();
			for (int i=0; i<slantVals.length; i++) {
				slantRadio[i] = Jcomp.createRadio(slantVals[i]+"", "Slant labels at a " + slantVals[i] + " angle");
				slantRadio[i].addActionListener(this);
				grp1.add(slantRadio[i]);
				row.add(slantRadio[i]); row.add(Box.createHorizontalStrut(hw));
			}
			slantRadio[0].setSelected(true);
			optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));
			
			// Swap reference
			row = Jcomp.createRowPanel();
			JLabel refLab = Jcomp.createLabel("Species", "Change species");
			row.add(refLab); row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, refLab)));
			optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));
			
			row = Jcomp.createRowPanel();
			JLabel swapRefLab = Jcomp.createLabel("   Swap", "Swap species and colors");
			row.add(swapRefLab); row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, swapRefLab)));
			
			ButtonGroup grp3 = new ButtonGroup ();
			for (int i=0; i<swapVals.length; i++) {
				swapRadio[i] = Jcomp.createRadio(swapVals[i]+"", swapTips[i]);
				swapRadio[i].addActionListener(this);
				grp3.add(swapRadio[i]);
				row.add(swapRadio[i]); row.add(Box.createHorizontalStrut(hw));
			}
			swapRadio[0].setSelected(true);
			
			if (flatPanel.maxNumGrps>50) {// if over 50 chromosomes for either, cannot swap colors
				swapRadio[2].setEnabled(false);
				swapRadio[3].setEnabled(false);
			}
			optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));
		
			/**********************************************************/
			// Save, cancel, Default
			okButton = Jcomp.createButton("Save", "Save and close"); 
			okButton.addActionListener(this);
			
			cancelButton = Jcomp.createButton("Cancel", "Cancel and close"); 
			cancelButton.addActionListener(this);
			
			defButton = Jcomp.createButton("Defaults", "Set defaults"); 
			defButton.addActionListener(this);
			
			info3Button = Jcomp.createIconButton(null,helpPanel,buttonListener,"/images/info.png",
					"Quick Linear Display Help" );
			info3Button.addMouseListener(new MouseAdapter() {
	            public void mousePressed(MouseEvent e) {
	                popupHelp3();
	            }
	        });
			JPanel rowx = new JPanel(); rowx.setBackground(Color.white); // need to do this to center
			rowx.add(okButton);
			rowx.add(cancelButton); 
			rowx.add(defButton); 
			rowx.add(info3Button);
			
			/******** Grid ********************************/
			JPanel buttonPanel = new JPanel(new BorderLayout()); buttonPanel.setBackground(Color.white);
			buttonPanel.add(new JSeparator(), "North");
			buttonPanel.add(rowx, "Center");
			
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
			
			/***********************************************************/
			setModal(true);
			setBackground(Color.white);
			setResizable(false);
			setAlwaysOnTop(true); // doesn't work on Ubuntu
			setLocationRelativeTo(null);	
			setVisible(true);
		}
		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			
			if (src == okButton) 			okAndCloseAction();
			else if (src == cancelButton) 	cancelAndCloseAction();
			else if (src == defButton) 		setDefaults();
			
			else if (src==invBlkCheck) 		flatPanel.bInvBlk 	= invBlkCheck.isSelected();
			else if (src==invStrokeCheck) 	flatPanel.bInvStroke = invStrokeCheck.isSelected();
			
			else if (src==allBlkRadio)		flatPanel.blkChoice = 0;
			else if (src==twoBlkRadio)		flatPanel.blkChoice = 1;
			else if (src==notInvBlkRadio) 	flatPanel.blkChoice = 2;
			else if (src==invBlkRadio)		flatPanel.blkChoice = 3;
			
			else if (src==chr0LabelCheck)	flatPanel.setChrShow(0, chr0LabelCheck.isSelected());
			else if (src==chr1LabelCheck)	flatPanel.setChrShow(1, chr1LabelCheck.isSelected());
			else {
				boolean found=false;
				for (int i=0; i<slantVals.length && !found; i++) {
					if (src==slantRadio[i]) {
						flatPanel.slantChoice = i;
						flatPanel.slantRad = -slantVals[i];
						found = true;
					}
				}
				if (!found) {
					for (int i=0; i<showChrVals.length && !found; i++) {
						if (src==chrRadio[i]) {
							flatPanel.chrChoice = i;
							found = true;
						}
					}
				}
				if (!found) {
					for (int i=0; i<swapVals.length && !found; i++) {
						if (src==swapRadio[i]) {
							flatPanel.swapSpecies(i);
							found = true;
						}
					}
				}
				if (!found) return;
			}
			flatPanel.makeRepaint();
		}
		private void okAndCloseAction() { // action already done; save
			bInvBlkSave 	= invBlkCheck.isSelected();
			bInvStrokeSave 	= invStrokeCheck.isSelected();
			
			if (allBlkRadio.isSelected()) 			blkChoiceSave=0;
			else if (notInvBlkRadio.isSelected()) 	blkChoiceSave=1;
			else if (invBlkRadio.isSelected()) 		blkChoiceSave=2;
			else if (twoBlkRadio.isSelected()) 		blkChoiceSave=3;
			
			for (int i=0; i<slantVals.length; i++) 
				if (slantRadio[i].isSelected()) slantChoiceSave = i;
			
			bChr0LabelSave = chr0LabelCheck.isSelected();
			bChr1LabelSave = chr1LabelCheck.isSelected();
			
			for (int i=0; i<showChrVals.length; i++) 
				if (chrRadio[i].isSelected()) chrChoiceSave = i;
			
			for (int i=0; i<swapVals.length; i++) 
				if (swapRadio[i].isSelected()) swapChoiceSave = i;
			
			setVisible(false); 
		}
		private void cancelAndCloseAction() {
			flatPanel.bInvBlk = bInvBlkSave;   	   invBlkCheck.setSelected(bInvBlkSave); // Blocks
			flatPanel.bInvStroke = bInvStrokeSave; invStrokeCheck.setSelected(bInvStrokeSave);
				
			flatPanel.blkChoice = blkChoiceSave;
			if (blkChoiceSave==0) 		allBlkRadio.setSelected(true);
			else if (blkChoiceSave==1) 	twoBlkRadio.setSelected(true);
			else if (blkChoiceSave==2) 	invBlkRadio.setSelected(true);
			else if (blkChoiceSave==3) 	notInvBlkRadio.setSelected(true);
			
			flatPanel.slantChoice = slantChoiceSave; flatPanel.slantRad = -slantVals[slantChoiceSave]; 
			slantRadio[slantChoiceSave].setSelected(true);
			
			flatPanel.setChrShow(0, bChr0LabelSave);	chr0LabelCheck.setSelected(bChr0LabelSave);
			flatPanel.setChrShow(1, bChr1LabelSave);	chr1LabelCheck.setSelected(bChr1LabelSave);
			
			flatPanel.chrChoice = chrChoiceSave; 
			chrRadio[chrChoiceSave].setSelected(true);
			
			if (swapChoiceSave != flatPanel.swapChoice) {
				flatPanel.swapSpecies(swapChoiceSave); 
				swapRadio[swapChoiceSave].setSelected(true);
			}
			
			setVisible(false); 
		}
		private void setDefaults() {	
			flatPanel.bInvBlk = fbInvBlk;  	invBlkCheck.setSelected(fbInvBlk);
			flatPanel.bInvStroke = fbInvStroke; invStrokeCheck.setSelected(fbInvStroke);
			
			flatPanel.blkChoice = fBlkChoice; allBlkRadio.setSelected(true);
			
			flatPanel.slantChoice = fSlantChoice; flatPanel.slantRad=0; slantRadio[0].setSelected(true);
			
			flatPanel.setChrShow(0, fbChr0Label);	chr0LabelCheck.setSelected(fbChr0Label);
			flatPanel.setChrShow(1, fbChr1Label);	chr1LabelCheck.setSelected(fbChr1Label);
			
			flatPanel.chrChoice = fChrChoice; chrRadio[0].setSelected(true);
			
			flatPanel.swapSpecies(0); swapRadio[0].setSelected(true);
		}
		private void popupHelp3() {
			String msg = "All labels have tool-tips explaining their function.\n "
					   + "You can also try them as each works immediately on the display.\n\n" 
					
					   + "The only option that is not obvious is: With displayed blocks.\n"
					   + "  A chromosome may have blocks but they are not displayed because:\n"
					   + "  they fail the >=Nbp, >=N hits, or the inverted or non-inverted option has been used.\n\n"
					   
					   + "If there are over 50 chromosomes for either of the projects,\n"
					   + "  the Species Swap Colors or Both options are disabled because they will not work."
					;
			Popup.displayInfoMonoSpace(this, "Quick Linear Help", msg, false);
		}
		// These are set on Save only; used in Cancel
		private boolean bInvBlkSave=fbInvBlk, bInvStrokeSave=fbInvStroke;
		private int swapChoiceSave = fSwapChoice;
		private int blkChoiceSave=fBlkChoice, slantChoiceSave=fSlantChoice;
		private boolean bChr0LabelSave=fbChr0Label, bChr1LabelSave=fbChr1Label;
		private int chrChoiceSave = fChrChoice;
	}
	private void addToGrid(Container c, GridBagLayout gbl, GridBagConstraints gbc, Component comp, int i) {
		gbc.gridwidth = i;
		gbl.setConstraints(comp,gbc);
		c.add(comp);
	}
}
