package symap.projectmanager.common;

/**********************************************************
 * Creates the Summary window from the Summary Button on the main panel
 * The Summary is of the alignment from two different sequence projects
 * 
 * CAS42 1/7/18 Made the tables a little bigger as the 2nd row was partially chopped
 * CAS500 Split the file into methods, used numbers of Mysql get, added FPC-Seq
 */
import javax.swing.*;

import backend.Constants;
import backend.UpdatePool;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.sql.*;

import util.DatabaseReader;
import util.ErrorReport;
import util.Utilities;

import java.util.TreeSet;
import java.util.TreeMap;

public class SumFrame extends JFrame
{
	private static final long serialVersionUID = 246739533083628235L;
	private final int WIDTH = 800;
	private JTable spTbl = null, hTbl = null, bTbl = null, fTbl=null;
	private JScrollPane spTblPane = null, hTblPane = null, bTblPane = null, fTblPane=null;
	private JPanel headerRow = null;
	private JButton btnHelp = null;
	
	private JPanel panel = new JPanel();
	private JScrollPane scroller = new JScrollPane(panel);
	private Statement s = null;
	private int pair_idx=0, pidx1=0, pidx2=0;
	private int lenKb1=0, lenKb2=0;
	private String pName1, pName2, params="";
	private long ctg_len=0, seq2_len=0;
	
    // This one is called from the project manager
	public SumFrame(DatabaseReader db, int idx1, int idx2) 
	{
		super("SyMAP Summary");
		pidx1=idx1; pidx2=idx2;
		
		// The width and height is set in the ProjectManagerFrameCommon for the Desktop
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		addComponentListener(new ComponentListener() {
			public void componentHidden(ComponentEvent arg0) {}
			public void componentMoved(ComponentEvent arg0) {}

			public void componentResized(ComponentEvent arg0) {
				headerRow.setPreferredSize(
					new Dimension(Math.max(WIDTH, getContentPane().getSize().width), headerRow.getPreferredSize().height));
				headerRow.setMaximumSize(headerRow.getPreferredSize());
				headerRow.setMinimumSize(headerRow.getPreferredSize());
				if (spTbl == null) return;
				
				spTbl.setPreferredScrollableViewportSize(
						new Dimension(Math.max(WIDTH, getContentPane().getSize().width), spTbl.getRowHeight() * (spTbl.getRowCount()+2)));
				spTblPane.setMaximumSize(spTbl.getPreferredScrollableViewportSize());
				spTblPane.setMinimumSize(spTbl.getPreferredScrollableViewportSize());
				
				hTbl.setPreferredScrollableViewportSize(
						new Dimension(Math.max(WIDTH, getContentPane().getSize().width), hTbl.getRowHeight() * (hTbl.getRowCount()+2)));
				hTblPane.setMaximumSize(hTbl.getPreferredScrollableViewportSize());
				hTblPane.setMinimumSize(hTbl.getPreferredScrollableViewportSize());
				
				bTbl.setPreferredScrollableViewportSize(
						new Dimension(Math.max(WIDTH, getContentPane().getSize().width), bTbl.getRowHeight() * (bTbl.getRowCount()+2)));
				bTblPane.setMaximumSize(bTbl.getPreferredScrollableViewportSize());
				bTblPane.setMinimumSize(bTbl.getPreferredScrollableViewportSize());
				
				if (fTbl!=null) {
					fTbl.setPreferredScrollableViewportSize(
							new Dimension(Math.max(WIDTH, getContentPane().getSize().width), fTbl.getRowHeight() * (fTbl.getRowCount()+2)));
					fTblPane.setMaximumSize(fTbl.getPreferredScrollableViewportSize());
					fTblPane.setMinimumSize(fTbl.getPreferredScrollableViewportSize());
				}
			}
			public void componentShown(ComponentEvent arg0) {}
		});
		ResultSet rs;
		try
		{
			headerRow = new JPanel();
			headerRow.setLayout(new BoxLayout(headerRow, BoxLayout.LINE_AXIS));
			headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			headerRow.setBackground(Color.WHITE);
			
			panel.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
			panel.setBackground(Color.WHITE);
		
			// CAS511 added 'params' only (not schema update)
			UpdatePool pool = new UpdatePool(db);
			boolean hasParams = false;
			if (pool.tableColumnExists("pairs", "params")) hasParams=true;
			String sql="select idx, aligndate";
			if (hasParams) sql += ", params";  
			
			String alignDate = "Unknown";
			
			s = db.getConnection().createStatement();
			rs = s.executeQuery(sql + " from pairs where proj1_idx=" + pidx1 + " and proj2_idx=" + pidx2 );
			if (rs.first()) {
				pair_idx=rs.getInt(1);
				alignDate = rs.getString(2);
				if (hasParams) params = rs.getString(3);
			}
			else {
				rs = s.executeQuery(sql + " from pairs where proj1_idx=" + pidx2 + " and proj2_idx=" + pidx1 );
				if (rs.first()){
					pair_idx = rs.getInt(1);
					alignDate = rs.getString(2);
					if (hasParams) params = rs.getString(3);
					int tmp = pidx1;
					pidx1 = pidx2;
					pidx2 = tmp;
				}
				else{
					panel.add(new JLabel("Pair has not been aligned"));
					return;
				}
			}
			
			rs = s.executeQuery("select name,type from projects where idx=" + pidx1);
			String type1="";
			if (rs.first()){
				pName1 = rs.getString(1);
				type1 = rs.getString(2);
			}
			rs = s.executeQuery("select name,type from projects where idx=" + pidx2);
			if (rs.first()) 
				pName2 = rs.getString(1);
			
			rs = s.executeQuery("SELECT value FROM proj_props WHERE proj_idx=" + pidx1 + " AND name='display_name'");
			if (rs.next())
				pName1 = rs.getString(1);
			rs = s.executeQuery("SELECT value FROM proj_props WHERE proj_idx=" + pidx2 + " AND name='display_name'");
			if (rs.next())
				pName2 = rs.getString(1);
			
			String d = alignDate.substring(0, alignDate.indexOf(" "));
			headerRow.add(new JLabel(pName1 + " vs. " + pName2 + "   Created " + d));
			getContentPane().add(scroller,BorderLayout.CENTER);
			
			btnHelp = new JButton("Help");
			btnHelp.setBackground(Color.WHITE);
			btnHelp.setAlignmentX(Component.RIGHT_ALIGNMENT);
			btnHelp.setBackground(Utilities.HELP_PROMPT);
			btnHelp.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					Utilities.showHTMLPage(null, "Summary Help", "/html/SummaryHelp.html");
				}
			});
			
			JPanel tPanel = new JPanel();
			tPanel.setLayout(new BoxLayout(tPanel, BoxLayout.LINE_AXIS));
			tPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			tPanel.setBackground(Color.WHITE);
			tPanel.add(btnHelp);
			tPanel.add(Box.createHorizontalStrut(30));
			
			headerRow.add(Box.createHorizontalGlue());
			headerRow.add(tPanel);
	
			panel.add(headerRow);
			panel.add(Box.createVerticalStrut(10));
			
			boolean isFPC = type1.equals(Constants.fpcType);
			if (isFPC) {
				createProjFPC();
				panel.add(new JLabel("FPC Statistics"));
				panel.add(Box.createVerticalStrut(5));
				panel.add(fTblPane);
				panel.add(Box.createVerticalStrut(10));
				
				int tmp = pidx1; 		pidx1 = pidx2; // force it to only do seq2
				String tmpS = pName1; 	pName1 = pName2;
				createProjSeq();
				pidx1 = tmp;				pName1 = tmpS;
			}
			else {
				createProjSeq();
			}
			panel.add(new JLabel("Genome and Annotation Statistics"));
			panel.add(Box.createVerticalStrut(5));
			panel.add(spTblPane);
			panel.add(Box.createVerticalStrut(10));
			
			if (isFPC) createAnchorFPC();
			else       createAnchorSeq();
			panel.add(new JLabel("Anchor (Hit) Statistics"));
			panel.add(Box.createVerticalStrut(5));
			panel.add(hTblPane);
			panel.add(Box.createVerticalStrut(10));
			
			if (isFPC) createBlockFPC();
			else createBlockSeq();
			panel.add(new JLabel("Block Statistics"));
			panel.add(Box.createVerticalStrut(5));
			panel.add(bTblPane);
			
			panel.add(Box.createVerticalStrut(5));
			
			String [] x = params.split("::");
			for (int i=0; i<x.length; i++) {
				JPanel paramRow = new JPanel();
				paramRow.setLayout(new BoxLayout(paramRow, BoxLayout.LINE_AXIS));
				paramRow.setAlignmentX(Component.LEFT_ALIGNMENT);
				paramRow.setBackground(Color.WHITE);
				paramRow.add(new JLabel(x[i]));
				panel.add(paramRow);
			}
		}
		catch(Exception e){ErrorReport.print(e, "Create summary");}
	}
	
	/****************************************************************/
	private void createProjSeq() {
		try {
			long ngrp1=0, ngrp2=0, maxlen1=0, minlen1=0, maxlen2=0,minlen2=0;
			TreeMap<Integer,Integer> lens1 = new TreeMap<Integer,Integer>();
			TreeMap<Integer,Integer> lens2 = new TreeMap<Integer,Integer>();
			
			String q = "select count(*) as cnt," +
					"round(sum(length)/1000,1), " +
					"round(max(length)/1000,1), " +
					"round(min(length)/1000,1), " + 
					"sum(length)" + 
					"from pseudos as p join xgroups as g on p.grp_idx=g.idx " +
					"where g.proj_idx=";
			ResultSet rs = s.executeQuery(q + pidx1);
			if (rs.first())
			{
				ngrp1=rs.getInt(1);
				lenKb1 = rs.getInt(2);
				maxlen1 = rs.getInt(3);
				minlen1 = rs.getInt(4);
			}
			rs = s.executeQuery(q + pidx2);
			if (rs.first())
			{
				ngrp2=rs.getInt(1);
				lenKb2 = rs.getInt(2);
				maxlen2 = rs.getInt(3);
				minlen2 = rs.getInt(4);
				seq2_len = rs.getLong(5);
			}
			q = "select floor(log10(length)) as llen, " +
					"count(*) as cnt " +
					"from pseudos as p join xgroups as g on p.grp_idx=g.idx " + 
					"where g.proj_idx=";
			rs = s.executeQuery(q + pidx1 + " and length > 0 group by llen");
			while (rs.next())
			{
				int llen = rs.getInt(1);
				int cnt = rs.getInt(2);
				lens1.put(llen,cnt);
			}
			rs = s.executeQuery(q + pidx2 + " and length > 0 group by llen");
			while (rs.next())
			{
				int llen = rs.getInt(1);
				int cnt = rs.getInt(2);
				lens2.put(llen,cnt);
			}
			
			int c11=0,c21=0,c31=0,c41=0,c12=0,c22=0,c32=0,c42=0;
			for (int i = 0; i <= 4; i++)
			{
				c12 += (lens1.containsKey(i) ? lens1.get(i) : 0);
				c22 += (lens2.containsKey(i) ? lens2.get(i) : 0);
			}
			for (int i = 7; i <= 20; i++)
			{
				c41 += (lens1.containsKey(i) ? lens1.get(i) : 0);
				c42 += (lens2.containsKey(i) ? lens2.get(i) : 0);
			}
			c21 = (lens1.containsKey(5) ? lens1.get(5) : 0);
			c22 = (lens2.containsKey(5) ? lens2.get(5) : 0);
			c31 = (lens1.containsKey(6) ? lens1.get(6) : 0);
			c31 = (lens2.containsKey(6) ? lens2.get(6) : 0);
			
			q = "select count(*) as cnt," +
				"sum(end-start) as len " +
				"from pseudo_annot as pa " +
				"join xgroups as g on pa.grp_idx=g.idx " + 
				"where pa.type='gene' and g.proj_idx=";
			long ngenes1=0, glen1=0,ngenes2=0,glen2=0;
			rs = s.executeQuery(q + pidx1);
			if (rs.first())
			{
				ngenes1 = rs.getInt(1);
				glen1 = rs.getInt(2);
			}
			rs = s.executeQuery(q + pidx2);
			if (rs.first())
			{
				ngenes2 = rs.getInt(1);
				glen2 = rs.getInt(2);
			}
			int pctAnnot1 = Math.round((100*glen1)/(1000*lenKb1)); // note we want a %, and lens are in kb
			int pctAnnot2 = Math.round((100*glen2)/(1000*lenKb2));
			
			String[] cnames1 = {"Species","#Seqs","Total Kb","#Genes","%Genes","Max Kb","Min Kb","<100kb","100kb-1Mb","1Mb-10Mb",">10Mb"};
			if (pidx1!=pidx2) {
				Object data1[][] = { 
					{pName1,ngrp1,lenKb1,ngenes1,pctAnnot1+"%",maxlen1,minlen1,c11,c21,c31,c41},
					{pName2,ngrp2,lenKb2,ngenes2,pctAnnot2+"%",maxlen2,minlen2,c12,c22,c32,c42}};
	
				spTbl = new JTable(data1,cnames1);		
			}
			else {
				Object data1[][] = { 
						{pName1,ngrp1,lenKb1,ngenes1,pctAnnot1+"%",maxlen1,minlen1,c11,c21,c31,c41},
						{"","","","","","","","","","",""}};
				spTbl = new JTable(data1,cnames1);
			}
			spTbl.getTableHeader().setBackground(Color.WHITE);
			
			spTblPane = new JScrollPane(spTbl);
			spTblPane.setAlignmentX(Component.LEFT_ALIGNMENT);
			spTblPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			spTblPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		}
		catch (Exception e) {ErrorReport.print(e, "Create annotation");}
	}
	/****************************************************************/
	private void createAnchorSeq() {
		try {
		long nhits=0,pInBlock=0, pAnno1=0,pAnno2=0;
		int h11=0,h21=0,h31=0,h41=0,h12=0,h22=0,h32=0,h42=0;
		TreeMap<Integer,Integer> hits1 = new TreeMap<Integer,Integer>();
		TreeMap<Integer,Integer> hits2 = new TreeMap<Integer,Integer>();
		
		// #Anchors
		ResultSet rs = s.executeQuery("select count(*) from pseudo_hits where pair_idx=" + pair_idx); 
		if (rs.first()) nhits = rs.getInt(1);
		
		// %InBlocks
		rs = s.executeQuery("select count(*) from pseudo_hits as h " +
				"join pseudo_block_hits as pbh on pbh.hit_idx=h.idx " +
				"where pair_idx=" + pair_idx); 
		if (rs.first())
		{
			pInBlock = rs.getInt(1);
			pInBlock = (nhits>0) ? Math.round(100*pInBlock/nhits) : 0;
		}
		
		// %Annotated 1
		String q = "select count(distinct h.idx) from pseudo_hits as h " +
				"join pseudo_hits_annot as pha on pha.hit_idx=h.idx " +
				"join pseudo_annot as pa on pa.idx=pha.annot_idx " +
				"join xgroups as g on g.idx=pa.grp_idx where pair_idx=";
		rs = s.executeQuery(q  + pair_idx + " and g.proj_idx=" + pidx1); 
		if (rs.first())
		{
			pAnno1 = rs.getInt(1);
			pAnno1 = (nhits>0) ? Math.round(100*pAnno1/nhits) : 0;
		}
		
		// %Annotated 2
		rs = s.executeQuery(q + pair_idx + " and g.proj_idx=" + pidx2); 
		if (rs.first())
		{
			pAnno2 = rs.getInt(1);
			pAnno2 = (nhits>0) ? Math.round(100*pAnno2/nhits) : 0;
		}
		
		// Alignment lengths
		rs = s.executeQuery("select floor(log10(end1-start1)) as len, " +
				"count(*) as cnt from pseudo_hits " + 
				"where end1-start1>0 and pair_idx=" + pair_idx + " group by len");
		while (rs.next())
		{
			int len = rs.getInt(1);
			int cnt = rs.getInt(2);
			hits1.put(len, cnt);
		}
		rs = s.executeQuery("select floor(log10(end2-start2)) as len, " +
				"count(*) as cnt from pseudo_hits " + 
				"where end2-start2>0 and pair_idx=" + pair_idx + " group by len");
		while (rs.next())
		{
			int len = rs.getInt(1);
			int cnt = rs.getInt(2);
			hits2.put(len, cnt);
		}
		for (int i = 0; i <= 1; i++)
		{
			h11 += (hits1.containsKey(i) ? hits1.get(i) : 0);
			h12 += (hits2.containsKey(i) ? hits2.get(i) : 0);
		}
		for (int i = 4; i <= 20; i++)
		{
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
		long hcov1=0,hcov2=0;
		TreeMap<Integer,TreeSet<Integer>> bins = new TreeMap<Integer,TreeSet<Integer>>();
		
		// Do the query twice to save memory. With query cache it should not be much slower.
		rs = s.executeQuery("select grp1_idx,start1,end1 from pseudo_hits where pair_idx=" + pair_idx);
		while (rs.next())
		{
			int idx = rs.getInt(1);
			long start1 = rs.getInt(2);
			long end1 = rs.getInt(3);
			if (!bins.containsKey(idx)) bins.put(idx, new TreeSet<Integer>());
			
			for (int b = (int)(start1/100); b <= (int)(end1/100); b++)
			{
				bins.get(idx).add(b);
			}
		}
		
		for (int b : bins.keySet())
		{
			hcov1 += bins.get(b).size();
		}
		hcov1 /= 10; // to kb
		
		// Do the query twice to save memory. With query cache it should not be much slower.
		rs = s.executeQuery("select grp2_idx,start2,end2 from pseudo_hits where pair_idx=" + pair_idx);
		bins.clear();
		
		while (rs.next())
		{
			int idx = rs.getInt(1);
			long start2 = rs.getInt(2);
			long end2 = rs.getInt(3);
			if (!bins.containsKey(idx)) bins.put(idx, new TreeSet<Integer>());
			
			for (int b = (int)(start2/100); b <= (int)(end2/100); b++)
			{
				bins.get(idx).add(b);
			}
		}
		for (int b : bins.keySet())
		{
			hcov2 += bins.get(b).size();
		}
		hcov2 /= 10; // to kb
		bins.clear();
		bins = null;
		
		int pcov1 = (int)((100*hcov1)/lenKb1);
		int pcov2 = (int)((100*hcov2)/lenKb2);
		
		String[] cnames2 = {"Species","#Anchors","%InBlocks","%Annotated","%Coverage","<100bp","100bp-1kb","1kb-10kb",">10kb"};
		
		if (pidx1==pidx2) {
			Object[][] data2 = { 
				{pName1, nhits, pInBlock+"%",pAnno1+"%",pcov1+"%", h11,h21,h31,h41},
				{"","","","","", "","","",""}};
			hTbl = new JTable(data2,cnames2);
		}
		else {
			Object[][] data2 = { 
				{pName1,nhits,pInBlock+"%",pAnno1+"%",pcov1+"%", h11,h21,h31,h41},
				{pName2,"''", "''",        pAnno2+"%",pcov2+"%", h12,h22,h32,h42}};
			hTbl = new JTable(data2,cnames2);
		}
		hTbl.getTableHeader().setBackground(Color.WHITE);
		
		hTblPane = new JScrollPane(hTbl);
		hTblPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		hTblPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		hTblPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		}
		catch (Exception e) {ErrorReport.print(e, "Summary anchors");}
	}
	/***********************************************************************/
	// Again we'll use bins for the block overlaps, this time with size 1kb
	private void createBlockSeq() {
	try {
		ResultSet rs=null;
		
		long bcov1=0,bcov2=0,bdcov1=0,bdcov2=0;
		TreeMap<Integer,TreeMap<Integer,Integer>> bins2 = new TreeMap<Integer,TreeMap<Integer,Integer>>();
		TreeMap<Integer,Integer> blks = new TreeMap<Integer,Integer>();
		int nblks = 0, ninv = 0, nblkgene1 = 0, ng1 = 0, nblkgene2 = 0, ng2 = 0;
		
		rs = s.executeQuery("select grp1_idx,grp2_idx," +
				"start1,end1,start2,end2," +
				"corr,ngene1,genef1,ngene2,genef2 " +
				"from blocks where pair_idx=" + pair_idx);
		while (rs.next())
		{
			nblks++;
			float corr = 	rs.getFloat(7);
			if (corr < 0)  	ninv++;
			int idx = 		rs.getInt(1); 
			long start = 	rs.getInt(3); // start1
			long end = 		rs.getInt(4); // end1
			int g1 = 		rs.getInt(8); // ngene1
			int g2 = 		rs.getInt(10); // ngene2
			ng1 += g1; 
			ng2 += g2;
			float gf1 = 		rs.getFloat(9);  // genef1
			float gf2 = 		rs.getFloat(11); // genef2
			nblkgene1 += 	(g1*gf1);
			nblkgene2 += 	(g2*gf2);
			
			if (!bins2.containsKey(idx))
				bins2.put(idx, new TreeMap<Integer,Integer>());

			for (int b = (int)(start/1000); b <= (int)(end/1000); b++)
			{
				if (!bins2.get(idx).containsKey(b))
					bins2.get(idx).put(b,1);
				else
					bins2.get(idx).put(b,1+bins2.get(idx).get(b));
			}			
			int logsize = 0;
			if (end > start)
				logsize = (int)(Math.log10(end-start));
			else {
				System.out.println("block end <= start??");
				continue;
			}
			if (!blks.containsKey(logsize))
				blks.put(logsize, 1);
			else
				blks.put(logsize, 1+blks.get(logsize));
		} // end rs while 
		
		int b11=0,b21=0,b31=0,b41=0;
		for (int i = 0; i <= 4; i++)
		{
			b11 += (blks.containsKey(i) ? blks.get(i) : 0);
		}
		for (int i = 7; i <= 20; i++)
		{
			b41 += (blks.containsKey(i) ? blks.get(i) : 0);
		}		
		b21 = (blks.containsKey(5) ? blks.get(5) : 0);
		b31 = (blks.containsKey(6) ? blks.get(6) : 0);
		
		for (int i : bins2.keySet())
		{
			for (int j : bins2.get(i).keySet())
			{
				bcov1++;
				if (bins2.get(i).get(j) > 1) bdcov1++;
			}
		}
		
		// start project2
		blks.clear();
		bins2.clear();
		rs.beforeFirst();
		while (rs.next())
		{
			int idx = 		rs.getInt(2);
			long start = 	rs.getInt(5);
			long end = 		rs.getInt(6);
			if (!bins2.containsKey(idx))
				bins2.put(idx, new TreeMap<Integer,Integer>());
	
			for (int b = (int)(start/1000); b <= (int)(end/1000); b++)
			{
				if (!bins2.get(idx).containsKey(b))
					bins2.get(idx).put(b,1);
				else
					bins2.get(idx).put(b, 1+bins2.get(idx).get(b));
			}			
			int logsize = 0;
			if (end > start)
				logsize = (int)(Math.log10(end-start));
			else {
				System.out.println("block end <= start??");
				continue;
			}
			if (!blks.containsKey(logsize))
				blks.put(logsize, 1);
			else
				blks.put(logsize, 1+blks.get(logsize));
		} // end rs while
		
		int b12=0,b22=0,b32=0,b42=0;
		for (int i = 0; i <= 4; i++)
		{
			b12 += (blks.containsKey(i) ? blks.get(i) : 0);
		}
		for (int i = 7; i <= 20; i++)
		{
			b42 += (blks.containsKey(i) ? blks.get(i) : 0);
		}		
		b22 = (blks.containsKey(5) ? blks.get(5) : 0);
		b32 = (blks.containsKey(6) ? blks.get(6) : 0);
		for (int i : bins2.keySet())
		{
			for (int j : bins2.get(i).keySet())
			{
				bcov2++;
				if (bins2.get(i).get(j) > 1) bdcov2++;
			}
		}
		
		// convert to percents, note all lengths come out in kb due to binsize 1kb
		bcov1 =  (100*bcov1)/lenKb1;
		bcov2 =  (100*bcov2)/lenKb2;
		bdcov1 = (100*bdcov1)/lenKb1;
		bdcov2 = (100*bdcov2)/lenKb2;
	
		blks.clear();
		bins2.clear();				

		float genef1 = (ng1 > 0 ? ((float)nblkgene1)/((float)ng1) : 0);
		float genef2 = (ng2 > 0 ? ((float)nblkgene2)/((float)ng2) : 0);
		
		float gpct1 = ((int)(1000*genef1))/10;
		float gpct2 = ((int)(1000*genef2))/10;
			
		String[] cnames3 = {"Species","#Blocks","Inverted","%Coverage","%DoubleCov","%GenesHit","<100kb","100kb-1Mb","1Mb-10Mb",">10Mb"};
		
		if (pidx1!=pidx2) {
			Object[][] data3 = { 
				{pName1, nblks, ninv, bcov1+"%",bdcov1+"%", gpct1,b11,b21,b31,b41},
				{pName2,"''",  "''",  bcov2+"%",bdcov2+"%", gpct2,b12,b22,b32,b42}};
			bTbl = new JTable(data3,cnames3);
		}
		else {
			Object[][] data3 = { {pName1,nblks,bcov1+"%",bdcov1+"%",ninv, gpct1,b11,b21,b31,b41},
					{"","","","","", "","","","",""}};
			bTbl = new JTable(data3,cnames3);
		}
		
		bTbl.getTableHeader().setBackground(Color.WHITE);
		bTblPane = new JScrollPane(bTbl);
		bTblPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		bTblPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		bTblPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
	}
	catch (Exception e) {ErrorReport.print(e, "Making blocks summary");}
	}
	
	/********************************************************************/
	private void createProjFPC() {
	try {
		int nSeqs=0, nCtgs=0, nClone=0, nBES=0, nMrk=0;
		String sbes_len="unk", smrk_len="unk", sCB_size="";
		long bes_len=0, mrk_len=0;
		ResultSet rs;
		
		rs = s.executeQuery("select count(*) from xgroups where proj_idx=" + pidx1);
		if (rs.next()) nSeqs = rs.getInt(1);
		rs = s.executeQuery("select count(*) from clones where proj_idx=" + pidx1);
		if (rs.next()) nClone = rs.getInt(1);
		rs = s.executeQuery("select count(*), sum(size) from contigs where proj_idx=" + pidx1);
		if (rs.next()) {
			nCtgs = rs.getInt(1);
			ctg_len = rs.getLong(2);
		}
		rs = s.executeQuery("select count(*) from bes_seq where proj_idx=" + pidx1);
		if (rs.next()) nBES = rs.getInt(1);
		rs = s.executeQuery("select count(*) from markers where proj_idx=" + pidx1);
		if (rs.next()) nMrk = rs.getInt(1);
		
		rs = s.executeQuery("select value from proj_props where name='bes_len' and proj_idx=" + pidx1);
		if (rs.next()) sbes_len = rs.getString(1);
		rs = s.executeQuery("select value from proj_props where name='mrk_len' and proj_idx=" + pidx1);
		if (rs.next()) smrk_len = rs.getString(1);
		rs = s.executeQuery("select value from proj_props where name='cbsize' and proj_idx=" + pidx1);
		if (rs.next()) sCB_size = rs.getString(1);
		
		String bLenKb="-", mLenKb="-";
		try {
			bes_len = Integer.parseInt(sbes_len);
			mrk_len = Integer.parseInt(smrk_len);
			
			bLenKb = Math.round(bes_len/1000) + "";
			mLenKb = Math.round(mrk_len/1000) + "";
		}
		catch (Exception e) {/*use default - */} 
		
		String bAvg = String.format("%s (%.0f)", bLenKb, (double) bes_len/ (double)nBES);
		String mAvg = String.format("%s (%.0f)", mLenKb, (double) mrk_len/ (double)nMrk);
		
		
		String[] cnames = {"Species","#Seqs","#Contigs","#Clones", 
				"#BES","#Markers", "Ctg CB", "BES Kb (Avg)", "Mrk Kb (Avg)"};
		Object[][] data = { 
				{pName1,nSeqs,nCtgs, nClone, nBES,nMrk, ctg_len, bAvg, mAvg},
				{"","","","","", "","", "", ""}};
		fTbl = new JTable(data,cnames);
		fTbl.getTableHeader().setBackground(Color.WHITE);
		
		fTblPane = new JScrollPane(fTbl);
		fTblPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		fTblPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		fTblPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);	
	}
	catch (Exception e) {ErrorReport.print(e, "Making FPC summary");}
	}
	/**********************************************************************/
	private void createAnchorFPC() {
	try {
		ResultSet rs;
		int nBesHits=0, nMrkHits=0, nBesBlock=0, nMrkBlock=0, nBesAnno=0, nMrkAnno=0;
		int nBesCov1=0, nBesCov2=0, nMrkCov1=0, nMrkCov2=0;
		double dBesAvg1=0.0, dBesAvg2=0.0, dMrkAvg1=0.0, dMrkAvg2=0.0;
		
		rs = s.executeQuery("select count(*) from bes_hits where pair_idx=" + pair_idx); 
		if (rs.first()) nBesHits = rs.getInt(1);
		
		rs = s.executeQuery("select count(*) from mrk_hits where pair_idx=" + pair_idx); 
		if (rs.first()) nMrkHits = rs.getInt(1);
		
		// %InBlocks
		rs = s.executeQuery("select count(*) from bes_hits as h " +
				"join bes_block_hits as pbh on pbh.hit_idx=h.idx " +
				"where pair_idx=" + pair_idx); 
		if (rs.first()) nBesBlock = rs.getInt(1);
		
		rs = s.executeQuery("select count(*) from mrk_hits as h " +
				"join mrk_block_hits as pbh on pbh.hit_idx=h.idx " +
				"where pair_idx=" + pair_idx); 
		if (rs.first()) nMrkBlock = rs.getInt(1);
		
		// Annotated
		rs = s.executeQuery("select count(*) from bes_hits as h " +
				"join bes_block_hits as pbh on pbh.hit_idx=h.idx " +
				"where h.gene_overlap=1 AND pair_idx=" + pair_idx); 
		if (rs.first()) nBesAnno = rs.getInt(1);
		
		rs = s.executeQuery("select count(*) from mrk_hits as h " +
				"join mrk_block_hits as pbh on pbh.hit_idx=h.idx " +
				"where h.gene_overlap=1 AND pair_idx=" + pair_idx); 
		if (rs.first()) nMrkAnno = rs.getInt(1);
		
		// Coverage -- total length of anchored BES and Mrk
		rs = s.executeQuery("select sum(end1-start1), avg(end1-start1) from bes_hits as h " +
				"join bes_block_hits as bh on bh.hit_idx=h.idx " +
				"where end1>start1 AND pair_idx=" + pair_idx); 
		if (rs.first()) {
			nBesCov1 = rs.getInt(1);
			dBesAvg1 = rs.getDouble(2);
		}
			
		rs = s.executeQuery("select sum(end1-start1), avg(end1-start1) from mrk_hits as h " +
				"join mrk_block_hits as bh on bh.hit_idx=h.idx " +
				"where end1>start1 AND pair_idx=" + pair_idx); 
		if (rs.first()) {
			nMrkCov1 = rs.getInt(1);
			dMrkAvg1 = rs.getDouble(2);
		}
		
		rs = s.executeQuery("select sum(end2-start2), avg(end2-start2) from bes_hits as h " +
				"join bes_block_hits as bh on bh.hit_idx=h.idx " +
				"where end2>start2 AND pair_idx=" + pair_idx); 
		if (rs.first()) {
			nBesCov2 = rs.getInt(1);
			dBesAvg2 = rs.getDouble(2);
		}
			
		rs = s.executeQuery("select sum(end2-start2), avg(end2-start2) from mrk_hits as h " +
				"join mrk_block_hits as bh on bh.hit_idx=h.idx " +
				"where end2>start2 AND pair_idx=" + pair_idx); 
		if (rs.first()) {
			nMrkCov2 = rs.getInt(1);
			dMrkAvg2 = rs.getDouble(2);
		}
		
		String b1 = String.format("%5d (%3.0f)", Math.round(nBesCov1/1000), dBesAvg1);
		String m1 = String.format("%5d (%3.0f)", Math.round(nMrkCov1/1000), dMrkAvg1);
		String b2 = String.format("%5d (%3.0f)", Math.round(nBesCov2/1000), dBesAvg2);
		String m2 = String.format("%5d (%3.0f)", Math.round(nMrkCov2/1000), dMrkAvg2);
		
		String[] cnames2 = {"Species",
						"#BES Anchors", "#Mrk Anchors",
						"#BES InBlocks", "#Mrk InBlocks","" +
						"#BES Anno", "#Mrk Anno", "BES Cov Kb (Avg)", "Mrk Cov Kb (Avg)"};
		Object[][] data2 = { 
		{pName1,  nBesHits, nMrkHits, nBesBlock, nMrkBlock, "-", "-", b1, m1},
		{pName2,  "-", "-", "-", "-",            nBesAnno, nMrkAnno,  b2, m2}};
		
		hTbl = new JTable(data2,cnames2);
		hTbl.getTableHeader().setBackground(Color.WHITE);
		
		hTblPane = new JScrollPane(hTbl);
		hTblPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		hTblPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		hTblPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
	}
	catch (Exception e) {ErrorReport.print(e, "Making anchor summary");}
	}


	private void createBlockFPC() {
	try {
		int nblks=0, bcov1=0, bcov2=0, ninv=0, nBlockCtgs=0;
		long cov1=0, cov2=0;
		String sCtgs="";
		int lastS1=0, lastE1=0, lastS2=0, lastE2=0;
		
		ResultSet rs = s.executeQuery("select start1, end1, corr, ctgs1 " +
				"from blocks where pair_idx=" + pair_idx + " order by start1");
		while (rs.next())
		{
			nblks++;
			
			int start1 = rs.getInt(1);
			int end1 = rs.getInt(2);
			if (lastS1!=start1 && lastE1!=end1)
				cov1 += (end1-start1);
			lastS1=start1; lastE1=end1;
			
			double corr = rs.getDouble(3);
			if (corr<0) ninv++;
			
			sCtgs += rs.getString(4) + ",";	
		}
		 rs = s.executeQuery("select start2, end2 " +
					"from blocks where pair_idx=" + pair_idx + " order by start2");
		while (rs.next())
		{
			int start2 = rs.getInt(1);
			int end2 = rs.getInt(2);
			
			if (lastS2!=start2 && lastE2!=end2)
				cov2 += (end2-start2);
			lastS2=start2; lastE2=end2;
		}
			
		bcov1 = (int) (((double) cov1/ (double) ctg_len)  * 100.0);
		bcov2 = (int) (((double) cov2/ (double) seq2_len) * 100.0);
		
		String [] ctgs = sCtgs.split(",");
		nBlockCtgs = ctgs.length;
		
		String[] cnames3 = {"Species","#Blocks","Inverted", "Approx %Cov", "Ctg inBlock"};
		Object[][] data3 = { {pName1, nblks, ninv, bcov1+"% (CB)", nBlockCtgs},
				             {pName2, "''", "''",  bcov2+"% (Kb)", "-"}};
		bTbl = new JTable(data3,cnames3);
		bTbl.getTableHeader().setBackground(Color.WHITE);
		bTblPane = new JScrollPane(bTbl);
		bTblPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		bTblPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		bTblPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
	}
	catch (Exception e) {ErrorReport.print(e, "Making blocks summary");}
	}
	
}
