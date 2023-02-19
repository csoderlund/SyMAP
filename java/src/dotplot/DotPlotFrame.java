package dotplot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import database.DBconn;
import symap.Globals;
import symap.frame.HelpBar;

/*****************************************************************
 * Called from the SyMAP manager and Explorer
 * CAS522 from the dotplot package, removed FPC and lots of useless code
 * CAS533 from the dotplot package, removed massive amounts of more useless code
 */

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class DotPlotFrame extends JFrame {
	Data data;
	
	// ProjectManagerFrameCommon
	public DotPlotFrame(DBconn dbReader, int projXIdx, int projYIdx) {
		this(dbReader, new int[] { projXIdx, projYIdx }, null, null, null, true);
	}
	// SyMAPFrameCommon, ProjectManagerFrameCommon, above
	public DotPlotFrame(DBconn dbReader, int[] projIDs,
			int[] xGroupIDs, int[] yGroupIDs, 	// null if whole genome
			HelpBar helpBar, 					// not null if from CE
			boolean hasReferenceSelector)  		// false if from CE
	{
		super("SyMAP Dot Plot " + Globals.VERSION);
		if (projIDs==null || projIDs.length==0) System.err.println("No Projects! Email symap@agcol.arizona.edu"); 
	
		data = new Data( new DotPlotDBUser(dbReader) ); // created FilterData object
		data.getSyMAP().clear(); 						// Clear caches - fix bug due to stale pairs data
		data.initialize(projIDs, xGroupIDs, yGroupIDs);
		
		HelpBar hb = (helpBar!=null) ?  helpBar : new HelpBar(-1, 17);// CAS521 removed dead args
		
		Plot plot = new Plot(data, hb);
		
		ControlPanel controls = new ControlPanel(data,plot,hb);
		controls.setProjects( data.getProjects() ); 
		if (!hasReferenceSelector) controls.setProjects(null);

	// Setup frame
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
				if (data != null) data.kill();
				data = null;
				controls.kill(); // CAS533 add
			}
		});
		setLayout( new BorderLayout() );
		add( controls, BorderLayout.NORTH );
		add( plot.getScrollPane(),BorderLayout.CENTER );
		if (helpBar==null) add( hb, BorderLayout.SOUTH ); // otherwise, in CE on side
		
		Dimension dim = getToolkit().getScreenSize(); // CAS533 this works
		setLocation(dim.width / 4,dim.height / 4);
		//setLocationRelativeTo(null); this puts in lower corner
	}
	
	public Data getData() { return data; } // SyMAPFrameCommon
}
