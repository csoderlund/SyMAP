package symap.closeup;

import java.sql.SQLException;
import symap.SyMAP;
import colordialog.ColorDialogHandler;
import symap.drawingpanel.DrawingPanel;
import symap.sequence.Sequence;
import util.PropertiesReader;

public class CloseUp {
	public static final int MAX_CLOSEUP_BP;
	public static final int MIN_CLOSEUP_BP;

	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/closeup.properties"));
		MIN_CLOSEUP_BP         = props.getInt("minCloseupBP");
		MAX_CLOSEUP_BP         = props.getInt("maxCloseupBP");
	}

	private DrawingPanel dp;
	private ColorDialogHandler cdh;

	public CloseUp(DrawingPanel dp, ColorDialogHandler cdh) {
		this.dp = dp;
		this.cdh = cdh;
	}

	/**
	 * Method <code>showCloseUp</code> shows a blast dialog if the distance is 
	 * under MIN_CLOSEUP_BP and the CloseUpDialog otherwise.  
	 * If start-end > MAX_CLOSEUP_BP, start+MAX_CLOSEUP_BP is used as the end.
	 * If no hits are found, then no dialog is shown.
	 *
	 * @param seq a <code>Sequence</code> value
	 * @param start an <code>int</code> BP value of start
	 * @param end an <code>int</code> BP value of end
	 * @return an <code>int</code> value of the number of hits found or -1 if 
	 * an error occurs (i.e. SQLException)
	 */
	public int showCloseUp(Sequence seq, int start, int end) {
		HitDialogInterface ad = null;

		if (end - start > MAX_CLOSEUP_BP) end = start + MAX_CLOSEUP_BP;
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
			System.err.println("First attempt at creating a CloseUpDialog Failed:");
			e.printStackTrace();
			try {
				dialog = new CloseUpDialog(this,seq,start,end);
			}
			catch (SQLException e2) {
				System.err.println("Second attempt at creating a CloseUpDialog Failed:");
				e2.printStackTrace();	
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
