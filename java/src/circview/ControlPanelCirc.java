package circview;

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
 * CAS575 add Display, >=N Len, >=N Hits
 */
public class ControlPanelCirc extends JPanel implements HelpListener  {
	private static final long serialVersionUID = 1L;
	
    // Control bar
    private JButton homeButton, plusButton, minusButton, rotateLButton, rotateRButton; 
    private JButton helpButton, infoButton, saveButton;		
    private JButton scaleToggle, selfToggle; 
    
    static protected final int [] lenCuts = {0,100000, 200000, 500000, 750000, 1000000,2000000}; // For FlatPanel; 
    private JButton lenPopupButton;   // if change, change CircPanel.paintGroupBlk						
    private String[] lenOptions = {"\u2265 0bp", "\u2265 100k", "\u2265 250k", "\u2265 500k", 
    		                      "\u2265 750k", "\u2265 1M","\u2265 2M"}; 
    private JRadioButtonMenuItem [] lenRadio = new JRadioButtonMenuItem [lenOptions.length];
    
    static protected final int [] numHits = {0,15,25,50,100,200};		// For CircPanel
    private JButton hitPopupButton;   // if change, change CircPanel.paintGroupBlk						
    private String[] hitOptions = {"\u2265 0 hits", "\u2265 15 hits", "\u2265 25 hits", 
    		                      "\u2265 50 hits", "\u2265 100 hits","\u2265 200 hits"}; // >= as one char
    private JRadioButtonMenuItem [] hitRadio = new JRadioButtonMenuItem [lenOptions.length];
   
    // Class Color
    private JButton colorButton;  						
    private ColorDialogHandler cdh;
	private PersistentProps    persistentProps;
	
	// Class Display
	private JButton displayButton;
	private ChgDisplay disObj = null; // created first time it is opened

	// Local
	private CircPanel circPanel;
    private HelpBar helpPanel;
    private boolean bIsWG, bIsSelf;
    
    public ControlPanelCirc(CircPanel cp, HelpBar hb, boolean bIsWG, 
    		boolean isSelf, boolean hasSelf) { // isSelf - only self align; hasSelf - at least one project has self align
    	helpPanel = hb;
    	circPanel = cp;
    	
    	this.bIsWG = bIsWG;
    	this.bIsSelf = isSelf;
    	
    	persistentProps = new PersistentProps(); // does not work unless this is global
		cdh = new ColorDialogHandler(persistentProps); // needs to be called on creation to init non-default colors
		
    	homeButton   =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/home.gif",
							"Home: Reset to original size settings.");
    	setHome();
    	
		minusButton   =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/minus.gif",
							"Decrease the circle size.");
		plusButton    =  Jcomp.createIconButton(null, helpPanel, buttonListener, "/images/plus.gif",
							"Increase the circle size.");
		rotateRButton  =  Jcomp.createIconButton(null, helpPanel, buttonListener,"/images/rotate-right.png",
							"Rotate the image clock-wise.");
		rotateLButton  =  Jcomp.createIconButton(null, helpPanel, buttonListener,"/images/rotate-left.png",
							"Rotate the image counter-clock-wise.");
		
		String msg = (bIsWG) ? "genome size." : "chromosome size.";
		scaleToggle =  Jcomp.createBorderIconButton(null, helpPanel, buttonListener, "/images/scale.gif",
							"Toggle: Scale to the " + msg); 
		scaleToggle.setBackground(Jcomp.getBorderColor(circPanel.bToScale));
		scaleToggle.setSelected(cp.bToScale);
		
		selfToggle = Jcomp.createBorderButton(null, helpPanel, buttonListener, "Self-synteny",
				"Toggle: Show self-alignment synteny blocks"); 
		selfToggle.setBackground(Jcomp.getBorderColor(circPanel.bShowSelf));
		selfToggle.setSelected(cp.bShowSelf);
		
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
		
		colorButton  =  Jcomp.createIconButton(null, helpPanel, buttonListener,"/images/colorPalette.png",
				"Menu of ways to alter block colors.");
		colorButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                new ChgColor();
            }
        });
		
		saveButton = Jcomp.createIconButton(this,helpPanel,buttonListener,"/images/print.gif",
							"Save image" );
		infoButton = Jcomp.createIconButton(this,helpPanel,buttonListener,"/images/info.png",
							"Quick Circle Help" );
		
		helpButton = (Ext.isLinux()) ? Jhtml.createHelpIconUserSm(Jhtml.circle)
				   					 : Jhtml.createHelpIconUserLg(Jhtml.circle);
		
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
		addToGrid(row, gbl,gbc,rotateRButton,sp1);
		addToGrid(row, gbl,gbc,rotateLButton,sp1);
		addToGrid(row, gbl,gbc,scaleToggle,  sp1); // needs extra space between image
		addToGrid(row, gbl,gbc,lenPopupButton,  sp1); 
		addToGrid(row, gbl,gbc,hitPopupButton,  sp2); 
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp1); // End home functions
		
		if (hasSelf && !isSelf && !bIsWG) {			// only multi-species 2D where at least one species hasSelf
			addToGrid(row, gbl,gbc,selfToggle,sp1); 	
			selfToggle.setSelected(true);
		}
		addToGrid(row, gbl,gbc,displayButton, sp1); 		
		addToGrid(row, gbl,gbc,colorButton,  sp2); 
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp2); // end alter display
		
		addToGrid(row, gbl,gbc,saveButton,sp1);
		addToGrid(row, gbl,gbc,helpButton,sp1);
		addToGrid(row, gbl,gbc,infoButton,sp1);
		
		add(row);
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
			for (int i=0; i<lenRadio.length && idx==-1; i++) {
				if (src==lenRadio[i]) idx=i;
			}
			
			if (idx!=-1) {
				circPanel.lenChoice = idx;
				lenPopupButton.setText(lenOptions[idx]);
				setHome();
				circPanel.makeRepaint();
				return;
			}
			// View minimal hits
			for (int i=0; i<hitRadio.length && idx==-1; i++) {
				if (src==hitRadio[i]) idx=i;
			}
			
			if (idx!=-1) {
				circPanel.hitChoice = idx;
				hitPopupButton.setText(hitOptions[idx]);
				setHome();
				circPanel.makeRepaint();
			}
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
			setHome();
	    }
	};
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
   
	private void setHome() {
		homeButton.setEnabled(!circPanel.isHome());
	}
	private void resetToHome() {
		scaleToggle.setBackground(Jcomp.getBorderColor(false));
		lenPopupButton.setText(lenOptions[0]); lenPopupButton.setSelected(true); 
		hitPopupButton.setText(hitOptions[0]); hitPopupButton.setSelected(true);
		circPanel.resetToHome(); // arc, zoom, scale, len, hit
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
	private void addToGrid(Container c, GridBagLayout gbl, GridBagConstraints gbc, Component comp, int i) {
		gbc.gridwidth = i;
		gbl.setConstraints(comp,gbc);
		c.add(comp);
	}
	/*****************************************************************
	 * Classes
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
	/********************************************************************
	 * XXX Display of blocks
	 * Settings are not saved between sessions, so this is created once, and made visible after that
	 */
	private class ChgDisplay extends JDialog implements ActionListener {
		private static final long serialVersionUID = 1L;
		// Set the same in CircPanel
		private final int fBlkChoice=0;	// the radio has be set for this on
		private final boolean fbChr0Label=true, fbChr1Label=true;
		private final int fChrChoice=0;
		private final boolean fbSwapSp=false, fbRotateText=false;
	   
		private JButton okButton, defButton, cancelButton, info3Button;
		
		// blocks
		private String [] blkVals = {"All", "Two-color", "Non-inverted", "Inverted"};
		private String [] showBlkTips = {"Show all blocks", "Show inverted and non-inverted blocks in two colors",
				"Show non-inverted blocks only", "Show inverted blocks only"};
		private JRadioButton [] blkRadio = new JRadioButton [blkVals.length];
		
		// labels - 2 chr only
		private JCheckBox chr0LabelCheck, chr1LabelCheck;
		private JCheckBox rotateCheck;
		
		// chromosomes
		private String [] chrVals = {"All", "With blocks", "With displayed blocks"};
		private String [] showChrTips = {"Show all chromosomes", "Only show chromosome that have blocks", "Only display chromosomes that have displayed blocks"};
		private JRadioButton [] chrRadio = new JRadioButton [chrVals.length];
		
		private JCheckBox swapCheck;
		
		private ChgDisplay () {
			super();
			setModal(false);
			setTitle("Circle Display"); 
			setResizable(true);
			
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
			addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {}
			});
			
			/***********************************************************/
			JPanel optionPanel = Jcomp.createPagePanel();
			
			JLabel xxx = Jcomp.createLabel("xxxxxxxxxxxxxxx");
			int hw=5, head=xxx.getPreferredSize().width;
			int vw=3;
			JPanel row;
		
		// Blocks: 
			row = Jcomp.createRowPanel();
			JLabel blkLab = Jcomp.createLabel("Blocks", "Change the display of the blocks");
			row.add(blkLab); 			row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, blkLab)));
			optionPanel.add(row); 		optionPanel.add(Box.createVerticalStrut(vw));
			
			// Display Blocks: All Two-color Non-inverted Inverted; Always
			row = Jcomp.createRowPanel();
			JLabel disBlkLab = Jcomp.createLabel("   Display", "Change the display of the blocks");
			row = Jcomp.createRowPanel();
			row.add(disBlkLab); 			row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, disBlkLab)));
			
			ButtonGroup grp = new ButtonGroup ();
			for (int i=0; i<blkVals.length; i++) {
				blkRadio[i] = Jcomp.createRadio(blkVals[i], showBlkTips[i]); 
				blkRadio[i].addActionListener(this);
				grp.add(blkRadio[i]);
				row.add(blkRadio[i]);
			}
			int x = circPanel.blkChoice;
			blkRadio[x].setSelected(true);
			blkRadio[x].setText(blkVals[x]);
			optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));
						
		// Chromsomes: 2D Slant only
			row = Jcomp.createRowPanel();
			JLabel chrLab = Jcomp.createLabel("Chromosomes", "Change chromosomes display");
			row.add(chrLab);       row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, chrLab)));
			optionPanel.add(row);  optionPanel.add(Box.createVerticalStrut(vw));
			
			// Display: Has Blocks, Has Displayed Blocks: WG 
			row = Jcomp.createRowPanel();
			JLabel disChrLab = Jcomp.createLabel("   Display", "Show chromosomes based on filter");
			row.add(disChrLab);       row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, disChrLab)));
			ButtonGroup grp2 = new ButtonGroup ();
			for (int i=0; i<chrVals.length; i++) {
				chrRadio[i] = Jcomp.createRadio(chrVals[i]+"", showChrTips[i]);
				chrRadio[i].addActionListener(this);
				grp2.add(chrRadio[i]);
				row.add(chrRadio[i]); row.add(Box.createHorizontalStrut(hw));
			}
			chrRadio[0].setSelected(true);
			if (bIsWG) {optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));}
						
			// Labels Display: WG
			row = Jcomp.createRowPanel();
			JLabel txtLab = Jcomp.createLabel("   Labels", "Change chromosome labels");
			row.add(txtLab);       row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, txtLab)));
			
			row.add(Jcomp.createMonoLabel(" Display:", 10));
			chr0LabelCheck = Jcomp.createCheckBox(circPanel.getProj(0), "Display chromosome labels for " + circPanel.getProj(0), fbChr0Label); 
			chr0LabelCheck.addActionListener(this);
			row.add(chr0LabelCheck);  row.add(Box.createHorizontalStrut(2)); 
			
			if (!bIsSelf) {// no proj(1)
				chr1LabelCheck = Jcomp.createCheckBox(circPanel.getProj(1), "Display chromosome labels for " + circPanel.getProj(1), fbChr1Label); 
				chr1LabelCheck.addActionListener(this);
				row.add(chr1LabelCheck);  row.add(Box.createHorizontalStrut(hw)); 
			}
			else chr1LabelCheck = Jcomp.createCheckBox("Self dummy", "Self dummy ", fbChr1Label);
			if (bIsWG) {optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));}
					
			// Slant Labels Rotate: WG and 2D
			row = Jcomp.createRowPanel();
			JLabel noLab = Jcomp.createLabel("", "");
			row.add(noLab);   row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, noLab)));
			
			row.add(Jcomp.createMonoLabel(" Slant:  ", 10));
			rotateCheck = Jcomp.createCheckBox("Rotate", "Rotate chromosome labels", false);
			rotateCheck.addActionListener(this);
			rotateCheck.setSelected(circPanel.bRotateText);
			row.add(rotateCheck);
			optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));
			
			// Swap reference: WG !Self
			row = Jcomp.createRowPanel();
			JLabel swapRefLab = Jcomp.createLabel("Species", "Swap species");
			row.add(swapRefLab); row.add(Box.createHorizontalStrut(Jcomp.getWidth(head, swapRefLab)));
			swapCheck = Jcomp.createCheckBox("Swap", "Put lower species on top.", false);
			swapCheck.addActionListener(this);
			row.add(swapCheck);
			if (bIsWG && !bIsSelf) {optionPanel.add(row); optionPanel.add(Box.createVerticalStrut(vw));}
		
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
			
			else if (src == chr0LabelCheck)	circPanel.setChrShow(0, chr0LabelCheck.isSelected());
			else if (src == chr1LabelCheck)	circPanel.setChrShow(1, chr1LabelCheck.isSelected());
			
			else if (src == swapCheck) circPanel.swapSpecies(swapCheck.isSelected());
			else if (src == rotateCheck) circPanel.bRotateText = rotateCheck.isSelected();
			
			else {
				boolean found=false;
				if (!found) {
					for (int i=0; i<blkVals.length && !found; i++) {
						if (src==blkRadio[i]) {
							circPanel.blkChoice = i;
							found = true;
						}
					}
				}
				if (!found) {
					for (int i=0; i<chrVals.length && !found; i++) {
						if (src==chrRadio[i]) {
							circPanel.chrChoice = i;
							found = true;
						}
					}
				}
				if (!found) return;
			}
			circPanel.makeRepaint();
		}
		private void okAndCloseAction() { // action already done; save
			for (int i=0; i<blkVals.length; i++) 
				if (blkRadio[i].isSelected()) {blkChoiceSave = i; break;}
			
			bChr0LabelSave = chr0LabelCheck.isSelected();
			bChr1LabelSave = chr1LabelCheck.isSelected();
			
			for (int i=0; i<chrVals.length; i++) 
				if (chrRadio[i].isSelected()) {chrChoiceSave = i; break;}
			
			bRotateTextSave = rotateCheck.isSelected();
			bSwapSpSave = swapCheck.isSelected();
			
			setVisible(false); 
		}
		private void cancelAndCloseAction() {	
			circPanel.blkChoice = blkChoiceSave;	blkRadio[blkChoiceSave].setSelected(true);
			
			circPanel.setChrShow(0,bChr0LabelSave); chr0LabelCheck.setSelected(bChr0LabelSave);
			circPanel.setChrShow(1,bChr1LabelSave); chr1LabelCheck.setSelected(bChr1LabelSave);
			
			circPanel.chrChoice = chrChoiceSave; 	chrRadio[chrChoiceSave].setSelected(true);
			
			circPanel.swapSpecies(bSwapSpSave); 	swapCheck.setSelected(bSwapSpSave);
			
			circPanel.bRotateText = bRotateTextSave;		rotateCheck.setSelected(bRotateTextSave);
			
			setVisible(false); 
		}
		private void setDefaults() {	
			circPanel.blkChoice = fBlkChoice; 	blkRadio[fBlkChoice].setSelected(true);
			
			circPanel.setChrShow(0,fbChr0Label);	chr0LabelCheck.setSelected(fbChr0Label);
			circPanel.setChrShow(1,fbChr1Label);	chr1LabelCheck.setSelected(fbChr1Label);
			
			circPanel.chrChoice = fChrChoice; chrRadio[0].setSelected(true);
			
			circPanel.swapSpecies(fbSwapSp); 	swapCheck.setSelected(fbSwapSp);
			
			circPanel.bRotateText = fbRotateText; 		rotateCheck.setSelected(fbRotateText);
		}
		private void setSession(int x, boolean b) {
			circPanel.blkChoice = x; 	blkRadio[x].setSelected(true); blkChoiceSave=x;
			circPanel.bRotateText = b; 	rotateCheck.setSelected(b); bRotateTextSave=b;
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
			Popup.displayInfoMonoSpace(this, "Quick Circle Help", msg, false);
		}
		// These are set on Save only; used in Cancel
		private int blkChoiceSave=fBlkChoice;
		private boolean bChr0LabelSave=fbChr0Label, bChr1LabelSave=fbChr1Label;
		private int chrChoiceSave = fChrChoice;
		private boolean bSwapSpSave = fbSwapSp, bRotateTextSave = fbRotateText;
	} // End ChgDisplay
}
