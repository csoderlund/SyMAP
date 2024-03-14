package symap.manager;

import java.awt.BorderLayout;
import java.awt.Color;
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
import java.util.TreeMap;
import java.sql.ResultSet;

import backend.Constants;
import backend.Utils;

import database.DBconn2;
import util.ErrorReport;
import util.Utilities;

/**********************************************************
 * Creates the Summary window from the Summary Button on the main panel
 * The Summary is of the alignment from two different sequence projects
 * 
 * CAS42 1/7/18 Made the tables a little bigger as the 2nd row was partially chopped
 * CAS500 Split the file into methods, used numbers of Mysql get, added FPC-Seq
 * CAS520 removed FPC
 * CAS540 changed from using Java table stuff to using text Utilities.makeTable
 * 		  changed statistics computed; changed coverage algorithm
 * Old vs New:
 * Anchors: %InBlock => Blocks SCH, Annotated => CHwGN, Coverage => Cover SH (slight difference); Ranges CL=>SH
 * Blocks: Coverage => Cover SB, Double => 2x, 
 *         %GeneHit  was "Percentage of genes located in synteny block regions which have syntenic hits"
 *         => GNwSCH now "Percent of genes with a synteny hit"; e.g. Brap:Cabb 35%=>99.9%
 */

public class SumFrame extends JDialog implements ActionListener {
	private static final long serialVersionUID = 246739533083628235L;
	private boolean bPrtStats=Constants.PRT_STATS; // -s; this will regenerate the summary on display
	private boolean bTrace=false; 
	
	private JButton btnHelp = null, btnOK = null;
	private int pairIdx=0, proj1Idx=0, proj2Idx=0;
	private int nHits=0;
	private Mpair mp;
	private DBconn2 dbc2; // CAS540 changed DBconn to DBuser
	
	private Proj proj1=null, proj2=null; // contains idx and lengths of groups
	
	// Called from AlignProjs
	public SumFrame(DBconn2 dbc2, Mpair mp) {
		this.mp = mp;
		this.dbc2 = dbc2;
		createSummary(false);
	}
    // Called from the project manager
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
			
		btnOK = new JButton("OK"); btnOK.setBackground(Color.white);
		btnOK.addActionListener(this);
		btnHelp = new JButton("Explain"); btnHelp.setBackground(Color.white);
		btnHelp.addActionListener(this);
		JPanel buttonPanel = new JPanel();
		
		buttonPanel.add(btnHelp); buttonPanel.add(Box.createHorizontalStrut(20));
		buttonPanel.add(btnOK);
		
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(sPane,BorderLayout.CENTER);
		getContentPane().add(buttonPanel,BorderLayout.SOUTH);
		pack();
		
		setLocationRelativeTo(null);	
		// setAlwaysOnTop(true);
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
		proj1Idx = mp.getProj1Idx(); 
		proj2Idx = mp.getProj2Idx();
		
		ResultSet rs;
		if (dbc2.tableColumnExists("pairs", "summary")) { // CAS540 exist if called from Summary popup
			rs = dbc2.executeQuery("select summary from pairs where idx="+ pairIdx);
			String sum = (rs.first()) ? rs.getString(1) : null;
			if (sum!=null && sum.length()>20 && !bPrtStats) return sum;
		}
		
		proj1 = makeProjObj(proj1Idx);
		proj2 = makeProjObj(proj2Idx);
		
		proj1.name = mp.getProj1().getDisplayName();
		proj2.name = mp.getProj2().getDisplayName();
		if (isReadOnly) System.out.println("Create Summary for display");
		else System.out.println("Create Summary and save to database ");
		
		String alignDate = "Unknown", syver="Unk", params="";
		
		rs = dbc2.executeQuery("select aligndate, params, syver from pairs where idx=" + pairIdx);
		if (rs.first()) {
			alignDate = rs.getString(1);
			params = rs.getString(2);
			syver = rs.getString(3);
		}
		rs.close();

		String [] x = params.split("\n");
		params = "";
		for (int i=0; i<x.length; i++) params += x[i] + "\n";
		params += mp.getChangedParams(Mpair.DB);
				
		String d = Utilities.getNormalizedDate(alignDate);
		String v = (syver!=null) ? ("  " + syver) : "";
		
		String info = (proj1.name + " vs. " + proj2.name + "   Updated " + d + v) + "\n\n"; // CAS548 was 'Created'
		info += createProject() + "\n";
		info += createAnchor()  + "\n";
		info += createBlock()  + "\n";
		String c = createCollinear();
		if (c!=null) info += c + "\n";
		info += "Abbrev: Gene (GN), Cluster Hit (CH), SubHit (SH), Synteny Block (SB), CH in Block (CHB)\n";
		info += "________________________________________________________________________________________\n";
		info += params;
	
		if (!isReadOnly) {
			dbc2.tableCheckAddColumn("pairs", "summary", "text", null); // CAS540 new, no DB update but in Schema
			dbc2.executeUpdate("update pairs set summary='" + info + "' where idx=" + pairIdx);
		}
		return info;
	}
	catch(Exception e){ErrorReport.print(e, "Create summary"); return "summary error";}
	}
	/****************************************************************
	 * Project
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
			if (rs.first()){
				proj1.nGenes = rs.getInt(1);
				gSum1 = rs.getInt(2);
				gAvg1 = rs.getInt(3);
				gMax1 = rs.getInt(4);
			}
			rs = dbc2.executeQuery(q + proj2Idx);
			if (rs.first()){
				proj2.nGenes = rs.getInt(1);
				gSum2   = rs.getInt(2);
				gAvg2   = rs.getInt(3);
				gMax2   = rs.getInt(4);
			}
			q = "select count(*), sum(end-start+1), avg(end-start+1), max(end-start+1) from pseudo_annot as pa " +
				"join xgroups as g on pa.grp_idx=g.idx where pa.type='exon' and g.proj_idx=";
				
			rs = dbc2.executeQuery(q + proj1Idx);
			if (rs.first()){
				nExon1 = rs.getInt(1);
				eSum1 = rs.getInt(2);
				eAvg1 = rs.getInt(3);
				eMax1 = rs.getInt(4);
			}
			rs = dbc2.executeQuery(q + proj2Idx);
			if (rs.first()){
				nExon2 = rs.getInt(1);
				eSum2   = rs.getInt(2);
				eAvg2   = rs.getInt(3);
				eMax2  = rs.getInt(4);
			}
			rs.close();
			
		// make table
			String [] fields = {"","#Genes", "~Cover", "Avg", "Max", "  ", "#Exons", "~Cover",  "Avg", "Max", "#Seqs","Length (bp)"};
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
		    if (proj1Idx==proj2Idx) {
		    	for (c=0; c<fields.length; c++) rows[r][c]="";
		    }
		    else {
		    	rows[r][c++] = proj2.name;
		    	rows[r][c++] = String.format("%,d",  proj2.nGenes);
		    	rows[r][c++] = String.format("%s",    pct(gSum2,proj2.genomeLen));
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
	 * CAS540 rewrote for diff results and diff algo for coverage (slightly more accurate; see createAnchorOrig)
	 * CHwGN: query both+one with proj1/nHits   
	 * GNwCH: query (#genes-orphans)/#genes
	 * Cover CH: if there is NO overlap, sum Hlen/genomeLen; otherwise, must take into account embedded vs overlap.
	 * Cover SH: none of these numbers can be computed from the interface
	 ***************************************************************/
	private String createAnchor() {
	try {
		String title = "Cluster Hits";
		long startTime = Utils.getTime();
		
		nHits = dbc2.executeInteger("select count(*) from pseudo_hits where pair_idx=" + pairIdx); 
		if (nHits<=0) return title + "\n No hits";
		
		String sq;
		// %Genes with hits 
		sq = "select count(distinct pha.annot_idx) "
				+ " from pseudo_annot      as pa "
				+ " join pseudo_hits_annot as pha on pa.idx = pha.annot_idx"
				+ " join pseudo_hits       as ph  on ph.idx = pha.hit_idx  "
				+ " join xgroups            as xg on xg.idx = pa.grp_idx "
				+ " where pa.type='gene' and ph.pair_idx=" + pairIdx + " and xg.proj_idx=";
		int gene1Hit = dbc2.executeInteger(sq + proj1Idx); // query: gene1Hit = #totGene1-#orphan genes
		int gene2Hit = dbc2.executeInteger(sq + proj2Idx);
		
		// %Hits with genes
		sq = "select count(distinct ph.idx) "
						+ "from pseudo_hits       as ph " 
						+ "join pseudo_hits_annot as pha on pha.hit_idx = ph.idx " 
						+ "join pseudo_annot      as pa  on pa.idx      = pha.annot_idx " 
						+ "join xgroups           as xg  on xg.idx      = pa.grp_idx "
						+ "where pair_idx=" + pairIdx;
		int hitGene1 = dbc2.executeInteger(sq  + " and ph.annot1_idx>0 and xg.proj_idx=" + proj1Idx); 
		int hitGene2 = dbc2.executeInteger(sq  + " and ph.annot2_idx>0 and xg.proj_idx=" + proj2Idx); 
		
		int hit2Gene = dbc2.executeInteger("select count(*) from pseudo_hits where gene_overlap=2 and pair_idx=" + pairIdx);
		int hit1Gene = dbc2.executeInteger("select count(*) from pseudo_hits where gene_overlap=1 and pair_idx=" + pairIdx);
		int hit0Gene = dbc2.executeInteger("select count(*) from pseudo_hits where gene_overlap=0 and pair_idx=" + pairIdx);
		String pCHwGN1  = pct((double)hitGene1, (double) nHits);
		String pCHwGN2  = pct((double)hitGene2, (double) nHits);
		
/* Coverage */
		ResultSet rs;
		int totSh=0, totRev=0;
		
	/* Loop1: proj1 chrN  **/	
		int chCov1=0, chCov1x=0, shCov1=0, shCov1x=0, cnt11=0, cnt12=0, cnt13=0, cnt14=0;
		int chCov1sum=0;
		
		for (int g1=0; g1<proj1.grpIdx.size(); g1++) {
			System.gc();
			int grp1Idx = proj1.grpIdx.get(g1);
			int grp1Len = proj1.grpLen.get(g1);
			
			byte [] clustArr = new byte [grp1Len]; Arrays.fill(clustArr,(byte) 0);
			byte [] subArr =   new byte [grp1Len]; Arrays.fill(subArr,(byte) 0);
			  
			for (int g2=0; g2<proj2.grpIdx.size(); g2++) {
				int grp2Idx = proj2.grpIdx.get(g2);	
				rs = dbc2.executeQuery("select start1, end1, query_seq, strand, gene_overlap " 
						+ " from pseudo_hits "
						+ " where pair_idx=" + pairIdx + " and grp1_idx="+grp1Idx + " and grp2_idx="+grp2Idx);
				while (rs.next()){
					int hstart = rs.getInt(1); 
					int hend = 	 rs.getInt(2);
					for (int h=hstart; h<=hend && h<grp1Len; h++) clustArr[h]++; // clusters
				
					String seq = 	rs.getString(3);			// subhits
					String [] subhits = (seq.contentEquals("")) ? null : seq.split(",");
					int [] sstart = seqToSubhits(0, hstart, subhits);
					int [] send =   seqToSubhits(1, hend,   subhits);
					
					totSh += sstart.length;
					String strand = rs.getString(4);
					if (strand.contains("+") && strand.contains("-")) totRev++;
					
					for (int i=0; i<sstart.length; i++) {
						int len = send[i]-sstart[i]+1;
						if      (len<100) 	cnt11++;
						else if (len<1000) 	cnt12++;
						else if (len<5000) 	cnt13++;
						else 				cnt14++;
						
						for (int h=sstart[i]; h<=send[i] && h<grp1Len; h++) subArr[h]++;
					}
				}
				rs.close();
			}
			// process arrays for proj1 chrN; 2x, xx, xxx is only shown on -s
			for (byte a : clustArr) {if (a>0) chCov1++; if (a>1) chCov1x++; chCov1sum+=a;}
			for (byte a : subArr)   {if (a>0) shCov1++; if (a>1) shCov1x++;}
			clustArr=subArr=null;
		}
		
	/* Loop2: proj2 chrN  **/
		int chCov2=0, chCov2x=0, shCov2=0, shCov2x=0, cnt21=0, cnt22=0, cnt23=0, cnt24=0;
	
		for (int g2=0; g2<proj2.grpIdx.size(); g2++) {
			System.gc();
			int grp2Idx = proj2.grpIdx.get(g2);
			int grp2Len = proj2.grpLen.get(g2);
			
			byte [] clustArr = new byte [grp2Len]; Arrays.fill(clustArr,(byte) 0);
			byte [] subArr =   new byte [grp2Len];   Arrays.fill(subArr,(byte) 0);
			
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
			for (byte a : clustArr) {if (a>0) chCov2++; if (a>1) chCov2x++;}
			for (byte a : subArr)   {if (a>0) shCov2++; if (a>1) shCov2x++;}
			clustArr=subArr=null;
		}
		
		String pGNwCH1  = pct((double)gene1Hit, (double) proj1.nGenes);
		String pGNwCH2  = pct((double)gene2Hit, (double) proj2.nGenes);
		
		String pchCov1 = pct((double)chCov1, (double)proj1.genomeLen); 
		String pchCov2 = pct((double)chCov2, (double)proj2.genomeLen);
		String pshCov1 = pct((double)shCov1, (double)proj1.genomeLen); 
		String pshCov2 = pct((double)shCov2, (double)proj2.genomeLen);			
	
		int nInBlock = dbc2.executeInteger("select count(*) "
				+ "from pseudo_hits as h " 
				+ "join pseudo_block_hits as pbh on pbh.hit_idx=h.idx " 
				+ "where pair_idx=" + pairIdx); // Query: block hits/all hits
		String pInBlock = pct((double)nInBlock, (double)nHits);

	// make cluster only table; CAS541 left justify 
		String [] field1 = {"#Clusters", "#Rev", "CHnBlock", "#CH", "g2", "g1", "g0", " ", "#SubHits", "AvgNum"};
		int nCol1 = field1.length;
		int [] justify1 =  new int [nCol1];
		for (int i=0; i<nCol1; i++) justify1[i]=0;
		
		int nRow1 = 1;
	    String [][] rows1 = new String[nRow1][nCol1];
	    int r=0, c=0;
	    rows1[r][c++] = String.format("%s", cntK(nHits));
	    rows1[r][c++] = String.format("%s", cntK(totRev)); 
	    rows1[r][c++] = String.format("%s",  pInBlock);
	    rows1[r][c++] = "  ";
	    rows1[r][c++] = String.format("%s", cntK(hit2Gene));  
	    rows1[r][c++] = String.format("%s", cntK(hit1Gene));
	    rows1[r][c++] = String.format("%s", cntK(hit0Gene));
	    rows1[r][c++] = "  ";
	    rows1[r][c++] = String.format("%s", cntK(totSh)); 
	    rows1[r][c++] = String.format("%.1f", ((double)totSh/(double)nHits)); 
	   
	    String tab1 = util.Utilities.makeTable(nCol1, nRow1, field1, justify1, rows1);
		
	// make table	
		String [] fields = {"","GNwCH (CHwGN)", "Cover CH (SH)", "SH", "<100b","100b-1kb","1k-5kb", ">5kb"};
		int nCol = fields.length;
		int [] justify =   new int [nCol];
		justify[0]=1;
		for (int i=1; i<nCol; i++) justify[i]=0;
		
		int nRow = 2;
	    String [][] rows = new String[nRow][nCol];
	    r=c=0;
	    
	    rows[r][c++] = proj1.name;
	    rows[r][c++] = String.format("%s (%s)", pGNwCH1, pCHwGN1);  
	    rows[r][c++] = String.format("%s (%s)", pchCov1, pshCov1);
	    rows[r][c++] = " "; 
	   
	    rows[r][c++] = cntK(cnt11);
	    rows[r][c++] = cntK(cnt12);
	    rows[r][c++] = cntK(cnt13);
	    rows[r][c++] = cntK(cnt14);
	    
	    r++; c=0;
	    if (proj1Idx==proj2Idx) {
	    	for (c=0; c<fields.length; c++) rows[r][c]="";
	    }
	    else {
	    	rows[r][c++] = proj2.name;
		    rows[r][c++] = String.format("%s (%s)", pGNwCH2, pCHwGN2);  
		    rows[r][c++] = String.format("%s (%s)", pchCov2, pshCov2);
		    rows[r][c++] = "  ";
		    rows[r][c++] = cntK(cnt21);
		    rows[r][c++] = cntK(cnt22);
		    rows[r][c++] = cntK(cnt23);
		    rows[r][c++] = cntK(cnt24);
	    }
	    String tab = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
	    
	    if (bPrtStats && bTrace) {
	    	String cxCov1 = pct((double)chCov1x, (double)proj1.genomeLen); 
			String cxCov2 = pct((double)chCov2x, (double)proj2.genomeLen);
			String sxCov1 = pct((double)shCov1x, (double)proj1.genomeLen); 
			String sxCov2 = pct((double)shCov2x, (double)proj2.genomeLen);			
			
			System.out.format("   Gene with hit: Proj1 %,d   Proj2 %,d\n", gene1Hit,  gene2Hit);	
			System.out.format("   Hit with gene: Proj1 %,d (%s)  Proj2 %,d (%s)\n", hitGene1, pCHwGN1, hitGene2, pCHwGN2);
			
			System.out.format("   Cover 1x: CH %,10d %,10d    SH %,10d %,10d\n", chCov1,  chCov2, shCov1, shCov2 ); 
	    	System.out.format("   Cover 2x: CH %,10d %,10d    SH %,10d %,10d\n", chCov1x,  chCov2x,  shCov1x,  shCov2x );
	    	System.out.format("   For 1 sum: CH %,10d\n", chCov1sum);
	    			
	    	System.out.format("   Cover 1x: CH %s %s    SH %s %s\n", pchCov1,  pchCov2, pshCov1, pshCov2 ); // in summary
	    	System.out.format("   Cover 2x: CH %s %s    SH %s %s\n", cxCov1,   cxCov2,  sxCov1,  sxCov2 );
	    	Utils.prtTimeMemUsage("Anchors: ", startTime); 
	    	
			createAnchorDelete(); // -tt
		}
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
	/******************************************************************************
	 * Blocks: original for finding coverage, the rest is new
	 ****************************************************************************/
	private String createBlock() {
	try {
		String title = "Synteny Blocks";
		if (nHits==0) return "";
		
		long startTime = Utils.getTime();
		System.gc();
		ResultSet rs=null;
		
		// CAS540 was from block loop: String pGene1 = pct(nblkgene1, ng1); String pGene2 = pct(nblkgene2, ng2);
		String sq ="select count(distinct pa.idx)"
			 +" from pseudo_annot		as pa"
			 +" join xgroups			as g  on pa.grp_idx=g.idx"
			 +" join pseudo_hits_annot as pha on pha.annot_idx=pa.idx " 
			 +" join pseudo_block_hits as pbh on pha.hit_idx=pbh.hit_idx"
			 +" where pa.type='gene' and g.proj_idx=";
		
		int nBlkGenes1 = dbc2.executeInteger(sq + proj1Idx);
		int nBlkGenes2 = dbc2.executeInteger(sq + proj2Idx);
				
		String pBlkGene1 = pct((double)nBlkGenes1, (double)proj1.nGenes); // query: block hits, sort on GeneNum
		String pBlkGene2 = pct((double)nBlkGenes2, (double)proj2.nGenes);
		
		/// Coverage
		TreeMap<Integer,TreeMap<Integer,Integer>> bins2 = new TreeMap<Integer,TreeMap<Integer,Integer>>();
		TreeMap<Integer,Integer> blks = new TreeMap<Integer,Integer>();
		int nblks = 0, ninv = 0;
		
		rs = dbc2.executeQuery("select grp1_idx,grp2_idx, start1,end1,start2,end2, corr  " +
				"from blocks where pair_idx=" + pairIdx);
		while (rs.next()) {
			nblks++;
			float corr = 	rs.getFloat(7);
			if (corr < 0)  	ninv++;
			int idx = 		rs.getInt(1); 
			long start = 	rs.getInt(3); // start1
			int end = 		rs.getInt(4); // end1
			assert(end>start);
				
			if (!bins2.containsKey(idx))  bins2.put(idx, new TreeMap<Integer,Integer>());

			for (int b = (int)(start/1000); b <= (int)(end/1000); b++){
				if (!bins2.get(idx).containsKey(b)) bins2.get(idx).put(b,1);
				else								bins2.get(idx).put(b,1+bins2.get(idx).get(b));
			}		
			
			int logsize = (int)(Math.log10(end-start));
			if (!blks.containsKey(logsize)) blks.put(logsize, 1);
			else							blks.put(logsize, 1+blks.get(logsize));
		} 
		
		int b11=0,b21=0,b31=0,b41=0, bcov1=0,bdcov1=0;
		for (int i = 0; i <= 4; i++) {
			b11 += (blks.containsKey(i) ? blks.get(i) : 0);
		}
		b21 = (blks.containsKey(5) ? blks.get(5) : 0);
		b31 = (blks.containsKey(6) ? blks.get(6) : 0);
		for (int i = 7; i <= 20; i++) {
			b41 += (blks.containsKey(i) ? blks.get(i) : 0);
		}		
		
		for (int i : bins2.keySet()) {
			for (int j : bins2.get(i).keySet()) {
				bcov1++;
				if (bins2.get(i).get(j) > 1) bdcov1++;
			}
		}
		blks.clear(); bins2.clear();
		
		// start project2
		rs.beforeFirst();
		while (rs.next()) {
			int idx = 		rs.getInt(2);
			int start = 	rs.getInt(5);
			int end = 		rs.getInt(6);
			assert(end>start);
			
			if (!bins2.containsKey(idx)) bins2.put(idx, new TreeMap<Integer,Integer>());
	
			for (int b = (int)(start/1000); b <= (int)(end/1000); b++){
				if (!bins2.get(idx).containsKey(b))	bins2.get(idx).put(b,1);
				else								bins2.get(idx).put(b, bins2.get(idx).get(b)+1);
			}	
			int logsize = (int)(Math.log10(end-start));
			
			if (!blks.containsKey(logsize))		blks.put(logsize, 1);
			else								blks.put(logsize, 1+blks.get(logsize));
		} 
		
		int b12=0,b22=0,b32=0,b42=0, bcov2=0,bdcov2=0;
		for (int i = 0; i <= 4; i++){
			b12 += (blks.containsKey(i) ? blks.get(i) : 0);
		}
		b22 = (blks.containsKey(5) ? blks.get(5) : 0);
		b32 = (blks.containsKey(6) ? blks.get(6) : 0);
		for (int i = 7; i <= 20; i++){
			b42 += (blks.containsKey(i) ? blks.get(i) : 0);
		}		
		
		for (int i : bins2.keySet()){
			for (int j : bins2.get(i).keySet()) {	
				bcov2++;
				if (bins2.get(i).get(j) > 1) bdcov2++;
				
			}
		}
		blks.clear(); bins2.clear();				

		// convert to percents, note all lengths come out in kb due to binsize 1kb
		double lenKb1 = (int) (proj1.genomeLen/1000.0);
		double lenKb2 = (int) (proj2.genomeLen/1000.0);
		String cov1 =  pct((double) bcov1, lenKb1);
		String xcov1 = pct((double) bdcov1, lenKb1);
		String cov2 =  pct((double) bcov2, lenKb2);
		String xcov2 = pct((double) bdcov2, lenKb2);
		
		// make block table
		String [] fields = {"","#Blocks", "#Inv", "GNwCHB", "  Cover SB (2x)","<100kb","100kb-1Mb","1Mb-10Mb",">10Mb"};
		int nCol=  fields.length;
		int [] justify =   new int [nCol];
		justify[0]=1;
		for (int i=1; i<nCol; i++) justify[i]=0;
		
		int nRow = 2;
	    String [][] rows = new String[nRow][nCol];
	    int r=0, c=0;
	    
	    rows[r][c++] = proj1.name;
	    rows[r][c++] = String.format("%,d",nblks);   // same for both
	    rows[r][c++] = String.format("%,d",ninv);   // same for both
	    rows[r][c++] = String.format("%s", pBlkGene1);
	    rows[r][c++] = String.format("%s (%s)",cov1,xcov1);
	    
	    rows[r][c++] = String.format("%,d", b11);
	    rows[r][c++] = String.format("%,d", b21);
	    rows[r][c++] = String.format("%,d", b31);
	    rows[r][c++] = String.format("%,d", b41);
	    
	    r++; c=0;
	    if (proj1Idx==proj2Idx) {
	    	for (c=0; c<fields.length; c++) rows[r][c]="";
	    }
	    else {
	    	rows[r][c++] = proj2.name;
		    rows[r][c++] = " "; // same for both
		    rows[r][c++] = " ";
		    rows[r][c++] = String.format("%s", pBlkGene2);
		    rows[r][c++] = String.format("%s (%s)",cov2,xcov2);
		    
		    rows[r][c++] = String.format("%,d", b12);
		    rows[r][c++] = String.format("%,d", b22);
		    rows[r][c++] = String.format("%,d", b32);
		    rows[r][c++] = String.format("%,d", b42);	    
	    }
	    String tab = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
	    
		if (bPrtStats && bTrace) {
			Utils.prtTimeMemUsage("Blocks: ", startTime); 
			createBlockDelete();
		}
		
		return title + "\n" + tab;
	}
	catch (Exception e) {ErrorReport.print(e, "Summary blocks orig"); return "error";}
	}
	
	/*************************************************************************/
	private String createCollinear() {
	try {
		String title = "Collinear Sets";
		if (nHits==0) return "";
		int nsets = dbc2.executeInteger("SELECT count(*) "
				+ " FROM pseudo_hits WHERE runnum>0 and pair_idx=" + pairIdx);
		if (nsets==0) return "Collinear \n   None\n";
	
		int n2=0, n3=0, n4=0, n5=0, n6=0, n7=0, n8=0, n9=0, n14=0,n19=0,n20=0;
		
		TreeSet <String> setMap = new TreeSet <String>  ();
		
		ResultSet rs = dbc2.executeQuery("select grp1_idx, grp2_idx, runnum, runsize from pseudo_hits where runnum>0 and pair_idx=" + pairIdx);
		while (rs.next()) {
			int grp1 = rs.getInt(1);
			int grp2 = rs.getInt(2);
			int num = rs.getInt(3);
			int sz = rs.getInt(4);
			
			String key = grp1+"."+grp2+"."+num;
			if (setMap.contains(key)) continue;
			setMap.add(key);
			
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
		
		String [] fields = {"Sets","  =2","  =3","  =4", "  =5", "  =6", "  =7", "  =8", "  =9", "10-14", "15-19", " >=20"};
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
	    
	    String tab = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
		return title + "\n" + tab;
	}
	catch (Exception e) {ErrorReport.print(e, "Making collinear summary"); return "error on collinear";}
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
	
	private String pct(double n, double m) {
		if (n==0 || m==0) return "   0%";
		double x = (100.0*n)/m;
		if (x>99.9) return "99.9%";
		return String.format("%4.1f%s", x, "%");
	}

	/** 
	 * Original for finding cluster hit cover taking into account overlaps; can be faster but not as accurate
	 */
	private void createAnchorDelete() {
		try {
			System.gc();
			long startTime = Utils.getTime();
			int h11=0,h21=0,h31=0,h41=0, h12=0,h22=0,h32=0,h42=0;
			TreeMap<Integer,Integer> hits1 = new TreeMap<Integer,Integer>();
			TreeMap<Integer,Integer> hits2 = new TreeMap<Integer,Integer>();
			
			// ******************* Alignment lengths
			ResultSet rs = dbc2.executeQuery("select floor(log10(end1-start1)) as len, " +
					"count(*) as cnt from pseudo_hits " + 
					"where end1-start1>0 and pair_idx=" + pairIdx + " group by len");
			while (rs.next()){
				int len = rs.getInt(1);
				int cnt = rs.getInt(2);
				hits1.put(len, cnt);
			}
			rs = dbc2.executeQuery("select floor(log10(end2-start2)) as len, " +
					"count(*) as cnt from pseudo_hits " + 
					"where end2-start2>0 and pair_idx=" + pairIdx + " group by len");
			while (rs.next()){
				int len = rs.getInt(1);
				int cnt = rs.getInt(2);
				hits2.put(len, cnt);
			}
			for (int i = 0; i <= 1; i++){
				h11 += (hits1.containsKey(i) ? hits1.get(i) : 0);
				h12 += (hits2.containsKey(i) ? hits2.get(i) : 0);
			}
			for (int i = 4; i <= 20; i++){
				h41 += (hits1.containsKey(i) ? hits1.get(i) : 0);
				h42 += (hits2.containsKey(i) ? hits2.get(i) : 0);
			}		
			h21 = (hits1.containsKey(2) ? hits1.get(2) : 0);
			h22 = (hits2.containsKey(2) ? hits2.get(2) : 0);
			h31 = (hits1.containsKey(3) ? hits1.get(3) : 0);
			h32 = (hits2.containsKey(3) ? hits2.get(3) : 0);
			
			// Coverage
			// Calculate the hit coverage on each side, accounting for double covering by using bins of 100 bp.
			// This should be accurate enough.
			// The alternative is to do a full merge of overlapping hit ranges, which is more painful and memory-intensive. 
			// (There should in fact not be overlapping hits because of our clustering, but we don't want to rely on this.)
			int hcov1=0,hcov2=0;
			TreeMap<Integer,TreeSet<Integer>> bins = new TreeMap<Integer,TreeSet<Integer>>();
			
			// Do the query twice to save memory. With query cache it should not be much slower.
			rs = dbc2.executeQuery("select grp1_idx,start1,end1 from pseudo_hits where pair_idx=" + pairIdx);
			while (rs.next()){
				int idx = rs.getInt(1);
				int start1 = rs.getInt(2);
				int end1 = rs.getInt(3);
				if (!bins.containsKey(idx)) bins.put(idx, new TreeSet<Integer>());
				
				for (int b = (int)(start1/100); b <= (int)(end1/100); b++){
					bins.get(idx).add(b);
				}
			}
			
			for (int b : bins.keySet()) {
				hcov1 += bins.get(b).size();
			}
			hcov1 /= 10; // to kb
			
			// Do the query twice to save memory. With query cache it should not be much slower.
			rs = dbc2.executeQuery("select grp2_idx,start2,end2 from pseudo_hits where pair_idx=" + pairIdx);
			bins.clear();
			
			while (rs.next()) {
				int idx = rs.getInt(1);
				int start2 = rs.getInt(2);
				int end2 = rs.getInt(3);
				if (!bins.containsKey(idx)) bins.put(idx, new TreeSet<Integer>());
				
				for (int b = (int)(start2/100); b <= (int)(end2/100); b++){
					bins.get(idx).add(b);
				}
			}
			for (int b : bins.keySet()) {
				hcov2 += bins.get(b).size();
			}
			hcov2 /= 10; // to kb
			bins.clear();
			bins = null;
			
			String pcov1 = pct((double) hcov1,(proj1.genomeLen/1000.0));
			String pcov2 = pct((double) hcov2,(proj2.genomeLen/1000.0));
			
			// make table	
			String [] fields = {"","#Clusters", "Cover CH", "<100b","100b-1kb","1k-5kb", ">5kb"};
			int nCol = fields.length;
			int [] justify =   new int [nCol];
			justify[0]=1;
			for (int i=1; i<nCol; i++) justify[i]=0;
			
			int nRow = 2;
		    String [][] rows = new String[nRow][nCol];
		    int r=0, c=0;
		    
		    rows[r][c++] = proj1.name;
		    rows[r][c++] = String.format("%,d", nHits); // same for both 
		    rows[r][c++] = String.format("%s", pcov1);
		   
		    rows[r][c++] = cntK(h11);
		    rows[r][c++] = cntK(h21);
		    rows[r][c++] = cntK(h31);
		    rows[r][c++] = cntK(h41);
		    
		    r++; c=0;
		    if (proj1Idx==proj2Idx) {
		    	for (c=0; c<fields.length; c++) rows[r][c]="";
		    }
		    else {
		    	rows[r][c++] = proj2.name;
			    rows[r][c++] = ""; // same for both
			    rows[r][c++] = String.format("%s", pcov2);  
			   
			    rows[r][c++] = cntK(h12);
			    rows[r][c++] = cntK(h22);
			    rows[r][c++] = cntK(h32);
			    rows[r][c++] = cntK(h42);
		    }
		    String tab = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
		    System.out.println("Orig Anchors\n" + tab);
			Utils.prtTimeMemUsage("Orig Anchors: ", startTime); 
		}
		catch (Exception e) {ErrorReport.print(e, "Summary anchors");}
	}
	
	/***********************************************************************
	 * CAS540 rewrote - same results but slower
	 */
	private String createBlockDelete() {
	try {
		String title = "Synteny Blocks";
		if (nHits==0) return "";
		
		long startTime = Utils.getTime();
		ResultSet rs;
		int nInBlock = dbc2.executeInteger("select count(*) "
						+ "from pseudo_hits as h " 
						+ "join pseudo_block_hits as pbh on pbh.hit_idx=h.idx " 
						+ "where pair_idx=" + pairIdx); // Query: block hits/all hits
		String pInBlock = pct((double)nInBlock, (double)nHits);
	
		// slow query
		String sq ="select count(distinct pa.idx)"
			 +" from pseudo_annot		as pa"
			 +" join xgroups			as g  on pa.grp_idx=g.idx"
			 +" join pseudo_hits_annot as pha on pha.annot_idx=pa.idx " 
			 +" join pseudo_block_hits as pbh on pha.hit_idx=pbh.hit_idx"
			 +" where pa.type='gene' and g.proj_idx=";
		
		int nBlkGenes1 = dbc2.executeInteger(sq + proj1Idx);
		int nBlkGenes2 = dbc2.executeInteger(sq + proj2Idx);
				
		String pBlkGene1 = pct((double)nBlkGenes1, (double)proj1.nGenes); 
		String pBlkGene2 = pct((double)nBlkGenes2, (double)proj2.nGenes);
			
/* Coverage */
		int nblks=0, ninv = 0;
		int bcov1=0, bdcov1=0, b11=0, b12=0, b13=0, b14=0;
		int bcov2=0, bdcov2=0, b21=0, b22=0, b23=0, b24=0;
		
		/* Proj1: process chrN against all others to catch duplications **/		
		for (int g1=0; g1<proj1.nGrp; g1++) {
			System.gc();
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
					
					for (int i=bstart; i<bend; i++) arrB[i]++;
				}
				rs.close();
			}// finished proj1 chrN
			
			for (byte a : arrB) {if (a>0) bcov1++; if (a>1) bdcov1++;}
			arrB=null;
		}
		/* Proj1: process chrN against all others to catch duplications **/		
		for (int g2=0; g2<proj2.nGrp; g2++) {
			System.gc();
			int grp2Idx = proj2.grpIdx.get(g2);
			int grp2Len = proj2.grpLen.get(g2);
			byte [] arrB = new byte [grp2Len]; Arrays.fill(arrB,(byte) 0);
			
			for (int g1=0; g1<proj1.nGrp; g1++) {
				int grp1Idx = proj1.grpIdx.get(g1);
				
				rs = dbc2.executeQuery("select corr, start2, end2 from blocks "
						+ "where pair_idx=" + pairIdx + " and grp1_idx="+grp1Idx + " and grp2_idx="+grp2Idx);
				while (rs.next()) {
					int bstart = 	rs.getInt(2); 
					int bend = 	rs.getInt(3); 
					
					int olap1 = (bend-bstart+1);
					if (olap1<100000) 		b21++;
					else if (olap1<1000000) b22++;
					else if (olap1<10000000)b23++;
					else 					b24++;
					
					for (int i=bstart; i<bend; i++) arrB[i]++;
				}
				rs.close();	
			}// finished proj2 chrN
			for (byte a : arrB) {if (a>0) bcov2++;if (a>1) bdcov2++;}
			arrB=null;
		}
		String sbCov1 =   pct((double)bcov1, (double)proj1.genomeLen); 
		String sbCov2 =   pct((double)bcov2, (double)proj2.genomeLen); 
		String sb2xCov1 = pct((double)bdcov1,(double)proj1.genomeLen);
		String sb2xCov2 = pct((double)bdcov2,(double)proj2.genomeLen);
	
	// make block table
		String [] fields = {"","#Blocks", "CHB","GNwCHB", "  Cover SB (2x)","<100kb","100kb-1Mb","1Mb-10Mb",">10Mb"};
		int nCol=  fields.length;
		int [] justify =   new int [nCol];
		justify[0]=1;
		for (int i=1; i<nCol; i++) justify[i]=0;
		
		int nRow = 2;
	    String [][] rows = new String[nRow][nCol];
	    int r=0, c=0;
	    
	    rows[r][c++] = proj1.name;
	    rows[r][c++] = String.format("%,d (%,d)",nblks, ninv);   // same for both
	    rows[r][c++] = String.format("%s", pInBlock);// same for both
	    
	    rows[r][c++] = String.format("%s", pBlkGene1);
	    rows[r][c++] = String.format("%s (%s)",sbCov1,sb2xCov1);
	    
	    rows[r][c++] = String.format("%,d", b11);
	    rows[r][c++] = String.format("%,d", b12);
	    rows[r][c++] = String.format("%,d", b13);
	    rows[r][c++] = String.format("%,d", b14);
	    
	    r++; c=0;
	    if (proj1Idx==proj2Idx) {
	    	for (c=0; c<fields.length; c++) rows[r][c]="";
	    }
	    else {
	    	rows[r][c++] = proj2.name;
		    rows[r][c++] = " ";// same for both
		    rows[r][c++] = " ";
		    rows[r][c++] = String.format("%s", pBlkGene2);
		    rows[r][c++] = String.format("%s (%s)",sbCov2,sb2xCov2);
		    
		    rows[r][c++] = String.format("%,d", b21);
		    rows[r][c++] = String.format("%,d", b22);
		    rows[r][c++] = String.format("%,d", b23);
		    rows[r][c++] = String.format("%,d", b24);	    
	    }
	    String tab = util.Utilities.makeTable(nCol, nRow, fields, justify, rows);
	    
	    if (bPrtStats) {
	    	System.out.println("New " + title + "\n" + tab);
	    	Utils.prtTimeMemUsage("New Blocks: ", startTime); 
		}
	    return title + "\n" + tab;
	}
	catch (Exception e) {ErrorReport.print(e, "Making blocks summary"); return "error on blocks";}
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
			p.genomeLen += rs.getInt(2);
		}
		p.nGrp = p.grpIdx.size();
		return p;
	}
	catch (Exception e) {ErrorReport.print(e, "Making grp map"); return null;}
	}
	private class Proj {
		String name;
		int genomeLen=0;
		int nGenes=0, nGrp=0;
		Vector <Integer> grpIdx = new Vector <Integer> ();
		Vector <Integer> grpLen = new Vector <Integer> ();
	}
}
