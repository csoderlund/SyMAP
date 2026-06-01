package circflatview;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.Random;
import java.util.Vector;

import database.DBconn2;
import symap.frame.HelpBar;
import util.ErrorReport;

/*********************************************************
 * Draw Linear (Flat Circle) view. 
 * Draws on screen size 825x900; set in ManagerFrame
 */
public class FlatFrame extends JFrame {
	private static final long serialVersionUID = 2371747762964367253L;

	private FlatPanel flatPanel;
	private HelpBar  helpPanel;
	private ControlPanelFlat controls;
	private DBconn2 tdbc2;
	
    /** Called by Manager Frame; 2-WG **/
	public FlatFrame(String title, DBconn2 dbc2, int projXIdx, int projYIdx) {
		super(title);
		
		int[] pidxList = {projXIdx, projYIdx}; 
		
		tdbc2 = new DBconn2("LinearG-" + DBconn2.getNumConn(), dbc2);
		
		build(pidxList, pidxList[0]);
	}
	
	private void build(int[] projIdxList, int ref){
		try { 
			helpPanel = new HelpBar(-1, 17);
		
			flatPanel = new FlatPanel(this, tdbc2, helpPanel, projIdxList, ref);
			
			controls =  new ControlPanelFlat(flatPanel, helpPanel);
			
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
			addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {
					clear();
				}
			});
			
			// Dimensions are set in ManagerFrame 
			setLayout(new BorderLayout());
			add(controls, BorderLayout.NORTH );
			add(flatPanel.getScrollPane(),BorderLayout.CENTER );
			add( helpPanel, BorderLayout.SOUTH );
			
			Dimension dim = getToolkit().getScreenSize(); 
			setLocation(dim.width / 4,dim.height / 4);
		}
		catch(Exception e){ ErrorReport.print(e, "Creating linear panel");}
	}	
	
	public void clear() {
		tdbc2.close();
		flatPanel.clear();
	} 
	
	/**************************************************************
	 * XXX Color control - CAS585 moved ColorSets to ColorSets.java
	 */
	private Vector<Integer> mColors = new Vector <Integer> ();
	
	protected Vector<Integer> getColorVec(int set, boolean bScale, double scale, 
			boolean bOrder, boolean bRevOrder, boolean bShuffle, int seed) {
		double sc = (bScale) ? scale : 1.0; // used in rgbToInt
		
		if (set==1) mColors = ColorSets.createSet1Colors(sc);
		else        mColors = ColorSets.createSet2Colors();
		
		if (bShuffle) Collections.shuffle(mColors, new Random(seed));
		if (bOrder || bRevOrder) Collections.sort(mColors);
		if (bRevOrder) Collections.reverse(mColors);
		
		return mColors;
	}
}