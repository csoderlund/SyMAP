package circview;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.Random;
import java.util.TreeSet;
import java.util.Vector;

import database.DBconn2;
import symap.Globals;
import symap.frame.HelpBar;
import util.ErrorReport;

/*********************************************************
 * Draw circle view. 
 * Draws on screen size 825x900; set in ManagerFrame and ChrExpFrame after creation
 */
public class CircFrame extends JFrame {
	private static final long serialVersionUID = 2371747762964367253L;

	private CircPanel circPanel;
	private HelpBar  helpPanel;
	private boolean  bIsWG = false, bHasSelf = false;
	private ControlPanelCirc controls;
	private DBconn2 tdbc2;
	
    /** Called by Manager Frame; 2-WG **/
	public CircFrame(String title, DBconn2 dbc2, int projXIdx, int projYIdx, boolean hasSelf) {
		super(title);
		int[] pidxList = {projXIdx, projYIdx};
		bHasSelf = hasSelf;
		bIsWG = true;
		tdbc2 = new DBconn2("CircleG-" + DBconn2.getNumConn(), dbc2);
		build(pidxList, pidxList[0], null, null, null);
	}
	/** Called by ChrExpFrame; N-chromosomes **/
	public CircFrame(DBconn2 dbc2, int[] projIdxList, TreeSet<Integer> selGrps, HelpBar hb, 
			int ref, boolean hasSelf, double [] lastParams) {
		super("SyMAP Circle " + Globals.VERSION);
		bHasSelf = hasSelf;
		bIsWG = false;
		this.tdbc2 = dbc2; // created in ChrExpFrame
		build(projIdxList, ref, hb, selGrps, lastParams);
	}
	private void build(int[] projIdxList, int ref, HelpBar hb, TreeSet<Integer> selGrps,  double [] lastParams){
		if (projIdxList.length == 0) {
			System.out.println("Circle view called with no projects!"); 
			return;
		}
		try { 
			if (bIsWG)  helpPanel = new HelpBar(-1, 17);
			else 		helpPanel = hb;
			                 // WG											 // 2D
			boolean isSelf = (projIdxList.length==2 && projIdxList[1]==0) || projIdxList.length==1;
		
			circPanel = new CircPanel(this, tdbc2, helpPanel, projIdxList, ref, selGrps, lastParams, bHasSelf);
			
			controls =  new ControlPanelCirc(circPanel, helpPanel, bIsWG, isSelf, bHasSelf);
			
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
			addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {
					clear();
				}
			});
			
			// Dimensions are set in calling program; i.e. ManagerFrame (WG) and ChrExpFrame (2D)
			setLayout( new BorderLayout() );
			add( controls, BorderLayout.NORTH );
			add( circPanel.getScrollPane(),BorderLayout.CENTER );
			if (bIsWG) add( helpPanel, BorderLayout.SOUTH );
			
			Dimension dim = getToolkit().getScreenSize(); 
			setLocation(dim.width / 4,dim.height / 4);
		}
		catch(Exception e){ ErrorReport.print(e, "Creating circle panel");}
	}	
	
	public void clear() {
		tdbc2.close();
		circPanel.clear();
	} 
	public double [] getLastParams() { return circPanel.getLastParams();} // use last settings; called from frame.ChrExpFrame
	
	/**************************************************************
	 * XXX Color control
	 */
	private Vector<Integer> mColors = new Vector <Integer> ();
	
	protected Vector<Integer> getColorVec(int set, boolean bScale, double scale, 
			boolean bOrder, boolean bRevOrder, boolean bShuffle, int seed) {
		double sc = (bScale) ? scale : 1.0; // used in rgbToInt
		
		if (set==1) mColors = circflatview.ColorSets.createSet1Colors(sc); // CAS575 used shared ColorSets
		else        mColors = circflatview.ColorSets.createSet2Colors();
		
		if (bShuffle) Collections.shuffle(mColors, new Random(seed));
		if (bOrder || bRevOrder) Collections.sort(mColors);
		if (bRevOrder) Collections.reverse(mColors);
		
		return mColors;
	}
}