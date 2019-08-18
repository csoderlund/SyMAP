package dotplot;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import java.applet.Applet;

import symap.frame.HelpBar;
import util.DatabaseReader;

// mdb added 7/10/09 for ProjectManagerFrame - FIXME: redundant with DotPlot.java
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class DotPlotFrame extends JFrame {
	Data data;
	
	public DotPlotFrame(DatabaseReader dbReader, int projXIdx, int projYIdx) {
		this(null, dbReader, new int[] { projXIdx, projYIdx }, null, null, null, true);
	}
	
	public DotPlotFrame(DatabaseReader dbReader) {
		this(null, dbReader, null, null, null, null, true);
	}
	
	public DotPlotFrame(Applet applet, DatabaseReader dbReader, 
			int[] projIDs, int[] xGroupIDs, int[] yGroupIDs, 
			HelpBar helpBar, boolean hasReferenceSelector) 
	{
		super("SyMAP Dot Plot");
		
		if (applet != null)
			data = new Data( applet );
		else
			data = new Data( new DotPlotDBUser(dbReader) );
		
		data.getSyMAP().clear(); // Clear caches - fixes "Couldn't find pair for projects" bug due to stale pairs data
		
		HelpBar hb = helpBar;
		if (helpBar == null)
			hb = new HelpBar(-1, 17, true, false, false);
		
		Plot plot = new Plot(data,hb);
		ControlPanel controls = new ControlPanel(applet,data,plot,hb);
		
		if (projIDs != null) {
			data.initialize(projIDs, xGroupIDs, yGroupIDs);
			controls.setProjects( data.getProjects() ); // mdb added 2/1/10 #210
		}
		
		if (projIDs == null || !hasReferenceSelector)
			controls.setProjects(null);

		// Setup frame
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
				if (data != null) data.kill();
				data = null;
			}
		});
		setLayout( new BorderLayout() );
		add( controls, BorderLayout.NORTH );
		add( plot.getScrollPane(),BorderLayout.CENTER );
		if (helpBar == null)
			add( hb, BorderLayout.SOUTH );
	}
	
	public Data getData() { return data; }
}
