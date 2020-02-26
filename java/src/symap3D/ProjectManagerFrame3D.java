package symap3D;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import symap.SyMAP;
import symap.projectmanager.common.*;
import util.Utilities;
import util.DatabaseReader;
import symap.SyMAPConstants;

public class ProjectManagerFrame3D extends ProjectManagerFrameCommon
{
	private static final long serialVersionUID = 1L; // CAS500
	ProjectManagerFrame3D()
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
			
			// Not displayed from here; displayed from perl script symap3D
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
		
		ProjectManagerFrame3D frame = new ProjectManagerFrame3D();
		frame.setVisible(true);
	}
	private void showExplorer() 
	{
		Utilities.setCursorBusy(this, true);
		try 
		{
			SyMAP3D symap3d = new SyMAP3D(DatabaseReader.getInstance(SyMAPConstants.DB_CONNECTION_SYMAP_APPLET_3D, dbReader));
			for (Project p : selectedProjects) 
				symap3d.addProject( p.getDBName(), p.getType() );
			symap3d.build();
			symap3d.getFrame().build();
			symap3d.getFrame().setVisible(true); 
		}
		catch (Exception err) 
		{
			System.out.println("The Explorer could not be opened.");
			System.out.println("Usually, this means that the computer is not capable of displaying");
			System.out.println("Java 3D. You can still use the other displays by re-starting SyMAP");
			System.out.println("with 'symapNo3D'");
			Utilities.showErrorMessage("The Explorer could not be opened.\n" +
			  "Usually, this means that the computer is not capable of displaying " +
			  "Java 3D. \nYou can still use the other displays by re-starting SyMAP " +
					"with 'symapNo3D'");
			err.printStackTrace();
		}
		finally 
		{
			Utilities.setCursorBusy(this, false);
		}
	}
}

