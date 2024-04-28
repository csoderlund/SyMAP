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
 * Draw circle view. CAS521 remove lots of dead code
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
		bIsWG=true;
		tdbc2 = new DBconn2("CircleG-" + DBconn2.getNumConn(), dbc2);
		build(pidxList, pidxList[0], null, null, null);
	}
	/** Called by ChrExpFrame; N-chromosomes **/
	public CircFrame(DBconn2 dbc2, int[] projIdxList, TreeSet<Integer> selGrps, HelpBar hb, 
			int ref, boolean hasSelf, double [] lastParams) {
		super("SyMAP Circle " + Globals.VERSION);
		bHasSelf = hasSelf;
		bIsWG=false;
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
			                 // WG											 From CE
			boolean isSelf = (projIdxList.length==2 && projIdxList[1]==0) || projIdxList.length==1;
		
			circPanel = new CircPanel(this, tdbc2, helpPanel, projIdxList, ref, selGrps, lastParams);
			
			controls =  new ControlPanelCirc(circPanel, helpPanel, bIsWG, isSelf, bHasSelf);
			if (lastParams!=null) controls.setLastParams((int)lastParams[0],
					lastParams[1]==1, lastParams[2]==1, lastParams[3]==1);
			
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); // CAS552 moved init() here
			addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {
					clear();
				}
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
	public double [] getLastParams() { return circPanel.getLastParams();} // CAS553 use last settings
	
	/**************************************************************
	 * XXX Color control
	 * CAS521 moved from CirPanel to get it out of the way of the display logic
	 * CAS553 made if call-able from CircPanel and added 2nd set, scale, sort, shuffle
	 */
	private double scale = 1.0;
	private Vector<Integer> mColors = new Vector <Integer> ();
	
	protected Vector<Integer> getColorVec(int set, boolean bScale, double scale, 
			boolean bOrder, boolean bRevOrder, boolean bShuffle, int seed) {
		this.scale = (bScale) ? scale : 1.0; // used in rgbToInt
		
		if (set==1) createSet1Colors();
		else        createSet2Colors();
		
		if (bShuffle) Collections.shuffle(mColors, new Random(seed));
		if (bOrder || bRevOrder) Collections.sort(mColors);
		if (bRevOrder) Collections.reverse(mColors);
		
		return mColors;
	}
	private int fromHexToInt(String hexCode) {
		String hex = (hexCode.startsWith("#")) ? hexCode.substring(1) : hexCode;
		int r = Integer.valueOf(hex.substring(0, 2), 16);
	    int g = Integer.valueOf(hex.substring(2, 4), 16);
	    int b = Integer.valueOf(hex.substring(4, 6), 16);
	    return rgbToInt(r,g,b);
	}
	private int rgbToInt(int R, int G, int B) { 
		if (scale!=1.0) {
			R = (int) ((double)R * scale);
			G = (int) ((double)G * scale);
			B = (int) ((double)B * scale);
		}
		return (R<<16) + (G<<8) + B;
	}
	private void createSet1Colors() {
	    mColors.clear();
	    								
	    mColors.add(rgbToInt(255,0,0));			// 1 red
	    mColors.add(rgbToInt(0,0,255));			// 2 blue
	    mColors.add(rgbToInt(20,200,70));		// 3 sea green
	    mColors.add(rgbToInt(138,0,188));		// 4 purple
	    mColors.add(rgbToInt(255,165,0));		// 5 orange
	    mColors.add(rgbToInt(255,181,197));		// 6 light pink
	    mColors.add(rgbToInt(210, 180, 140));	// 7 beige
	    mColors.add(rgbToInt(64,224,208));		// 8 torquise 
	    mColors.add(rgbToInt(165,42,42));		// 9 dark red
	    mColors.add(rgbToInt(0,255,255));		// 10 dark brown
	    mColors.add(rgbToInt(230,230,250));		// 11 pea green-yellowish
	    mColors.add(rgbToInt(255,255,0));		// 12 almost same as 3
	    mColors.add(rgbToInt(85,107, 47));		// 13 blue, slightly lighter than 2
	    mColors.add(rgbToInt(70,130,180));		// 14 forest green
	    mColors.add(rgbToInt(127,125,21));		// 15 green, slightly lighter than 14
	    mColors.add(rgbToInt(207,137,97));		// 16 cornflower blue, close to 13
	    mColors.add(rgbToInt(144,67,233));
	    mColors.add(rgbToInt(199,189,70));
	    mColors.add(rgbToInt(82,203,128));
	    mColors.add(rgbToInt(120,202,102));
	    mColors.add(rgbToInt(194,102,115));
	    mColors.add(rgbToInt(20,17,118));
	    mColors.add(rgbToInt(145,21,129));
	    mColors.add(rgbToInt(109,62,232));
	    mColors.add(rgbToInt(108,86,28));
	    mColors.add(rgbToInt(185,206,7));
	    mColors.add(rgbToInt(50,200,133));
	    mColors.add(rgbToInt(46,102,237));
	    mColors.add(rgbToInt(27,149,81));
	    mColors.add(rgbToInt(114,155,241));
	    mColors.add(rgbToInt(33,240,129));
	    mColors.add(rgbToInt(117,170,160));
	    mColors.add(rgbToInt(93,14,79));
	    mColors.add(rgbToInt(129,210,166));
	    mColors.add(rgbToInt(124,191,79));
	    mColors.add(rgbToInt(188,55,188));
	    mColors.add(rgbToInt(117,219,105));
	    mColors.add(rgbToInt(11,142,235));
	    mColors.add(rgbToInt(144,97,194));
	    mColors.add(rgbToInt(215,77,161));
	    mColors.add(rgbToInt(192,148,92));
	    mColors.add(rgbToInt(197,86,215));
	    mColors.add(rgbToInt(103,159,140));
	    mColors.add(rgbToInt(42,80,186));
	    mColors.add(rgbToInt(136,44,28));
	    mColors.add(rgbToInt(59,207,239));
	    mColors.add(rgbToInt(115,62,251));
	    mColors.add(rgbToInt(136,179,26));
	    mColors.add(rgbToInt(48,29,82));
	    mColors.add(rgbToInt(31,187,116));
	    mColors.add(rgbToInt(98,129,244));
	    mColors.add(rgbToInt(214,198,72));
	    mColors.add(rgbToInt(30,104,54));
	    mColors.add(rgbToInt(11,240,45));
	    mColors.add(rgbToInt(182,73,232));
	    mColors.add(rgbToInt(181,116,173));
	    mColors.add(rgbToInt(154,54,156));
	    mColors.add(rgbToInt(157,62,134));
	    mColors.add(rgbToInt(95,155,130));
	    mColors.add(rgbToInt(1,49,242));
	    mColors.add(rgbToInt(207,10,187));
	    mColors.add(rgbToInt(62,82,11));
	    mColors.add(rgbToInt(118,19,178));
	    mColors.add(rgbToInt(168,242,211));
	    mColors.add(rgbToInt(173,147,121));
	    mColors.add(rgbToInt(67,38,212));
	    mColors.add(rgbToInt(27,229,235));
	    mColors.add(rgbToInt(9,82,242));
	    mColors.add(rgbToInt(57,155,84));
	    mColors.add(rgbToInt(114,18,40));
	    mColors.add(rgbToInt(132,11,12));
	    mColors.add(rgbToInt(33,22,144));
	    mColors.add(rgbToInt(41,3,241));
	    mColors.add(rgbToInt(164,18,247));
	    mColors.add(rgbToInt(48,16,230));
	    mColors.add(rgbToInt(220,101,8));
	    mColors.add(rgbToInt(190,216,38));
	    mColors.add(rgbToInt(135,190,4));
	    mColors.add(rgbToInt(174,225,161));
	    mColors.add(rgbToInt(60,218,203));
	    mColors.add(rgbToInt(93,171,163));
	    mColors.add(rgbToInt(106,58,113));
	    mColors.add(rgbToInt(155,100,221));
	    mColors.add(rgbToInt(92,208,48));
	    mColors.add(rgbToInt(79,252,70));
	    mColors.add(rgbToInt(47,6,104));
	    mColors.add(rgbToInt(141,198,123));
	    mColors.add(rgbToInt(195,19,156));
	    mColors.add(rgbToInt(214,18,222));
	    mColors.add(rgbToInt(28,110,137));
	    mColors.add(rgbToInt(137,51,155));
	    mColors.add(rgbToInt(167,54,22));
	    mColors.add(rgbToInt(69,157,85));
	    mColors.add(rgbToInt(146,24,202));
	    mColors.add(rgbToInt(58,64,207));
	    mColors.add(rgbToInt(216,108,174));
	    mColors.add(rgbToInt(78,58,136));
	    mColors.add(rgbToInt(146,82,91));
	    mColors.add(rgbToInt(40,76,111));
	    mColors.add(rgbToInt(80,34,231));
	    mColors.add(rgbToInt(193,81,118));
	}
	// from https://mokole.com/palette.html; some were too close visually so I changed
	private void createSet2Colors() {
		mColors.clear();
		mColors.add(fromHexToInt("#4c1130"));//dark maroon
		mColors.add(fromHexToInt("#8b4513"));//saddlebrown
		mColors.add(fromHexToInt("#c15176"));//darkpink
		mColors.add(fromHexToInt("#2e8b57"));//seagreen
		mColors.add(fromHexToInt("#191970"));//midnightblue
		mColors.add(fromHexToInt("#800000"));//maroon
		mColors.add(fromHexToInt("#483d8b"));//darkslateblue
		mColors.add(fromHexToInt("#006400"));//darkgreen
		mColors.add(fromHexToInt("#708090"));//slategray
		mColors.add(fromHexToInt("#b22222"));//firebrick
		mColors.add(fromHexToInt("#5f9ea0"));//cadetblue
		mColors.add(fromHexToInt("#008000"));//green
		mColors.add(fromHexToInt("#3cb371")); //mediumseagreen
		mColors.add(fromHexToInt("#bc8f8f"));//rosybrown
		mColors.add(fromHexToInt("#663399"));//rebeccapurple
		mColors.add(fromHexToInt("#b8860b"));//darkgoldenrod
		mColors.add(fromHexToInt("#d8bfd8"));//thistle
		mColors.add(fromHexToInt("#bdb76b"));//darkkhaki
		mColors.add(fromHexToInt("#008b8b"));//darkcyan
		mColors.add(fromHexToInt("#cd853f"));//peru
		mColors.add(fromHexToInt("#4682b4"));//steelblue
		mColors.add(fromHexToInt("#d2691e"));//chocolate
		mColors.add(fromHexToInt("#9acd32"));//yellowgreen
		mColors.add(fromHexToInt("#20b2aa"));//lightseagreen
		mColors.add(fromHexToInt("#cd5c5c"));//indianred
		mColors.add(fromHexToInt("#00008b"));//darkblue
		mColors.add(fromHexToInt("#980000"));// maroony orangered
		mColors.add(fromHexToInt("#4b0082"));//indigo
		mColors.add(fromHexToInt("#32cd32"));//limegreen
		mColors.add(fromHexToInt("#a0522d"));//sienna
		mColors.add(fromHexToInt("#daa520"));//goldenrod
		mColors.add(fromHexToInt("#7f007f"));//purple2
		mColors.add(fromHexToInt("#8fbc8f"));//darkseagreen
		mColors.add(fromHexToInt("#b03060"));//maroon3
		mColors.add(fromHexToInt("#66cdaa"));//mediumaquamarine
		mColors.add(fromHexToInt("#9932cc"));//darkorchid
		mColors.add(fromHexToInt("#ff0000"));//red
		
		mColors.add(fromHexToInt("#00ced1"));//darkturquoise
		mColors.add(fromHexToInt("#808000"));//olive
		mColors.add(fromHexToInt("#f1c232"));//
		mColors.add(fromHexToInt("#ffd700"));//gold
		mColors.add(fromHexToInt("#6a5acd"));//slateblue
		mColors.add(fromHexToInt("#ffff00"));//yellow
		mColors.add(fromHexToInt("#c71585"));//mediumvioletred
		mColors.add(fromHexToInt("#0000cd"));//mediumblue
		mColors.add(fromHexToInt("#deb887"));//burlywood
		mColors.add(fromHexToInt("#40e0d0"));//turquoise
		mColors.add(fromHexToInt("#00ff00"));//lime
		mColors.add(fromHexToInt("#9400d3"));//darkviolet
		mColors.add(fromHexToInt("#ba55d3"));//mediumorchid
		mColors.add(fromHexToInt("#00fa9a"));//mediumspringgreen
		mColors.add(fromHexToInt("#8a2be2"));//blueviolet
		mColors.add(fromHexToInt("#00ff7f"));//springgreen
		mColors.add(fromHexToInt("#4169e1"));//royalblue
		mColors.add(fromHexToInt("#e9967a"));//darksalmon
		mColors.add(fromHexToInt("#dc143c"));//crimson
		mColors.add(fromHexToInt("#00ffff"));//aqua
		mColors.add(fromHexToInt("#00bfff"));//deepskyblue
		mColors.add(fromHexToInt("#f4a460"));//sandybrown
		mColors.add(fromHexToInt("#556b2f"));//darkolivegreen	
		mColors.add(fromHexToInt("#9370db"));//mediumpurple
		mColors.add(fromHexToInt("#0000ff"));//blue
		mColors.add(fromHexToInt("#a020f0"));//purple3
		mColors.add(fromHexToInt("#a64d79"));//  
		mColors.add(fromHexToInt("#adff2f"));//greenyellow
		mColors.add(fromHexToInt("#ff6347"));//tomato
		mColors.add(fromHexToInt("#da70d6"));//orchid
		mColors.add(fromHexToInt("#a9a9a9"));//darkgray
		mColors.add(fromHexToInt("#ff00ff"));//fuchsia
		mColors.add(fromHexToInt("#2f4f4f"));//darkslategray
		mColors.add(fromHexToInt("#b0c4de"));//lightsteelblue
		mColors.add(fromHexToInt("#ff7f50"));//coral
		mColors.add(fromHexToInt("#1e90ff"));//dodgerblue
		mColors.add(fromHexToInt("#db7093"));//palevioletred
		mColors.add(fromHexToInt("#f0e68c"));//khaki
		mColors.add(fromHexToInt("#fa8072"));//salmon
		mColors.add(fromHexToInt("#eee8aa"));//palegoldenrod
		mColors.add(fromHexToInt("#134f5c"));
		mColors.add(fromHexToInt("#6495ed"));//cornflower
		mColors.add(fromHexToInt("#dda0dd"));//plum
		mColors.add(fromHexToInt("#add8e6"));//lightblue
		mColors.add(fromHexToInt("#87ceeb"));//skyblue
		mColors.add(fromHexToInt("#ff1493"));//deeppink
		mColors.add(fromHexToInt("#6b8e23"));//olivedrab
		mColors.add(fromHexToInt("#7b68ee"));//mediumslateblue
		mColors.add(fromHexToInt("#ffa07a"));//lighsalmon
		mColors.add(fromHexToInt("#afeeee"));//paleturquoise
		mColors.add(fromHexToInt("#ee82ee"));//violet
		mColors.add(fromHexToInt("#98fb98"));//palegreen
		mColors.add(fromHexToInt("#560000"));//
		mColors.add(fromHexToInt("#7fffd4"));//aquamarine
		mColors.add(fromHexToInt("#ffdead"));//navajowhite
		mColors.add(fromHexToInt("#808080"));//gray
		mColors.add(fromHexToInt("#dd7e6b"));//
		mColors.add(fromHexToInt("#ff69b4"));//hotpink
		mColors.add(fromHexToInt("#a52a2a"));//brown
		mColors.add(fromHexToInt("#ffb6c1"));//lightpink		
		mColors.add(fromHexToInt("#696969"));//dimgray
		mColors.add(fromHexToInt("#7fff00"));//chartreuse
		mColors.add(fromHexToInt("#c0c0c0"));//silver
		mColors.add(fromHexToInt("#ffa500"));//orange
	}
}