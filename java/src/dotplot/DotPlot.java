package dotplot;

import java.awt.*;
import javax.swing.*;
import symap.pool.ProjectProperties;
import symap.pool.NamedProjectPair;
import util.SizedJFrame;
import util.PropertiesReader;
import dotplot.Project;
import dotplot.DotPlotConstants;

// mdb FIXME: this class has been replaced by DotPlotFrame, it needs to be removed
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class DotPlot extends SizedJFrame {
	public static final String DOTPLOT_PROPS = "/properties/dotplot.properties";

	// The total number of runs.  First one is the regular block hits.  
	// Next, there is the main Sytry run.  Then there are the comparison 
	// runs defined in the properties file DOTPLOT_PROPS.  The last one is 
	// an extra run for the sub chains.
	public static final int TOT_RUNS;

	// The number of runs used in Sytry including the default. 
	// So, TOT_RUNS - 1 (the sub-chain run).
	public static final int FINDER_RUNS;

	public static final int SUB_CHAIN_RUN; // The sub-chain run (i.e., TOT_RUNS - 1)
	
	public static final boolean RUN_SUBCHAIN_FINDER;

	static {
		PropertiesReader props = new PropertiesReader(DotPlot.class.getResource(DOTPLOT_PROPS));
		int n = props.getInt("numCompRuns");
		if (n < 0) n = 0;
		FINDER_RUNS   = n + 2;
		TOT_RUNS      = n + 3;
		SUB_CHAIN_RUN = n + 2;
		RUN_SUBCHAIN_FINDER = props.getBoolean("runSubChainFinder");
	}

	private Data data;
	private Plot plot;
	private ControlPanel controls;
	private String x,y;


	public Data getDPData() {
		return data;
	}

	public ControlPanel getDPControlPanel() {
		return controls;
	}
	/** CAS 12/26/17 dead code
	public boolean setProjects() {
		ProjectProperties props = data.getSyMAP().getDrawingPanel().getPools().getProjectProperties();
		NamedProjectPair pair = getProjectPair(props);
		if (pair != null)
			setProjects(pair.getP2Name(),pair.getP1Name());
		return pair != null;
	}
	**/
	public static NamedProjectPair getProjectPair(ProjectProperties props) {
		NamedProjectPair[] pairs = props.getNamedProjectPairs();
		if (pairs == null || pairs.length == 0) {
			JOptionPane.showMessageDialog(null,"There are no project pairs in the database.",
					"No Project Pairs",JOptionPane.WARNING_MESSAGE);
			return null;
		}

		return (NamedProjectPair)JOptionPane.showInputDialog(null,"Choose the desired species:","Select Species",
				JOptionPane.INFORMATION_MESSAGE,null,pairs,pairs[0]);
	}

	public void setProjects(Project[] projects) {
		x = projects[DotPlotConstants.X].getName();
		y = projects[DotPlotConstants.Y].getName();

		Container cp = getContentPane();
		cp.removeAll();

		cp.setLayout(new BorderLayout());
		cp.add(controls, BorderLayout.NORTH);
		cp.add(plot.getScrollPane(),BorderLayout.CENTER);
	}
	/** CAS 12/26/17 dead code
	public void setProjects(String xProject, String yProject) {
		x = xProject;
		y = yProject;

		Container cp = getContentPane();
		cp.removeAll();

		cp.setLayout(new BorderLayout());
		cp.add(controls, BorderLayout.NORTH);
		cp.add(plot.getScrollPane(),BorderLayout.CENTER);

		new Thread(new Runnable() {
			public void run() {				
				data.initialize(x,y);
				repaint();
			}
		}).start();
	}
	**/

}
