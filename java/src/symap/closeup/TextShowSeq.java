package symap.closeup;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;

import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import symap.drawingpanel.DrawingPanel;
import symap.mapper.HitData;
import symap.sequence.Annotation;
import symap.sequence.Sequence;
import util.ErrorReport;

/*****************************************************
 * Show Seq Options pull-down; called when sequence is selected
 * Uses speciallized displayInfoMonoSpace for display
 */
public class TextShowSeq extends JDialog implements ActionListener {
	private static final long serialVersionUID = 1L;
	private final int INC=80;
	private final boolean bGene=false; // wait until next release - needs some work
	
	private AlignPool alignPool;
	private DrawingPanel drawPanel;
	private Sequence seqObj;
	private String projectName;
	private int rStart, rEnd;
	private int grpID;
	private boolean isQuery;
	private String type="";
	
	private Vector <HitData> hitList;
	private Vector<Annotation> geneList;
	
	private JButton okButton, cancelButton;
	private JRadioButton exonButton, transExonButton, geneButton;
	private JRadioButton hitButton, revHitButton, transHitButton, fullHitButton,  regionButton;
	
	public TextShowSeq (DrawingPanel dp, Sequence sequence, String project, 
			int grpID, int start, int end, boolean isQuery) 
	{
		super();
		setModal(false);
		setResizable(true);
		
		alignPool = new AlignPool(dp.getDBC());
		drawPanel = dp;
		this.seqObj = sequence;
		this.projectName = project;
		this.rStart = start;
		this.rEnd = end;
		this.grpID = grpID;
		this.isQuery = isQuery;
		
		hitButton = 	new JRadioButton("Hit"); 				hitButton.setBackground(Color.white);
		revHitButton = 	new JRadioButton("Reverse Hit"); 		revHitButton.setBackground(Color.white);
		fullHitButton = new JRadioButton("Full Hit"); 			fullHitButton.setBackground(Color.white);
		regionButton = 	new JRadioButton("Region"); 			regionButton.setBackground(Color.white);
		transHitButton = new JRadioButton("Translated Hit");	transHitButton.setBackground(Color.white);
		geneButton = 	new JRadioButton("Gene"); 				geneButton.setBackground(Color.white);
		exonButton = 	new JRadioButton("Exon"); 				exonButton.setBackground(Color.white);
		transExonButton = new JRadioButton("Translated Exon"); 	transExonButton.setBackground(Color.white);
		
		ButtonGroup group = new ButtonGroup();
		group.add(hitButton); 	 group.add(transHitButton);	
		group.add(revHitButton); group.add(fullHitButton);
		group.add(regionButton); group.add(geneButton);		
		group.add(exonButton);	 group.add(transExonButton);
		
		JPanel buttonPanel = new JPanel();
		okButton = new JButton("OK"); okButton.setBackground(Color.white);
		okButton.addActionListener(this);
		cancelButton = new JButton("Cancel"); cancelButton.setBackground(Color.white);
		cancelButton.addActionListener(this);
		buttonPanel.add(okButton); buttonPanel.add(cancelButton);
		
		Container cp = getContentPane();
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		cp.setLayout(gbl);
		gbc.fill = 2;
		gbc.gridheight = 1;
		gbc.ipadx = 5;
		gbc.ipady = 8;
		
		int n = GridBagConstraints.REMAINDER;
		addToGrid(cp,gbl,gbc,hitButton, 1);    
		addToGrid(cp,gbl,gbc,revHitButton, n);
		addToGrid(cp,gbl,gbc,fullHitButton, 1);
		addToGrid(cp,gbl,gbc,regionButton, n);
		
		if (bGene) {
			//addToGrid(cp,gbl,gbc,transHitButton, 2);
			addToGrid(cp,gbl,gbc,exonButton,  1);  
			addToGrid(cp,gbl,gbc,transExonButton,  n);
			addToGrid(cp,gbl,gbc,geneButton, n);
		}
		addToGrid(cp,gbl,gbc,new JSeparator(),n);
		addToGrid(cp,gbl,gbc,buttonPanel,n);
		hitButton.setSelected(true);
		
		init();

		pack();
		
		Dimension d = new Dimension (330, 200); 
		if (getWidth() >= d.width || getHeight() >= d.height) setSize(d);
		setVisible(true);	
		setAlwaysOnTop(true);
		setLocationRelativeTo(null);
		setTitle("Show Sequence Options");
	}
	// if no genes in region, disable gene/exon. If no hits in region, disable hit/full hit.
	private void init() {
		hitList = drawPanel.getHitsInRange(seqObj,rStart,rEnd);
		geneList = seqObj.getAnnoGene(rStart, rEnd); // EXON_INT if select Exon
		if (hitList.size()==0) {
			hitButton.setEnabled(false);      revHitButton.setEnabled(false);
			transHitButton.setEnabled(false); fullHitButton.setEnabled(false);
			regionButton.setSelected(true);
		}
	
		if (geneList.size()==0) {
			geneButton.setEnabled(false); exonButton.setEnabled(false); transExonButton.setEnabled(false);
		}
	}
	private void addToGrid(Container c, GridBagLayout gbl, GridBagConstraints gbc, Component comp, int i) {
		gbc.gridwidth = i;
		gbl.setConstraints(comp,gbc);
		c.add(comp);
	}
	/****************************************************************/
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == okButton) {
			showSeq();
			setVisible(false); 
		}
		else if (e.getSource() == cancelButton) {
			setVisible(false);
		}
	}
	// Each show will return Vector <SeqData> to be shown as FASTA 
	private void showSeq() {
		Vector <SeqData> seqs=null;
		
		if      (hitButton.isSelected()) 		seqs = showHits(false);
		else if (revHitButton.isSelected()) 	seqs = showHits(true);
		else if (fullHitButton.isSelected()) 	seqs = showFullHits();
		else if (regionButton.isSelected())   	seqs = showRegion();
		
		else if (transHitButton.isSelected())	seqs = showTransHits();
		else if (geneButton.isSelected()) 		seqs = showGenes();
		else if (exonButton.isSelected()) 		seqs = showSepExons();
		else if (transExonButton.isSelected()) 	seqs = showExons(true);
		if (seqs==null) return;
		
		// Create formated line
		StringBuffer bfLines = new StringBuffer ();
		
		for (SeqData sd : seqs) {
			bfLines.append(String.format("> %s", sd.getHeader()) + "\n");
			String seq = sd.getSeq();
			
			int len = seq.length(), x=0;
			while ((x+INC)<len) {
				bfLines.append(seq.substring(x, x+INC) + "\n");
				x+=INC;
			}
			if (x<len) bfLines.append(seq.substring(x, len) + "\n");
			bfLines.append("\n");
		}
		String title =  type + ": " + projectName + " " + SeqData.coordsStr(rStart, rEnd);
		displayInfoMonoSpace(null, title, bfLines.toString(), false /*!isModal*/);
	}
	/***********************************************************************************/
	// selected region
	private Vector <SeqData> showRegion() {
		try {
			type="Region";
			String coords = rStart + ":" + rEnd;
			String title = "Region " + SeqData.coordsStr(rStart, rEnd);
			 
			String seq = alignPool.loadPseudoSeq(coords, grpID);
			
			SeqData sd = new SeqData(seq, ' ', title);
			Vector <SeqData> seqs = new Vector <SeqData>(1);
			seqs.add(sd);
			
			return seqs;
		} catch (Exception e) {ErrorReport.print(e, "show gene"); return null;}
	}
	// all subhits 
	private Vector <SeqData> showHits(boolean bRev) {
		try {
			type = (hitList.size()>1) ? " Hits" : "Hit";
			if (bRev) type = " Reverse " + type;
			if (hitList.size()>1) type = hitList.size() + type;
			return alignPool.getHitSequence(hitList, grpID, isQuery, bRev);
			
		} catch (Exception e) {ErrorReport.print(e, "show gene"); return null;}
	}
	// merged
	private Vector <SeqData> showFullHits() {
		try {
			type=  "Clustered hit region ";
			type = type + hitList.size();
			int s, e;
			
			Vector <SeqData> seqs = new Vector <SeqData>(hitList.size());
			for (HitData hd : hitList) {
				s = (isQuery) ? hd.getStart1() : hd.getStart2();
				e  =  (isQuery) ? hd.getEnd1() : hd.getEnd2();
				
				String seq = alignPool.loadPseudoSeq(s + ":" + e, grpID);
				
				String title = hd.getName() + " " + SeqData.coordsStr(s, e);
				SeqData sd = new SeqData(seq, ' ', title);
				seqs.add(sd);
			}
			return seqs;
			
		} catch (Exception e) {ErrorReport.print(e, "show full hit region"); return null;}
	}
	//Translated hits (best frame? or all frames?)
	private Vector <SeqData> showTransHits() {
		try {
			type="Translated hits";
			
			return alignPool.getHitSequence(hitList, grpID, isQuery, false); // TODO translated
			
		} catch (Exception e) {ErrorReport.print(e, "show gene"); return null;}
	}
	// Full gene sequence
	private Vector <SeqData> showGenes() {
	try {
		type = "Gene";
		Vector <SeqData> seqs = new Vector <SeqData>();
		int s,e; 
		for (Annotation ad : geneList) {
			s = ad.getStart();
			e = ad.getEnd();
			
			String seq = alignPool.loadPseudoSeq(s + ":" + e, grpID);
			
			String title = ad.getTag() + " " + SeqData.coordsStr(s, e);
			SeqData sd = new SeqData(seq, ' ', title);
			seqs.add(sd);
		}
		
		return seqs;
	} catch (Exception e) {ErrorReport.print(e, "show gene"); return null;}
	}
	
	// concatenated exons
	private Vector <SeqData> showExons(boolean bTrans) {
		try {
			type = "Exons";
			int geneIdx = seqObj.getAnnoGene(rStart, rEnd).get(0).getAnnoIdx(); 
			Vector<Annotation> exonList = seqObj.getAnnoExon(geneIdx);
			Annotation [] annoArr = new Annotation [exonList.size()];
			for (int i=0; i<exonList.size(); i++) annoArr[i] = exonList.get(i);
			Comparator <Annotation> comp = Annotation.getExonStartComparator();
			Arrays.sort(annoArr, comp);
			
			Vector <SeqData> seqs = new Vector <SeqData>(exonList.size());
			int last_gene_idx = -1, s, e, farS=Integer.MAX_VALUE, farE=0;
			boolean isPos=true;
			String concatExon="", geneTag="";
			
			for (Annotation ad : annoArr) {
				if (last_gene_idx!=ad.getGeneIdx()) {
					if (last_gene_idx != -1) {
						String title = geneTag + " " + SeqData.coordsStr(farS, farE);
						if (bTrans) concatExon = SeqData.translate(concatExon);
						SeqData sd = new SeqData(concatExon, ' ', title);
						seqs.add(sd);
						
						farS=Integer.MAX_VALUE; farE=0;
						concatExon="";
					}
					
					last_gene_idx = ad.getGeneIdx();
					for (Annotation gd : geneList) {
						if (last_gene_idx==gd.getAnnoIdx()) {
							geneTag = gd.getTag();
							isPos = gd.isStrandPos();
						}
					}
				}
				s = ad.getStart(); e = ad.getEnd();
				String exon = alignPool.loadPseudoSeq(s + ":" + e, grpID);
				if (!isPos) exon = SeqData.revComplement(exon);
				concatExon +=  exon;
				
				farS = Math.min(farS, s); farE = Math.max(farE, e);	
			}
			// process last one
			String title = geneTag + " " + SeqData.coordsStr(farS, farE);
			if (bTrans) concatExon = SeqData.translate(concatExon);
			SeqData sd = new SeqData(concatExon, ' ', title);
			seqs.add(sd);
			
			return seqs;
		} catch (Exception e) {ErrorReport.print(e, "show exon"); return null;}
	}
	// separate exons
	private Vector <SeqData> showSepExons() {
		try {
			type = "Exons";
			int geneIdx = seqObj.getAnnoGene(rStart, rEnd).get(0).getAnnoIdx(); 
			Vector<Annotation> exonList = seqObj.getAnnoExon(geneIdx);
			Annotation [] annoArr = new Annotation [exonList.size()];
			for (int i=0; i<exonList.size(); i++) annoArr[i] = exonList.get(i);
			Comparator <Annotation> comp = Annotation.getExonStartComparator();
			Arrays.sort(annoArr, comp);
			
			Vector <SeqData> seqs = new Vector <SeqData>(exonList.size());
			int last_gene_idx = -1, s, e;
			boolean isPos=true;
			String geneTag="", exon="";
			int cnt=1;
			
			for (Annotation ad : annoArr) {
				if (last_gene_idx!=ad.getGeneIdx()) {
					last_gene_idx = ad.getGeneIdx();
					for (Annotation gd : geneList) {
						if (last_gene_idx==gd.getAnnoIdx()) {
							geneTag = gd.getTag();
							isPos = gd.isStrandPos();
						}
					}
				}
				s = ad.getStart(); e = ad.getEnd();
				exon = alignPool.loadPseudoSeq(s + ":" + e, grpID);
				if (isPos) exon = SeqData.revComplement(exon);
				SeqData sd = new SeqData(exon, ' ', geneTag + " " + cnt);
				seqs.add(sd);
				cnt++;
			}
		
			return seqs;
		} catch (Exception e) {ErrorReport.print(e, "show exon"); return null;}
	}
	/******************************************************
	 * Copied from util.Utilities.displayInfoMonoSpace to specialize
	 */
	public static void displayInfoMonoSpace(Component parentFrame, String title, String theSeq, boolean isModal) {
		JOptionPane pane = new JOptionPane();
		
		JTextArea messageArea = new JTextArea(theSeq);

		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		pane.setMessage(sPane);
		pane.setMessageType(JOptionPane.PLAIN_MESSAGE);

		JDialog diag = pane.createDialog(parentFrame, title);
		int h = Math.min(450, sPane.getHeight());
		Dimension d = new Dimension (620, h+200); // width, height
		if (sPane.getWidth() >= d.width || sPane.getHeight() >= d.height) diag.setSize(d);
		
		diag.setModal(isModal);
		diag.setResizable(true);
		diag.setVisible(true);		
	}
}
