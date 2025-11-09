package symapQuery;

import java.util.Vector;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JLabel;

import symap.manager.Mproject;
import util.Jcomp;

/******************************************************
 * The Instruction panel on the right side of the query frame
 */
public class OverviewPanel extends JPanel {
	private static final long serialVersionUID = 6074102283606563987L;
	
	protected OverviewPanel(QueryFrame parentFrame) {
		theQueryFrame = parentFrame;
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		
		buildOverview();
		add(mainPanel);
	}
	
	private void buildOverview() {
		mainPanel = Jcomp.createPagePanel();
		mainPanel.add(Box.createVerticalStrut(30));
		
		// Project table
		mainPanel.add(Jcomp.createItalicsBoldLabel("Selected projects:", 12)); mainPanel.add(Box.createVerticalStrut(2));
		Vector<Mproject> projVec = theQueryFrame.getProjects();
		
		int w=8, cntHasGenes=0;
		for (Mproject p : projVec) w = Math.max(w, p.getDisplayName().length());
		String nf = String.format("     %s%ds", "%-", w); // e.g. "%-20s"
		
		String headings = String.format(nf, "Name") +
				      String.format("   %4s   %7s   %s",  "Abbr", "#Genes", "Description");
		JLabel lblHead = Jcomp.createItalicsLabel(headings, 12);
		mainPanel.add(lblHead); mainPanel.add(Box.createVerticalStrut(2));

		for (Mproject mp : projVec) {
			String line= String.format(nf, mp.getDisplayName()) +
					String.format("   %4s   %,7d   %s", mp.getdbAbbrev() , mp.getGeneCnt(), mp.getdbDesc());
			mainPanel.add(Jcomp.createMonoLabel(line, 12));
			mainPanel.add(Box.createVerticalStrut(5));
			
			if (mp.getGeneCnt()>0) cntHasGenes++;
		}
		mainPanel.add(Box.createVerticalStrut(20));
		
		// Notes
		String head = "<html><body style='font-family: verdana, Times New Roman; font-size: 10px'>";
		String tail = "</body></html>";
		
		mainPanel.add(Jcomp.createItalicsBoldLabel("Notes:", 12)); mainPanel.add(Box.createVerticalStrut(5));
		String msg = head;
		
		if (cntHasGenes>0) {
			// Olap
			String algo = theQueryFrame.isAlgo2() ? "<i>Algo2:</i>  " : "<i>Algo1:</i>  "; 
			algo += "The Olap column is ";
			algo += theQueryFrame.isAlgo2() ? "exon overlap; Olap may be 0 if hits only overlap intron." 
					                        : "gene overlap; at least one project used Algo1 or has no genes.";
			msg += "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + algo;	
			
			// Pseudo
			msg += "<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<i>Pseudo</i>: "; 
			int cntPseudo = theQueryFrame.cntUsePseudo; 
			int cntSynteny = theQueryFrame.cntSynteny;
			
			String pg = "Un-annotated Pseudo Gene# assigned";
			if (projVec.size()==2)  msg += (cntPseudo==1) ? pg : "No " + pg;	
			else {
				if (cntPseudo>0 && cntPseudo!=cntSynteny)  
									    msg += cntPseudo + " pairs of " + cntSynteny + " have " + pg;
				else if (cntPseudo>0)	msg += "All pairs have " + pg;
				else 					msg += "No pairs have " + pg;
			}
			
			// Synteny
			if (projVec.size()!=2) {
				int nProjSyn = (projVec.size()*(projVec.size()-1))/2;
				if (nProjSyn!=cntSynteny) {
					msg+="<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<i>Synteny</i>: ";
					String s = " (from " + nProjSyn + " possible)";
					if (cntSynteny==1) msg+="One pair has synteny " + s;
					else               msg+= cntSynteny + " pairs have synteny " + s;
				}
			}
		}
		mainPanel.add(Jcomp.createLabel(msg + tail)); mainPanel.add(Box.createVerticalStrut(5));
		
		if (cntHasGenes<=1) {
			msg = head;
			if (cntHasGenes==0) msg += "<i>No genes in any species</i>. The only relevant filters are on chromosomes and blocks.";
			else                msg += "<i>Only one species has genes</i>. Some of the filters will not be relevant.";
			mainPanel.add(Jcomp.createLabel(msg + tail)); 
		}
		mainPanel.add(Box.createVerticalStrut(20));
		
		// instructions
		mainPanel.add(Jcomp.createItalicsBoldLabel("Instructions:", 12)); mainPanel.add(Box.createVerticalStrut(8));
		
		msg =  head + "&gt; Query Setup: click to set filters, followed by search, producing a table of results.";
		mainPanel.add(Jcomp.createLabel(msg+ tail)); mainPanel.add(Box.createVerticalStrut(8));
		
		msg =  head + "&gt; Results: click to view the list of query results, and to remove results.";
		mainPanel.add(Jcomp.createLabel(msg + tail)); mainPanel.add(Box.createVerticalStrut(8));
		
		msg= head + "The query results are listed under the Results tab, and can be viewed by selecting one.</html";
		mainPanel.add(Jcomp.createLabel(msg + tail));
	}

	private QueryFrame theQueryFrame = null;
	private JPanel mainPanel = null;
}