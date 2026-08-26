package symap.manager;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import java.util.TreeSet;
import java.util.Vector;
import java.util.Arrays;
import java.sql.ResultSet;

import database.DBconn2;
import symap.Globals;
import util.ErrorReport;
import util.Jcomp;
import util.Utilities;

/**********************************************************
 * Creates the Summary window from the Summary Button on the main panel
 * The Summary is of the alignment from two different sequence projects
 * CAS575 correct for self-synteny; remove old createBlock and createAnchor
 */

public class SumFrame extends JDialog implements ActionListener {
	private static final long serialVersionUID = 246739533083628235L;
	private boolean bArgRedo=Globals.bRedoSum; // -s regenerate summary on display; 
	private boolean bRedo=false;
	
	private JButton btnHelp = null, btnOK = null;
	private int pairIdx=0, proj1Idx=0, proj2Idx=0;
	private int nHits=0;
	private Mpair mp;
	private DBconn2 dbc2; 
	
	private Proj proj1=null, proj2=null; // contains idx and lengths of groups
	private boolean isSelf=false, isAlgo1=false;		 
	
	// Called from DoAlignSynPair to create
	public SumFrame(DBconn2 dbc2, Mpair mp) { 
		this.mp = mp;
		this.dbc2 = dbc2;
		
		bRedo=true;			// expects the summary to be regenerated if still exists
		createSummary(false);
	}
	// do not need to regenerate summary with 'Number Pseudo' or NumHits, 
	// just add to end; keep current version as not major update; CAS579; CAS579c add action
	public SumFrame(DBconn2 dbc2, Mpair mp, String action) { 
		this.mp = mp;
		this.dbc2 = dbc2;
		
		try {
			ResultSet rs = dbc2.executeQuery("select summary from pairs where idx="+ mp.getPairIdx());
			String sum = (rs.next()) ? rs.getString(1) : null; 
			if (sum==null) {
				createSummary(true);
				return;
			}
			if (sum.contains(action)) return;
			sum += "\n  " + action + " " + Utilities.getDateOnly(); // CAS579c add date
			dbc2.executeUpdate("update pairs set summary='" + sum + "' where idx=" + mp.getPairIdx());
		}
		catch (Exception e) {ErrorReport.print("Updating summary for pseudo"); }
	}
    // Called from the project manager to display
	public SumFrame(String title, DBconn2 dbc2, Mpair mp, boolean isReadOnly) {
	super();
	try {	
		setModal(false);
		setTitle(title); 
		setResizable(true);
		
		this.mp = mp;
		this.dbc2 = dbc2;
		
		String theInfo = createSummary(isReadOnly); 
		JTextArea messageArea = new JTextArea(theInfo);
		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.PLAIN, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
			
		btnOK = Jcomp.createButton(Jcomp.ok, "Close window");
		btnOK.addActionListener(this);
		btnHelp = Jcomp.createButton("Explain", "Popup with explanation of statistics");
		btnHelp.addActionListener(this);
		JPanel buttonPanel = new JPanel();
		
		buttonPanel.add(btnHelp); buttonPanel.add(Box.createHorizontalStrut(20));
		buttonPanel.add(btnOK);
		
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(sPane,BorderLayout.CENTER);
		getContentPane().add(buttonPanel,BorderLayout.SOUTH);
		pack();
		
		toFront();
		setLocationRelativeTo(null);	
		setVisible(true);
	}
	catch(Exception e){ErrorReport.print(e, "Create summary");}
	}
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == btnOK) setVisible(false); 
		else if (e.getSource() == btnHelp)  util.Jhtml.showHTMLPage(null, "Summary Help", "/html/SummaryHelp.html");
	}
	/*******************************************************************************
	 * Create summary
	 ******************************************************************************/
	private String createSummary(boolean isReadOnly) {
	try {
		pairIdx  = mp.getPairIdx();
	
		if (dbc2.tableColumnExists("pairs", "summary")) { // exist if called from Summary popup
			ResultSet rs = dbc2.executeQuery("select summary from pairs where idx="+ pairIdx);
			String sum = (rs.next()) ? rs.getString(1) : null; 
			if (sum!=null && sum.length()>20 && !bArgRedo && !bRedo) return sum;
		}
		if (isReadOnly) System.out.println("Create Summary for display...");
		else 			System.out.println("Creating Summary for save to database... ");
		
		isAlgo1 = mp.isAlgo1(Mpair.DB);
		proj1Idx = mp.getProj1Idx(); 
		proj2Idx = mp.getProj2Idx();
		isSelf = proj1Idx==proj2Idx;
		proj1 = makeProjObj(proj1Idx);
		proj2 = makeProjObj(proj2Idx);
		proj1.name = mp.getProj1().getDisplayName();
		proj2.name = mp.getProj2().getDisplayName();
		String [] params = createParams();
		
		// Build display
		String info = (isSelf) ? proj1.name + " self-synteny" :  proj1.name + " vs. " + proj2.name;
		info += params[0]; 		// updated\n\n
		
		info += createProject()   + "\n";
		info += createCluster()   + "\n";
		info += createBlock()     + "\n";
		info += createCollinear();
		
		// XXX Key
		info += "_____________________________________________________________________________________\n";
		info += "Key: Genome(G) Gene(GN) Exon(EX) Cluster Hit(CH) SubHit(SH) Block(B) CH in Block(CHB)\n\n";
		info += "Covered by (c) - percent aligned bases. Aligned to (a) - regardless of cover. \n";
		info += "_____________________________________________________________________________________\n";
		info += params[1]; 		// params
	
		if (!isReadOnly) {
			dbc2.tableCheckAddColumn("pairs", "summary", "text", null); 
			dbc2.executeUpdate("update pairs set summary='" + info + "' where idx=" + pairIdx);
		}
		return info;
	}
	catch(Exception e){ErrorReport.print(e, "Create summary"); return "summary error";}
	}
	/****************************************************************
	 * Project - genes; does not need isSelf check
	 * Exon# or cover cannot be found through interface
	 */
	private String createProject() {
		try {
			String title = "Genome and Annotation";
			ResultSet rs;
			
		// genes
			int gSum1=0, gSum2=0, gAvg1=0, gAvg2=0, gMax1=0, gMax2=0;
			int nExon1=0, nExon2=0, eSum1=0, eSum2=0, eAvg1=0, eAvg2=0, eMax1=0, eMax2=0;
			
			String q = "select count(*), sum(end-start+1), avg(end-start+1), max(end-start+1) from pseudo_annot as pa " +
				       "join xgroups as g on pa.grp_idx=g.idx where pa.type='gene' and g.proj_idx=";
			
			rs = dbc2.executeQuery(q + proj1Idx);
			if (rs.next()) { 
				proj1.nGenes = rs.getInt(1);
				gSum1 = rs.getInt(2);
				gAvg1 = rs.getInt(3);
				gMax1 = rs.getInt(4);
			} else Globals.eprt("Failed Genome and Annotation query for proj1");
			
			rs = dbc2.executeQuery(q + proj2Idx);
			if (rs.next()) { 
				proj2.nGenes = rs.getInt(1);
				gSum2   = rs.getInt(2);
				gAvg2   = rs.getInt(3);
				gMax2   = rs.getInt(4);
			} else Globals.eprt("Failed Genome and Annotation query for proj2");
			
			q = "select count(*), sum(end-start+1), avg(end-start+1), max(end-start+1) from pseudo_annot as pa " +
				"join xgroups as g on pa.grp_idx=g.idx where pa.type='exon' and g.proj_idx=";
				
			rs = dbc2.executeQuery(q + proj1Idx);
			if (rs.next()) { 
				nExon1 = rs.getInt(1);
				eSum1 = rs.getInt(2);
				eAvg1 = rs.getInt(3);
				eMax1 = rs.getInt(4);
			}
			rs = dbc2.executeQuery(q + proj2Idx);
			if (rs.next()) { 
				nExon2 = rs.getInt(1);
				eSum2   = rs.getInt(2);
				eAvg2   = rs.getInt(3);
				eMax2  = rs.getInt(4);
			}
			rs.close();
			
		// make table
			String type1 = mp.mProj1.getAnnoType(); // CAS588 add types
			String type2 = mp.mProj2.getAnnoType();
			if (type1.equals(type2)) type1 = "#" + type1;
			else type1 = "#" + type1+"/"+type2;
			
			// XXX table 1
			String [] fields = {"","#Genes", "GcGN", "AvgLen", "MaxLen", "  ", type1, "GcEX",  "AvgLen", "MaxLen", "#Seqs","Length (bp)"};
			int [] justify =   {1, 0,0,0, 0,0,0, 0,0, 0, 0, 0};
			int nRow = 2;
		    int nCol=  fields.length;
		    String [][] rows = new String[nRow][nCol];
		    int r=0, c=0;
		    
		    rows[r][c++] = proj1.name;
		    rows[r][c++] = String.format("%,d",  proj1.nGenes);
		    rows[r][c++] = String.format("%s",   pct(gSum1,proj1.genomeLen));
		    rows[r][c++] = String.format("%s",   cntK(gAvg1));
		    rows[r][c++] = String.format("%s",   cntK(gMax1));
		    rows[r][c++] = "";
		    rows[r][c++] = String.format("%,d",  nExon1);
		    rows[r][c++] = String.format("%s",   pct(eSum1,proj1.genomeLen));
		    rows[r][c++] = String.format("%s",   cntK(eAvg1));
		    rows[r][c++] = String.format("%s",   cntK(eMax1));
		    
		    rows[r][c++] = cntK(proj1.nGrp);
		    rows[r][c++] = String.format("%,d", proj1.genomeLen);
		   
		    r++; c=0;
		    if (isSelf) {
		    	for (c=0; c<fields.length; c++) rows[r][c]="";
		    }
		    else {
		    	rows[r][c++] = proj2.name;
		    	rows[r][c++] = String.format("%,d",  proj2.nGenes);
		    	rows[r][c++] = String.format("%s",   pct(gSum2,proj2.genomeLen));
			    rows[r][c++] = String.format("%s",   cntK(gAvg2));
			    rows[r][c++] = String.format("%s",   cntK(gMax2));
				rows[r][c++] = "";
				rows[r][c++] = String.format("%,d",  nExon2);
				rows[r][c++] = String.format("%s",   pct(eSum2,proj2.genomeLen));
				rows[r][c++] = String.format("%s",   cntK(eAvg2));
				rows[r][c++] = String.format("%s",   cntK(eMax2));
				    
			    rows[r][c++] = cntK(proj2.nGrp);
			    rows[r][c++] = String.format("%,d", proj2.genomeLen);
		    }
		   
			String tab = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
			return title + "\n" + tab;    
		}
		catch (Exception e) {ErrorReport.print(e, "Create annotation"); return "error on project";}
	}
	/****************************************************************
	 * GNwCH: query (#genes-orphans)/#genes		or Every*, Genes:/#Genes		includes minors
	 * CHwGN: query both+one with proj1/nHits   								does not include minors
	 * Cover CH: if there is NO overlap, sum Hlen/genomeLen; otherwise, must take into account embedded vs overlap.
	 * Cover SH: none of these numbers can be computed from the interface
	 *  "pctid               TINYINT UNSIGNED NOT NULL," +   // avg %id mummer Col6     
		"cvgpct				 TINYINT UNSIGNED NOT NULL," +   // avg %sim  mummer Col7     
		"countpct			 INTEGER UNSIGNED default 0," +  // number of merged   
	 ***************************************************************/
	private String createCluster() {
	try {
		String title = "Cluster Hits";
		nHits = dbc2.executeInteger("select count(*) from pseudo_hits where pair_idx=" + pairIdx); 
		if (nHits<=0) return title + "\n No hits";
		if (isSelf) nHits /= 2;
		
		String sq;
		// %Genes with hits (GNwCH)
		sq = "select count(distinct pha.annot_idx) "
				+ " from pseudo_annot      as pa "
				+ " join pseudo_hits_annot as pha on pa.idx = pha.annot_idx"
				+ " join pseudo_hits       as ph  on ph.idx = pha.hit_idx  "
				+ " join xgroups as xg  on xg.idx = pa.grp_idx " 
				+ " where pa.type='gene' and ph.pair_idx=" + pairIdx +  " and xg.proj_idx=";
		int gene1Hit = dbc2.executeInteger(sq + proj1Idx); // isSelf: distinct annot_idx takes care of mirrored hits
		int gene2Hit = dbc2.executeInteger(sq + proj2Idx); // query:  gene1Hit = #totGene1-#orphan genes
				
		// %Hits with genes; make sure not to count pseudo
		int hitGene1, hitGene2;
		int cntPseudo = dbc2.executeCount("select idx from pseudo_annot where type='pseudo' limit 1");
		if (cntPseudo>0) {			// confirmed same results between two queries
			sq = "select count(distinct ph.idx) from pseudo_hits as ph " 
					+ "join pseudo_hits_annot as pha on pha.hit_idx = ph.idx "
					+ "join pseudo_annot as pa  on pa.idx = pha.annot_idx " 
					+ "where pa.type='gene' and pair_idx=" + pairIdx; 
			
			if (isSelf) sq += " and ((grp1_idx>grp2_idx and refidx=0) or (grp1_idx=grp2_idx and start1>start2))";
			
			hitGene1 = dbc2.executeInteger(sq  + " and ph.annot1_idx=pha.annot_idx"); 
			hitGene2 = dbc2.executeInteger(sq  + " and ph.annot2_idx=pha.annot_idx"); 
		}
		else {// pseudos can have gene_overlap=1, so cannot do this simpler query
			sq = "select count(distinct idx) from pseudo_hits where gene_overlap>0 and pair_idx=" + pairIdx; 
			
			if (isSelf) sq += " and ((grp1_idx>grp2_idx and refidx=0) or (grp1_idx=grp2_idx and start1>start2))";
			
			hitGene1 = dbc2.executeInteger(sq  + " and annot1_idx>0"); 
			hitGene2 = dbc2.executeInteger(sq  + " and annot2_idx>0"); 
		}
				
		int hit2Gene = dbc2.executeInteger("select count(*) from pseudo_hits where gene_overlap=2 and pair_idx=" + pairIdx);
		int hit1Gene = dbc2.executeInteger("select count(*) from pseudo_hits where gene_overlap=1 and pair_idx=" + pairIdx);
		int hit0Gene = dbc2.executeInteger("select count(*) from pseudo_hits where gene_overlap=0 and pair_idx=" + pairIdx);
		
		float avgId  = dbc2.executeFloat("select AVG(pctid) from pseudo_hits where pair_idx=" + pairIdx); // CAS588
		float avgSim = dbc2.executeFloat("select AVG(cvgpct) from pseudo_hits where pair_idx=" + pairIdx);// CAS588
		if (isSelf) {hit2Gene /=2; hit1Gene /= 2; hit0Gene /=2;}

/* Coverage */
		ResultSet rs;
		int totSh=0, totRev=0;
		// Gene olap, Exon olap averages CAS588
		float g1olap  = dbc2.executeFloat("select AVG(pha.olap) from pseudo_hits_annot as pha"
				+ " join pseudo_hits as ph on pha.hit_idx = ph.idx "
				+ " join pseudo_annot as pa  on pa.idx = pha.annot_idx "
				+ " join xgroups as g on g.idx = pa.grp_idx"
				+ " where ph.pair_idx=" + pairIdx + " and g.proj_idx=" + proj1Idx); 
		float g2olap  = dbc2.executeFloat("select AVG(pha.olap) from pseudo_hits_annot as pha"
				+ " join pseudo_hits as ph on pha.hit_idx = ph.idx "
				+ " join pseudo_annot as pa  on pa.idx = pha.annot_idx "
				+ " join xgroups as g on g.idx = pa.grp_idx"
				+ " where ph.pair_idx=" + pairIdx + " and g.proj_idx=" + proj2Idx); 
		float ex1olap=0.0f, ex2olap=0.0f;
		if (!isAlgo1) {
			ex1olap  = dbc2.executeFloat("select AVG(pha.exlap) from pseudo_hits_annot as pha"
					+ " join pseudo_hits as ph on pha.hit_idx = ph.idx "
					+ " join pseudo_annot as pa  on pa.idx = pha.annot_idx "
					+ " join xgroups as g on g.idx = pa.grp_idx"
					+ " where ph.pair_idx=" + pairIdx + " and g.proj_idx=" + proj1Idx); 
			ex2olap  = dbc2.executeFloat("select AVG(pha.exlap) from pseudo_hits_annot as pha"
					+ " join pseudo_hits as ph on pha.hit_idx = ph.idx "
					+ " join pseudo_annot as pa  on pa.idx = pha.annot_idx "
					+ " join xgroups as g on g.idx = pa.grp_idx"
					+ " where ph.pair_idx=" + pairIdx + " and g.proj_idx=" + proj2Idx); 
		}
		// average of a percentage
		String sCHcGN1 = (isAlgo1) ? String.format("%.1f%s   n/a", g1olap, "%") : 
			                        String.format("%.1f%s  %.1f%s", g1olap, "%", ex1olap, "%"); 
		String sCHcGN2 = (isAlgo1) ? String.format("%.1f%s   n/a", g2olap, "%") : 
									String.format("%.1f%s  %.1f%s", g2olap, "%", ex2olap, "%"); 
	
	/* Loop1: proj1 chrN  **/	
		long chCov1=0, shCov1=0;	// Cov CH (Sh)
		int cnt11=0, cnt12=0, cnt13=0, cnt14=0;	// SH <100, etc
		
		for (int g1=0; g1<proj1.grpIdx.size(); g1++) { // for the 1st project, go through its chromosome
			System.gc();
			int grp1Idx = proj1.grpIdx.get(g1);
			int grp1Len = proj1.grpLen.get(g1);
			
			byte [] clustArr = new byte [grp1Len]; Arrays.fill(clustArr, (byte) 0);
			byte [] subArr =   new byte [grp1Len]; Arrays.fill(subArr, (byte) 0);
			  
			for (int g2=0; g2<proj2.grpIdx.size(); g2++) { // for the 2nd project, compare it's chromsomes with the 1st
				int grp2Idx = proj2.grpIdx.get(g2);	
				rs = dbc2.executeQuery("select start1, end1, query_seq, strand, gene_overlap " 
						+ " from pseudo_hits "
						+ " where pair_idx=" + pairIdx + " and grp1_idx="+grp1Idx + " and grp2_idx="+grp2Idx);
				while (rs.next()){
					int hstart = rs.getInt(1); 
					int hend = 	 rs.getInt(2);
					for (int h=hstart; h<=hend && h<grp1Len; h++) clustArr[h]++; // clusters cover of the chromosome
				
					String qseq = rs.getString(3);			// subhits
					String [] subhits = (qseq.contentEquals("")) ? null : qseq.split(",");
					int [] sstart = seqToSubhits(0, hstart, subhits);
					int [] send =   seqToSubhits(1, hend,   subhits);
					
					totSh += sstart.length;
					String strand = rs.getString(4);
					if (strand.contains("+") && strand.contains("-")) totRev++;
					
					for (int i=0; i<sstart.length; i++) { // size of subhits
						int len = send[i]-sstart[i]+1;
						if      (len<100) 	cnt11++;
						else if (len<1000) 	cnt12++;
						else if (len<5000) 	cnt13++;
						else 				cnt14++;
						
						for (int h=sstart[i]; h<=send[i] && h<grp1Len; h++) subArr[h]++; // subhits cover of the chromosome
					}
				}
				rs.close();
			}
			// process arrays for proj1
			for (byte a : clustArr) {if (a>0) chCov1++;  }
			for (byte a : subArr)   {if (a>0) shCov1++;}
			clustArr=subArr=null;
		}
		if (isSelf) {
			cnt11  /= 2; cnt12  /= 2; cnt13   /= 2; cnt14 /= 2; 
			chCov1 /= 2; shCov1 /= 2;  
			totRev /= 2; totSh  /= 2; 
		}
	/* Loop2: proj2 chrN  **/
		long chCov2=0, shCov2=0; // got overflow on human self
		int cnt21=0, cnt22=0, cnt23=0, cnt24=0;
		for (int g2=0; g2<proj2.grpIdx.size(); g2++) {
			System.gc();
			int grp2Idx = proj2.grpIdx.get(g2);
			int grp2Len = proj2.grpLen.get(g2);
			
			byte [] clustArr = new byte [grp2Len]; Arrays.fill(clustArr,(byte) 0);
			byte [] subArr =   new byte [grp2Len]; Arrays.fill(subArr,(byte) 0);
			
			for (int g1=0; g1<proj1.grpIdx.size(); g1++) {
				int grp1Idx = proj1.grpIdx.get(g1);
		
				rs = dbc2.executeQuery("select start2, end2, target_seq " 
						+ "from pseudo_hits "
						+ "where pair_idx=" + pairIdx + " and grp1_idx="+grp1Idx + " and grp2_idx="+grp2Idx);
				while (rs.next()){
					int hstart = rs.getInt(1); 
					int hend = 	 rs.getInt(2);
					for (int h=hstart; h<=hend && h<grp2Len; h++) clustArr[h]++;
				
					String seq = 	rs.getString(3);	
					String [] subhits = (seq.contentEquals("")) ? null : seq.split(",");
					int [] start = seqToSubhits(0, hstart, subhits);
					int [] end =   seqToSubhits(1, hend,   subhits);
					
					for (int i=0; i<start.length; i++) {
						int len = end[i]-start[i]+1;
						if      (len<100) 	cnt21++;
						else if (len<1000) 	cnt22++;
						else if (len<5000) 	cnt23++;
						else 				cnt24++;
						
						for (int h=start[i]; h<=end[i]&& h<grp2Len; h++) subArr[h]++;
					}
				}
				rs.close();
			}
			// process arrays for proj2 chrN
			for (byte a : clustArr) {if (a>0) chCov2++;}
			for (byte a : subArr)   {if (a>0) shCov2++;}
			clustArr=subArr=null;
		}
		if (isSelf) {
			cnt21  /= 2; cnt22  /= 2; cnt23   /= 2; cnt24 /= 2; 
			chCov2 /= 2; shCov2 /=2;  
		}
		String pGNwCH1  = pct((double)gene1Hit, (double) proj1.nGenes);
		String pGNwCH2  = pct((double)gene2Hit, (double) proj2.nGenes);
		String pCHwGN1  = pct((double)hitGene1, (double) nHits);
		String pCHwGN2  = pct((double)hitGene2, (double) nHits);

		String pchCov1 = pct((double)chCov1, (double)proj1.genomeLen); 
		String pchCov2 = pct((double)chCov2, (double)proj2.genomeLen);
		String pshCov1 = pct((double)shCov1, (double)proj1.genomeLen); 
		String pshCov2 = pct((double)shCov2, (double)proj2.genomeLen);			
	
		

	// XXX table 2
	// make cluster only table; same for both sides
		String [] field1 = {"#Clusters", "#Rev",  "  g2", "g1", "g0", " ","AvgId", "AvgSim", "  #SubHits", "AvgNum"};
		int nCol1 = field1.length;
		int [] justify1 =  new int [nCol1];
		for (int i=0; i<nCol1; i++) justify1[i]=0;
		
		int nRow1 = 1;
	    String [][] rows1 = new String[nRow1][nCol1];
	    int r=0, c=0;
	    rows1[r][c++] = String.format("%s", cntK(nHits));
	    rows1[r][c++] = String.format("%s", cntK(totRev)); 
	   
	    
	    rows1[r][c++] = String.format("  %s", cntK(hit2Gene));  
	    rows1[r][c++] = String.format("%s", cntK(hit1Gene));
	    rows1[r][c++] = String.format("%s", cntK(hit0Gene));
	    rows1[r][c++] = "   "; 								// space before AvgID
	    
	    rows1[r][c++] = String.format("%.1f%s", avgId, "%"); 
	    rows1[r][c++] = String.format("%.1f%s", avgSim, "%"); 
	    rows1[r][c++] = String.format("  %s", cntK(totSh)); 
	    rows1[r][c++] = String.format("%.1f", ((double)totSh/(double)nHits)); 
	    
	    String tab1 = util.Utilities.makeTable(nCol1, nRow1, field1, justify1, rows1);
		
	// XXX make cover table; diff for both sides
		String [] fields = {"", "GNcCH  EXcCH", " ", "GNaCH  CHaGN", " ", "GcCH   GcSH", "#SH <100b","100b-1kb","1k-5kb", ">5kb"};
		int nCol = fields.length;
		int [] justify =   new int [nCol];
		justify[0]=1;
		for (int i=1; i<nCol; i++) justify[i]=0;
		
		int nRow = 2;
	    String [][] rows = new String[nRow][nCol];
	    r=c=0;
	    
	    rows[r][c++] = proj1.name;
	    rows[r][c++] = sCHcGN1;  
	    rows[r][c++] = " ";
	    rows[r][c++] = String.format("%s  %s", pGNwCH1, pCHwGN1); 
	    rows[r][c++] = " ";
	    rows[r][c++] = String.format("%s  %s", pchCov1, pshCov1);
	    rows[r][c++] = cntK(cnt11);
	    rows[r][c++] = cntK(cnt12);
	    rows[r][c++] = cntK(cnt13);
	    rows[r][c++] = cntK(cnt14);
	    
	    r++; c=0;
	    if (isSelf) { // proj2 can be a little different 
	    	for (c=0; c<fields.length; c++) rows[r][c]="";
	    } else {
	    	rows[r][c++] = proj2.name;
	    	rows[r][c++] = sCHcGN2; 
	    	rows[r][c++] = " ";
		    rows[r][c++] = String.format("%s  %s", pGNwCH2, pCHwGN2);  
		    rows[r][c++] = " ";
		    rows[r][c++] = String.format("%s  %s", pchCov2, pshCov2);
		    rows[r][c++] = cntK(cnt21);
		    rows[r][c++] = cntK(cnt22);
		    rows[r][c++] = cntK(cnt23);
		    rows[r][c++] = cntK(cnt24);
	    }
	    String tab = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
	    
	    return title + "\n" + tab1 + "\n" + tab;
	}
	catch (Exception e) {ErrorReport.print(e, "Summary anchors"); return "error on anchors";}
	}
	private int [] seqToSubhits(int index, int val, String [] shs) {
	try {
		if (shs==null) {
			int [] arr = new int [1];
			arr[0] = val;
			return arr;
		}
		int [] arr = new int [shs.length];
		for (int i = 0; i < shs.length; i++) {
			String [] coords = shs[i].split(":");
			arr[i] = Utilities.getInt(coords[index]);
		}
		return arr;
	}
	catch (Exception e) {ErrorReport.print(e, "Coverting subhits "); return new int [0];}
	}
	
	/*****************************************************************
	 * Block section
	 */
	private String createBlock() {
		try {
			String title = "Synteny Blocks";
			if (nHits==0) return "";
			
			// hits in blocks
			int nInBlock = dbc2.executeInteger("select count(*) "
					+ "from pseudo_hits as h " 
					+ "join pseudo_block_hits as pbh on pbh.hit_idx=h.idx " 
					+ "where pair_idx=" + pairIdx); // Query: block hits/all hits
			if (isSelf) nInBlock /= 2;
			String pInBlock = pct((double)nInBlock, (double)nHits);
			
			// genes in blocks
			String sq ="select count(distinct pa.idx)"
				 +" from pseudo_annot		as pa"
				 +" join xgroups			as xg  on xg.idx=pa.grp_idx"
				 +" join pseudo_hits_annot  as pha on pha.annot_idx=pa.idx " 
				 +" join pseudo_hits        as ph  on ph.idx=pha.hit_idx "
				 +" join pseudo_block_hits  as pbh on pha.hit_idx=pbh.hit_idx"
				 +" where pa.type='gene' and ph.pair_idx= " + pairIdx + " and xg.proj_idx=";
			int nBlkGenes1 = dbc2.executeInteger(sq + proj1Idx);
			int nBlkGenes2 = dbc2.executeInteger(sq + proj2Idx);
			
			String pBlkGene1 = pct((double)nBlkGenes1, (double)proj1.nGenes); 
			String pBlkGene2 = pct((double)nBlkGenes2, (double)proj2.nGenes);
				
	/* Coverage <100kb 100kb-1Mb 1Mb-10Mb >10Mb*/
			int nblks=0, ninv = 0;
			long bcov1=0, bdcov1=0, bcov2=0, bdcov2=0; 
			int  b11=0, b12=0, b13=0, b14=0;
			int  b21=0, b22=0, b23=0, b24=0;
			ResultSet rs;
			
			/* Proj1: count blocks for each chr-chr pair **/		
			for (int g1=0; g1<proj1.nGrp; g1++) {
				int grp1Idx = proj1.grpIdx.get(g1);
				int grp1Len = proj1.grpLen.get(g1);
				byte [] arrB = new byte [grp1Len]; Arrays.fill(arrB,(byte) 0);
				
				for (int g2=0; g2<proj2.nGrp; g2++) {
					int grp2Idx = proj2.grpIdx.get(g2);
					
					rs = dbc2.executeQuery("select corr, start1, end1 from blocks "
							+ "where pair_idx=" + pairIdx + " and grp1_idx="+grp1Idx + " and grp2_idx="+grp2Idx);
					while (rs.next()) {
						nblks++;
						float corr = 	rs.getFloat(1);
						if (corr < 0)  	ninv++;
						int bstart = rs.getInt(2); 
						int bend = 	 rs.getInt(3); 
						
						int olap1 = (bend-bstart+1);
						if (olap1<100000) 		b11++;
						else if (olap1<1000000) b12++;
						else if (olap1<10000000)b13++;
						else 					b14++;
						
						for (int i=bstart; i<bend && i<grp1Len; i++) arrB[i]++; // CAS587 add <grp1Len; block end goes over the grpLen 
					}
					rs.close();
				}// finished proj1 chrN
				
				for (byte a : arrB) {if (a>0) bcov1++; if (a>1) bdcov1++;}
				arrB=null;
			}
			/* Proj2 **/		
			for (int g2=0; g2<proj2.nGrp; g2++) {
				int grp2Idx = proj2.grpIdx.get(g2);
				int grp2Len = proj2.grpLen.get(g2);
				byte [] arrB = new byte [grp2Len]; Arrays.fill(arrB,(byte) 0);
				
				for (int g1=0; g1<proj1.nGrp; g1++) {
					int grp1Idx = proj1.grpIdx.get(g1);
					
					rs = dbc2.executeQuery("select corr, start2, end2 from blocks "
							+ "where pair_idx=" + pairIdx + " and grp1_idx="+grp1Idx + " and grp2_idx="+grp2Idx);
					while (rs.next()) {
						int bstart = rs.getInt(2); 
						int bend   = rs.getInt(3); 
			
						int olap1 = (bend-bstart+1);
						if (olap1<100000) 		b21++;
						else if (olap1<1000000) b22++;
						else if (olap1<10000000)b23++;
						else 					b24++;
						
						for (int i=bstart; i<bend && i<grp2Len; i++) arrB[i]++;
					}
					rs.close();	
				}// finished proj2 chrN
				for (byte a : arrB) {if (a>0) bcov2++; if (a>1) bdcov2++;}
				arrB=null;
			}			
			if (isSelf) {
				nblks /= 2; ninv /= 2; bcov1 /= 2; bdcov1 /= 2;
				b11 /= 2; b12 /= 2; b13 /= 2; b14 /= 2;
				
				bcov2 /= 2; bdcov2 /= 2;
				b21 /= 2; b22 /= 2; b23 /= 2; b24 /= 2;
			}
			
			String sbCov1 =   pct((double)bcov1, (double)proj1.genomeLen); 
			String sbCov2 =   pct((double)bcov2, (double)proj2.genomeLen); 
			String sb2xCov1 = pct((double)bdcov1,(double)proj1.genomeLen);
			String sb2xCov2 = pct((double)bdcov2,(double)proj2.genomeLen);
		
		// XXX make block table
			String [] fields = {"","#Blocks", "Inv"," CHB", "GNaCHB", "   GcB  (2x)","  <100kb","100kb-1Mb","1Mb-10Mb",">10Mb"};
			int nCol=  fields.length;
			int [] justify =   new int [nCol];
			justify[0]=1;
			for (int i=1; i<nCol; i++) justify[i]=0;
			
			int nRow = 2;
		    String [][] rows = new String[nRow][nCol];
		    int r=0, c=0;
		    
		    rows[r][c++] = proj1.name;
		    rows[r][c++] = String.format("%,d",nblks);   
		    rows[r][c++] = String.format("%,d", ninv);			 
		    rows[r][c++] = String.format("%s",  pInBlock);
		    
		    rows[r][c++] = String.format("%s", pBlkGene1);
		    rows[r][c++] = String.format("  %s (%s)",sbCov1,sb2xCov1);
		    
		    rows[r][c++] = String.format("%,d", b11);
		    rows[r][c++] = String.format("%,d", b12);
		    rows[r][c++] = String.format("%,d", b13);
		    rows[r][c++] = String.format("%,d", b14);
		    
		    r++; c=0;
		    
		    if (isSelf) { // proj2 can be a little different, 
		    	for (c=0; c<fields.length; c++) rows[r][c]="";
		    } else {
		    	rows[r][c++] = proj2.name;
			    rows[r][c++] = " ";// same for both
			    rows[r][c++] = " ";
			    rows[r][c++] = "";
			    rows[r][c++] = String.format("%s", pBlkGene2);
			    rows[r][c++] = String.format("  %s (%s)",sbCov2,sb2xCov2);
			    
			    rows[r][c++] = String.format("%,d", b21);
			    rows[r][c++] = String.format("%,d", b22);
			    rows[r][c++] = String.format("%,d", b23);
			    rows[r][c++] = String.format("%,d", b24);	    
		    }
		    String tab = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
		    
		    return title + "\n" + tab;
		}
		catch (Exception e) {ErrorReport.print(e, "Making blocks summary"); return "error on blocks";}
		}
	
	/*************************************************************************/
	private String createCollinear() {
	try {
		String title = "Collinear Sets";
		if (nHits==0) return "";
		int nSetsHits = dbc2.executeInteger("SELECT count(*) FROM pseudo_hits WHERE runnum>0 and pair_idx=" + pairIdx);
		if (nSetsHits==0) return "Collinear \n   None\n";
	
		int n2=0, n3=0, n4=0, n5=0, n6=0, n7=0, n8=0, n9=0, n14=0,n19=0,n20=0, nGenes=0; 
		
		TreeSet <String> setMap = new TreeSet <String>  ();
		
		String sq = "select grp1_idx, grp2_idx, runnum, runsize from pseudo_hits where runnum>0 and pair_idx=" + pairIdx;
		if (isSelf) {
			sq += " and ((grp1_idx>grp2_idx and refidx=0) or (grp1_idx=grp2_idx and start1>start2))";
		}
		ResultSet rs = dbc2.executeQuery(sq);
		while (rs.next()) {
			int grp1 = rs.getInt(1);
			int grp2 = rs.getInt(2);
			int num = rs.getInt(3);
			int sz = rs.getInt(4);
			
			String key = grp1+"."+grp2+"."+num;
			if (setMap.contains(key)) continue;
			setMap.add(key);
			
			nGenes += sz;
			if (sz==2) n2++;
			else if (sz==3) n3++;
			else if (sz==4) n4++;
			else if (sz==5) n5++;
			else if (sz==6) n6++;
			else if (sz==7) n7++;
			else if (sz==8) n8++;
			else if (sz==9) n9++;
			else if (sz<=14) n14++;
			else if (sz<=19) n19++;
			else n20++;
		}
		rs.close();
		
		String [] fields = {"Sets","  =2","  =3","  =4", "  =5", "  =6", "  =7", "  =8", "  =9", "10-14", "15-19", " >=20", "#Genes"};
		int [] justify =   new int[fields.length];
		for (int i=0; i<fields.length; i++) justify[i]=0;
		int nRow = 1;
	    int nCol=  fields.length;
	    String [][] rows = new String[nRow][nCol];
	    int r=0, c=0;
	    rows[r][c++] = String.format("%,d", setMap.size());
	    rows[r][c++] = String.format("%,d", n2);
	    rows[r][c++] = String.format("%,d", n3);
	    rows[r][c++] = String.format("%,d", n4);
	    rows[r][c++] = String.format("%,d", n5);
	    rows[r][c++] = String.format("%,d", n6);
	    rows[r][c++] = String.format("%,d", n7);
	    rows[r][c++] = String.format("%,d", n8);
	    rows[r][c++] = String.format("%,d", n9);
	    rows[r][c++] = String.format("%,d", n14);
	    rows[r][c++] = String.format("%,d", n19);
	    rows[r][c++] = String.format("%,d", n20);
	    rows[r][c++] = String.format("%,d", nGenes);
	    
	    String tab = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
		return title + "\n" + tab;
	}
	catch (Exception e) {ErrorReport.print(e, "Making collinear summary"); return "error on collinear";}
	}
	/*****************************************************************/
	private String [] createParams() {
	String [] info = {"", "error on params"};
	try {	
		String alignDate = null, syver=null, allParams=null; 
		ResultSet rs = dbc2.executeQuery("select aligndate, params, syver from pairs where idx=" + pairIdx);
		if (rs.next()) { 				
			alignDate = rs.getString(1);
			allParams = rs.getString(2);  // these are the params that were used for align
			syver = rs.getString(3);
		}
		rs.close();
		
		if (allParams==null) { // if MUMmer fails...
			allParams  = "Align Unknown";
		}
		else {
			String [] x = allParams.split("\n");
			allParams = "";
			for (int i=0; i<x.length; i++) if (x[i]!=null) allParams += x[i] + "\n"; // update collinear leaves this null
			allParams += mp.getChgClustSyn(Mpair.DB); // includes cluster params
		}
		String d = (alignDate==null) ? "Date Unknown"      : Utilities.getNormalizedDate(alignDate); 
		String v = (syver==null)     ? "  Version Unknown" : ("  " + syver);
		
		info[0] = "   Updated " + d + v + "\n\n"; 
		info[1] = allParams;
		return info;
	}
	catch (Exception e) {ErrorReport.print(e, "Making params"); return info;}
	}
	/**********************************************************************/
	private String cntK(int len) {
		double d = (double) len;
		
		if (len>=1000000000) {
			d = d/1000000.0;
			return String.format("%,dM", (int) d);
		}
		else if (len>=1000000) {
			d = d/1000.0;
			return String.format("%,dk", (int) d);
		}
		return String.format("%,d", len);
	}
	
	private String pct(double t, double b) { 
		String ret;
		if (b==0) 		ret = "n/a";
		else if (t==0) 	ret = "0%";
		else {
			double x = ((double)t/(double)b)*100.0;
			if (x<0) Globals.tprt(String.format("%,d  %,d   %.3f", (long)t, (long)b, x));
			ret = String.format("%4.1f%s", x, "%");
		}
		return String.format("%5s", ret);
	}
	
	/**********************************************************************
	 * Mproject does not have group lengths
	 */
	private Proj makeProjObj(int projIdx) {	
	try {
		Proj p = new Proj();
		ResultSet rs = dbc2.executeQuery("select x.idx, p.length "
				+ " from xgroups as x join pseudos as p on x.idx=p.grp_idx where x.proj_idx=" + projIdx);
		while (rs.next()) {
			p.grpIdx.add(rs.getInt(1));
			p.grpLen.add(rs.getInt(2));
			p.genomeLen += rs.getLong(2);
		}
		p.nGrp = p.grpIdx.size();
		return p;
	}
	catch (Exception e) {ErrorReport.print(e, "Making grp map"); return null;}
	}
	private class Proj {
		String name;
		long genomeLen=0;
		int nGenes=0, nGrp=0;
		Vector <Integer> grpIdx = new Vector <Integer> ();
		Vector <Integer> grpLen = new Vector <Integer> ();
	}
}
