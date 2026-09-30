package symap.closeup;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.JCheckBox;

import symap.Globals;
import symap.mapper.HitData;
import symap.sequence.Annotation;
import util.ErrorReport;
import util.Jcomp;

/*************************************************************************
 * Called from 2D SeqHits.PseudoHit,  Sequence.Annotation, util.TextBox
 * Show Info:  When a hit-wire or gene is right-clicked, this is called to show its info.
 * Show Align: For hit-wire, it also has an "Align" button
 */
public class TextShowInfo extends JDialog implements ActionListener {
	private static final long serialVersionUID = 1L;
	
	// also used by SeqDataInfo
	protected static final String disOrder = "#"; 		
	protected static final String titleMerge = "Merged", buttonMerge = "Merge"; 
	protected static final String titleOrder = "Order", titleRemove = "Remove" + disOrder; // Remove is -ii only, doesn't work
	protected static final String totalMerge = "Total"; // Merge has total
	
	// For hit popup
	private HitData hitDataObj=null;
	private String  title, theInfo, proj1, proj2,  queryHits, targetHits;
	private Annotation aObj1, aObj2;
	private boolean isQuery;    // isQuery for runAlign;
	private boolean st1LTst2;   // T query on left; F query on right
	private boolean isInvHit;	// !strands (but the block may be Inv)
	private boolean bLoadBlast; // disable aligning hits
	
	private AlignPool alignPool;
	
	private JButton alignHitButton, orderButton, mergeButton, removeButton;
	
	// for Gene popup
	private Annotation annoDataObj=null;
	
	private JButton closeButton;
	
	/***************************************************
	 * 2D Gene info. Called by Annotation.java
	 */
	public TextShowInfo (Component parentFrame, String title, String theInfo, Annotation annoDataObj) {
		super();
		setModal(false);
		setTitle(title); 
		setResizable(true);
		
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {annoDataObj.setIsPopup(false);}
		});
		this.annoDataObj = annoDataObj;
		
		JTextArea messageArea = new JTextArea(theInfo);
		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		closeButton = Jcomp.createButton(Jcomp.close, "Close window"); 
		closeButton.addActionListener(this);
		
		JPanel buttonPanel = new JPanel(); buttonPanel.setBackground(Color.white);
		buttonPanel.add(closeButton);
	
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(sPane,BorderLayout.CENTER);
		getContentPane().add(buttonPanel,BorderLayout.SOUTH);
		pack();
		
		Dimension d = new Dimension (330, 200); 
		if (getWidth() >= d.width || getHeight() >= d.height) setSize(d);
		setAlwaysOnTop(true); 										// doesn't work on Ubuntu
		setLocationRelativeTo(null);	
		setVisible(true);
	}
	/***************************************************
	 * 2D Hit info;
	 */
	public TextShowInfo (AlignPool alignPool, HitData hitDataObj, String title, String theInfo, String trailer,
			boolean st1LTst2, String proj1, String proj2, Annotation aObj1, Annotation aObj2, String queryHits, String targetHits, 
			boolean isQuery, boolean isInv, boolean bSort, boolean bLoadBlast) {
		
		super();
		setModal(false);
		setTitle(title); 
		setResizable(true);
		
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {hitDataObj.setIsPopup(false);}
		});
	
		this.alignPool =  alignPool;	this.hitDataObj = hitDataObj;
		this.title = title;				this.theInfo = theInfo;
		this.proj1 = proj1;				this.proj2 = proj2;
		this.aObj1 = aObj1;				this.aObj2 = aObj2;  
		this.queryHits = queryHits;		this.targetHits = targetHits;
		this.isQuery = isQuery;			this.st1LTst2 = st1LTst2;	this.isInvHit = isInv; 
		this.bLoadBlast = bLoadBlast;
		
		String gene1 = (aObj1!=null) ? "   #" + aObj1.getFullGeneNum() : "";	
		String gene2 = (aObj2!=null) ? "   #" + aObj2.getFullGeneNum() : "";
	
		boolean bMerge = title.startsWith(titleMerge);
		boolean bOrder = title.startsWith(titleOrder);
		boolean bRemove = title.startsWith(titleRemove);
		
		String name1 = String.format("%-16s %s", proj1, gene1);
		String name2 = String.format("%-16s %s", proj2, gene2);
		
		/** Text - the tables and info **/
		String table1 = SeqDataInfo.formatHit(Globals.Q, name1, aObj1, queryHits, title, false, false, bLoadBlast);
		int cntNegGap = SeqDataInfo.cntMergeNeg;
		
		String table2 = SeqDataInfo.formatHit(Globals.T, name2, aObj2, targetHits, title, isInv, bSort, bLoadBlast);
		cntNegGap += SeqDataInfo.cntMergeNeg;
		
		// theInfo
		theInfo +=  st1LTst2 ? ("\nL " + table1+ "\nR " + table2) : ("\nL " + table2+"\nR " + table1);
		theInfo += trailer; // if -ii, contains indices
		
		if (bMerge && (table1.contains(totalMerge) || table2.contains(totalMerge)))  // no total in only 1 merged hit
			theInfo += "\nThe merged hits are not 1-to-1.";
		
		boolean bDisorder = (SeqDataInfo.cntDisorder>0);	
	
		if (bDisorder) {
			if (bOrder) theInfo += "\n" + disOrder  + disOrder + " Disordered subhits";
			else theInfo += "\n Disordered # column (same # subhits align)";
		}
		/*****/
		
		JTextArea messageArea = new JTextArea(theInfo);
		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		closeButton = Jcomp.createButton(Jcomp.close, "Close window"); 
		closeButton.addActionListener(this);
		
		// Hit only
		alignHitButton = Jcomp.createButton("Align...", "Popup window of text alignment");
		alignHitButton.addActionListener(this);
		
		orderButton = Jcomp.createButton(titleOrder, "Order disordered hits(#)");// was (*) CAS587
		orderButton.addActionListener(this);
		
		removeButton = Jcomp.createButton(titleRemove, "Remove disordered hits(#)");
		removeButton.addActionListener(this);
		
		mergeButton = Jcomp.createButton(buttonMerge, "Merge overlapping hits");
		mergeButton.addActionListener(this);
		
		JPanel buttonPanel = new JPanel(); buttonPanel.setBackground(Color.white);
		if (!bMerge) {
			if (cntNegGap>0 && bSort) 	buttonPanel.add(mergeButton); 
			if (bDisorder && !bOrder) 	buttonPanel.add(orderButton); 
			if (!bSort && !bRemove && Globals.INFO) buttonPanel.add(removeButton); 
			buttonPanel.add(alignHitButton); buttonPanel.add(Box.createHorizontalStrut(5));
		}
		buttonPanel.add(closeButton);
	
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(sPane,BorderLayout.CENTER);
		getContentPane().add(buttonPanel,BorderLayout.SOUTH);
		pack();
		
		Dimension d = new Dimension (350, 200); // w,h
		if (getWidth() >= d.width || getHeight() >= d.height) setSize(d);
		toFront(); //setAlwaysOnTop(true); 						
		setLocationRelativeTo(null);	
		setVisible(true);
	}
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == closeButton) {		
			if (hitDataObj!=null) hitDataObj.setIsPopup(false);
			else				  annoDataObj.setIsPopup(false);
			setVisible(false); 					// close popup
		}
		else if (e.getSource() == alignHitButton) {
			new Align(this); 						     // new align popup
		}
		else if (e.getSource() == orderButton) {
			runOrder(); 							// two hit popups will be shown 
		}
		else if (e.getSource() == mergeButton) {
			runMerge(); 							// two hit popups will be shown 
		}
		else if (e.getSource() == removeButton) {
			runRemove(); 							// two hit popups will be shown 
		}
	}
	
	/*************************************************************
	 * toggle all hits and merged hits;
	 */
	private void runMerge() {
		String queryShow  = SeqDataInfo.calcMergeHits(Globals.Q, queryHits, false);
		String targetShow = SeqDataInfo.calcMergeHits(Globals.T, targetHits, isInvHit);
		
		new TextShowInfo(alignPool, hitDataObj, titleMerge + " " + title, 
				theInfo, "", st1LTst2, proj1, proj2, aObj1, aObj2, 
				queryShow, targetShow, isQuery, isInvHit, true, bLoadBlast); 
	}
	/*************************************************************
	 * retain target ordered by query - the '#' will be out-of-order;
	 */
	private void runOrder() { 	
		new TextShowInfo(alignPool, hitDataObj, titleOrder + " " + title, 
				theInfo, "", st1LTst2, proj1, proj2, aObj1, aObj2, 
				queryHits, targetHits, isQuery, isInvHit, false /* keep order */, bLoadBlast); 
	}
	/*************************************************************
	 * run after runOrder to remove disordered hits
	 */
	private void runRemove() {
		String [] sort = SeqDataInfo.calcRemoveDisorder(queryHits, targetHits, isInvHit);
		
		new TextShowInfo(alignPool, hitDataObj, titleRemove + " " + title, 
				theInfo, "", st1LTst2, proj1, proj2, aObj1, aObj2, 
				sort[Globals.Q], sort[Globals.T], isQuery, isInvHit, false /* keep order */, bLoadBlast); 
	}
	/****************************************************************/
	private class Align extends JDialog {
		private static final long serialVersionUID = 1L;
		private boolean bGene=false, bTrim=true, bOkay=false;
		private TextShowInfo parent;
		private HitAlignment[] hitAlignArr=null;
		
		private Align(TextShowInfo obj) {
			parent = obj;
			createMenu();
			
			if (bOkay) {
				if (bGene) runGeneAlign();
				else runHitAlign();
			}
		}
		private void createMenu() {
			setModal(true);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			setTitle("Align options");
			
			JPanel page = Jcomp.createPagePanel();
			
			JRadioButton radHit = Jcomp.createRadio("Align hit sequences", "Align each subhit separately.");
			radHit.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bGene = false; 
				}
			});
			JRadioButton radGene = Jcomp.createRadio("Align genes sequences", "Align the spliced transcripts");
			radGene.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bGene = true;
				}
			});
			ButtonGroup rg = new ButtonGroup();
			rg.add(radHit); rg.add(radGene); 
			
			if (bLoadBlast) {// CAS589 add
				radHit.setEnabled(false);
				radGene.setSelected(true);
				bGene = true;
			}
			else {
				radHit.setSelected(true);
				bGene = false;
			}
			if (!hitDataObj.is2Gene()) radGene.setEnabled(false);
			
			JCheckBox chkTrim = Jcomp.createCheckBox("Trim", "Trim end bases that have no match", bTrim);
			chkTrim.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bTrim  = chkTrim.isSelected();
				}
			});
			
			page.add(Box.createVerticalStrut(5)); 	
			page.add(radHit); 		page.add(Box.createVerticalStrut(3));
			page.add(radGene); 		page.add(Box.createVerticalStrut(3));
			
			page.add(new JSeparator());
			page.add(chkTrim); 		page.add(Box.createVerticalStrut(5));	
			page.add(new JSeparator());
			
			// Buttons
			JPanel row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(5));
			JButton btnOK = Jcomp.createButton("Align", "Run align"); 	
	    	btnOK.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bOkay=true;
					setVisible(false);
				}
			});
	    	row.add(Box.createHorizontalStrut(5));
	    	row.add(btnOK); 				row.add(Box.createHorizontalStrut(5));
	    	
	    	JButton btnCancel = Jcomp.createButton(Jcomp.cancel, "Cancel align"); 
	    	btnCancel.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bOkay=false;
					setVisible(false);
				}
			});
	    	row.add(btnCancel); 			row.add(Box.createHorizontalStrut(5));
	    	
	    	JButton btnInfo = Jcomp.createBorderIconButton("/images/info.png", "Quick Help Popup");
			btnInfo.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					popupHelp();
				}
			});
			row.add(btnInfo); 			row.add(Box.createHorizontalStrut(5));
			
	    	page.add(row);
	    	page.add(Box.createVerticalStrut(5));
	    	
	    	page.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
	    	add(page);
	    	
			pack();
			setModal(true); // nothing else can happen until ok,cancel
			setAlwaysOnTop(true); //toFront();
			this.setResizable(false);
			setLocationRelativeTo(parent); 
			setVisible(true);
		}
		/****************************************************************/
		private void runGeneAlign() {
		try {
			// Run align
			HitAlignment hs = alignPool.buildGeneAlignments(hitDataObj, proj1, aObj1,proj2, aObj2, bTrim);
			
			// Format
			String g1 = "#" + aObj1.getFullGeneNum().trim(), g2 = "#" + aObj2.getFullGeneNum().trim();
			String title = "Gene " + proj1 + " " + g1 + " to " + proj2+ " " + g2;
			String line1 = "Gene " + proj1 + " " + g1 + "(" + aObj1.getAnnoType() + ") to "
					               + proj2 + " " + g2 + "(" + aObj2.getAnnoType() + ")";
				
			String result = hs.toText(false, g1, g2);
			String [] toks = result.split("\t");
		
			Vector <String> lines = new Vector <String> ();
			lines.add(line1);
			lines.add(toks[0]); // Align stats
			lines.add(toks[1]); // Proj & coords
			
			addAlign(lines, toks[2], toks[3], toks[4], hs.getCoords());// aseq1, aseq2, match
				
			String toStr="";
			for (String l : lines) toStr += l + "\n";
			 
			displayAlign(title, toStr);	
		} 
		catch (Exception e) {ErrorReport.print(e, "run align");}
		}
		/****************************************************************/
		private void runHitAlign() {
		try {
			// Run Align 
			Vector <HitData> hitList = new Vector <HitData> ();
			hitList.add(hitDataObj);
			hitAlignArr = alignPool.buildHitAlignments(hitList, true, isQuery, bTrim);
			
			// format
			String p1 = proj1.substring(0, proj1.indexOf(" ")); // proj1 has chromosome at end
			String p2 = proj2.substring(0, proj2.indexOf(" "));
			
			int cnt=0;
			Vector <String> lines = new Vector <String> ();
			for (HitAlignment hs : hitAlignArr) {
				if (cnt>0) lines.add("_______________________________________________________________________");
				String desc = hs.toText(false, p1, p2);
				String [] toks = desc.split("\t");
				lines.add(toks[0]); // Align Stats
				lines.add(toks[1]); // proj & coords
				
				addAlign(lines, toks[2], toks[3], toks[4], hs.getCoords());
				
				cnt++;
			}
			String toStr="";
			for (String l : lines) toStr += l + "\n";
			 
			String title =  hitDataObj.getName() + "; " + proj1 + " to " + proj2;
			
			displayAlign(title, toStr);	
		} 
		catch (Exception e) {ErrorReport.print(e, "run align");}
		}
		/**********************************************
		 * Specialized displayInfoMonoSpace
		 */
		private void displayAlign(String title, String theAlign) {
			JDialog diag = new JDialog();
			diag.setTitle(title);
			
			// scrollable selectable message area
			JTextArea messageArea = new JTextArea(theAlign);
			JScrollPane sPane = new JScrollPane(messageArea); 
			messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
			messageArea.setEditable(false);
			messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
			
			JButton okButton = Jcomp.createButton(Jcomp.ok, "Close window"); 
			okButton.addActionListener(new ActionListener() {
		   		public void actionPerformed(ActionEvent arg0) {
		   			diag.setVisible(false);
		   		}
			});  
			
			JPanel buttonPanel = new JPanel(); buttonPanel.setBackground(Color.white); // will not center if use Jcomp 
			buttonPanel.add(okButton); 
			
			diag.getContentPane().setLayout(new BorderLayout());
			diag.getContentPane().add(sPane,BorderLayout.CENTER);
			diag.getContentPane().add(buttonPanel,BorderLayout.SOUTH);
			diag.pack();
			
			diag.setModal(false); // true - freeze other windows
			diag.setResizable(true);
			Dimension d = new Dimension (600, 450); // width, height
			if (diag.getWidth() >= d.width || diag.getHeight() >= d.height) diag.setSize(d);
			diag.setLocationRelativeTo(parent);
			diag.toFront();
			diag.setVisible(true);	
		}
		/**************************************************************/
		private void addAlign(Vector <String> lines, String aSeq1, String aSeqM, String aSeq2, int [] coords) {
		try {
			int nStart1=coords[0], nStart2=coords[2], nEnd=aSeq1.length();
			
			int maxC = Math.max(coords[1], coords[3]);
			int sz = (maxC+"").length();
			String formatC =  "%" + sz + "d  "; 
			String formatM =  "%" + sz + "s  ";
			
			int inc = 60; // output in rows of 60
			int x;
			StringBuffer sb = new StringBuffer (inc);
			
			for (int offset=0; offset<nEnd; offset+=inc) {
				lines.add("");
				sb.append(String.format(formatC, nStart1)); 
				for (x=0; x<inc && (x+offset)<nEnd; x++) sb.append(aSeq1.charAt(x+offset));
				sb.append("  " + (nStart1+x));
				lines.add(sb.toString());
				sb.delete(0, sb.length());
				
				sb.append(String.format(formatM, " "," "));
				for (int i=0; i<inc && (i+offset)<nEnd; i++) sb.append(aSeqM.charAt(i+offset));
				lines.add(sb.toString());
				sb.delete(0, sb.length());
				
				sb.append(String.format(formatC, nStart2));
				for (x=0; x<inc && (x+offset)<nEnd; x++) sb.append(aSeq2.charAt(x+offset));
				sb.append("  " + (nStart2+x));
				lines.add(sb.toString());
				sb.delete(0, sb.length());
				
				nStart1+=inc;
				nStart2+=inc;
			}
		} catch (Exception e) {ErrorReport.print(e, "run align add align");}
		}
		
		private void popupHelp() {
			String msg = "Align hit sequences: Align each subhit separately.";
			msg += "\nAlign gene sequence: Align the spliced transcripts. ";
			msg += "\n\nSee ? for details.\n";
			util.Popup.displayInfoMonoSpace(this, "Quick Help", msg, false);
		}
	}
}
