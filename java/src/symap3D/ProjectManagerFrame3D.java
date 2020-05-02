package symap3D;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import symap.SyMAP;
import symap.projectmanager.common.*;
import util.Utilities;
import util.DatabaseReader;
import symap.SyMAPConstants;
import util.ErrorReport;

public class ProjectManagerFrame3D extends ProjectManagerFrameCommon
{
	private static final long serialVersionUID = 1L; // CAS500
	ProjectManagerFrame3D(String args[])
	{
		super(args); // CAS505 moved parse args to ProjectManagerFrameCommon
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
		
		ProjectManagerFrame3D frame = new ProjectManagerFrame3D(args);
		frame.setVisible(true);
	}
	private void showExplorer() 
	{
		Utilities.setCursorBusy(this, true);
		try 
		{
			SyMAP3D symap3d = new SyMAP3D(DatabaseReader.getInstance(SyMAPConstants.DB_CONNECTION_SYMAP_APPLET_3D, dbReader));
			for (Project p : availProjects) 
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
					"with 'symap' or 'viewSymap'");
			ErrorReport.print(err, "Explorer with 3D");
		}
		finally 
		{
			Utilities.setCursorBusy(this, false);
		}
	}
}

