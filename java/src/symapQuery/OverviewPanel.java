package symapQuery;

/******************************************************
 * The Overview panel on the right side of the query frame
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
		String overView = "<FONT FACE='arial'><p>&nbsp;"; 
		overView += "<div style='width:500px;'>"; // CAS504 change table entries
		overView += "<TABLE >" +
				"<TR><TH ALIGN=LEFT>Project</TH><TH>&nbsp;&nbsp;&nbsp;&nbsp;</TH>" +
				"<TH ALIGN=LEFT>Abbrev</TH><TH>&nbsp;&nbsp;&nbsp;&nbsp;</TH>" +		// CAS519 add
					"<TH ALIGN=LEFT>Category</TH><TH>&nbsp;&nbsp;&nbsp;&nbsp;</TH>" +
					"<TH ALIGN=LEFT>Description</TH></TR>";
		Iterator<Mproject> iter = theParentFrame.getProjects().iterator();
		while(iter.hasNext()) {
			Mproject temp = iter.next();
			overView += "<TR><TD>" + temp.getDisplayName() + "</TD><TD>&nbsp;</TD>";
			overView += "<TD>" + temp.getdbAbbrev() + "</TD><TD>&nbsp;</TD>";
			overView += "<TD>" + temp.getdbCat() + "</TD><TD>&nbsp;</TD>";
			overView += "<TD>" + temp.getdbDesc() + "</TD>";
			overView += "</TR>";
		}
		overView += "</TABLE>";
		
		overView += "<P>"; // CAS503 reword
		overView += "<P><b>Instructions:</b>";
		overView += "<P>Select <B>Query Setup</B> to set filters on annotation and pairwise hit properties.</P>";
		overView += "<P>Select <B>Results</B> to view the list of query results, and to remove results.</P>";
		overView += "<br>The query results are listed under the <b>Results</b> tab, and can be viewed by selecting one.</P>";
		
		String algo = theParentFrame.isAlgo2() ? "Algo2: " : "Algo1: "; 
		algo += "The Olap column is ";
		algo += theParentFrame.isAlgo2() ? "exon overlap" : "gene overlap; no multi-hit exon option";
		overView += "<P><b>Note:</b> " + algo;	// CAS548
		
		overView += "</FONT></div>";
		mainPanel.setText(overView);
	}

	private SyMAPQueryFrame theParentFrame = null;
	private JEditorPane mainPanel = null;
}