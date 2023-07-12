package dotplot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import colordialog.ColorDialogHandler;
import database.DBconn2;
import props.PersistentProps;
import symap.frame.HelpBar;

/*****************************************************************
 * Called from the SyMAP manager and Explorer
 * CAS522 from the dotplot package, removed FPC and lots of useless code
 * CAS533 from the dotplot package, removed massive amounts of more useless code
 */

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class DotPlotFrame extends JFrame {
	private Data data;
	private PersistentProps    persistentProps;
	
	// ManagerFrame for selected genomes
	public DotPlotFrame(String title, DBconn2 dbc2, int projXIdx, int projYIdx) {
		this(title, dbc2, new int[] { projXIdx, projYIdx }, null, null, null, true); // CAS543 add title
	}
	// ManagerFrame for all genomes, DotPlotFrame for chromosomes, ChrExpFrame for chromosomes; 
	// after new Data; data.getSyMAP().clear(); // Clear caches - fix bug due to stale pairs data; CAS541 clear on close
	public DotPlotFrame(String title, DBconn2 dbc2, int[] projIDs,
			int[] xGroupIDs, int[] yGroupIDs, 	// null if whole genome
			HelpBar helpBar, 					// not null if from CE
			boolean hasRefSelector)  		    // false if from CE
	{
		super(title);
		if (projIDs==null || projIDs.length==0) System.err.println("No Projects! Email symap@agcol.arizona.edu"); 
	
		String type =  (hasRefSelector) ? "G" : "E";    // CAS541 add just to mark connections in Data
		boolean is2D = (hasRefSelector) ? false : true; // CAS541 add for info display
		
		data = new Data(dbc2, type); // created FilterData object	
		data.initialize(projIDs, xGroupIDs, yGroupIDs);
		
		HelpBar hb = (helpBar!=null) ?  helpBar : new HelpBar(-1, 17);// CAS521 removed dead args
		
		Plot plot = new Plot(data, hb, is2D);
		
		persistentProps = new PersistentProps(); // does not work unless this is global
		ColorDialogHandler colorDialogHandler = new ColorDialogHandler(persistentProps); 
		colorDialogHandler.setDotPlot();
		
		ControlPanel controls = new ControlPanel(data, plot, hb, colorDialogHandler);
		
		if (hasRefSelector)	controls.setProjects( data.getProjects() ); // CAS543 was always doing
		else 				controls.setProjects(null);

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
	public void clear() {// CAS541 need to close connection from ChrExpFrame
		if (data != null) data.kill();
		data = null;
	}
	public Data getData() { return data; } // SyMAPFrameCommon
}
