package symap.projectmanager.common;

/**********************************************************
 * Creates the Summary window from the Summary Button on the main panel
 * The Summary is of the alignment from two different sequence projects
 * 
 * CAS 1/7/18 Made the tables a little bigger as the 2nd row was partially chopped
 */
import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.sql.*;

import symapQuery.UserPrompt;
import util.DatabaseReader;
import util.Utilities;

import java.util.TreeSet;
import java.util.TreeMap;

public class SumFrame extends JFrame
{
	private static final long serialVersionUID = 246739533083628235L;
	private final int WIDTH = 800;
	private JTable spTbl = null;
	private JScrollPane spTblPane = null;
	private JTable hTbl = null;
	private JScrollPane hTblPane = null;
	private JTable bTbl = null;
	private JScrollPane bTblPane = null;
	private JPanel headerRow = null;
	private JButton btnHelp = null;
	
	JPanel panel = new JPanel();
	JScrollPane scroller = new JScrollPane(panel);
	Statement s = null;
    // This one is called from the project manager
	public SumFrame(DatabaseReader db, int pidx1, int pidx2) 
	{
		super("SyMAP Summary");
		// The width and height is set in the ProjectManagerFrameCommon for the Desktop
		
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		addComponentListener(new ComponentListener() {
			public void componentHidden(ComponentEvent arg0) {
			}

			public void componentMoved(ComponentEvent arg0) {
			}

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
			}
			public void componentShown(ComponentEvent arg0) {
			}
		});
		ResultSet rs;
		try
		{
			headerRow = new JPanel();
			headerRow.setLayout(new BoxLayout(headerRow, BoxLayout.LINE_AXIS));
			headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			headerRow.setBackground(Color.WHITE);
			
			s = db.getConnection().createStatement();

			panel.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
			panel.setBackground(Color.WHITE);
		
			int pair_idx = 0;
			rs = s.executeQuery("select idx from pairs where proj1_idx=" + pidx1 + " and proj2_idx=" + pidx2 );
			if (rs.first())
			{
				pair_idx=rs.getInt("idx");
			}
			else
			{
				rs = s.executeQuery("select idx from pairs where proj1_idx=" + pidx2 + " and proj2_idx=" + pidx1 );
				if (rs.first())
				{
					pair_idx = rs.getInt("idx");
					int tmp = pidx1;
					pidx1 = pidx2;
					pidx2 = tmp;
				}
				else
				{
					panel.add(new JLabel("Pair has not been aligned"));
					return;
				}
			}
			
			rs = s.executeQuery("select name,type from projects where idx=" + pidx1);
			String pName1="", pName2="", type1="", type2="";
			if (rs.first())
			{
				pName1 = rs.getString("name");
				type1 = rs.getString("type");
			}
			rs = s.executeQuery("select name,type from projects where idx=" + pidx2);
			if (rs.first())
			{
				pName2 = rs.getString("name");
				type2 = rs.getString("type");
			}
			rs = s.executeQuery("SELECT value FROM proj_props WHERE proj_idx=" + pidx1 + " AND name='display_name'");
			if (rs.next())
				pName1 = rs.getString("value");
			rs = s.executeQuery("SELECT value FROM proj_props WHERE proj_idx=" + pidx2 + " AND name='display_name'");
			if (rs.next())
				pName2 = rs.getString("value");
			
			
			headerRow.add(new JLabel("Alignment Summary - " + pName1 + " vs. " + pName2));
			getContentPane().add(scroller,BorderLayout.CENTER);
			if (type1.equals("fpc") || type2.equals("fpc"))
			{
				headerRow.add(new JLabel("Not supported for FPC projects"));
				return;
			}
			
			btnHelp = new JButton("Help");
			btnHelp.setBackground(Color.WHITE);
			btnHelp.setAlignmentX(Component.RIGHT_ALIGNMENT);
			btnHelp.setBackground(UserPrompt.PROMPT);
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
			//
// XXX First the stats for individual genomes and annotation
			//
			long ngrp1=0, len1=0, ngrp2=0, len2=0, maxlen1=0, minlen1=0, maxlen2=0,minlen2=0;
			TreeMap<Integer,Integer> lens1 = new TreeMap<Integer,Integer>();
			TreeMap<Integer,Integer> lens2 = new TreeMap<Integer,Integer>();
			rs = s.executeQuery("select count(*) as cnt,round(sum(length)/1000,1) as len, round(max(length)/1000,1) as maxl, round(min(length)/1000,1) as minl " + 
						"from pseudos as p join groups as g on p.grp_idx=g.idx where g.proj_idx=" + pidx1);
			if (rs.first())
			{
				ngrp1=rs.getInt("cnt");
				len1 = rs.getInt("len");
				maxlen1 = rs.getInt("maxl");
				minlen1 = rs.getInt("minl");
			}
			rs = s.executeQuery("select count(*) as cnt,round(sum(length)/1000,1) as len, round(max(length)/1000,1) as maxl, round(min(length)/1000,1) as minl " + "" +
					"from pseudos as p join groups as g on p.grp_idx=g.idx where g.proj_idx=" + pidx2);
			if (rs.first())
			{
				ngrp2=rs.getInt("cnt");
				len2 = rs.getInt("len");
				maxlen2 = rs.getInt("maxl");
				minlen2 = rs.getInt("minl");
			}
			rs = s.executeQuery("select floor(log10(length)) as llen, count(*) as cnt from pseudos as p join groups as g on p.grp_idx=g.idx " + "" +
					"where g.proj_idx=" + pidx1 + " and length > 0 group by llen");
			while (rs.next())
			{
				int llen = rs.getInt("llen");
				int cnt = rs.getInt("cnt");
				lens1.put(llen,cnt);
			}
			rs = s.executeQuery("select floor(log10(length)) as llen, count(*) as cnt from pseudos as p join groups as g on p.grp_idx=g.idx " + "" +
					"where g.proj_idx=" + pidx2 + " and length > 0 group by llen");
			while (rs.next())
			{
				int llen = rs.getInt("llen");
				int cnt = rs.getInt("cnt");
				lens2.put(llen,cnt);
			}
			String[] cnames1 = {"Species","#Seqs","Total Kb","#genes","%genes","Max Kb","Min Kb","<100kb","100kb-1Mb","1Mb-10Mb",">10Mb"};
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
			
			long ngenes1=0, glen1=0,ngenes2=0,glen2=0;
			rs = s.executeQuery("select count(*) as cnt,sum(end-start) as len from pseudo_annot as pa join groups as g on pa.grp_idx=g.idx " + 
					" where pa.type='gene' and g.proj_idx=" + pidx1);
			if (rs.first())
			{
				ngenes1 = rs.getInt("cnt");
				glen1 = rs.getInt("len");
			}
			rs = s.executeQuery("select count(*) as cnt,sum(end-start) as len from pseudo_annot as pa join groups as g on pa.grp_idx=g.idx " + 
					" where pa.type='gene' and g.proj_idx=" + pidx2);
			if (rs.first())
			{
				ngenes2 = rs.getInt("cnt");
				glen2 = rs.getInt("len");
			}
			int pctAnnot1 = Math.round((100*glen1)/(1000*len1)); // note we want a %, and lens are in kb
			int pctAnnot2 = Math.round((100*glen2)/(1000*len2));
			Object data1[][] = { 
					{pName1,ngrp1,len1,ngenes1,pctAnnot1+"%",maxlen1,minlen1,c11,c21,c31,c41},
					{pName2,ngrp2,len2,ngenes2,pctAnnot2+"%",maxlen2,minlen2,c12,c22,c32,c42}};
	
			spTbl = new JTable(data1,cnames1);		
			spTbl.getTableHeader().setBackground(Color.WHITE);
			
			spTblPane = new JScrollPane(spTbl);
			spTblPane.setAlignmentX(Component.LEFT_ALIGNMENT);
			spTblPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			spTblPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
	
			panel.add(Box.createVerticalStrut(10));
			panel.add(new JLabel("Genome and Annotation Statistics"));
			panel.add(Box.createVerticalStrut(5));
			panel.add(spTblPane);
			panel.add(Box.createVerticalStrut(10));
			
			//
// XXX Hit stats
			//
			long nhits=0,pctbhits=0;
			long ahits1=0,ahits2=0;
			int h11=0,h21=0,h31=0,h41=0,h12=0,h22=0,h32=0,h42=0;
			TreeMap<Integer,Integer> hits1 = new TreeMap<Integer,Integer>();
			TreeMap<Integer,Integer> hits2 = new TreeMap<Integer,Integer>();
			rs = s.executeQuery("select count(*) as nhits from pseudo_hits where pair_idx=" + pair_idx); 
			if (rs.first())
			{
				nhits = rs.getInt("nhits");
			}
			rs = s.executeQuery("select count(*) as nhits from pseudo_hits as h join pseudo_block_hits as pbh on pbh.hit_idx=h.idx " + "" +
					"where pair_idx=" + pair_idx); 
			if (rs.first())
			{
				pctbhits = rs.getInt("nhits");
				if (nhits > 0)
				{
					pctbhits = Math.round(100*pctbhits/nhits);
				}
			}
			rs = s.executeQuery("select count(distinct h.idx) as nhits from pseudo_hits as h join pseudo_hits_annot as pha on pha.hit_idx=h.idx " +
					" join pseudo_annot as pa on pa.idx=pha.annot_idx join groups as g on g.idx=pa.grp_idx " +
					"where pair_idx=" + pair_idx + " and g.proj_idx=" + pidx1); 
			if (rs.first())
			{
				ahits1 = rs.getInt("nhits");
				if (nhits > 0)
				{
					ahits1 = Math.round(100*ahits1/nhits);
				}
			}
			rs = s.executeQuery("select count(distinct h.idx) as nhits from pseudo_hits as h join pseudo_hits_annot as pha on pha.hit_idx=h.idx " +
					" join pseudo_annot as pa on pa.idx=pha.annot_idx join groups as g on g.idx=pa.grp_idx " +
					"where pair_idx=" + pair_idx + " and g.proj_idx=" + pidx2); 
			if (rs.first())
			{
				ahits2 = rs.getInt("nhits");
				if (nhits > 0)
				{
					ahits2 = Math.round(100*ahits2/nhits);
				}
			}
			rs = s.executeQuery("select floor(log10(end1-start1)) as len, count(*) as cnt from pseudo_hits " + 
					" where end1-start1>0 and pair_idx=" + pair_idx + " group by len");
			while (rs.next())
			{
				int len = rs.getInt("len");
				int cnt = rs.getInt("cnt");
				hits1.put(len, cnt);
			}
			rs = s.executeQuery("select floor(log10(end2-start2)) as len, count(*) as cnt from pseudo_hits " + 
					" where end2-start2>0 and pair_idx=" + pair_idx + " group by len");
			while (rs.next())
			{
				int len = rs.getInt("len");
				int cnt = rs.getInt("cnt");
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
			
			// Calculate the hit coverage on each side, accounting for double covering by using bins of 100 bp.
			// This should be accurate enough.
			// The alternative is to do a full merge of overlapping hit ranges, which is more painful and memory-intensive. 
			// (There should in fact not be overlapping hits because of our clustering, but we don't want to rely on this.)
			long hcov1=0,hcov2=0;
			TreeMap<Integer,TreeSet<Integer>> bins = new TreeMap<Integer,TreeSet<Integer>>();
			// Do the query twice to save memory. With query cache it should not be much slower.
			rs = s.executeQuery("select grp1_idx,grp2_idx,start1,end1,start2,end2 from pseudo_hits where pair_idx=" + pair_idx);
			while (rs.next())
			{
				int idx = rs.getInt("grp1_idx");
				long start1 = rs.getInt("start1");
				long end1 = rs.getInt("end1");
				if (!bins.containsKey(idx))
				{
					bins.put(idx, new TreeSet<Integer>());
				}
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
			
			bins.clear();
			// Do the query twice to save memory. With query cache it should not be much slower.
			rs = s.executeQuery("select grp1_idx,grp2_idx,start1,end1,start2,end2 from pseudo_hits where pair_idx=" + pair_idx);
			while (rs.next())
			{
				int idx = rs.getInt("grp2_idx");
				long start1 = rs.getInt("start2");
				long end1 = rs.getInt("end2");
				if (!bins.containsKey(idx))
				{
					bins.put(idx, new TreeSet<Integer>());
				}
				for (int b = (int)(start1/100); b <= (int)(end1/100); b++)
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
			
			int pcov1 = (int)((100*hcov1)/len1);
			int pcov2 = (int)((100*hcov2)/len2);
			
			String[] cnames2 = {"Species","#Anchors","%InBlocks","%Annotated","%Coverage","<100bp","100bp-1kb","1kb-10kb",">10kb"};
			Object[][] data2 = { {pName1,nhits,pctbhits+"%",ahits1+"%",pcov1+"%", h11,h21,h31,h41},
					{pName2,nhits,pctbhits+"%",ahits2+"%",pcov2+"%", h12,h22,h32,h42}};
			
			hTbl = new JTable(data2,cnames2);
			hTbl.getTableHeader().setBackground(Color.WHITE);
			
			hTblPane = new JScrollPane(hTbl);
			hTblPane.setAlignmentX(Component.LEFT_ALIGNMENT);
			hTblPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			hTblPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
	
			panel.add(new JLabel("Anchor Statistics"));
			panel.add(Box.createVerticalStrut(5));
			panel.add(hTblPane);
			panel.add(Box.createVerticalStrut(10));
			
			//
// XXX Now do the synteny block stats
			// Again we'll use bins for the block overlaps, this time with size 1kb
	
			long bcov1=0,bcov2=0,bdcov1=0,bdcov2=0;
			TreeMap<Integer,TreeMap<Integer,Integer>> bins2 = new TreeMap<Integer,TreeMap<Integer,Integer>>();
			TreeMap<Integer,Integer> blks = new TreeMap<Integer,Integer>();
			int nblks = 0;
			int ninv = 0;
			int nblkgene1 = 0;
			int ng1 = 0;
			int nblkgene2 = 0;
			int ng2 = 0;
			rs = s.executeQuery("select grp1_idx,grp2_idx,start1,end1,start2,end2,corr,ngene1,genef1,ngene2,genef2 " +
					" from blocks where pair_idx=" + pair_idx);
			while (rs.next())
			{
				nblks++;
				float corr = rs.getFloat("corr");
				if (corr < 0) ninv++;
				int idx = rs.getInt("grp1_idx");
				long start = rs.getInt("start1");
				long end = rs.getInt("end1");
				int g1 = rs.getInt("ngene1");
				int g2 = rs.getInt("ngene2");
				ng1 += g1; ng2 += g2;
				float gf1 = rs.getFloat("genef1");
				float gf2 = rs.getFloat("genef2");
				nblkgene1 += (g1*gf1);
				nblkgene2 += (g2*gf2);
				if (!bins2.containsKey(idx))
				{
					bins2.put(idx, new TreeMap<Integer,Integer>());
				}
				for (int b = (int)(start/1000); b <= (int)(end/1000); b++)
				{
					if (!bins2.get(idx).containsKey(b))
					{
						bins2.get(idx).put(b,1);
					}
					else
					{
						bins2.get(idx).put(b,1+bins2.get(idx).get(b));
					}
				}			
				int logsize = 0;
				if (end > start)
				{
					logsize = (int)(Math.log10(end-start));
				}
				else
				{
					System.out.println("block end <= start??");
					continue;
				}
				if (!blks.containsKey(logsize))
				{
					blks.put(logsize, 1);
				}
				else
				{
					blks.put(logsize, 1+blks.get(logsize));
				}
			}
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
					if (bins2.get(i).get(j) > 1)
					{
						bdcov1++;
					}
				}
			}
			
			blks.clear();
			bins2.clear();
			rs.beforeFirst();
			while (rs.next())
			{
				int idx = rs.getInt("grp2_idx");
				long start = rs.getInt("start2");
				long end = rs.getInt("end2");
				if (!bins2.containsKey(idx))
				{
					bins2.put(idx, new TreeMap<Integer,Integer>());
				}
				for (int b = (int)(start/1000); b <= (int)(end/1000); b++)
				{
					if (!bins2.get(idx).containsKey(b))
					{
						bins2.get(idx).put(b,1);
					}
					else
					{
						bins2.get(idx).put(b,1+bins2.get(idx).get(b));
					}
				}			
				int logsize = 0;
				if (end > start)
				{
					logsize = (int)(Math.log10(end-start));
				}
				else
				{
					System.out.println("block end <= start??");
					continue;
				}
				if (!blks.containsKey(logsize))
				{
					blks.put(logsize, 1);
				}
				else
				{
					blks.put(logsize, 1+blks.get(logsize));
				}
			}
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
					if (bins2.get(i).get(j) > 1)
					{
						bdcov2++;
					}
				}
			}
			
			// convert to percents, note all lengths come out in kb due to binsize 1kb
			bcov1 = (100*bcov1)/len1;
			bcov2 = (100*bcov2)/len2;
			bdcov1 = (100*bdcov1)/len1;
			bdcov2 = (100*bdcov2)/len2;
			
			blks.clear();
			bins2.clear();				
	
			float genef1 = (ng1 > 0 ? ((float)nblkgene1)/((float)ng1) : 0);
			float genef2 = (ng2 > 0 ? ((float)nblkgene2)/((float)ng2) : 0);
			
			float gpct1 = ((int)(1000*genef1))/10;
			float gpct2 = ((int)(1000*genef2))/10;
				
			String[] cnames3 = {"Species","#Blocks","%Coverage","%DoubleCov","Inverted","%GenesHit","<100kb","100kb-1Mb","1Mb-10Mb",">10Mb"};
			Object[][] data3 = { {pName1,nblks,bcov1+"%",bdcov1+"%",ninv, gpct1,b11,b21,b31,b41},
					{pName2,nblks,bcov2+"%",bdcov2+"%",ninv, gpct2,b12,b22,b32,b42}};
					
			bTbl = new JTable(data3,cnames3);
			bTbl.getTableHeader().setBackground(Color.WHITE);

			bTblPane = new JScrollPane(bTbl);
			bTblPane.setAlignmentX(Component.LEFT_ALIGNMENT);
			bTblPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			bTblPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
	
			panel.add(new JLabel("Block Statistics"));
			panel.add(Box.createVerticalStrut(5));
			panel.add(bTblPane);
		}
		catch(Exception e){e.printStackTrace();}
	}
}
