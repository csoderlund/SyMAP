package symapCE;

/*********************************************
 * Called by symap and viewSymap scripts
 * ProjectManagerFrameCommon displays interface, SyMAPFrameCommon calls showExplorer
 */
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.projectmanager.common.Project;
import symap.projectmanager.common.ProjectManagerFrameCommon;
import util.DatabaseReader;
import util.Utilities;
import util.ErrorReport;

public class SyMAPmanager extends ProjectManagerFrameCommon
{
	private static final long serialVersionUID = 1L;
	
	public static void main(String args[]) 
	{	
		if (!SyMAP.checkJavaSupported(null)) return;
		
		if (Utilities.hasCommandLineOption(args, "-h") || Utilities.hasCommandLineOption(args, "-help") 
			|| Utilities.hasCommandLineOption(args, "--h")) {
			
			prtParams(args); // see ProjectManagerFrameCommon for all variable stuff
			System.exit(0);
		}
		
		SyMAPmanager frame = new SyMAPmanager(args);
		frame.setVisible(true);
	}
	
	SyMAPmanager(String args[]) {
		super(args); // CAS505 moved parse args to ProjectManagerFrameCommon
		
		explorerListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) { 
				showExplorer();
			}
		};
	}
	
	private void showExplorer() {
		Utilities.setCursorBusy(this, true);
		try {
			DatabaseReader dr = DatabaseReader.getInstance(SyMAPConstants.DB_CONNECTION_SYMAP_3D, dbReader);
			
			SyMAPExp symapExp = new SyMAPExp(dr);
			
			for (Project p : availProjects) 
				symapExp.addProject( p.getDBName(), p.getType() );
			symapExp.build();
			symapExp.getFrame().build();
			symapExp.getFrame().setVisible(true); 
		}
		catch (Exception err) {ErrorReport.print(err, "Show SyMAP graphical window");}
		finally {
			Utilities.setCursorBusy(this, false);
		}
	}		
}