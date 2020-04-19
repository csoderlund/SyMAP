package symap.closeup;

import java.sql.SQLException;

import symap.SyMAP;
import symap.drawingpanel.DrawingPanel;
import symap.sequence.Sequence;
import colordialog.ColorDialogHandler;
import util.ErrorReport;
import util.PropertiesReader;

public class CloseUp {
	public static final int MAX_CLOSEUP_BP;
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/closeup.properties"));
		MAX_CLOSEUP_BP         = props.getInt("maxCloseupBP");
	}

	private DrawingPanel dp;
	private ColorDialogHandler cdh;

	public CloseUp(DrawingPanel dp, ColorDialogHandler cdh) {
		this.dp = dp;
		this.cdh = cdh;
	}

	/**
	 * If no hits are found, then no dialog is shown.
	 */
	public int showCloseUp(Sequence seq, int start, int end) {
		HitDialogInterface ad = null;

		// if (end - start > MAX_CLOSEUP_BP) end = start + MAX_CLOSEUP_BP; // already checked
		
		ad = getCloseUpDialog(seq,start,end);
		
		if (ad != null && cdh != null) cdh.addListener(ad);
		return ad == null ? -1 : ad.showIfHits();
	}

	protected CloseUpDialog getCloseUpDialog(Sequence seq, int start, int end) {
		if (dp != null) dp.setFrameEnabled(false);
		CloseUpDialog dialog = null;
		try {
			dialog = new CloseUpDialog(this,seq,start,end);
		}
		catch (SQLException e) {
			ErrorReport.print(e, "First attempt at creating a CloseUpDialog Failed");
			
			try {
				dialog = new CloseUpDialog(this,seq,start,end);
			}
			catch (SQLException e2) {
				ErrorReport.print(e, "Second attempt at creating a CloseUpDialog Failed");
				System.err.println("Giving up on creating a CloseUpDialog.");
			}
		}
		if (dp != null) dp.setFrameEnabled(true);
		return dialog;
	}

	public DrawingPanel getDrawingPanel() {
		return dp;
	}

}
