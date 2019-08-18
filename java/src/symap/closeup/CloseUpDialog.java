package symap.closeup;

import java.awt.Container;
import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import javax.swing.JScrollPane;
import javax.swing.JDialog;
import java.sql.SQLException;
import java.util.List;
import java.util.Vector;

import symap.sequence.Sequence;
import symap.sequence.Annotation;
import symap.mapper.AbstractHitData;
import symap.closeup.components.BlastComponent;
import symap.closeup.components.CloseUpComponent;
import symap.closeup.components.CloseUpListener;
import symap.closeup.alignment.HitAlignment;
import symap.closeup.alignment.GeneAlignment;
import symap.closeup.alignment.Exon;
import symap.drawingpanel.DrawingPanel;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.Utilities;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class CloseUpDialog extends JDialog implements HitDialogInterface, CloseUpListener,
														HelpListener // mdb added 7/8/09
{
	//private static final boolean METHOD_TRACE = false; 

	private SequencePool sp;
	private CloseUpComponent view;
	private JScrollPane blastScroll, viewScroll;
	private BlastComponent blast;
	private HitAlignment selectedHa; // ASD added
	private HelpBar helpBar; // mdb added 7/8/09

	public CloseUpDialog(CloseUp closeup) {
		super(closeup.getDrawingPanel().getFrame(),false);
		this.sp = closeup.getDrawingPanel().getPools().getSequencePool();
		initView();
	}

	public CloseUpDialog(CloseUp closeup, Sequence seq, int start, int end) throws SQLException {
		this(closeup);
		
		DrawingPanel dp = closeup.getDrawingPanel();
		List<AbstractHitData> hitList = dp.getHitsInRange(seq,start,end);
		
		setView(seq,hitList,start,end);
		setTitle(seq.getProjectDisplayName()+" "+seq.getName()+" "+view.getStart()+"-"+view.getEnd());
	}

// mdb unused 12/11/08
//	public CloseUpDialog(Frame owner, SequencePool sp) {
//		super(owner,false);
//		this.sp = sp;
//		initView();
//	}

	public void resetColors() {
		blast.setBackground(BlastComponent.backgroundColor);
		blastScroll.getViewport().setBackground(BlastComponent.backgroundColor);
		blast.repaint();
		view.setBackground(CloseUpComponent.backgroundColor);
		viewScroll.getViewport().setBackground(CloseUpComponent.backgroundColor);
		view.repaint();
	}

// mdb unused 7/24/07
//	public CloseUpDialog(Frame owner, SequencePool sp, List fpcPseudoData, int start, int end) throws SQLException {
//		this(owner,sp);
//		setView(fpcPseudoData,start,end);
//	}

	public int getNumberOfHits() {
		if (view == null) {
			System.err.println("CloseUpDialog: view is null");
			return 0;
		}
		return view.getNumberOfHits();
	}

	public int showIfHits() {
		int h = getNumberOfHits();
		if (h > 0) show();
		return h;
	}
	
	// mdb added 1/8/09 for pseudo-pseudo closeup - replaces SequencePool.getGeneAlignments()
	private GeneAlignment[] getGeneAlignments(Sequence s, int start, int end) {
		Vector<Annotation> annotations = s.getAnnotations(Annotation.GENE_INT, start, end);
		Vector<GeneAlignment> alignments = new Vector<GeneAlignment>();
		
		for (Annotation a : annotations) {
			GeneAlignment ga = new GeneAlignment(a.getShortDescription(), a.getStart(), a.getEnd(), a.getStrand());
			Vector<Annotation> exons = s.getAnnotations(Annotation.EXON_INT, a.getStart(), a.getEnd());
			Exon[] exonArray = (exons.isEmpty() ? null : new Exon[exons.size()]);
			
			for (int i = 0;  i < exons.size();  i++)
				exonArray[i] = new Exon(exons.get(i).getStart(), exons.get(i).getEnd());
			ga.setExons(exonArray);
			
			if (!alignments.contains(ga)) // in case database has redundant entries (as with Rice)
				alignments.add(ga);
			else 
				System.err.println("Duplicate gene annotation: " + a.getShortDescription());
		}
		
		return alignments.toArray(new GeneAlignment[0]);
	}

	public void setView(Sequence src, List<AbstractHitData> hitList, int start, int end) // mdb added "src" arg 1/7/09
	throws SQLException 
	{
		HitAlignment[] ha = null;
		GeneAlignment[] ga = null;
		int min_x = Integer.MAX_VALUE;
		int max_x = Integer.MIN_VALUE;
		
		if (hitList != null && !hitList.isEmpty()) {
			ha = sp.getHitAlignments(src,hitList);
			
			// Expand to show all of alignments			
			for (HitAlignment h : ha) {
				min_x = Math.min(min_x, h.getStart());
				max_x = Math.max(max_x, h.getEnd());
			}
			
			ga = getGeneAlignments(src, min_x, max_x);//sp.getGeneAlignments(group,start,end); // mdb changed 1/8/09 for pseudo-pseudo closeup
			
			// Expand the reduced region to show full gene		
			for (GeneAlignment g : ga) {
				if (g.getMax() < min_x) continue; // no hits on this gene so don't show
				if (g.getMin() < min_x) min_x = g.getMin();
				if (g.getMax() > max_x && g.getMin() < max_x) max_x = g.getMax();
			}
		}
		
		if (ha != null && ha.length > 0) { 
			selectedHa = ha[0]; 
			ha[0].setSelected(true);
		}

		view.set(min_x, max_x, ga, ha);
		if (ha != null && ha.length > 0)
			hitClicked(ha[0]);
		Utilities.setFullSize(this,view);
	}

	// ASD modified to handle color change
	public void hitClicked(HitAlignment ha) {
		if (ha != null) {
			if (selectedHa != null)
				selectedHa.setSelected(false);
			ha.setSelected(true); 
			selectedHa = ha;
			view.repaint(); // Repaint to show color change
			blast.setAlignment(ha);
			getContentPane().validate();
		}
	}

	private void initView() {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		view = new CloseUpComponent();
		blast = new BlastComponent();
		
		// mdb added 7/8/09
		helpBar = new HelpBar(-1, 17, true, false, false);
		helpBar.addHelpListener(view,this);
		helpBar.addHelpListener(blast,this);

		view.addCloseUpListener(this);

		viewScroll  = new JScrollPane(view,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		blastScroll = new JScrollPane(blast,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		viewScroll.getViewport().setBackground(view.getBackground());
		blastScroll.getViewport().setBackground(blast.getBackground());

		Container cp = getContentPane();
		cp.setLayout(new BorderLayout());
		cp.add(viewScroll,BorderLayout.NORTH);
		cp.add(blastScroll,BorderLayout.CENTER);
		cp.add(helpBar, BorderLayout.SOUTH); // mdb added 7/8/09
	}
	
	// mdb added 7/8/09
	public String getHelpText(MouseEvent e) {
		if (e.getSource() == view)
			return "Click on a hit to select it and view the base alignment.";
		else if (e.getSource() == blast)
			return "Highlight text and press CTRL-C to copy.";
		else
			return null;
	}
}
