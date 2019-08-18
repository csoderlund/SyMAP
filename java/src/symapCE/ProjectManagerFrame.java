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
			symapExp.getFrame().setVisible(true); // mdb added 12/31/09 #208
		}
		catch (Exception err) {
			err.printStackTrace();
		}
		finally {
			Utilities.setCursorBusy(this, false);
		}
	}		
}