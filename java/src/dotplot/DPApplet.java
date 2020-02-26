/**
 * @author marti@genome.arizona.edu
 *
 */

package dotplot;

import java.util.Vector;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;

import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import symap.frame.HelpBar;
import symap.SyMAP;
import util.Utilities;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class DPApplet extends JApplet implements DotPlotConstants {
	private Data data;
	private Plot plot;
	private ControlPanel controls;

	public void init() {
		super.init();

		SyMAP.printVersion();
			
		if (!SyMAP.checkJavaSupported(this))
			return;

		// Get parameters 
		final Vector<String> projects = new Vector<String>();
		String projName = getParameter("xSpecies"); // legacy interface
		if (projName != null) {
			projects.add(projName);
			projName = getParameter("ySpecies");
			projects.add(projName);
		}
		else { // new interface for consistency with SyMAPApplet
			for (int i = 1;  i <= SyMAP.MAX_PROJECTS;  i++) {
				projName = getParameter("PROJECT" + i);
				if (!Utilities.isStringEmpty(projName))
					projects.add(projName);
			}
		}
		
		if (projects.isEmpty()) {
			System.out.println("No projects specified!");
			return;
		}
		System.out.println("Projects: "+projects.toString());
		
		HelpBar helpBar = new HelpBar(-1, 17, true, false, false); 
		
		// Start data download
		data = new Data(this);
		plot = new Plot(data, helpBar);
		controls = new ControlPanel(this, data, plot, helpBar);

		new Thread(new Runnable() {
			public void run() {
				data.initialize( projects.toArray(new String[0]) );
				controls.setProjects( data.getProjects() ); 
				repaint();
			}
		}).start();

		
		JFrame topFrame = new JFrame();
		topFrame.setPreferredSize(new Dimension(800,600));
		topFrame.setMinimumSize(new Dimension(800,600));
		Container cp = topFrame.getContentPane();
		cp.add(controls, 			 BorderLayout.NORTH);
		cp.add(plot.getScrollPane(), BorderLayout.CENTER);
		cp.add(helpBar, 			 BorderLayout.SOUTH); 
		topFrame.toFront();
		topFrame.show();
		
		System.out.println("Initialization done, applet is ready.");
	}

	public void fit() { // called from javascript
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				validate();
			}
		});
	}
	
	public void destroy() {
		if (data != null) data.kill();
		data = null;
		plot = null;
		controls = null;
		super.destroy();
	}
}

