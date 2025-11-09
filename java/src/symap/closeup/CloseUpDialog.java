package symap.closeup;

import java.awt.Container;
import java.awt.Font;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseEvent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JDialog;
import java.sql.SQLException;
import java.util.Vector;

import colordialog.ColorListener;
import symap.sequence.Sequence;
import symap.sequence.Annotation;
import symap.mapper.HitData;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ErrorReport;

/*************************************************
 * The panel for displaying the alignment, with CloseupComponent on top and TextComponent on bottom;
 * called from CloseUp, which is called by SyMAP2d
 * Gets hits and genes in region, and builds the HitAlignment and GeneAlignment for display
 */
public class CloseUpDialog extends JDialog implements  ColorListener, HelpListener {
	private static final long serialVersionUID = 1L;
	private AlignPool alignPool;
	private CloseUpComponent viewComp;
	private JScrollPane blastScroll, viewScroll;
	private TextComponent textComp;
	private HitAlignment selectedHa; 
	private HelpBar helpBar; 
	private final int MAX_WIDTH=1500;
	private String projS, projO; // selected and other

	// hitList will have at least ONE hit
	protected CloseUpDialog(CloseUp closeup, Vector <HitData> hitList, Sequence seqObj, 
			int selStart, int selEnd, boolean isProj1, String otherChr, int numShow) throws SQLException {
		alignPool = new AlignPool(closeup.getDrawingPanel().getDBC()); 
		projS = seqObj.getProjectDisplayName();
		projO = seqObj.getOtherProjectName();
		initView();
		
		setView(seqObj, hitList, selStart, selEnd, isProj1);
		
		String d = SeqData.coordsStr(selStart, selEnd);
		String other = projO + " " + otherChr;
		String x = String.format("Selected Region: %s  %s  %s to %s", projS, seqObj.getChrName(), d, other); 
		setTitle(x);
	
		util.Jcomp.setFullSize(this,viewComp, MAX_WIDTH); 
		if (numShow==1) setLocationRelativeTo(null);
	}
	
	protected int showIfHits() {
		if (viewComp==null) return -1;
		
		int h = viewComp.getNumberOfHits();
		
		if (h > 0) setVisible(true); 
		return h;
	}
	private void initView() {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		viewComp = new CloseUpComponent();
		textComp = new TextComponent(projS, projO);
		
		helpBar = new HelpBar(-1, 17); 
		helpBar.addHelpListener(viewComp,this);
		helpBar.addHelpListener(textComp,this);

		viewComp.addCloseUpListener(this);

		viewScroll  = new JScrollPane(viewComp,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		blastScroll = new JScrollPane(textComp,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		viewScroll.getViewport().setBackground(viewComp.getBackground());
		blastScroll.getViewport().setBackground(textComp.getBackground());

		Container cp = getContentPane();
		cp.setLayout(new BorderLayout());
		cp.add(viewScroll,BorderLayout.NORTH);
		cp.add(blastScroll,BorderLayout.CENTER);
		cp.add(helpBar, BorderLayout.SOUTH); 
	}
	
	// the subhits and exons are only shown if they are within or overlap the selected boundary
	protected void setView(Sequence src, Vector <HitData> hitList, int sstart, int send, boolean isProj1) throws SQLException 
	{
	try {
		HitAlignment[] hitAlignArr = alignPool.buildHitAlignments(hitList, isProj1, projS, projO, sstart, send);
		
		GeneAlignment[] geneAlignArr = buildGeneAlignments(src, sstart, send);
		
		// compute the start and end of graphics; all hits within sstart/send
		int sHit = Integer.MAX_VALUE;
		int eHit = Integer.MIN_VALUE;
		for (HitAlignment h : hitAlignArr) {
			sHit = Math.min(sHit, h.getSstart());
			eHit = Math.max(eHit, h.getAend()); // start+width
		}
		
		int sEx = Integer.MAX_VALUE;
		int eEx = Integer.MIN_VALUE;
		for (GeneAlignment gn : geneAlignArr) {
			if (gn.getGmax() < sHit || gn.getGmin()>eHit) {
				gn.setNoShow();
				continue; 
			}
			
			gn.setDisplayExons(sstart, send); //  set exons within selected
			
			sEx = Math.min(sEx, gn.getEmin());
			eEx = Math.max(eEx, gn.getEmax());
		}	
		int startGr = Math.min(sHit, sEx) - 10; // a little padding
		int endGr  =  Math.max(eHit, eEx) + 10;
		
		viewComp.setData(startGr, endGr, geneAlignArr, hitAlignArr);
		
		selectedHa = hitAlignArr[0]; 
		hitAlignArr[0].setSelected(true);
		hitClicked(hitAlignArr[0]);
	}
	catch (Exception e) {ErrorReport.print(e, "Closeup: determining hits and exons to show");}
	}
	
	private GeneAlignment[] buildGeneAlignments(Sequence seq, int sstart, int send) {
		Vector<Annotation> annoVec =  seq.getAnnoGene(sstart, send);
		
		Vector<GeneAlignment> alignments = new Vector<GeneAlignment>();
		for (Annotation a : annoVec) {
			Vector<Annotation> exons = seq.getAnnoExon(a.getAnnoIdx()); // get specific exons
			
			GeneAlignment ga = new GeneAlignment(
					a.getCloseUpDesc(), a.getStart(), a.getEnd(), a.isStrandPos(), exons);
			
			if (!alignments.contains(ga)) // in case database has redundant entries (as with Rice)
				alignments.add(ga);
		}
		
		return alignments.toArray(new GeneAlignment[0]);
	}
	public void resetColors() {
		textComp.setBackground(TextComponent.backgroundColor);
		blastScroll.getViewport().setBackground(TextComponent.backgroundColor);
		textComp.repaint();
		viewComp.setBackground(CloseUpComponent.backgroundColor);
		viewScroll.getViewport().setBackground(CloseUpComponent.backgroundColor);
		viewComp.repaint();
	}

	// modified to handle color change
	protected void hitClicked(HitAlignment ha) {
		if (ha != null) {
			if (selectedHa != null) selectedHa.setSelected(false);
			ha.setSelected(true); 
			selectedHa = ha;
			viewComp.repaint(); // Repaint to show color change
			textComp.setAlignment(ha);
			getContentPane().validate();
		}
	}

	public String getHelpText(MouseEvent e) {
		if (e.getSource() == viewComp) 		return "Click on a hit to select it and view the base alignment.";
		else if (e.getSource() == textComp) return "Highlight text and press CTRL-C to copy.";
		else return null;
	}
	/***************************************************
	 * Draws the text area at bottom of display with alignment
	 * CAS575 cleanup; was its own file
	 */
	private class TextComponent extends JTextArea {
		private static final long serialVersionUID = 1L;
		private static final Color backgroundColor = Color.white;
		private static Font sequenceFont = null;

		private HitAlignment alignment;
		
		private TextComponent(String proj1, String proj2) {
			super();
			setEditable(false);	
			if (sequenceFont == null) sequenceFont = new Font(Font.MONOSPACED,0,16);
			
			setFont(sequenceFont);
			setBackground(backgroundColor);
			this.alignment = null;
		}
		private void setAlignment(HitAlignment ha) {
			alignment = ha;
			setVisible(false);
			selectAll();
			replaceRange(alignment.toString(), getSelectionStart(), getSelectionEnd());
			setVisible(true);
		}
	}
}
