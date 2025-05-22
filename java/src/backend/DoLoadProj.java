package backend;

import java.io.FileWriter;
import java.util.Vector;

import database.DBconn2;
import symap.manager.ManagerFrame;
import symap.manager.Mproject;
import util.Cancelled;
import util.ErrorCount;
import util.ErrorReport;
import util.ProgressDialog;

/********************************************************************************
 * DoLoadProj - calls SeqLoadMain and AnnotLoadMain; Called from ManagerFrame; 
 * All ProgressDialog must call finish in order to close fw
 *******************************************************************************/
public class DoLoadProj {
	private DBconn2 dbc2;
	private ManagerFrame frame;
	private FileWriter fw;
	
	public DoLoadProj(ManagerFrame frame, DBconn2 dbc2, FileWriter fw) {
		this.dbc2 = dbc2;
		this.frame = frame;
		this.fw = fw;
	}
	/********************************************************************/
	public void loadAllProjects(Vector <Mproject> selectedProjVec) {
		ErrorCount.init();
		
		final ProgressDialog progress = new ProgressDialog(frame, 
				"Loading All Projects", "Loading all projects" ,  true, fw); // Writes version and date to file
		progress.closeIfNoErrors();
	
		Thread loadThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText(">>> Load all projects (" + selectedProjVec.size() + ")"); 
				
				for (Mproject mProj : selectedProjVec) {
					if (mProj.getStatus() != Mproject.STATUS_ON_DISK) continue;
					
					try {
						progress.appendText(">>> Load " + mProj.getDBName()); 
						mProj.createProject();
						success = new SeqLoadMain().run(dbc2, progress, mProj);
					
						if (success && !Cancelled.isCancelled())  {
							AnnotLoadMain annot = new AnnotLoadMain(dbc2, progress, mProj);
							success = annot.run( mProj.getDBName());
						}
						
						if (!success || Cancelled.isCancelled()) {
							System.out.println("Removing project from database " + mProj.getDBName());
							mProj.removeProjectFromDB(); 
						}
						else mProj.saveParams(mProj.xLoad);
						
						if (Cancelled.isCancelled()) break;
					}
					catch (Exception e) {
						success = false;
						ErrorReport.print(e, "Loading all projects");
					}
				}
				if (!progress.wasCancelled()) progress.finish(success);
			}
		};	
		progress.start( loadThread ); // blocks until thread finishes or user cancels
	}
	/********************************************************************************/
	public void loadProject(final Mproject mProj) { // Reload Project deletes project before calling
		ErrorCount.init();
		
		final ProgressDialog progress = new ProgressDialog(frame, 
				"Loading Project", "Loading project " + mProj.getDBName() + " ...",  true, fw);
		progress.closeIfNoErrors();
		
		Thread loadThread = new Thread() {
			public void run() {
				boolean success = true;
				
				try {
					progress.appendText(">>> Load " + mProj.getDBName()); 
					
					mProj.createProject();		
					success = new SeqLoadMain().run(dbc2, progress, mProj);	
					
					if (success && !Cancelled.isCancelled())  {
						AnnotLoadMain annot = new AnnotLoadMain(dbc2, progress, mProj);
						success = annot.run( mProj.getDBName());
					}
					
					if (!success || Cancelled.isCancelled()) {
						System.out.println("Removing project from database " + mProj.getDBName());
						mProj.removeProjectFromDB(); 
					}
					else mProj.saveParams(mProj.xLoad);
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Loading project");
				}
				finally {
					if (!progress.wasCancelled()) progress.finish(success);
				}
			}
		};
		progress.start( loadThread ); // blocks until thread finishes or user cancels	
	}
	/********************************************************************************/
	public void reloadAnno(final Mproject mProj) {
		String title = "Reload annotation";
		String msg = ">>>Reloading annotation " + mProj.getDBName();
		final ProgressDialog progress = new ProgressDialog(frame, title, msg + " ...", true, fw);
		progress.closeIfNoErrors();
		
		Thread loadThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText(msg); 
				
				try { 
					mProj.removeAnnoFromDB(); 
					
					AnnotLoadMain annot = new AnnotLoadMain(dbc2, progress, mProj);
					success = annot.run(mProj.getDBName()); 
				
					if (!success || Cancelled.isCancelled()) {
						System.out.println("Removing annotation from database " + mProj.getDBName());
						mProj.removeAnnoFromDB(); 
					}
					else mProj.saveParams(mProj.xLoad);
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Run Reload");
				}
				finally {
					if (!progress.wasCancelled()) progress.finish(success); 
				}
			}
		};	
		progress.start( loadThread ); // blocks until thread finishes or user cancels
	}
}
