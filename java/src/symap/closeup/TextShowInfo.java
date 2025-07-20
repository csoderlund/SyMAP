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
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
	
	private AlignPool alignPool;
	private HitAlignment[] hitAlignArr=null;
	private JCheckBox ntCheckBox;
	private JButton alignHitButton, orderButton, mergeButton, removeButton;
	
	// for Gene popup
	private Annotation annoDataObj=null;
	
	private JButton closeButton;
	
	/***************************************************
	 * 2D Gene info; CAS560 make separate from hit
	 * Called by Annotation.java
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
		
		closeButton = Jcomp.createButton(Jcomp.ok, "Close window"); 
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
			boolean isQuery, boolean isInv, boolean bSort) {
		
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
		
		String gene1 = (aObj1!=null) ? "   #" + aObj1.getFullGeneNum() : "";	
		String gene2 = (aObj2!=null) ? "   #" + aObj2.getFullGeneNum() : "";
	
		boolean bMerge = title.startsWith(titleMerge);
		boolean bOrder = title.startsWith(titleOrder);
		boolean bRemove = title.startsWith(titleRemove);
		
		String name1 = String.format("%-16s %s", proj1, gene1);// CAS570 removed () and line up gene#
		String name2 = String.format("%-16s %s", proj2, gene2);
		
		/** Text - the tables and info **/
		String table1 = SeqDataInfo.formatHit(Globals.Q, name1, aObj1, queryHits, title, false, false);
		int cntNegGap = SeqDataInfo.cntMergeNeg;
		
		String table2 = SeqDataInfo.formatHit(Globals.T, name2, aObj2, targetHits, title, isInv, bSort);
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
		
		closeButton = Jcomp.createButton(Jcomp.ok, "Close window"); 
		closeButton.addActionListener(this);
		
		// Hit only
		alignHitButton = Jcomp.createButton("Align", "Popup window of text alignment");
		alignHitButton.addActionListener(this);
		ntCheckBox = new JCheckBox("NT", true); 	// not shown, was going to do AA align...
		
		orderButton = Jcomp.createButton(titleOrder, "Order disordered hits(*)");
		orderButton.addActionListener(this);
		
		removeButton = Jcomp.createButton(titleRemove, "Remove disordered hits(*)");
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
		setAlwaysOnTop(true); 						
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
			runAlign(true); 						     // new align popup
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
	/**********************************************
	 * Specialized displayInfoMonoSpace
	 */
	private void displayAlign(String title, String theAlign, boolean isFirst) {
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
		JButton revButton = Jcomp.createButton("Reverse", "Align the reverse complement sequence"); 
		revButton.addActionListener(new ActionListener() {
	   		public void actionPerformed(ActionEvent arg0) {
	   			revButton.setEnabled(false); // the data structures is reused and changed
	   			reverseAlign();
	   		}
		}); 
		JButton trimButton = Jcomp.createButton("No trim", "Align without trimming"); 
		trimButton.addActionListener(new ActionListener() {
	   		public void actionPerformed(ActionEvent arg0) {
	   			trimButton.setEnabled(false); // the data structures is reused and changed
	   			runAlign(false);
	   		}
		}); 
		
		JPanel buttonPanel = new JPanel(); buttonPanel.setBackground(Color.white); // will not center if use Jcomp 
		if (isFirst) {
			buttonPanel.add(revButton); buttonPanel.add(Box.createHorizontalStrut(5));
			buttonPanel.add(trimButton); buttonPanel.add(Box.createHorizontalStrut(5));
		}
		buttonPanel.add(okButton); 
		
		diag.getContentPane().setLayout(new BorderLayout());
		diag.getContentPane().add(sPane,BorderLayout.CENTER);
		diag.getContentPane().add(buttonPanel,BorderLayout.SOUTH);
		diag.pack();
		
		diag.setModal(false); // true - freeze other windows
		diag.setResizable(true);
		Dimension d = new Dimension (600, 450); // width, height
		if (!isFirst) d = new Dimension (600, 420); // goes on top of align, make the other show
		if (diag.getWidth() >= d.width || diag.getHeight() >= d.height) diag.setSize(d);
		diag.setLocationRelativeTo(null);
		//diag.setAlwaysOnTop(true);
		diag.setVisible(true);	
	}
	/****************************************************************/
	private void runAlign(boolean bTrim) {
		try {
			boolean isNT = ntCheckBox.isSelected();
			Vector <HitData> hitList = new Vector <HitData> ();
			hitList.add(hitDataObj);
			
			// Run Align 
			hitAlignArr = alignPool.buildHitAlignments(hitList, isNT, isQuery, bTrim);
			
			String p1 = proj1.substring(0, proj1.indexOf(" "));
			String p2 = proj2.substring(0, proj2.indexOf(" "));
			
			int cnt=0;
			Vector <String> lines = new Vector <String> ();
			for (HitAlignment hs : hitAlignArr) {
				if (cnt>0) lines.add("_______________________________________________________________________");
				String desc = (isNT) ? hs.toText(false, p1, p2) : hs.toTextAA(p1, p2);
				String [] toks = desc.split("\t");
				lines.add(toks[0]); // Block Hit#
				lines.add(toks[1]); // %Id
				
				addAlign(lines, toks[2], toks[3], toks[4], hs.getCoords());
				
				cnt++;
			}
			String msg="";
			for (String l : lines) msg += l + "\n";
			 
			String x = (bTrim) ? "Align trim " : "Align";
			String title =  x + hitDataObj.getName() + "; " + proj1 + " to " + proj2;
			displayAlign(title, msg, bTrim);	// true says put Reverse/Trim buttons on bottom
		} 
		catch (Exception e) {ErrorReport.print(e, "run align");}
	}
	private void reverseAlign() {
		hitAlignArr = alignPool.getHitReverse(hitAlignArr);
		
		int cnt=0;
		String p1 = proj1.substring(0, proj1.indexOf(" "));
		String p2 = proj2.substring(0, proj2.indexOf(" "));
		
		Vector <String> lines = new Vector <String> ();
		for (HitAlignment hs : hitAlignArr) {
			if (cnt>0) lines.add("_______________________________________________________________________");
			String desc = hs.toText(false, p1, p2);
			String [] toks = desc.split("\t");
			lines.add(toks[0]); // Block Hit#
			lines.add(toks[1]); // %Id
			
			addAlign(lines, toks[2], toks[3], toks[4], hs.getCoords());
			
			cnt++;
		}
		String msg="";
		for (String l : lines) msg += l + "\n";
		 
		String title =  "Reverse Align " + hitDataObj.getName() + "; " + proj1 + " to " + proj2;
		displayAlign(title, msg, false);
	}
	
	/*************************************************************
	 * toggle all hits and merged hits;
	 */
	private void runMerge() {
		String queryShow  = SeqDataInfo.calcMergeHits(Globals.Q, queryHits, false);
		String targetShow = SeqDataInfo.calcMergeHits(Globals.T, targetHits, isInvHit);
		
		new TextShowInfo(alignPool, hitDataObj, titleMerge + " " + title, 
				theInfo, "", st1LTst2, proj1, proj2, aObj1, aObj2, 
				queryShow, targetShow, isQuery, isInvHit, true); 
	}
	/*************************************************************
	 * retain target ordered by query - the '#' will be out-of-order;
	 */
	private void runOrder() { 	
		new TextShowInfo(alignPool, hitDataObj, titleOrder + " " + title, 
				theInfo, "", st1LTst2, proj1, proj2, aObj1, aObj2, 
				queryHits, targetHits, isQuery, isInvHit, false /* keep order */); 
	}
	/*************************************************************
	 * run after runOrder to remove disordered hits; CAS560 add
	 */
	private void runRemove() {
		String [] sort = SeqDataInfo.calcRemoveDisorder(queryHits, targetHits, isInvHit);
		
		new TextShowInfo(alignPool, hitDataObj, titleRemove + " " + title, 
				theInfo, "", st1LTst2, proj1, proj2, aObj1, aObj2, 
				sort[Globals.Q], sort[Globals.T], isQuery, isInvHit, false /* keep order */); 
	}
}
