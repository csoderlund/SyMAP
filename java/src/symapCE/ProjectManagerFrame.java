package symapCE;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.projectmanager.common.Project;
import symap.projectmanager.common.ProjectManagerFrameCommon;
import util.DatabaseReader;
import util.Utilities;

public class ProjectManagerFrame extends ProjectManagerFrameCommon
{
	private static final long serialVersionUID = 1L;
	ProjectManagerFrame()
	{
		super();
		explorerListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) { 
				showExplorer();
			}
		};
	}
	
	public static void main(String args[]) 
	{
		//System.out.println(System.getProperty("java.library.path"));	
		if (!SyMAP.checkJavaSupported(null))
			return;
		
		if (args.length > 0) {
			if (Utilities.hasCommandLineOption(args, "-r")) {
				System.out.println("Read only mode");
				inReadOnlyMode = true;
			}
			if (Utilities.hasCommandLineOption(args, "-s")) {// not shown in -h help
				System.out.println("Print Stats");
				printStats = true;
			}
			if (Utilities.hasCommandLineOption(args, "-a")) {// not shown in -h help
				System.out.println("Align largest project to smallest");
				lgProj1st = true;
			}
			if (Utilities.hasCommandLineOption(args, "-p")) { // 
				String x = Utilities.getCommandLineOption(args, "-p"); //CAS500
				try {
					maxCPUs = Integer.parseInt(x);
					System.out.println("Max CPUs " + maxCPUs);
				}
				catch (Exception e){ System.err.println(x + " is not an integer. Ignoring.");}
			}
			if (Utilities.hasCommandLineOption(args, "-c")) {// CAS501
				MAIN_PARAMS = Utilities.getCommandLineOption(args, "-c");
				System.out.println("Configuration file " + MAIN_PARAMS);
			}
			
			// not displayed from here; displayed from perl script symapNo3D
			if (Utilities.hasCommandLineOption(args, "-h"))
			{
				System.out.println("Usage: symap [optional arguments]");
				System.out.println("-p N (integer): use N CPUs");
				System.out.println("-c filename (string): use filename as configuration file instead of symap.config");
				System.out.println("-r : viewing-only mode (no project updates)");
				System.out.println("-h : show help");
				System.exit(0);
			}
		}
		
		ProjectManagerFrame frame = new ProjectManagerFrame();
		frame.setVisible(true);
	}
	private void showExplorer() {
		Utilities.setCursorBusy(this, true);
		try {
			SyMAPExp symapExp = new SyMAPExp(
					DatabaseReader.getInstance(SyMAPConstants.DB_CONNECTION_SYMAP_APPLET_3D, dbReader));
			for (Project p : selectedProjects) 
				symapExp.addProject( p.getDBName(), p.getType() );
			symapExp.build();
			symapExp.getFrame().build();
			symapExp.getFrame().setVisible(true); 
		}
		catch (Exception err) {
			err.printStackTrace();
		}
		finally {
			Utilities.setCursorBusy(this, false);
		}
	}		
}