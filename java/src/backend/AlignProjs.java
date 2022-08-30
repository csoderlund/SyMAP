package backend;

/**********************************************************
 * Computes alignment and synteny for two projects
 * // CAS508 moved all the following from PMFC
 */
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.Date;
import java.util.TreeMap;

import javax.swing.JFrame;

import symap.projectmanager.common.Project;
import symap.projectmanager.common.ProjectManagerFrameCommon;
import util.Cancelled;
import util.DatabaseReader;
import util.ErrorReport;
import util.Logger;
import util.ProgressDialog;
import util.PropertiesReader;
import util.Utilities;

public class AlignProjs extends JFrame {
	private static final long serialVersionUID = 1L;
	private DatabaseReader dbReader;
	private PropertiesReader mProps;
	private ProjectManagerFrameCommon frame;
	
	public void run(ProjectManagerFrameCommon frame, final Project p1, final Project p2, 
			boolean closeWhenDone, DatabaseReader dbReader, PropertiesReader mProps, int maxCPUs, boolean bDoCat) {

		this.frame = frame;
		this.dbReader = dbReader;
		this.mProps = mProps;
		
		FileWriter syFW =    symapLog(p1,p2);
		String alignLogDir = frame.buildLogAlignDir(p1,p2);

		System.out.println("\n>>Starting " + p1 + " and " + p2 + "     " + Utilities.getDateTime());
	
		try {
			syFW.write("\n---------------------------------------------------------\n");
		} catch (Exception e){}
		
		String msg = (p1 == p2) ? 
				"Aligning project "  + p1.getDisplayName() + " to itself ..." :
				"Aligning projects " + p1.getDisplayName() + " and " + p2.getDisplayName() + " ...";
		
		final ProgressDialog diaLog = new ProgressDialog(this, 
				"Aligning Projects", msg, false, true, syFW);
		
		if (closeWhenDone) 	diaLog.closeWhenDone();	
		else 				diaLog.closeIfNoErrors();
		
		// getting existing pair props
		int id1=p1.getID(), id2=p2.getID();
		SyProps pairProps = frame.getPairPropsFromDB(id1, id2);
		
		// remove existing pair stuff and reassign pair props 
		int pairIdx = pairIdxRenew(id1, id2);
		Logger syLog = diaLog;
		savePairProps(pairProps, pairIdx, p1, p2, syLog);
		
		final AlignMain aligner = 
			new AlignMain(new UpdatePool(dbReader), diaLog, 
				p1.getDBName(), p2.getDBName(), maxCPUs, bDoCat, mProps, pairProps, alignLogDir);
		if (aligner.mCancelled) return;
		
		final AnchorsMain anchors = 
			new AnchorsMain(pairIdx, new UpdatePool(dbReader), diaLog, mProps, pairProps );
		
		final SyntenyMain synteny = 
			new SyntenyMain( new UpdatePool(dbReader), diaLog, mProps, pairProps );
		
		final Thread statusThread = new Thread() {
			public void run() {
				
				while (aligner.notStarted() && !Cancelled.isCancelled()) Utilities.sleep(1000);
				if (aligner.getNumRemaining() == 0 && aligner.getNumRunning() == 0) return;
				if (Cancelled.isCancelled()) return;
				
				// CAS506 reworded progress message
				String msg = "\nAlignments:  running " + aligner.getNumRunning()   + 
						               ", completed " + aligner.getNumCompleted();
				if (aligner.getNumRemaining()>0) 
					            msg += ", queued " + aligner.getNumRemaining();
				
				diaLog.updateText("\nRunning alignments:\n", aligner.getStatusSummary() + msg + "\n");

				while (aligner.getNumRunning() > 0 || aligner.getNumRemaining() > 0) {
					if (Cancelled.isCancelled()) return;
					Utilities.sleep(10000);
					if (Cancelled.isCancelled()) return;
					
					msg = "\nAlignments:  running " + aligner.getNumRunning()   + 
							                  ", completed " + aligner.getNumCompleted();
					if (aligner.getNumRemaining()>0) 
			            msg += ", queued " + aligner.getNumRemaining();
					
					diaLog.updateText("\nRunning alignments:\n",
							aligner.getStatusSummary() + msg + "\n");
				}
				diaLog.updateText("Alignments:" ,  "Completed " + aligner.getNumCompleted() + "\n\n");
			}
		};
		
		// Perform alignment, load anchors and compute synteny
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
					
					// CAS501 - guess deleted so anchor could add, removed anchor add
					//pool.executeUpdate("delete from pairs where (" + 
					//	"	proj1_idx=" + p1.getID() + " and proj2_idx=" + p2.getID() + ") or (proj1_idx=" + p2.getID() + " and proj2_idx=" + p1.getID() + ")"); 
					
					if (Cancelled.isCancelled()) return;
					
					success &= anchors.run( p1.getDBName(), p2.getDBName() );
					if (!success) {
						diaLog.finish(false);
						return;
					}
					if (Cancelled.isCancelled()) return;
					
					success &= synteny.run( p1.getDBName(), p2.getDBName() );
					if (!success) {
						diaLog.finish(false);
						return;
					}
					if (Cancelled.isCancelled()) return;
											
					timeEnd = System.currentTimeMillis();
					diff = timeEnd - timeStart;
					timeMsg = Utilities.getDurationString(diff);
					diaLog.appendText(">> Summary for " + p1.getDisplayName() + " and " + p2.getDisplayName() + "\n");
					printStats(diaLog, p1, p2);
					diaLog.appendText("\nFinished in " + timeMsg + "\n\n");
					
					// CAS511 add params column (not schema update) to save args, mummer5, noCat	
					UpdatePool pool = new UpdatePool(dbReader);
					pool.tableCheckAddColumn("pairs", "params", "VARCHAR(128)", null); 
					
					pool.executeUpdate("update pairs set aligned=1,aligndate=NOW(), params='" + params + "'"
						+ " where (proj1_idx=" + p1.getID() + " and proj2_idx=" + p2.getID() 
						+ ") or   (proj1_idx=" + p2.getID() + " and proj2_idx=" + p1.getID() + ")"); 
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
				if (e.getActionCommand().equals("Cancel"))
				{
					diaLog.setCancelled();
					Cancelled.cancel();
				}
			}
		});
		
		alignThread.start();
		statusThread.start();
		diaLog.start(); // blocks until thread finishes or user cancels
		
		if (diaLog.wasCancelled()) {
			// Remove partially-loaded alignment
			try {
				Thread.sleep(1000);
				System.out.println("Cancel alignment (removing alignment from DB)");
				frame.removeAlignmentFromDB(p1, p2);
				System.out.println("Removal complete");
			}
			catch (Exception e) { 
				ErrorReport.print(e, "Removing alignment");
			}
		}
		else if (closeWhenDone) {
			diaLog.dispose();	
		}
		
		//System.gc(); // cleanup memory after AlignMain, AnchorsMain, SyntenyMain
		System.out.println("--------------------------------------------------");
		frame.refreshMenu();
		try {
			if (syFW != null) {
				Date date = new Date();
				syFW.write("\n-------------- done " + date.toString() + " --------------------\n");
				syFW.close();
			}
		}
		catch (Exception e) {}
	}
	// CAS500 was putting log in data/pseudo_pseudo/seq1_to_seq2/symap.log
	// changed to put in logs/seq1_to_seq2/symap.log
	
	private FileWriter symapLog(Project p1, Project p2) {
		FileWriter ret = null;
		try
		{
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
	private int pairIdxRenew(int id1, int id2) {
		try {
			int idx = frame.getPairIdx(id1, id2);
			UpdatePool pool = new UpdatePool(dbReader);
			
			// Remove all previous pair info
			if (idx>0) 
				pool.executeUpdate("DELETE FROM pairs WHERE idx=" + idx);
			
			return frame.pairIdxCreate(id1, id2, pool);
		}
		catch (Exception e) {ErrorReport.die(e, "SyMAP error renewing pairidx");}
		return 0;
	}
	private void savePairProps(SyProps props, int pairIdx, Project p1, Project p2, Logger log) {
		try {
			String n1 = p1.getDBName(), n2=p2.getDBName();
			String dir = Constants.getNameResultsDir(p1.strDBName, p1.isFPC(), p2.strDBName);
			props.saveNonDefaulted(log, dir, p1.getID(), p2.getID(), pairIdx, n1, n2, new UpdatePool(dbReader));
		} 
		catch (Exception e){ErrorReport.print(e, "Save pair parameters");}
	}
	private void printStats(ProgressDialog prog, Project p1, Project p2) throws Exception
	{
		if (p2.isFPC()) // in case order is backwards
		{
			Project temp = p1;
			p1 = p2;
			p2 = temp;
		}
		int pairIdx = frame.getPairIdx(p1.getID(),p2.getID());
		if (pairIdx==0) return;

		if (p1.isPseudo()) // pseudo/pseudo CAS511 removed #chromosomes
		{
			TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
			getPseudoCounts(counts,pairIdx);
			Utils.prtNumMsg(prog, counts.get("nhits"), "hits");
			Utils.prtNumMsg(prog, counts.get("blocks"), "synteny blocks");
			Utils.prtNumMsg(prog, counts.get("genehits"), "gene hits");
			Utils.prtNumMsg(prog, counts.get("blkhits"), "synteny hits");
		}
		else
		{
			int nMrk = getFPCMarkers(p1);
			Utils.prtNumMsg(prog, nMrk, "markers");
			Utils.prtNumMsg(prog, getFPCBES(p1), "BESs");
			
			TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
			getFPCCounts(counts,pairIdx);
			Utils.prtNumMsg(prog, counts.get("blocks"), "synteny blocks");
			Utils.prtNumMsg(prog, counts.get("beshits"), "BES hits");
			Utils.prtNumMsg(prog, counts.get("besblkhits"), "BES synteny hits");
			if (nMrk > 0)
			{
				Utils.prtNumMsg(prog, counts.get("mrkhits"), "marker hits");
				Utils.prtNumMsg(prog, counts.get("mrkblkhits"), "marker synteny hits");
			}
		}		
	}
	private void getPseudoCounts(TreeMap<String,Integer> hitCounts, int pidx) throws Exception
	{
		UpdatePool db = new UpdatePool(dbReader);
	
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
	private void getFPCCounts(TreeMap<String,Integer> counts, int pidx) throws Exception
	{
		UpdatePool db = new UpdatePool(dbReader);
	
		ResultSet rs = db.executeQuery("select count(*) as n from mrk_hits where pair_idx=" + pidx);	
		rs.first();
		counts.put("mrkhits", rs.getInt("n"));
		rs.close();
		
		rs = db.executeQuery("select count(*) as n from bes_hits where pair_idx=" + pidx);	
		rs.first();
		counts.put("beshits", rs.getInt("n"));
		rs.close();		

		rs = db.executeQuery("select count(*) as n from blocks where pair_idx=" + pidx);	
		rs.first();
		counts.put("blocks", rs.getInt("n"));
		rs.close();
		
		rs = db.executeQuery("select count(*) as n from mrk_block_hits as pbh join mrk_hits as ph on pbh.hit_idx=ph.idx " +
								" where ph.pair_idx=" + pidx);
		rs.first();
		counts.put("mrkblkhits", rs.getInt("n"));
		rs.close();

		rs = db.executeQuery("select count(*) as n from bes_block_hits as pbh join bes_hits as ph on pbh.hit_idx=ph.idx " +
								" where ph.pair_idx=" + pidx);
		rs.first();
		counts.put("besblkhits", rs.getInt("n"));
		rs.close();
	}
	private int getFPCMarkers(Project p) throws Exception
	{
		int num = 0;
		UpdatePool db = new UpdatePool(dbReader);
		ResultSet rs =db.executeQuery("select count(*) as n from markers where proj_idx=" + p.getID());
		rs.first();
		num = rs.getInt("n");
		rs.close();
		return num;
	}
	private int getFPCBES(Project p) throws Exception
	{
		int num = 0;
		UpdatePool db = new UpdatePool(dbReader);
		ResultSet rs =db.executeQuery("select count(*) as n from bes_seq where proj_idx=" + p.getID());
		rs.first();
		num = rs.getInt("n");
		rs.close();
		return num;
	}
	

}
