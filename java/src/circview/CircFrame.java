package circview;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.TreeSet;
import java.util.Vector;

import database.DBconn2;
import symap.Globals;
import symap.frame.HelpBar;
import util.ErrorReport;

/*********************************************************
 * Draw circle view. CAS521 remove lots of dead code
 */
public class CircFrame extends JFrame {
	private static final long serialVersionUID = 2371747762964367253L;

	private CircPanel circPanel;
	private HelpBar  helpPanel;
	private boolean  bIsWG = false, bHasSelf = false;
	private ControlPanelCirc controls;
	private Vector<Integer> mColors;
	private DBconn2 tdbc2;
	
    /** Called by Manager Frame; 2-WG **/
	public CircFrame(String title, DBconn2 dbc2, int projXIdx, int projYIdx, boolean hasSelf) {
		super(title);
		int[] pidxList = {projXIdx, projYIdx};
		bHasSelf = hasSelf;
		bIsWG=true;
		tdbc2 = new DBconn2("CircleG-" + DBconn2.getNumConn(), dbc2);
		build(pidxList, null, null, pidxList[0]);
	}
	/** Called by ChrExpFrame; N-chromosomes **/
	public CircFrame(DBconn2 dbc2, int[] projIdxList, TreeSet<Integer> selGrps, HelpBar hb, int ref, boolean hasSelf) {
		super("SyMAP Circle " + Globals.VERSION);
		bHasSelf = hasSelf;
		bIsWG=false;
		this.tdbc2 = dbc2; // created in ChrExpFrame
		build(projIdxList, selGrps, hb, ref);
	}
	private void build(int[] projIdxList, TreeSet<Integer> selGrps, HelpBar hb, int ref){
		if (projIdxList.length == 0) {
			System.out.println("Circle view called with no projects!"); 
			return;
		}
		try { 
			getUniqueColors();
			
			if (bIsWG)  helpPanel = new HelpBar(-1, 17);
			else 		helpPanel = hb;
			                 // WG											 From CE
			boolean isSelf = (projIdxList.length==2 && projIdxList[1]==0) || projIdxList.length==1;
		
			circPanel = new CircPanel(tdbc2, projIdxList, selGrps, ref, helpPanel, mColors);
			
			controls =  new ControlPanelCirc(circPanel, helpPanel, bIsWG, isSelf, bHasSelf);
			
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); // CAS552 moved init() here
			addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {clear();}
			});
			
			// Dimensions are set in calling program; i.e. ManagerFrame (CAS552 changed d) and ChrExpFrame
			setLayout( new BorderLayout() );
			add( controls, BorderLayout.NORTH );
			add( circPanel.getScrollPane(),BorderLayout.CENTER );
			if (bIsWG) add( helpPanel, BorderLayout.SOUTH );
			
			Dimension dim = getToolkit().getScreenSize(); // CAS534
			setLocation(dim.width / 4,dim.height / 4);
			
			//setLocationRelativeTo(null); //puts to side of MF
		}
		catch(Exception e){ ErrorReport.print(e, "Creating circle panel");}
	}	
	
	public void clear() {// CAS541 for new DBconn2, added circPanel clear
		tdbc2.close();
		circPanel.clear();
	} 
	
	/**************************************************************
	 * CAS521 moved from CirPanel to get it out of the way of the display logic
	 */
	private int colorInt(int R, int G, int B) {
		return (R<<16) + (G<<8) + B;
	}
	void getUniqueColors() {
	    mColors = new Vector<Integer>();
	    
	    mColors.add(colorInt(255,0,0));
	    mColors.add(colorInt(0,0,255));
	    mColors.add(colorInt(20,200,70));
	    mColors.add(colorInt(138,0,188));
	    mColors.add(colorInt(255,165,0));
	    mColors.add(colorInt(255,181,197));
	    mColors.add(colorInt(210, 180, 140));
	    mColors.add(colorInt(64,224,208));
	    mColors.add(colorInt(165,42,42));
	    mColors.add(colorInt(0,255,255));
	    mColors.add(colorInt(230,230,250));
	    mColors.add(colorInt(255,255,0));
	    mColors.add(colorInt(85,107, 47));
	    mColors.add(colorInt(70,130,180));
	    mColors.add(colorInt(127,125,21));
	    mColors.add(colorInt(207,137,97));
	    mColors.add(colorInt(144,67,233));
	    mColors.add(colorInt(199,189,70));
	    mColors.add(colorInt(82,203,128));
	    mColors.add(colorInt(120,202,102));
	    mColors.add(colorInt(194,102,115));
	    mColors.add(colorInt(20,17,118));
	    mColors.add(colorInt(145,21,129));
	    mColors.add(colorInt(109,62,232));
	    mColors.add(colorInt(108,86,28));
	    mColors.add(colorInt(185,206,7));
	    mColors.add(colorInt(50,200,133));
	    mColors.add(colorInt(46,102,237));
	    mColors.add(colorInt(27,149,81));
	    mColors.add(colorInt(114,155,241));
	    mColors.add(colorInt(33,240,129));
	    mColors.add(colorInt(117,170,160));
	    mColors.add(colorInt(93,14,79));
	    mColors.add(colorInt(129,210,166));
	    mColors.add(colorInt(124,191,79));
	    mColors.add(colorInt(188,55,188));
	    mColors.add(colorInt(117,219,105));
	    mColors.add(colorInt(11,142,235));
	    mColors.add(colorInt(144,97,194));
	    mColors.add(colorInt(215,77,161));
	    mColors.add(colorInt(192,148,92));
	    mColors.add(colorInt(197,86,215));
	    mColors.add(colorInt(103,159,140));
	    mColors.add(colorInt(42,80,186));
	    mColors.add(colorInt(136,44,28));
	    mColors.add(colorInt(59,207,239));
	    mColors.add(colorInt(115,62,251));
	    mColors.add(colorInt(136,179,26));
	    mColors.add(colorInt(48,29,82));
	    mColors.add(colorInt(31,187,116));
	    mColors.add(colorInt(98,129,244));
	    mColors.add(colorInt(214,198,72));
	    mColors.add(colorInt(30,104,54));
	    mColors.add(colorInt(11,240,45));
	    mColors.add(colorInt(182,73,232));
	    mColors.add(colorInt(181,116,173));
	    mColors.add(colorInt(154,54,156));
	    mColors.add(colorInt(157,62,134));
	    mColors.add(colorInt(95,155,130));
	    mColors.add(colorInt(1,49,242));
	    mColors.add(colorInt(207,10,187));
	    mColors.add(colorInt(62,82,11));
	    mColors.add(colorInt(118,19,178));
	    mColors.add(colorInt(168,242,211));
	    mColors.add(colorInt(173,147,121));
	    mColors.add(colorInt(67,38,212));
	    mColors.add(colorInt(27,229,235));
	    mColors.add(colorInt(9,82,242));
	    mColors.add(colorInt(57,155,84));
	    mColors.add(colorInt(114,18,40));
	    mColors.add(colorInt(132,11,12));
	    mColors.add(colorInt(33,22,144));
	    mColors.add(colorInt(41,3,241));
	    mColors.add(colorInt(164,18,247));
	    mColors.add(colorInt(48,16,230));
	    mColors.add(colorInt(220,101,8));
	    mColors.add(colorInt(190,216,38));
	    mColors.add(colorInt(135,190,4));
	    mColors.add(colorInt(174,225,161));
	    mColors.add(colorInt(60,218,203));
	    mColors.add(colorInt(93,171,163));
	    mColors.add(colorInt(106,58,113));
	    mColors.add(colorInt(155,100,221));
	    mColors.add(colorInt(92,208,48));
	    mColors.add(colorInt(79,252,70));
	    mColors.add(colorInt(47,6,104));
	    mColors.add(colorInt(141,198,123));
	    mColors.add(colorInt(195,19,156));
	    mColors.add(colorInt(214,18,222));
	    mColors.add(colorInt(28,110,137));
	    mColors.add(colorInt(137,51,155));
	    mColors.add(colorInt(167,54,22));
	    mColors.add(colorInt(69,157,85));
	    mColors.add(colorInt(146,24,202));
	    mColors.add(colorInt(58,64,207));
	    mColors.add(colorInt(216,108,174));
	    mColors.add(colorInt(78,58,136));
	    mColors.add(colorInt(146,82,91));
	    mColors.add(colorInt(40,76,111));
	    mColors.add(colorInt(80,34,231));
	    mColors.add(colorInt(193,81,118));
	}
}