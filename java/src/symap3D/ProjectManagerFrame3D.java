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
			if (Utilities.hasCommandLineOption(args, "-r"))
				inReadOnlyMode = true;
	

			if (Utilities.hasCommandLineOption(args, "-h"))
			{
				System.out.println("Usage: symap [optional arguments]");
				System.out.println("-r : viewing-only mode (no project updates)");
				System.out.println("-p N : use N cpus");
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
			symap3d.getFrame().setVisible(true); // mdb added 12/31/09 #208
		}
		catch (Exception err) 
		{
			System.out.println("The Explorer could not be opened.");
			System.out.println("Usually, this means that the computer is not capable of displaying");
			System.out.println("Java 3D. You can still use the other displays by re-starting SyMAP");
			System.out.println("with 'symap -no3d'");
			Utilities.showErrorMessage("The Explorer could not be opened.\n" +
			  "Usually, this means that the computer is not capable of displaying " +
			  "Java 3D. \nYou can still use the other displays by re-starting SyMAP " +
					"with 'symap -no3d'");
			err.printStackTrace();
		}
		finally 
		{
			Utilities.setCursorBusy(this, false);
		}
	}
}

