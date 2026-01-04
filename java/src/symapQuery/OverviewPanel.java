package symapQuery;

import java.util.Vector;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JLabel;

import symap.Globals;
import symap.manager.Mpair;
import symap.manager.Mproject;
import util.Jcomp;

/******************************************************
 * The Instruction panel on the right side of the query frame
 */
public class OverviewPanel extends JPanel {
	private static final long serialVersionUID = 6074102283606563987L;
	private Vector<Mproject> projVec;
	
	protected OverviewPanel(QueryFrame parentFrame) {
		qFrame = parentFrame;
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		
		buildOverview(qFrame.isSelf);
		add(mainPanel);
	}
	
	private void buildOverview(boolean isSelf) {
		mainPanel = Jcomp.createPagePanel();
		mainPanel.add(Box.createVerticalStrut(30));
		
	// Project table
		mainPanel.add(Jcomp.createItalicsBoldLabel("Selected projects:", 12)); mainPanel.add(Box.createVerticalStrut(2));
		projVec = qFrame.getProjects();
		
		int w=5, cntHasGenes=0;
		for (Mproject p : projVec) w = Math.max(w, p.getDisplayName().length());
		String fmt = "    %-" + w + "s   %-" + Globals.abbrevLen + "s  %7s   %s";	 // add Globals.abbrevLen CAS578
		
		String headings = String.format(fmt, "Name" , "Abbr", "#Genes", "Description");
		JLabel lblHead = Jcomp.createItalicsLabel(headings, 12);
		mainPanel.add(lblHead); mainPanel.add(Box.createVerticalStrut(2));

		for (Mproject mp : projVec) {
			String gc = String.format("%,d", mp.getGeneCnt());
			String line = String.format(fmt, mp.getDisplayName(), mp.getdbAbbrev() , gc, mp.getdbDesc());
			mainPanel.add(Jcomp.createMonoLabel(line, 12)); // font size
			mainPanel.add(Box.createVerticalStrut(5));
			
			if (mp.getGeneCnt()>0) cntHasGenes++;
		}
		int w1=20, w2=8; // section, items within section
		mainPanel.add(Box.createVerticalStrut(w1));
	
		String head = "<html><body style='font-family: verdana, Times New Roman; font-size: 10px'>";
		String tail = "</body></html>";
		String indent = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
		head += indent;
		
	// Notes: either gene or no gene 
		
		mainPanel.add(Jcomp.createItalicsBoldLabel("Notes:", 12)); mainPanel.add(Box.createVerticalStrut(5));
		
		if (isSelf) {
			String self = "Self-synteny: Project name ending with '" + Globals.SS_X 
					+ "' is duplicate project to show homologous hits.";
			mainPanel.add(Jcomp.createLabel(head + self + tail)); mainPanel.add(Box.createVerticalStrut(w2));
		}
		if (cntHasGenes>0) { // Algo
			if (cntHasGenes==1 && projVec.size()==2) {
				String gene = "<i>Only one of two species has genes</i>. Some filters are not relevant.";
				mainPanel.add(Jcomp.createLabel(head + gene + tail)); mainPanel.add(Box.createVerticalStrut(w2));	
			}
			String algo = qFrame.isAlgo2() ? "<i>Algo2:</i>&nbsp;  " : "<i>Algo1:</i>&nbsp;  "; 
			algo += "The Olap column is ";
			if  (qFrame.isAlgo2() && Globals.bQueryOlap==false) 
										algo += "exon overlap; Olap may be 0 if hits only overlap intron.";
			else {
				if (Globals.bQueryOlap) algo += "gene overlap; flag -go was used.";
				else 					algo += "gene overlap; at least one project used Algo1 or has no genes.";
			}
			mainPanel.add(Jcomp.createLabel(head + algo + tail)); mainPanel.add(Box.createVerticalStrut(w2));	
		}
		else {
			String gene = "<i>No genes in any species</i>. There are limited filters. Some columns are not relevant.";
			mainPanel.add(Jcomp.createLabel(head + gene + tail)); mainPanel.add(Box.createVerticalStrut(w2));	
		}
		// Pseudo - can belong to projects w/o genes
		int cntPseudo = qFrame.cntUsePseudo; 
		int cntSynteny = qFrame.cntSynteny;
		
		String pseudo = "<i>Pseudo</i>: "; 
		String pg = " numbered pseudo for un-annotated. ";
		if (projVec.size()==2)  pseudo += (cntPseudo==1) ? "Pair has " + pg : "Pair does not have " + pg;	
		else {
			if (cntPseudo>0 && cntPseudo!=cntSynteny)  {
				if (cntPseudo==1) pseudo += cntPseudo + " pair of " + cntSynteny + " has " + pg;
				else 			  pseudo += cntPseudo + " pairs of " + cntSynteny + " have " + pg;
				pseudo += getNoPseudo();
			}
			else if (cntPseudo>0) pseudo += "All pairs have " + pg;
			else 				  pseudo += "No pairs have " + pg;
		}
		mainPanel.add(Jcomp.createLabel(head + pseudo + tail)); mainPanel.add(Box.createVerticalStrut(w2));
					
		// Synteny
		if (projVec.size()!=2) {
			int nProjSyn = (projVec.size()*(projVec.size()-1))/2;
			if (nProjSyn!=cntSynteny) {
				String syn = "<i>Synteny</i>: ";
				if (cntSynteny==1) syn += cntSynteny + " pair of " + nProjSyn + " has synteny";
				else               syn += cntSynteny + " pairs of " + nProjSyn + " have synteny";
				syn += getNoSynteny();
				mainPanel.add(Jcomp.createLabel(head + syn + tail)); mainPanel.add(Box.createVerticalStrut(w2));
			}
		}
					
		mainPanel.add(Box.createVerticalStrut(w1));
		
	// instructions
		mainPanel.add(Jcomp.createItalicsBoldLabel("Instructions:", 12)); mainPanel.add(Box.createVerticalStrut(w2));
		
		String instruct =  "&gt; Query Setup: click to set filters, followed by search, producing a table of results.";
		mainPanel.add(Jcomp.createLabel(head + instruct + tail)); mainPanel.add(Box.createVerticalStrut(w2));
		
		instruct = "&gt; Results: click to view the list of query results, and to remove results.";
		mainPanel.add(Jcomp.createLabel(head + instruct + tail)); mainPanel.add(Box.createVerticalStrut(w2));
		
		instruct = "The query results are listed under the Results tab, and can be viewed by selecting one.</html";
		mainPanel.add(Jcomp.createLabel(head + instruct + tail));
	}
	private String getNoPseudo() {
		String msg="";
		int cnt=0;
		for (int i=0; i<projVec.size(); i++) {
			for (int j=i+1; j<projVec.size(); j++) {	
				String pair = projVec.get(i).getdbAbbrev() + ":" + projVec.get(j).getdbAbbrev();
				Mpair mp = qFrame.getMpair(projVec.get(i).getIdx(), projVec.get(j).getIdx());
				if (mp==null) Globals.tprt("No synteny for " + pair);
				else if (!mp.isNumPseudo(Mpair.DB) && mp.getPairIdx()>0) {
					if (!msg.equals("")) msg += ", ";
					msg += pair;
					cnt++;
				}
			}
		}
		return " (" + cnt + ": " + msg + ")";
	}
	private String getNoSynteny() {
		String msg="";
		int cnt=0;
		for (int i=0; i<projVec.size(); i++) {
			for (int j=i+1; j<projVec.size(); j++) {	
				Mpair mp = qFrame.getMpair(projVec.get(i).getIdx(), projVec.get(j).getIdx());
				if (mp==null || mp.getPairIdx()<0) {
					if (!msg.equals("")) msg += ", ";
					msg += projVec.get(i).getdbAbbrev() + ":" + projVec.get(j).getdbAbbrev();
					cnt++;
				}
			}
		}
		return " (" + cnt + ": " + msg + ")";
	}
	private QueryFrame qFrame = null;
	private JPanel mainPanel = null;
}