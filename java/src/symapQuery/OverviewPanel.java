package symapQuery;

/******************************************************
 * The Instruction panel on the right side of the query frame
 */
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

import symap.manager.Mproject;

public class OverviewPanel extends JPanel {
	private static final long serialVersionUID = 6074102283606563987L;
	
	public OverviewPanel(SyMAPQueryFrame parentFrame) {
		theParentFrame = parentFrame;
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		
		mainPanel = new JEditorPane();
		mainPanel.setEditable(false);
		mainPanel.setContentType("text/html");
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		buildOverview();
		add(mainPanel);
	}
	
	private void buildOverview() {
		int cntHasGenes=0;	// CAS561 change Category to #Genes. Add note if no genes. Move things around a little.
		String overView = "<FONT FACE='arial'><p>&nbsp;"; 
		overView += "<div style='width:500px;'>"; 
		overView += "<TABLE >" +
				"<TR><TH ALIGN=LEFT>Project</TH><TH>&nbsp;&nbsp;&nbsp;&nbsp;</TH>" +
				"<TH ALIGN=LEFT>Abbrev</TH><TH>&nbsp;&nbsp;&nbsp;&nbsp;</TH>" +		// CAS519 add
					"<TH ALIGN=LEFT>#Genes</TH><TH>&nbsp;&nbsp;&nbsp;&nbsp;</TH>" +
					"<TH ALIGN=LEFT>Description</TH></TR>";
		Iterator<Mproject> iter = theParentFrame.getProjects().iterator();
		while(iter.hasNext()) {
			Mproject temp = iter.next();
			overView += "<TR><TD>&nbsp;&nbsp;" + temp.getDisplayName() + "</TD><TD>&nbsp;</TD>";
			overView += "<TD>" + temp.getdbAbbrev() + "</TD><TD>&nbsp;</TD>";
			overView += "<TD>" + String.format("%,d",temp.getGeneCnt()) + "</TD><TD>&nbsp;</TD>";
			overView += "<TD>" + temp.getdbDesc() + "</TD>";
			overView += "</TR>";
			if (temp.getGeneCnt()>0) cntHasGenes++;
		}
		overView += "</TABLE>";
		
		overView += "<P><b>Notes:</b>";
		if (cntHasGenes>0) {
			String algo = theParentFrame.isAlgo2() ? "<i>Algo2:</i> " : "<i>Algo1:</i> "; 
			algo += "The Olap column is ";
			algo += theParentFrame.isAlgo2() ? "exon overlap; Olap may be 0 if hits only overlap intron." 
					                         : "gene overlap; at least one project used Algo1 or has no genes.";
			overView += "<P>&nbsp;&nbsp;" + algo;	// CAS548
		}
		if (cntHasGenes==0) 
			overView += "<P>&nbsp;&nbsp;<i>No genes in any species</i>. The only relevant filters are on chromosomes and blocks.";
		else if (cntHasGenes==1) 
			overView += "<P>&nbsp;&nbsp;<i>Only one species has genes</i>. Some of the filters will not be relevant.";
		
		overView += "<P><b>Instructions:</b>";
		overView += "<P>&nbsp;&nbsp;Select <B>Query Setup</B> to set filters on annotation and pairwise hit properties.</P>";
		overView += "<P>&nbsp;&nbsp;Select <B>Results</B> to view the list of query results, and to remove results.</P>";
		overView += "<br>&nbsp;&nbsp;The query results are listed under the <b>Results</b> tab, and can be viewed by selecting one.</P>";
		
		overView += "</FONT></div>";
		mainPanel.setText(overView);
	}

	private SyMAPQueryFrame theParentFrame = null;
	private JEditorPane mainPanel = null;
}