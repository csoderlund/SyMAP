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
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.Utilities;

/*************************************************
 * Displays the alignment; called from CloseUp, which is called by SyMAP
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

	public CloseUpDialog(CloseUp closeup, Vector <HitData> hitList, Sequence seqObj, int start, int end, String otherProject, boolean isProj1) throws SQLException {
		alignPool = closeup.getDrawingPanel().getPools().getAlignPool();
		projS = seqObj.getProjectDisplayName();
		projO = seqObj.getOtherProjectName();
		initView();
		
		setView(seqObj, hitList, start, end, isProj1);
		
		String d = SeqData.coordsStr(start, end);
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
	
	public void setView(Sequence src, Vector <HitData> hitList, int start, int end,  boolean isProj1_idx) throws SQLException 
	{
		HitAlignment[] hitAlignArr = alignPool.getHitAlignments(hitList, isProj1_idx, projS, projO);
		if (hitAlignArr==null || hitAlignArr.length==0) return;
		
		GeneAlignment[] geneAlignArr = getGeneAlignments(src, start, end);
		
		// find boundary of hits
		int startG = Integer.MAX_VALUE;
		int endG = Integer.MIN_VALUE;
		for (HitAlignment h : hitAlignArr) {
			startG = Math.min(startG, h.getSstart());
			endG =   Math.max(endG, h.getAend());
		}
		int startH=startG, endH=endG;
		
		// Expand the reduced region to show full gene	
		for (GeneAlignment gn : geneAlignArr) {
			if (gn.getMax() < startH || gn.getMin()>endH) continue; // no hits on this gene so don't show
			
			startG = Math.min(startG, gn.getMin());
			endG =   Math.max(endG, gn.getMax());
		}		
		viewComp.setData(startG, endG, geneAlignArr, hitAlignArr);
		
		selectedHa = hitAlignArr[0]; 
		hitAlignArr[0].setSelected(true);
		hitClicked(hitAlignArr[0]);
	}
	
	private GeneAlignment[] getGeneAlignments(Sequence seq, int start, int end) {
		Vector<Annotation> annoVec = 
				seq.getAnnotations(Annotation.GENE_INT, start, end);
		
		Vector<GeneAlignment> alignments = new Vector<GeneAlignment>();
		for (Annotation a : annoVec) {
			GeneAlignment ga = new GeneAlignment(a.getShortDescription(), a.getStart(), a.getEnd(), a.isStrandPos());
			
			Vector<Annotation> exons = seq.getAnnotations(Annotation.EXON_INT, a.getStart(), a.getEnd());
			Exon[] exonArray = (exons.isEmpty() ? null : new Exon[exons.size()]);
		
			for (int i = 0;  i < exons.size();  i++)
				exonArray[i] = new Exon(exons.get(i).getStart(), exons.get(i).getEnd(), exons.get(i).getTag());
			ga.setExons(exonArray);
			
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
