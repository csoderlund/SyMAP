package symap.closeup;

import java.awt.Container;
import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import javax.swing.JScrollPane;
import javax.swing.JDialog;
import java.sql.SQLException;
import java.util.Vector;

import colordialog.ColorListener;
import symap.sequence.Sequence;
import symap.sequence.Annotation;
import symap.mapper.HitData;
import symap.Globals;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ErrorReport;
import util.Utilities;

/*************************************************
 * The panel for displaying the alignment, with CloseupComponent on top and TextComponent on bottom;
 * called from CloseUp, which is called by SyMAP2d
 * Gets hits and genes in region, and builds the HitAlignment and GeneAlignment for display
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class CloseUpDialog extends JDialog implements CloseUpListener, ColorListener, HelpListener 
{
	private AlignPool alignPool;
	private CloseUpComponent viewComp;
	private JScrollPane blastScroll, viewScroll;
	private TextComponent textComp;
	private HitAlignment selectedHa; 
	private HelpBar helpBar; 
	private final int MAX_WIDTH=1500;
	private String projS, projO; // selected and other

	// hitList will have at least ONE hit
	public CloseUpDialog(CloseUp closeup, Vector <HitData> hitList, Sequence seqObj, int selStart, int selEnd, String otherProject, boolean isProj1) throws SQLException {
		alignPool = closeup.getDrawingPanel().getPools().getAlignPool();
		projS = seqObj.getProjectDisplayName();
		projO = seqObj.getOtherProjectName();
		initView();
		
		setView(seqObj, hitList, selStart, selEnd, isProj1);
		
		String d = SeqData.coordsStr(selStart, selEnd);
		String other = seqObj.getOtherProjectName() + " " + seqObj.getSeqHits().getOtherName(seqObj);
		String x = String.format("Selected Region: %s  %s  %s to %s", projS, seqObj.getName(), d, other); // CAS504, CAS531
		setTitle(x);
	
		Utilities.setFullSize(this,viewComp, MAX_WIDTH); // CAS531 makes it less than width of screen
		setLocationRelativeTo(null);
	}
	
	public int showIfHits() {
		if (viewComp==null) return -1;
		
		int h = viewComp.getNumberOfHits();
		
		if (h > 0) setVisible(true); // CAS512 show();
		return h;
	}
	private void initView() {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		viewComp = new CloseUpComponent();
		textComp = new TextComponent(projS, projO);
		
		helpBar = new HelpBar(-1, 17); // CAS521 removed dead args
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
	
	// CAS535 the subhits and exons are only shown if they are within or overlap the selected boundary
	public void setView(Sequence src, Vector <HitData> hitList, int sstart, int send, boolean isProj1) throws SQLException 
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
			
			gn.setDisplayExons(sstart, send); // CAS535 set exons within selected
			
			sEx = Math.min(sEx, gn.getEmin());
			eEx = Math.max(eEx, gn.getEmax());
		}	
		int startGr = Math.min(sHit, sEx) - 10; // a little padding
		int endGr  =  Math.max(eHit, eEx) + 10;
		
//System.out.format("CLOSEUPx: hit %,d %,d  exon %,d %,d  final %,d %,d  sel %,d %,d\n", sHit, eHit, sEx, eEx, startGr, endGr, sstart, send);

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
			Vector<Annotation> exons = seq.getAnnoExon(a.getAnnoIdx()); // CAS535 get specific exons
			
			GeneAlignment ga = new GeneAlignment(
					a.getShortDescription(), a.getStart(), a.getEnd(), a.isStrandPos(), exons);
			
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
	public void hitClicked(HitAlignment ha) {
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
}
