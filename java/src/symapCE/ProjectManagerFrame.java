package symapCE;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.projectmanager.common.Project;
import symap.projectmanager.common.ProjectManagerFrameCommon;
import util.DatabaseReader;
import util.Utilities;
import util.ErrorReport;

public class ProjectManagerFrame extends ProjectManagerFrameCommon
{
	private static final long serialVersionUID = 1L;
	ProjectManagerFrame(String args[])
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
		
		ProjectManagerFrame frame = new ProjectManagerFrame(args);
		frame.setVisible(true);
	}
	
	private void showExplorer() {
		Utilities.setCursorBusy(this, true);
		try {
			SyMAPExp symapExp = new SyMAPExp(
					DatabaseReader.getInstance(SyMAPConstants.DB_CONNECTION_SYMAP_3D, dbReader));
			for (Project p : availProjects) 
				symapExp.addProject( p.getDBName(), p.getType() );
			symapExp.build();
			symapExp.getFrame().build();
			symapExp.getFrame().setVisible(true); 
		}
		catch (Exception err) {
			ErrorReport.print(err, "Show explorer for non-3D");
		}
		finally {
			Utilities.setCursorBusy(this, false);
		}
	}		
}