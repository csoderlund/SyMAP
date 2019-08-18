package symapQuery;

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
				overView += "Pseudo";
			else
				overView += "Draft";
			overView += "</TD></TR>";
		}
		overView += "</TABLE>";
		
		overView += "<P>";
		overView += "<P>Select <B>Query Setup</B> to initiate a search.</P>";
		overView += "<P><B>Results</B> shows the searches which have been performed in this session.</P></FONT>";
		overView += "</div>";
		mainPanel.setText(overView);
	}

	private SyMAPQueryFrame theParentFrame = null;
	private JEditorPane mainPanel = null;
}