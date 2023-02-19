package symap.closeup;

import java.sql.SQLException;
import java.util.Vector;

import symap.Globals;
import symap.drawingpanel.DrawingPanel;
import symap.mapper.HitData;
import symap.sequence.Sequence;
import colordialog.ColorDialogHandler;
import util.ErrorReport;

/*******************************************************
 * created in symap.SyMAP at startup
 * CAS531 major rewrite of all closeup files
 * CAS531 it use to just have different subhits next to each other where gaps were so it
 * would correspond to sequence coordinates - now the gaps are there, so the coords will be a little off
 */
public class CloseUp  {
	private static final int MAX_CLOSEUP_BP= Globals.MAX_CLOSEUP_BP;
	private DrawingPanel dp;
	private ColorDialogHandler cdh;

	public CloseUp(DrawingPanel dp, ColorDialogHandler cdh) {
		this.dp = dp;
		this.cdh = cdh;
	}
	
	/**
	 * Called by Sequence on mouse release; If no hits are found, then no dialog is shown.
	 */
	public int showCloseUp(Vector <HitData> hitList, Sequence seqObj, int start, int end, String otherProject, boolean isQuery) {
		// create the panel
		if (dp != null) dp.setFrameEnabled(false);
		CloseUpDialog cuDialog;
		
		try {
			cuDialog = new CloseUpDialog(this, hitList, seqObj,start,end, otherProject, isQuery); // CAS531 changed from HitDialogInterface
		} catch (SQLException e) {ErrorReport.print(e, "Creating a CloseUpDialog"); return 0;}
		
		if (dp != null) dp.setFrameEnabled(true);
		if (cuDialog != null && cdh != null) cdh.addListener(cuDialog);
		
		return cuDialog == null ? -1 : cuDialog.showIfHits();
	}

	public DrawingPanel getDrawingPanel() {
		return dp;
	}
}
