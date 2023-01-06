package symap.closeup;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JCheckBox;

import symap.drawingpanel.DrawingPanel;
import symap.mapper.HitData;
import util.ErrorReport;

/*************************************************************************
 * Called from SeqHits.PseudoHit,  Sequence.Annotation, util.TextBox
 * Show Info:  When a hit-wire or gene is right-clicked, this is called to show its info.
 * Show Align: For hit-wire, it also has an "Align" button
 */
public class TextShowInfo extends JDialog implements ActionListener {
	private static final long serialVersionUID = 1L;
	private boolean bGene=false; // Fix gene align for next release
	
	private AlignPool alignPool;
	private HitData hitDataObj;
	private boolean isGene2=false;
	private String  proj1, proj2, title1, title2;
	private boolean isQuery=true;
	
	private JButton okButton, alignHitButton, alignGeneButton;
	private JCheckBox ntCheckBox;
	
	private HitAlignment[] hitAlignArr=null;
	
	// For gene - no align
	public TextShowInfo (Component parentFrame, String title, String theInfo) {
		new TextShowInfo( parentFrame, title, theInfo, null, null, null, null, null, null, true);
	}
	// for hit - with align
	public TextShowInfo (Component parentFrame, String title, String theInfo,
			DrawingPanel dp, HitData hitDataObj, String title1, String title2, String proj1, String proj2, boolean isQuery) {
		
		super();
		setModal(false);
		setTitle(title); 
		setResizable(true);
		
		if (hitDataObj!=null) {
			alignPool = dp.getPools().getAlignPool();
			this.hitDataObj = hitDataObj;
			isGene2 = hitDataObj.is2Gene();
			this.proj1 = proj1;
			this.proj2 = proj2;
			this.title1 = title1;
			this.title2 = title2;
			this.isQuery = isQuery;
		}
		JTextArea messageArea = new JTextArea(theInfo);
		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		okButton = new JButton("OK"); okButton.setBackground(Color.white);
		okButton.addActionListener(this);
		alignHitButton = new JButton("Align Hit"); alignHitButton.setBackground(Color.white);
		alignHitButton.addActionListener(this);
		alignGeneButton = new JButton("Exon"); alignGeneButton.setBackground(Color.white);
		alignGeneButton.addActionListener(this);
		ntCheckBox = new JCheckBox("NT", true);
		
		JPanel buttonPanel = new JPanel();
		if (hitDataObj!=null) {
			buttonPanel.add(alignHitButton);	
			if (isGene2 && bGene) {
				buttonPanel.add(ntCheckBox); // need 6-frame for AA-hit
				buttonPanel.add(alignGeneButton);
			}
		}
		buttonPanel.add(okButton);
	
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(sPane,BorderLayout.CENTER);
		getContentPane().add(buttonPanel,BorderLayout.SOUTH);
		pack();
		
		Dimension d = new Dimension (330, 200); 
		if (getWidth() >= d.width || getHeight() >= d.height) setSize(d);
		setLocationRelativeTo(null);	
		//setAlwaysOnTop(true);
		setVisible(true);
	}
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == okButton) setVisible(false); 
		else if (e.getSource() == alignHitButton) runAlign(); 
	}
	/**************************************************************/
	private void runAlign() {
	try {
		boolean isNT = ntCheckBox.isSelected();
		Vector <HitData> hitList = new Vector <HitData> ();
		hitList.add(hitDataObj);
		if (AlignPool.CDEBUG) System.out.println(proj1 + " " + proj2 + " " + isQuery);
		
		hitAlignArr = alignPool.getHitAlignments(hitList, isNT, isQuery);
		
		int cnt=0;
		Vector <String> lines = new Vector <String> ();
		for (HitAlignment hs : hitAlignArr) {
			if (cnt>0) lines.add("_______________________________________________________________________");
			String desc = (isNT) ? hs.toText(false, proj1, proj2) : hs.toTextAA(proj1, proj2);
			String [] toks = desc.split("\t");
			lines.add(toks[0]); // Block Hit#
			lines.add(toks[1]); // %Id
			
			addAlign(lines, toks[2], toks[3], toks[4], hs.getCoords());
			
			cnt++;
		}
		String msg="";
		for (String l : lines) msg += l + "\n";
		 
		String title =  "Align " + hitDataObj.getName() + "; " + title1 + " to " + title2;
		displayAlign(title, msg, true);
		
	} catch (Exception e) {ErrorReport.print(e, "run align");}
	}
	/**************************************************************/
	private void addAlign(Vector <String> lines, String aSeq1, String aSeqM, String aSeq2, int [] coords) {
	try {
		int nStart1=coords[0], nStart2=coords[2], nEnd=aSeq1.length();
		
		// create info for left label
		String tokS = HitAlignment.tokS;
		String tokO = HitAlignment.tokO;
		
		int maxC = Math.max(coords[1], coords[3]);
		int sz = (maxC+"").length();
		String formatC =  "%s " + "%" + sz + "d  "; // S Coord
		String formatM =  "%s " + "%" + sz + "s  ";
		
		int inc = 60; // output in rows of 60
		int x;
		StringBuffer sb = new StringBuffer (inc);
		
		for (int offset=0; offset<nEnd; offset+=inc) {
			lines.add("");
			sb.append(String.format(formatC, tokS, nStart1)); 
			for (x=0; x<inc && (x+offset)<nEnd; x++) sb.append(aSeq1.charAt(x+offset));
			sb.append("  " + (nStart1+x));
			lines.add(sb.toString());
			sb.delete(0, sb.length());
			
			sb.append(String.format(formatM, " "," "));
			for (int i=0; i<inc && (i+offset)<nEnd; i++) sb.append(aSeqM.charAt(i+offset));
			lines.add(sb.toString());
			sb.delete(0, sb.length());
			
			sb.append(String.format(formatC, tokO, nStart2));
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
		
		JButton okButton = new JButton("OK"); okButton.setBackground(Color.white);
		okButton.addActionListener(new ActionListener() {
	   		public void actionPerformed(ActionEvent arg0) {
	   			diag.setVisible(false);
	   		}
		});  
		JButton revButton = new JButton("Reverse"); okButton.setBackground(Color.white);
		revButton.addActionListener(new ActionListener() {
	   		public void actionPerformed(ActionEvent arg0) {
	   			revButton.setEnabled(false); // the data structures is reused and changed
	   			reverseAlign();
	   		}
		}); 
		JPanel buttonPanel = new JPanel(); buttonPanel.setBackground(Color.white);
		if (isFirst) buttonPanel.add(revButton); 
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

	private void reverseAlign() {
		hitAlignArr = alignPool.getHitReverse(hitAlignArr);
		
		int cnt=0;
		Vector <String> lines = new Vector <String> ();
		for (HitAlignment hs : hitAlignArr) {
			if (cnt>0) lines.add("_______________________________________________________________________");
			String desc = hs.toText(false, proj1, proj2);
			String [] toks = desc.split("\t");
			lines.add(toks[0]); // Block Hit#
			lines.add(toks[1]); // %Id
			
			addAlign(lines, toks[2], toks[3], toks[4], hs.getCoords());
			
			cnt++;
		}
		String msg="";
		for (String l : lines) msg += l + "\n";
		 
		String title =  "Reverse Align " + hitDataObj.getName() + "; " + title1 + " to " + title2;
		displayAlign(title, msg, false);
	}
}
