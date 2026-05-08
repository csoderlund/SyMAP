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
 */
public class ControlPanelFlat extends JPanel implements HelpListener  {
	private static final long serialVersionUID = 1L;
	private boolean bTest=false; // Outline inverted leaves residuals; slant text needs work
	private FlatPanel flatPanel;
    private HelpBar helpPanel;
  
    private JButton homeButton, plusButton, minusButton, scaleToggle; 
    private JButton helpButton, saveButton, infoButton;		
    
    static protected final int [] lenCuts = {0,100000, 200000, 500000, 750000, 1000000,2000000}; // For FlatPanel; 
    private JButton lenPopupButton;   // if change, change FlatPanel.paintGroupBlk						
    private String[] lenOptions = {"\u2265 0bp", "\u2265 100k", "\u2265 250k", "\u2265 500k", 
    		                      "\u2265 750k", "\u2265 1M","\u2265 2M"}; 
    private JRadioButtonMenuItem len0Radio	= new JRadioButtonMenuItem(lenOptions[0]);
    private JRadioButtonMenuItem len1Radio	= new JRadioButtonMenuItem(lenOptions[1]);
    private JRadioButtonMenuItem len2Radio	= new JRadioButtonMenuItem(lenOptions[2]);
    private JRadioButtonMenuItem len3Radio	= new JRadioButtonMenuItem(lenOptions[3]);
    private JRadioButtonMenuItem len4Radio	= new JRadioButtonMenuItem(lenOptions[4]);
    private JRadioButtonMenuItem len5Radio	= new JRadioButtonMenuItem(lenOptions[5]);
    private JRadioButtonMenuItem len6Radio	= new JRadioButtonMenuItem(lenOptions[6]);
    
    static protected final int [] numHits = {0,15,25,50,100,200};		// For FlatPanel
    private JButton hitPopupButton;   // if change, change FlatPanel.paintGroupBlk						
    private String[] hitOptions = {"\u2265 0 hits", "\u2265 15 hits", "\u2265 25 hits", 
    		                      "\u2265 50 hits", "\u2265 100 hits","\u2265 200 hits"}; // >= as one char
    private JRadioButtonMenuItem hit0Radio	= new JRadioButtonMenuItem(hitOptions[0]);
    private JRadioButtonMenuItem hit1Radio	= new JRadioButtonMenuItem(hitOptions[1]);
    private JRadioButtonMenuItem hit2Radio	= new JRadioButtonMenuItem(hitOptions[2]);
    private JRadioButtonMenuItem hit3Radio	= new JRadioButtonMenuItem(hitOptions[3]);
    private JRadioButtonMenuItem hit4Radio	= new JRadioButtonMenuItem(hitOptions[4]);
    private JRadioButtonMenuItem hit5Radio	= new JRadioButtonMenuItem(hitOptions[5]);
    
    private JButton colorButton;  						
    private ColorDialogHandler cdh;
	private PersistentProps    persistentProps;
	
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
				"Options for display of blocks");
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
    	PopupListener listener = new PopupListener();
		len0Radio.addActionListener(listener);
		len1Radio.addActionListener(listener);
		len2Radio.addActionListener(listener);
		len3Radio.addActionListener(listener);
		len4Radio.addActionListener(listener);
		len5Radio.addActionListener(listener);
		len6Radio.addActionListener(listener);
		
    	JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBackground(symap.Globals.white);
		
		JMenuItem popupTitle = new JMenuItem("Block Length");
		popupTitle.setFont(new Font(popupTitle.getFont().getName(),Font.PLAIN,11));
		
		popupTitle.setEnabled(false);
		popupMenu.add(popupTitle);
		popupMenu.addSeparator();
		
		popupMenu.add(len0Radio); popupMenu.add(len1Radio); popupMenu.add(len2Radio);
		popupMenu.add(len3Radio); popupMenu.add(len4Radio);	popupMenu.add(len5Radio); popupMenu.add(len6Radio);
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(len0Radio); grp.add(len1Radio); grp.add(len2Radio); 
		grp.add(len3Radio); grp.add(len4Radio); grp.add(len5Radio); grp.add(len6Radio);
		
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
		len0Radio.setSelected(true);
    }
    private void createHitsPopup() {
    	PopupListener listener = new PopupListener();
		hit0Radio.addActionListener(listener);
		hit1Radio.addActionListener(listener);
		hit2Radio.addActionListener(listener);
		hit3Radio.addActionListener(listener);
		hit4Radio.addActionListener(listener);
		hit5Radio.addActionListener(listener);
		
    	JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBackground(symap.Globals.white);
		
		JMenuItem popupTitle = new JMenuItem("Number hits");
		popupTitle.setFont(new Font(popupTitle.getFont().getName(),Font.PLAIN,11));
		
		popupTitle.setEnabled(false);
		popupMenu.add(popupTitle);
		popupMenu.addSeparator();
		
		popupMenu.add(hit0Radio); popupMenu.add(hit1Radio); popupMenu.add(hit2Radio);
		popupMenu.add(hit3Radio); popupMenu.add(hit4Radio); popupMenu.add(hit5Radio);
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(hit0Radio); grp.add(hit1Radio); grp.add(hit2Radio); 
		grp.add(hit3Radio); grp.add(hit4Radio); grp.add(hit5Radio);
		
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
		hit0Radio.setSelected(true);
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
			if      (src==len0Radio) idx=0;
			else if (src==len1Radio) idx=1;
			else if (src==len2Radio) idx=2;
			else if (src==len3Radio) idx=3;
			else if (src==len4Radio) idx=4;
			else if (src==len5Radio) idx=5;
			else if (src==len6Radio) idx=6;
			
			if (idx!=-1) {
				flatPanel.lenChoice = idx;
				lenPopupButton.setText(lenOptions[idx]);
			}
			// View minimal hits
			idx = -1;
			if      (src==hit0Radio) idx=0;
			else if (src==hit1Radio) idx=1;
			else if (src==hit2Radio) idx=2;
			else if (src==hit3Radio) idx=3;
			else if (src==hit4Radio) idx=4;
			else if (src==hit5Radio) idx=5;
			
			if (idx!=-1) {
				flatPanel.hitChoice = idx;
				hitPopupButton.setText(hitOptions[idx]);
			}	
			setEnables();
			flatPanel.makeRepaint();
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
		lenPopupButton.setText(lenOptions[0]);	// CAS584 add len/hit to home
		hitPopupButton.setText(hitOptions[0]);
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
		Chromosome Name: o No o Yes 
		Chromosome Label: o Horizontal o Vertical
	 */
	private class ChgDisplay extends JDialog implements ActionListener {
		private static final long serialVersionUID = 1L;

		private JButton okButton, defButton, cancelButton, info3Button;
		
		private JCheckBox invBlkCheck, invStrokeCheck;
		private JCheckBox revRefCheck, revColCheck;
		private JCheckBox chrVertCheck;
		private JRadioButton allBlkRadio, invBlkRadio, notInvBlkRadio, twoBlkRadio;
		
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
			
			JLabel xxx = Jcomp.createLabel("xxxxxxxxxxxxxxxxx");
			int h=5, head=xxx.getPreferredSize().width;
			
			// Inverted blocks
			JLabel invLab = Jcomp.createLabel("Inverted Blocks", "Reverse lower coordinates for inverted blocks");
			JPanel row1 = Jcomp.createRowPanel();
			row1.add(invLab);       row1.add(Box.createHorizontalStrut(Jcomp.getWidth(head, invLab)));
			
			invBlkCheck = Jcomp.createCheckBox("Reverse orientation", "Reverse orientation of the lower species", true); 
			invBlkCheck.addActionListener(this);
			row1.add(invBlkCheck);  row1.add(Box.createHorizontalStrut(h)); 
			
			invStrokeCheck = Jcomp.createCheckBox("Outline", "Inverted blocks are outlined", true); 
			invStrokeCheck.addActionListener(this);
			if (bTest) row1.add(invStrokeCheck); 
			
			optionPanel.add(row1); optionPanel.add(Box.createVerticalStrut(3));
			optionPanel.add(Box.createVerticalStrut(3));
			
			// Display Blocks
			JLabel blkLab = Jcomp.createLabel("Display Blocks", "Change the display of the blocks");
			row1 = Jcomp.createRowPanel();
			row1.add(blkLab); 			row1.add(Box.createHorizontalStrut(Jcomp.getWidth(head, blkLab)));
			
			allBlkRadio = 		Jcomp.createRadio("All", 		"Show all blocks"); 
			notInvBlkRadio = 	Jcomp.createRadio("Non-Invert", "Show non-inverted blocks only");
			invBlkRadio = 		Jcomp.createRadio("Invert", 	"Show inverted blocks only"); 
			twoBlkRadio = 		Jcomp.createRadio("Two-color", 	"Show inverted/non-inverted in two colors");
			allBlkRadio.addActionListener(this); notInvBlkRadio.addActionListener(this);
			invBlkRadio.addActionListener(this); twoBlkRadio.addActionListener(this);
			
			ButtonGroup grp = new ButtonGroup ();
			grp.add(allBlkRadio); grp.add(notInvBlkRadio); grp.add(invBlkRadio); grp.add(twoBlkRadio); 
			allBlkRadio.setSelected(true); invChoiceSave=0;
				
			row1.add(allBlkRadio); 		row1.add(Box.createHorizontalStrut(h)); 
			row1.add(notInvBlkRadio); 	row1.add(Box.createHorizontalStrut(h));
			row1.add(invBlkRadio); 		row1.add(Box.createHorizontalStrut(h));
			row1.add(twoBlkRadio); 		row1.add(Box.createHorizontalStrut(h));
			optionPanel.add(row1); optionPanel.add(Box.createVerticalStrut(3));
			optionPanel.add(Box.createVerticalStrut(3));
	
			// Reverse reference
			JLabel refLab = Jcomp.createLabel("Swap Species", "Swap species or colors");
			row1 = Jcomp.createRowPanel();
			row1.add(refLab); row1.add(Box.createHorizontalStrut(Jcomp.getWidth(head, refLab)));
			
			revRefCheck = Jcomp.createCheckBox("Species", "Put 2nd species on top", false); 
			revRefCheck.addActionListener(this);
			revColCheck = Jcomp.createCheckBox("Species&Colors", "Put 2nd species with colors on top", false); 
			revColCheck.addActionListener(this);
			
			row1.add(revRefCheck); row1.add(Box.createHorizontalStrut(h)); 
			row1.add(revColCheck);
			optionPanel.add(row1); optionPanel.add(Box.createVerticalStrut(3));
			optionPanel.add(Box.createVerticalStrut(3));
			
			// Inverted blocks
			JLabel txtLab = Jcomp.createLabel("Chr labels", "Switch chromosome labels from horizontal to vertical ");
			row1 = Jcomp.createRowPanel();
			row1.add(txtLab);       row1.add(Box.createHorizontalStrut(Jcomp.getWidth(head, txtLab)));
			
			chrVertCheck = Jcomp.createCheckBox("Vertical", "Display chromosome labels vertically", false); 
			chrVertCheck.addActionListener(this);
			row1.add(chrVertCheck);  row1.add(Box.createHorizontalStrut(h)); // Added in CAS854 but not working
		
			if (bTest) {
				optionPanel.add(row1); optionPanel.add(Box.createVerticalStrut(3));
				optionPanel.add(Box.createVerticalStrut(3));
			}		
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
			JPanel row = new JPanel(); row.setBackground(Color.white);
			row.add(okButton);
			row.add(cancelButton); 
			row.add(defButton); 
			row.add(info3Button);
			
			/******** Grid ********************************/
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
			
			else if (src==invBlkCheck) 		flatPanel.bInvBlk 		= invBlkCheck.isSelected();
			else if (src==invStrokeCheck) 	flatPanel.bInvStroke 	= invStrokeCheck.isSelected();
			else if (src==chrVertCheck)		flatPanel.bChrVert 		= chrVertCheck.isSelected();
			
			else if (src==revRefCheck) 	{
				flatPanel.reverseRef(revRefCheck.isSelected(), false); 		// ref, colors; 
				revColCheck.setEnabled(!revRefCheck.isSelected());
			}
			else if (src==revColCheck) 	{
				flatPanel.reverseRef(revColCheck.isSelected(), revColCheck.isSelected()); // both T or both F
				revRefCheck.setEnabled(!revColCheck.isSelected());
			}
			
			else if (src==allBlkRadio)		flatPanel.invChoice = 0;
			else if (src==notInvBlkRadio) 	flatPanel.invChoice = 1;
			else if (src==invBlkRadio)		flatPanel.invChoice = 2;
			else if (src==twoBlkRadio)		flatPanel.invChoice = 3;
		
			else return;
			
			flatPanel.makeRepaint();
		}
		private void okAndCloseAction() { // action already done; save
			bInvBlkSave 	= invBlkCheck.isSelected();
			bInvStrokeSave 	= invStrokeCheck.isSelected();
			bChrVertSave 	= chrVertCheck.isSelected();
			
			bRevRefSave = revRefCheck.isSelected(); 
			bRevColSave = revColCheck.isSelected(); 
			
			if (allBlkRadio.isSelected()) 			invChoiceSave=0;
			else if (notInvBlkRadio.isSelected()) 	invChoiceSave=1;
			else if (invBlkRadio.isSelected()) 		invChoiceSave=2;
			else if (twoBlkRadio.isSelected()) 		invChoiceSave=3;
			
			setVisible(false); 
		}
		private void cancelAndCloseAction() {
			flatPanel.bInvBlk = bInvBlkSave;   	// Blocks
			invBlkCheck.setSelected(bInvBlkSave);
			
			flatPanel.bInvStroke = bInvStrokeSave;
			invStrokeCheck.setSelected(bInvStrokeSave);
			
			flatPanel.bChrVert = bChrVertSave;
			chrVertCheck.setSelected(bChrVertSave);
			
			if (bRevColSave!=flatPanel.bRevCol) { // Swap
				flatPanel.reverseRef(bRevColSave, bRevColSave); 
				revColCheck.setSelected(bRevColSave); 
				revRefCheck.setEnabled(!bRevColSave);
			}
			else if (bRevRefSave!=flatPanel.bRevRef) {
				flatPanel.reverseRef(bRevRefSave, false); 
				revRefCheck.setSelected(bRevRefSave); 
				revColCheck.setEnabled(!bRevRefSave);
			}
				
			flatPanel.invChoice = invChoiceSave;
			if (invChoiceSave==0) 		allBlkRadio.setSelected(true);
			else if (invChoiceSave==1) 	invBlkRadio.setSelected(true);
			else if (invChoiceSave==2) 	notInvBlkRadio.setSelected(true);
			else if (invChoiceSave==3) 	twoBlkRadio.setSelected(true);
			
			setVisible(false); 
		}
		private void setDefaults() {	
			flatPanel.bInvBlk = true;  
			invBlkCheck.setSelected(true);
			
			flatPanel.bChrVert = false;
			chrVertCheck.setSelected(true);
			
			if (flatPanel.bRevRef || flatPanel.bRevCol) flatPanel.reverseRef(false, false); // flip
			revRefCheck.setSelected(false); revColCheck.setSelected(false);
			revRefCheck.setEnabled(true);   revColCheck.setEnabled(true);
			
			flatPanel.invChoice = 0; allBlkRadio.setSelected(true);
		}
		private void popupHelp3() {
			String msg = "Inverted block: By default, the inverted blocks have reversed orientation.\n" 
					   + "   If this is hard to view, uncheck reverse orientation --\n"
					   + "   using Two-Color will show the inversions by color.\n\n"
					   + "Display Blocks: Show all, inverted only, non-inverted only, or two-color, respectively.\n"
					   + "   Two-color shows the inverted in one color and non-inverted in another.\n\n"
					   + "Swap Reference: Only one of the two checkboxes can be checked.\n"
					   + "   Species: Will swap the species name and chromosomes.\n"
					   + "   Species&Colors: Will swap the species and colors.\n"
					;
			Popup.displayInfoMonoSpace(this, "Quick Linear Help", msg, false);
		}
		// These are set on Save only; used in Cancel
		private boolean bInvBlkSave=true, bInvStrokeSave=false;
		private boolean bRevRefSave=false, bRevColSave=false, bChrVertSave=false;;
		private int invChoiceSave=0;
	}
	private void addToGrid(Container c, GridBagLayout gbl, GridBagConstraints gbc, Component comp, int i) {
		gbc.gridwidth = i;
		gbl.setConstraints(comp,gbc);
		c.add(comp);
	}
}
