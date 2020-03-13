package symapQuery;

/******************************************************
 * The Overview panel on the right side of the query frame
 */
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

import symap.projectmanager.common.Project;

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
		String overView = "<FONT FACE='arial'><H2>Selected projects: </H2><p>";
		overView += "<div style='width:500px;'>\n";
		overView += "<TABLE><TR><TH ALIGN=LEFT>Project Name</TH><TH ALIGN=LEFT>Type</TH></TR>";
		Iterator<Project> iter = theParentFrame.getProjects().iterator();
		while(iter.hasNext()) {
			Project temp = iter.next();
			overView += "<TR><TD>" + temp.getDisplayName() + "</TD><TD>";
			if(temp.isFPC())
				overView += "FPC";
			else if(temp.isPseudo())
				overView += "Seq"; // CAS501 Pseudo->Seq
			else
				overView += "Draft";
			overView += "</TD></TR>";
		}
		overView += "</TABLE>";
		
		overView += "<P>"; // CAS503 reword
		overView += "<P><b>Instructions:</b>";
		overView += "<P>Select <B>Query Setup</B> to set filters on annotation and pairwise hit properties.</P>";
		overView += "<P>Select <B>Results</B> to view the list of query results, and to remove results.</P>";
		overView += "<br>The query results are listed under the <b>Results</b> tab, and can be viewed by selecting one.</P>";
		overView += "</FONT></div>";
		mainPanel.setText(overView);
	}

	private SyMAPQueryFrame theParentFrame = null;
	private JEditorPane mainPanel = null;
}