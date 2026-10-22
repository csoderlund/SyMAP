 package backend;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.TreeMap;

import javax.swing.JFileChooser;
import javax.swing.JFrame;

import backend.synteny.SyntenyMain;
import database.DBconn2;
import symap.manager.Mpair;
import symap.manager.Mproject;
import symap.manager.SumFrame;
import symap.manager.ManagerFrame;
import util.Cancelled;
import util.ErrorReport;
import util.ProgressDialog;
import util.Utilities;
import util.Popup;

/**********************************************************
 * DoAlignSynPair; Calls AlignMain, AnchorMain and SyntenyMain; Called from ManagerFrame
 *
 * When called, DoLoadProj has been executed:
 * 		the Sequence and Annotation have been loaded 
 * 		the GeneNum assigned
 * Calls AlignMain 
 * Calls AnchorMain
 * 		calls AnchorMain1 or AnchorMain2, which assign hits to genes
 * 			Note: Demo1 has many genes with identical coords but diff strands; 
 * 			both algorithms seem to be inconsistent as to which is assigned as the major hit.
 * 			It makes a difference to collinear, as only the major are processed.
 * 		computes hit# and assigns hitcnt to genes (for Query Singles)
 * Calls SyntenyMain
 * Calls AnchorPost which creates collinear sets from blocks
 */

public class DoAlignSynPair extends JFrame {
	private static final long serialVersionUID = 1L;
	private DBconn2 dbc2;
	private Mpair mp;
	
	public void run(ManagerFrame frame, DBconn2 dbc2, Mpair mp, boolean bclose,  int maxCPUs, boolean bAlignDone) {
		this.dbc2 = dbc2;
		this.mp = mp;
		
		Mproject mProj1 = mp.mProj1;
		Mproject mProj2 = mp.mProj2;
		String dbName1 = mProj1.getDBName();
		String dbName2 = mProj2.getDBName();
		String toName =  (mProj1 == mProj2) ? dbName1 + " to self (includes mirrored hits)": dbName1 + " to " + dbName2; 
		
		FileWriter syFW =   symapLog(mProj1,mProj2);
		String alignLogDir = buildLogAlignDir(mProj1,mProj2);

		String msg = (mProj1 == mProj2) ? "Self-synteny "  + dbName1 + " ..." : "Synteny " + toName + " ...";
		
		final ProgressDialog diaLog = new ProgressDialog(this, "Running Synteny", msg, true, syFW); // write version and date
		
		String v = (Constants.VERBOSE) ? "   Verbose" : "    !Verbose"; 
		diaLog.msgToFileOnly(">>> " + toName + v);
		
		if (mp.bSynOnly) { // CAS591 make separate method
			mp.removeSyntenyFromDB(); 
			syntenyOnly(diaLog, mProj1, mProj2);
			return;
		}
		if (mp.bPseudo) {
			pseudoOnly(diaLog, mProj1, mProj2);
			return;
		}
		if (mp.bCoSet) { // Popup is in ManagerFrame.alignSelectedPair( ) -- already been okay
			collinearOnly(diaLog, mProj1, mProj2);
			return;
		}
		if (Constants.NUMHITS_ONLY) {// hidden
			numHitsOnly(diaLog, mProj1, mProj2);
			return;
		}
		if (Constants.LOAD_BLOCKS) { // hidden 
			loadBlocks(diaLog, mProj1, mProj2);
			return;
		}
		if (mp.isPairInDB() && mp.isBlast(Mpair.FILE))
			  mp.removeSyntenyFromDB(); // isBlastAdd uses existing hits; CAS590 
		else  mp.renewIdx(); 			// Remove existing and restart, sets projIdx; 
		
		String chgMsg = (bAlignDone) ? mp.getChgClustSyn(Mpair.FILE) : mp.getChgAllParams(Mpair.FILE);  
		diaLog.msg("Pair Parameters" + "\n" + chgMsg); // getChgClustSyn includes MUMmer
		diaLog.closeWhenDone();					
		
		final boolean bLoadBlast = mp.isBlast(Mpair.FILE);
		
		final UseBlast loadBlast = (bLoadBlast)  ? new UseBlast(dbc2, diaLog, mp) : null;
		final AlignMain aligner =  (!bLoadBlast) ? new AlignMain(dbc2, diaLog, mp,  maxCPUs, alignLogDir) : null;
		
		final AnchorMain anchors = new AnchorMain(dbc2, diaLog, mp );
		
		final SyntenyMain synteny = new SyntenyMain(dbc2, diaLog, mp );
		
		final Thread statusThread = new Thread() {
			public void run() {
				if (aligner==null) return;
				
				while (aligner.notStarted() && !Cancelled.isCancelled()) Utils.sleep(1000);
				if (aligner.getNumRemaining() == 0 && aligner.getNumRunning() == 0) return;
				if (Cancelled.isCancelled()) return;
				
				// Alignments\n list errors and current\nStatus
				String status = "\nAlignments:  running " + aligner.getNumRunning() + ", completed " + aligner.getNumCompleted();
				if (aligner.getNumRemaining()>0) status += ", queued " + aligner.getNumRemaining();
				if (aligner.getNumErrors()>0) status += ", errors " + aligner.getNumErrors(); 
				
				diaLog.updateText("\nRunning alignments:\n", aligner.getStatusSummary() + status + "\n");

				while (aligner.getNumRunning()>0 || aligner.getNumRemaining()>0) {
					if (Cancelled.isCancelled()) return;
					Utils.sleep(10000);
					if (Cancelled.isCancelled()) return;
					
					// same as before loop
					status = "\nAlignments:  running " + aligner.getNumRunning() + ", completed " + aligner.getNumCompleted();
					if (aligner.getNumRemaining()>0) status += ", queued " + aligner.getNumRemaining();
					if (aligner.getNumErrors()>0)    status += ", errors " + aligner.getNumErrors(); 
				
					diaLog.updateText("\nRunning alignments:\n", aligner.getStatusSummary() + status + "\n");
				}
				diaLog.updateText("Alignments:" ,  "Completed " + aligner.getNumCompleted() + "\n\n");
			}
		};
		
		// Perform alignment, load anchors and compute synteny; cancel and success like original
		// each 'run' makes a new connection from dbc2
		final Thread alignThread = new Thread() {
			public void run() {
				boolean success = true;
				
				try {
					long timeStart = Utils.getTime();
					
					/** Align **/
					if (bLoadBlast) success = loadBlast.run(); 
					else            success = aligner.run(); 
					
					if (Cancelled.isCancelled()) {
						diaLog.setVisible(false);
						diaLog.setCancelled();
						return;
					}
					if (!success) {
						diaLog.finish(false);
						return;
					}	
					if (Cancelled.isCancelled()) return;
					
					/** Anchors **/
					long synStart = Utils.getTime(); // need time for symap part only
					success = (bLoadBlast) ? true : anchors.run( mProj1, mProj2); // add anchors, cluster, number, collinear, pseudo; bSynOnly
					
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
					if (mp.bSynOnly)     Utils.prtMsgTimeDone(diaLog, "Complete SyMAP", synStart);
					else if (bLoadBlast) Utils.prtMsgTimeDone(diaLog, "Complete Load & SyMAP", synStart);
					else                 Utils.prtMsgTimeDone(diaLog, "Complete Align & SyMAP", synStart);

					/** Finish **/
					new SumFrame(dbc2, mp);
					
					diaLog.appendText(">> Summary for " + toName + "\n");
					printStats(diaLog);
					
					Utils.prtMsgTimeDone(diaLog, "Complete processing", timeStart);
				}
				catch (OutOfMemoryError e) {
					success = false;
					statusThread.interrupt();
					diaLog.msg( "Not enough memory - increase 'mem' in symap script");
					Popup.showOutOfMemoryMessage(diaLog);
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
				if (aligner!=null) 			aligner.interrupt();
				else if (loadBlast!=null) 	loadBlast.interrupt();
				
				if (anchors!=null) anchors.interrupt();
				if (synteny!=null) synteny.interrupt(); // CAS589 add synteny interrupt
				
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
				String msgx =  "Confirm: Remove " + mp.toString() + " from database (slow if large database)" + 
						     "\nCancel:  The user removes the database and starts over";
				
				if (Popup.showConfirm2("Remove pair", msgx)) { 
					System.out.println("Cancel alignment (removing alignment from DB)");
						mp.removePairFromDB(true); 					// redo numHits;
					System.out.println("Removal complete");
				} 
				else System.out.println("Remove database and restart");
			}
			catch (Exception e) { ErrorReport.print(e, "Removing alignment");}
		}
		else if (bclose) {
			diaLog.dispose();	
		}
		
		System.out.println("--------------------------------------------------");
		frame.refreshMenu();
		try {
			if (syFW != null) {
				if (Cancelled.isCancelled())  syFW.write("Cancelled");
				syFW.write("\n-------------- done " + new Date().toString() + " --------------------\n");
				syFW.close();
			}
		}
		catch (Exception e) {}
	} // end of run
	/**********************************************************************/
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
			System.out.println("\nAppend to log file: " 
					+ pairDir + "/" + Constants.syntenyLog + " (Length " + Utilities.kMText(lf.length()) + ")");	
		}
		catch (Exception e) {ErrorReport.print(e, "Creating log file");}
		return ret;
	}

	private void printStats(ProgressDialog prog)  {
		int pairIdx = mp.getPairIdx();
		if (pairIdx<=0) return;

		TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
		
		try { 
			int cnt = dbc2.executeCount("select count(*) from pseudo_hits where pair_idx=" + pairIdx);	
			counts.put("nhits", cnt);
			
			cnt = dbc2.executeCount("select count(*) from pseudo_block_hits as pbh join pseudo_hits as ph on pbh.hit_idx=ph.idx " +
									" where ph.pair_idx=" + pairIdx);
			counts.put("blkhits", cnt);

			cnt =  dbc2.executeCount("select count(*) from pseudo_hits where gene_overlap > 0 and pair_idx=" + pairIdx);	
			counts.put("genehits", cnt);

			cnt = dbc2.executeCount("select count(*)  from blocks where pair_idx=" + pairIdx);	
			counts.put("blocks", cnt);
		}
		catch (Exception e) {ErrorReport.print(e, "Gtting counts"); }
		
		Utils.prtNumMsg(prog, counts.get("nhits"), "hits");
		Utils.prtNumMsg(prog, counts.get("blocks"), "synteny blocks");
		Utils.prtNumMsg(prog, counts.get("genehits"), "gene hits");
		Utils.prtNumMsg(prog, counts.get("blkhits"), "synteny hits");
	}
	
	private String buildLogAlignDir(Mproject p1, Mproject p2) {
		try {
			String logName = Constants.logDir + p1.getDBName() + Constants.projTo + p2.getDBName();
			if (util.FileDir.dirExists(logName)) System.out.println("Log alignments in directory: " + logName);
			else util.FileDir.checkCreateDir(logName, true /* bPrt */);
			return logName + "/";
		}
		catch (Exception e){ErrorReport.print(e, "Creating log file");}
		return null;
	}
	/**************************************************************/
	/*** Synteny - pair parameter pull-down  ***/ 
	private void syntenyOnly(ProgressDialog mLog, Mproject mProj1, Mproject mProj2) {
	try {
		mLog.msg("Only synteny");
		
		SyntenyMain synteny = new SyntenyMain(dbc2, mLog, mp);
		synteny.run( mProj1, mProj2);
		
		mp.saveParamsForOnly();		
		new SumFrame(dbc2, mp); 
		System.out.println("--------------------------------------------------");
	}
	catch (Exception e){ErrorReport.print(e, "Creating log file"); }
	}
	
	/*** Add Pseudo - pair parameters pull-down  ***/ 
	private void pseudoOnly(ProgressDialog mLog, Mproject mProj1, Mproject mProj2) {
	try {
		mLog.msg("Only add pseudo-genes");
		
		AnchorMain anchors = new AnchorMain(dbc2, mLog, mp);
		anchors.addPseudoFromFlag();
			
		mp.saveParamsForOnly();		
		new SumFrame(dbc2, mp, "Number pseudo"); // only so it will show the updated version
		System.out.println("--------------------------------------------------");
	}
	catch (Exception e){ErrorReport.print(e, "Creating log file"); }
	}
	/** Collinear only: pair parameter pull-down  **********************/
	private void collinearOnly(ProgressDialog mLog, Mproject mProj1, Mproject mProj2) {
	try {
		mLog.msg("Only run collinear set algorithm");
		if (!mProj1.hasGenes() || !mProj2.hasGenes()) { 
			mLog.msg("Both projects must have genes for the collinear set algorithm");
			return;
		}
		
		if (mp.isCoSetAlgo(Mpair.FILE)) new AnchorCset2(mp, mProj1, mProj2, dbc2, mLog).collinearSets(); // CAS588
		else 							new AnchorCset1(mp, mProj1, mProj2, dbc2, mLog).collinearSets(); // CAS588
			
		mp.saveParamsForOnly();
		new SumFrame(dbc2, mp);
		System.out.println("--------------------------------------------------");
	}
	catch (Exception e){ErrorReport.print(e, "Creating log file"); }
	}
	/*** Load Blocks Pair --blk ; CAS589 *************/
	private void loadBlocks(ProgressDialog mLog, Mproject mProj1, Mproject mProj2) {
	try {
		mLog.msg("Load blocks from file");
		if (!mProj1.hasGenes() || !mProj2.hasGenes()) { 
			mLog.msg("Both projects must have genes to load blocks");
			return;
		}
		String defaultDir = Constants.getNameResultsDir(mProj1.getDBName(), mProj2.getDBName());
		
		JFileChooser cf = new JFileChooser();
		cf.setMultiSelectionEnabled(false);
		cf.setCurrentDirectory(new File(defaultDir));
		String path=null;
		
		if (cf.showOpenDialog(getInstance()) == JFileChooser.APPROVE_OPTION) 
			path = cf.getSelectedFile().getAbsolutePath().trim();
		else {
			Utils.prtIndentMsgFile(mLog, 1, "Abort Load Blocks");
			return;
		}
		
		new UserBlocks(mLog, dbc2, mp, path);
		
		mp.saveLoad("Load Blocks " + path);
		new SumFrame(dbc2, mp);
		printStats(mLog);
		System.out.println("--------------------------------------------------");
	}
	catch (Exception e){ErrorReport.print(e, "Load block file"); }
	}
	
	private DoAlignSynPair getInstance() {return this;}
	
	/*** Update NumHits: -nh command line, not visible  ***/ 
	private void numHitsOnly(ProgressDialog mLog, Mproject mProj1, Mproject mProj2) {
	try {
		mLog.msg("Compute NumHits Only...");
		if (!mProj1.hasGenes() || !mProj2.hasGenes()) { 
			mLog.msg("Both projects must have genes to have numHits");
			return;
		}
		AnchorMain anchors = new AnchorMain(dbc2, mLog, mp);
		anchors.saveAnnoHitCnt(true);
		new SumFrame(dbc2, mp, "Recompute numHits"); // only so it will show the updated version
		System.out.println("--------------------------------------------------");
	}
	catch (Exception e){ErrorReport.print(e, "Creating log file"); }
	}
}
