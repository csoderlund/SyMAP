 package backend;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.Date;
import java.util.TreeMap;
import javax.swing.JFrame;

import database.DBconn;
import symap.manager.Mpair;
import symap.manager.Mproject;
import symap.manager.ManagerFrame;
import util.Cancelled;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;

/**********************************************************
 * Computes alignment and synteny for two projects
 * CAS508 moved all the following from ManagerFrame
 */

public class AlignProjs extends JFrame {
	private static final long serialVersionUID = 1L;
	private DBconn dbConn;
	private Mpair mp;
	
	public void run(ManagerFrame frame, DBconn dbConn, Mpair mp,  
			boolean closeWhenDone,  int maxCPUs, boolean bDoCat) {

		this.dbConn = dbConn;
		this.mp = mp;
		
		Mproject mProj1 = mp.mProj1;
		Mproject mProj2 = mp.mProj2;
		String dbName1 = mProj1.getDBName();
		String dbName2 = mProj2.getDBName();
		String toName =  (mProj1 == mProj2) ? dbName1 + " to self" : dbName1 + " to " + dbName2;
		
		
		FileWriter syFW =   symapLog(mProj1,mProj2);
		String alignLogDir = buildLogAlignDir(mProj1,mProj2);

		String msg = (mProj1 == mProj2) ? 
				"Aligning project "  + dbName1 + " to itself ..." :
				"Aligning projects " + toName + " ...";
		
		final ProgressDialog diaLog = new ProgressDialog(this, "Aligning Projects", msg, true, syFW); // write version and date
		diaLog.msgToFileOnly(">>> " + toName);
		System.out.println("\n>>> Starting " + toName + "     " + Utilities.getDateTime());
		
		// CAS535 always just close if (!closeWhenDone) diaLog.closeIfNoErrors();	
		diaLog.closeWhenDone();					
		
		final AlignMain aligner = new AlignMain(new UpdatePool(dbConn), diaLog, mp,  maxCPUs, bDoCat, alignLogDir);
		if (aligner.mCancelled) return;
		
		final AnchorsMain anchors = new AnchorsMain(new UpdatePool(dbConn), diaLog, mp );
		
		final SyntenyMain synteny = new SyntenyMain( new UpdatePool(dbConn), diaLog, mp );
		
		final Thread statusThread = new Thread() {
			public void run() {
				
				while (aligner.notStarted() && !Cancelled.isCancelled()) Utilities.sleep(1000);
				if (aligner.getNumRemaining() == 0 && aligner.getNumRunning() == 0) return;
				if (Cancelled.isCancelled()) return;
				
				String msg = "\nAlignments:  running " + aligner.getNumRunning() + ", completed " + aligner.getNumCompleted();
				if (aligner.getNumRemaining()>0) msg += ", queued " + aligner.getNumRemaining();
				
				diaLog.updateText("\nRunning alignments:\n", aligner.getStatusSummary() + msg + "\n");

				while (aligner.getNumRunning() > 0 || aligner.getNumRemaining() > 0) {
					if (Cancelled.isCancelled()) return;
					Utilities.sleep(10000);
					if (Cancelled.isCancelled()) return;
					
					msg = "\nAlignments:  running " + aligner.getNumRunning() + ", completed " + aligner.getNumCompleted();
					if (aligner.getNumRemaining()>0) msg += ", queued " + aligner.getNumRemaining();
					
					diaLog.updateText("\nRunning alignments:\n", aligner.getStatusSummary() + msg + "\n");
				}
				diaLog.updateText("Alignments:" ,  "Completed " + aligner.getNumCompleted() + "\n\n");
			}
		};
		
		// Perform alignment, load anchors and compute synteny; cancel and success like original
		final Thread alignThread = new Thread() {
			public void run() {
				boolean success = true;
				
				try {
					long timeStart = System.currentTimeMillis();
					
					/** Align **/
					success &= aligner.run(); 
					String params = aligner.getParams();
					
					if (Cancelled.isCancelled()) {
						diaLog.setVisible(false);
						diaLog.setCancelled();
						return;
					}
					if (!success) {
						diaLog.finish(false);
						return;
					}	
					
					long timeEnd = System.currentTimeMillis();
					long diff = timeEnd - timeStart;
					String timeMsg = Utilities.getDurationString(diff);
					if (Cancelled.isCancelled()) return;
					
					/** Anchors **/
					success &= anchors.run( mProj1, mProj2);
					
					if (Cancelled.isCancelled()) return;
					if (!success) {
						diaLog.finish(false);
						return;
					}
					
					/** Synteny **/
					success &= synteny.run( mProj1, mProj2);
					
					if (Cancelled.isCancelled()) return;
					if (!success) {
						diaLog.finish(false);
						return;
					}
					
					/** Finish **/
					mp.saveParams(params);
					mProj1.saveParams(mProj1.xAlign);
					mProj2.saveParams(mProj2.xAlign);
					
					timeEnd = System.currentTimeMillis();
					diff = timeEnd - timeStart;
					timeMsg = Utilities.getDurationString(diff);
					diaLog.appendText(">> Summary for " + toName + "\n");
					printStats(diaLog, mProj1, mProj2);
					diaLog.appendText("\nFinished in " + timeMsg + "\n\n");
				}
				catch (OutOfMemoryError e) {
					success = false;
					statusThread.interrupt();
					diaLog.msg( "Not enough memory - increase $maxmem in symap script");
					Utilities.showOutOfMemoryMessage(diaLog);
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e,"Running alignment");
					statusThread.interrupt();
				}
				diaLog.finish(success); 
			}
		};
		
		diaLog.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println(e.getActionCommand());
				// Stop aligner and wait on monitor threads to finish
				aligner.interrupt();
				anchors.interrupt();
				try {
					alignThread.wait(5000);
					statusThread.wait(5000);
				}
				catch (Exception e2) { }
				
				// Threads should be done by now, but kill just in case
				alignThread.interrupt();
				statusThread.interrupt();
				
				// Close dialog
				diaLog.setVisible(false);
				if (e.getActionCommand().equals("Cancel")) {
					diaLog.setCancelled();
					Cancelled.cancel();
				}
			}
		});
		
		alignThread.start();
		statusThread.start();
		diaLog.start(); // blocks until thread finishes or user cancels
		
		if (diaLog.wasCancelled()) { // Remove partially-loaded alignment
			try {
				Thread.sleep(1000);
				System.out.println("Cancel alignment (removing alignment from DB)");
				mp.removePairFromDB(); // CAS534 
				System.out.println("Removal complete");
			}
			catch (Exception e) { ErrorReport.print(e, "Removing alignment");}
		}
		else if (closeWhenDone) {
			diaLog.dispose();	
		}
		
		System.out.println("--------------------------------------------------");
		frame.refreshMenu();
		try {
			if (syFW != null) {
				if (Cancelled.isCancelled())  syFW.write("Cancelled");
				Date date = new Date();
				syFW.write("\n-------------- done " + date.toString() + " --------------------\n");
				syFW.close();
			}
		}
		catch (Exception e) {}
	} // end of run
	
	private FileWriter symapLog(Mproject p1, Mproject p2) {
		FileWriter ret = null;
		try {
			File pd = new File(Constants.logDir);
			if (!pd.isDirectory()) pd.mkdir();
			
			String pairDir = Constants.logDir + p1.getDBName() + Constants.projTo + p2.getDBName(); 
			pd = new File(pairDir);
			if (!pd.isDirectory()) pd.mkdir();
			
			File lf = new File(pd,Constants.syntenyLog);
			ret = new FileWriter(lf, true); // append
			System.out.println("Append to log file: " 
					+ pairDir + "/" + Constants.syntenyLog + " (Length " + Utilities.kMText(lf.length()) + ")");	
		}
		catch (Exception e) {ErrorReport.print(e, "Creating log file");}
		return ret;
	}

	private void printStats(ProgressDialog prog, Mproject p1, Mproject p2) throws Exception {
		int pairIdx = mp.getPairIdx();
		if (pairIdx<=0) return;

		TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
		getPseudoCounts(counts,pairIdx);
		Utils.prtNumMsg(prog, counts.get("nhits"), "hits");
		Utils.prtNumMsg(prog, counts.get("blocks"), "synteny blocks");
		Utils.prtNumMsg(prog, counts.get("genehits"), "gene hits");
		Utils.prtNumMsg(prog, counts.get("blkhits"), "synteny hits");
	}
	private void getPseudoCounts(TreeMap<String,Integer> hitCounts, int pidx) throws Exception {
		UpdatePool db = new UpdatePool(dbConn);
	
		ResultSet rs = db.executeQuery("select count(*) as nhits from pseudo_hits where pair_idx=" + pidx);	
		rs.first();
		hitCounts.put("nhits", rs.getInt("nhits"));
		rs.close();
		
		rs = db.executeQuery("select count(*) as nhits from pseudo_block_hits as pbh join pseudo_hits as ph on pbh.hit_idx=ph.idx " +
								" where ph.pair_idx=" + pidx);
		rs.first();
		hitCounts.put("blkhits", rs.getInt("nhits"));
		rs.close();

		rs =  db.executeQuery("select count(*) as nhits from pseudo_hits where gene_overlap > 0 and pair_idx=" + pidx);	
		rs.first();
		hitCounts.put("genehits", rs.getInt("nhits"));
		rs.close();

		rs = db.executeQuery("select count(*) as n from blocks where pair_idx=" + pidx);	
		rs.first();
		hitCounts.put("blocks", rs.getInt("n"));
		rs.close();

	}
	// CAS500 was putting log in data/pseudo_pseudo/seq1_to_seq2/symap.log
	// changed to put in logs/seq1_to_seq2/symap.log; 
	private String buildLogAlignDir(Mproject p1, Mproject p2) {
		try {
			String logName = Constants.logDir + p1.getDBName() + Constants.projTo + p2.getDBName();
			if (Utilities.dirExists(logName)) System.out.println("Log alignments in directory: " + logName);
			else Utilities.checkCreateDir(logName, true /* bPrt */);
			return logName + "/";
		}
		catch (Exception e){ErrorReport.print(e, "Creating log file");}
		return null;
	}
}
