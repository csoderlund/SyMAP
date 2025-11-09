package symap.closeup;

import java.sql.SQLException;
import java.util.Vector;

import symap.drawingpanel.DrawingPanel;
import symap.mapper.HitData;
import symap.sequence.Sequence;
import colordialog.ColorDialogHandler;
import util.ErrorReport;

/*******************************************************
 * created in symap.SyMAP at startup
 */
public class CloseUp  {
	private DrawingPanel dp;
	private ColorDialogHandler cdh;

	public CloseUp(DrawingPanel dp, ColorDialogHandler cdh) { // frame.SyMAP2d
		this.dp = dp;
		this.cdh = cdh;
	}
	
	/**
	 * Called by Sequence on mouse release; If no hits are found, then no dialog is shown.
	 */
	public int showCloseUp(Vector <HitData> hitList, Sequence seqObj, int start, int end,  
			boolean isQuery, String otherChr, int numShow) {
	try {
		if (dp != null) dp.setFrameEnabled(false);
	
		CloseUpDialog cuDialog = new CloseUpDialog(this, hitList, seqObj,start,end, isQuery, otherChr, numShow); 
	
		if (dp != null) dp.setFrameEnabled(true);
		if (cuDialog != null && cdh != null) cdh.addListener(cuDialog);
		
		return cuDialog == null ? -1 : cuDialog.showIfHits();
	} 
	catch (SQLException e) {ErrorReport.print(e, "Creating a CloseUpDialog"); return 0;}	
	}

	public DrawingPanel getDrawingPanel() {
		return dp;
	}
}
