package circflatview;

import java.util.Vector;

/*********************************************************
 * CAS575 shared between circview and circflatview
 * made a few changes to colors appear the same for colorset1 as before; now using 1st 50 instead of 25
 */
public class ColorSets {
	static double scale = 1.0;
	
	static public Vector <Integer> createSet1Colors(double s) {
		scale = s;
		Vector <Integer> mColors = new Vector <Integer> ();
	    								
	    mColors.add(rgbToInt(255,0,0));		// 1 red
	    mColors.add(rgbToInt(0,0,255));		// 2 blue
	    mColors.add(rgbToInt(20,200,70));	// 3 sea green
	    mColors.add(rgbToInt(138,0,188));	// 4 purple
	    mColors.add(rgbToInt(214,198,72));	// 5 gold (was 52 26)
	    mColors.add(rgbToInt(255,181,197));	// 6 light pink
	    mColors.add(rgbToInt(210,180,140));	// 7 beige
	    mColors.add(rgbToInt(64,224,208));	// 8 blue green 
	    mColors.add(rgbToInt(165,42,42));	// 9 dark red
	    mColors.add(rgbToInt(0,255,255));	// 10 torquoise
	    mColors.add(rgbToInt(170,170,170));	// 11 dark gray   CAS585 was 230,230,250, which is very light
	    mColors.add(rgbToInt(255,255,0));	// 12 yellow
	    mColors.add(rgbToInt(85,107, 47));	// 13 blue, slightly lighter than 2
	    mColors.add(rgbToInt(70,130,180));	// 14 forest green
	    mColors.add(rgbToInt(127,125,21));	// 15 green, slightly lighter than 14
	    mColors.add(rgbToInt(207,137,97));	// 16 cornflower blue, close to 13
	    mColors.add(rgbToInt(144,67,233));	// 17
	    mColors.add(rgbToInt(199,189,70));  // 18
	    mColors.add(rgbToInt(82,203,128));  // 19
	    mColors.add(rgbToInt(120,202,102)); // 20
	    mColors.add(rgbToInt(194,102,115));	// 21
	    mColors.add(rgbToInt(20,17,118));	// 22
	    mColors.add(rgbToInt(145,21,129));	// 23
	    mColors.add(rgbToInt(109,62,232));	// 24
	    mColors.add(rgbToInt(98,129,244));	// 25 51  CAS585 moved this set up
	    mColors.add(rgbToInt(255,165,0));	// 26  orange (was 5)
	    mColors.add(rgbToInt(30,104,54));	// 27 53 
	    mColors.add(rgbToInt(11,240,45));	// 28 54 
	    mColors.add(rgbToInt(182,73,232));	// 29 55 
	    mColors.add(rgbToInt(181,116,173));	// 30 56
	    mColors.add(rgbToInt(67,38,212));	// 31 40 66 (was 40 blueish)
	    mColors.add(rgbToInt(157,62,134));	// 32 58
	    mColors.add(rgbToInt(95,155,130));	// 33 59
	    mColors.add(rgbToInt(1,49,242));	// 34 60
	    mColors.add(rgbToInt(207,10,187));	// 35 61
	    mColors.add(rgbToInt(62,82,11));	// 36 62
	    mColors.add(rgbToInt(118,19,178));	// 37 63
	    mColors.add(rgbToInt(168,242,211));	// 38 64
	    mColors.add(rgbToInt(173,147,121));	// 39 65 
	    mColors.add(rgbToInt(154,54,156));	// 40 31 57 (was 31 light purple)
	    mColors.add(rgbToInt(27,229,235));	// 41 67
	    mColors.add(rgbToInt(9,82,242));	// 42 68
	    mColors.add(rgbToInt(57,155,84));	// 43 69
	    mColors.add(rgbToInt(114,18,40));	// 44 70
	    mColors.add(rgbToInt(132,11,12));	// 45 71
	    mColors.add(rgbToInt(33,22,144));	// 46 72
	    mColors.add(rgbToInt(41,3,241));	// 47 73
	    mColors.add(rgbToInt(164,18,247));	// 48 74
	    mColors.add(rgbToInt(48,16,230));	// 49 
	    mColors.add(rgbToInt(108,86,28));	// 50 25 
	    
	    mColors.add(rgbToInt(127, 81, 18)); // 51 start of second set
	    mColors.add(rgbToInt(185,206,7));	// 52 pea green (was 26 51 was start of 2nd set)
	    mColors.add(rgbToInt(174,108,174));	// 96 53 pink was 96 (216,108,174)
	    mColors.add(rgbToInt(78,58,136));	// 97 54 dark purple was 97
	    mColors.add(rgbToInt(27,149,81));	// 55 29
	    mColors.add(rgbToInt(114,155,241));	// 56 30
	    mColors.add(rgbToInt(33,240,129));	// 57 31
	    mColors.add(rgbToInt(117,170,160));	// 58 32
	    mColors.add(rgbToInt(93,14,79));	// 59 33
	    mColors.add(rgbToInt(129,210,166));	// 60 34
	    mColors.add(rgbToInt(124,191,79));	// 61 35 
	    mColors.add(rgbToInt(188,55,188));	// 62 36
	    mColors.add(rgbToInt(117,219,105));	// 63 37
	    mColors.add(rgbToInt(11,142,235));	// 64 38
	    mColors.add(rgbToInt(144,97,194));	// 65 39
	    mColors.add(rgbToInt(215,77,161));	// 66 40
	    mColors.add(rgbToInt(192,148,92));	// 67 41
	    mColors.add(rgbToInt(197,86,215));	// 68 42
	    mColors.add(rgbToInt(103,159,140));	// 69 43
	    mColors.add(rgbToInt(42,80,186));	// 70 44
	    mColors.add(rgbToInt(136,44,28));	// 71 45 
	    mColors.add(rgbToInt(59,207,239));	// 72 46
	    mColors.add(rgbToInt(115,62,251));	// 73 47 
	    mColors.add(rgbToInt(136,179,26));	// 74 48
	    mColors.add(rgbToInt(48,29,82));	// 75 49
	    
	    mColors.add(rgbToInt(31,187,116));	// 76
	    mColors.add(rgbToInt(220,101,8));	// 76
	    mColors.add(rgbToInt(190,216,38));	// 77
	    mColors.add(rgbToInt(135,190,4));	// 78
	    mColors.add(rgbToInt(174,225,161));	// 79
	    mColors.add(rgbToInt(60,218,203));	// 80
	    mColors.add(rgbToInt(93,171,163));	// 81
	    mColors.add(rgbToInt(106,58,113));	// 82
	    mColors.add(rgbToInt(155,100,221));	// 83
	    mColors.add(rgbToInt(92,208,48));	// 84
	    mColors.add(rgbToInt(79,252,70));	// 85
	    mColors.add(rgbToInt(47,6,104));	// 86
	    mColors.add(rgbToInt(141,198,123));	// 87
	    mColors.add(rgbToInt(195,19,156));	// 88
	    mColors.add(rgbToInt(214,18,222));	// 89
	    mColors.add(rgbToInt(28,110,137));	// 90
	    mColors.add(rgbToInt(137,51,155));	// 91
	    mColors.add(rgbToInt(167,54,22));	// 92
	    mColors.add(rgbToInt(69,157,85));	// 93
	    mColors.add(rgbToInt(146,24,202));	// 94
	    mColors.add(rgbToInt(58,64,207));	// 95
	    
	    mColors.add(rgbToInt(50,200,133));	// 53 27 replace 96 green
	    mColors.add(rgbToInt(46,102,237));	// 54 28 replace 97
	    
	    
	    mColors.add(rgbToInt(146,82,91));	// 98
	    mColors.add(rgbToInt(40,76,111));	// 99
	    mColors.add(rgbToInt(80,34,231));	// 100
	    mColors.add(rgbToInt(193,81,118));	// 101 
	    return mColors;
	}
	// from https://mokole.com/palette.html; some were too close visually so I changed
	static public Vector <Integer> createSet2Colors() {
		Vector <Integer> mColors = new Vector <Integer> ();
		
		mColors.add(fromHexToInt("#4c1130"));//0 dark maroon
		mColors.add(fromHexToInt("#8b4513"));//1 saddlebrown
		mColors.add(fromHexToInt("#c15176"));//2 darkpink
		mColors.add(fromHexToInt("#2e8b57"));//3 seagreen
		mColors.add(fromHexToInt("#191970"));//4 midnightblue
		mColors.add(fromHexToInt("#800000"));//5 maroon
		mColors.add(fromHexToInt("#483d8b"));//6 darkslateblue
		mColors.add(fromHexToInt("#006400"));//7 darkgreen
		mColors.add(fromHexToInt("#708090"));//8 slategray
		mColors.add(fromHexToInt("#b22222"));//9 firebrick
		mColors.add(fromHexToInt("#5f9ea0"));//10 cadetblue
		mColors.add(fromHexToInt("#008000"));//11 green
		mColors.add(fromHexToInt("#3cb371"));//12 mediumseagreen
		mColors.add(fromHexToInt("#bc8f8f"));//13 rosybrown
		mColors.add(fromHexToInt("#663399"));//14 rebeccapurple
		mColors.add(fromHexToInt("#b8860b"));//15 darkgoldenrod
		mColors.add(fromHexToInt("#d8bfd8"));//16 thistle
		mColors.add(fromHexToInt("#bdb76b"));//17 darkkhaki
		mColors.add(fromHexToInt("#008b8b"));//18 darkcyan
		mColors.add(fromHexToInt("#cd853f"));//19 peru
		mColors.add(fromHexToInt("#4682b4"));//20 steelblue
		mColors.add(fromHexToInt("#d2691e"));//21 chocolate
		mColors.add(fromHexToInt("#9acd32"));//22 yellowgreen
		mColors.add(fromHexToInt("#20b2aa"));//23 lightseagreen
		mColors.add(fromHexToInt("#00fa9a"));//24 mediumspringgreen	CAS575 changed sets of 25 to 50; keep colors the same
		
		mColors.add(fromHexToInt("#8a2be2"));//25 51 blueviolet
		mColors.add(fromHexToInt("#00ff7f"));//52 springgreen
		mColors.add(fromHexToInt("#4169e1"));//53 royalblue
		mColors.add(fromHexToInt("#e9967a"));//54 darksalmon
		mColors.add(fromHexToInt("#dc143c"));//55 crimson
		mColors.add(fromHexToInt("#00ffff"));//56 aqua
		mColors.add(fromHexToInt("#00bfff"));//57 deepskyblue
		mColors.add(fromHexToInt("#f4a460"));//58 sandybrown
		mColors.add(fromHexToInt("#556b2f"));//59 darkolivegreen	
		mColors.add(fromHexToInt("#9370db"));//60 mediumpurple
		mColors.add(fromHexToInt("#0000ff"));//61 blue
		mColors.add(fromHexToInt("#a020f0"));//62 purple3
		mColors.add(fromHexToInt("#a64d79"));//63  
		mColors.add(fromHexToInt("#adff2f"));//64 greenyellow
		mColors.add(fromHexToInt("#ff6347"));//65 tomato
		mColors.add(fromHexToInt("#da70d6"));//66 orchid
		mColors.add(fromHexToInt("#a9a9a9"));//67 darkgray
		mColors.add(fromHexToInt("#ff00ff"));//68 fuchsia
		mColors.add(fromHexToInt("#2f4f4f"));//69 darkslategray
		mColors.add(fromHexToInt("#b0c4de"));//70 lightsteelblue
		mColors.add(fromHexToInt("#ff7f50"));//71 coral
		mColors.add(fromHexToInt("#1e90ff"));//72 dodgerblue
		mColors.add(fromHexToInt("#db7093"));//73 palevioletred
		mColors.add(fromHexToInt("#f0e68c"));//49 khaki
		mColors.add(fromHexToInt("#ffa500"));//100 orange
		
		mColors.add(fromHexToInt("#cd5c5c"));//50 24 indianred actually started here cause numbering started at 24
		mColors.add(fromHexToInt("#00008b"));//25 darkblue
		mColors.add(fromHexToInt("#980000"));//26 maroony orangered
		mColors.add(fromHexToInt("#4b0082"));//27 indigo
		mColors.add(fromHexToInt("#32cd32"));//28 limegreen
		mColors.add(fromHexToInt("#a0522d"));//29 sienna
		mColors.add(fromHexToInt("#daa520"));//30 goldenrod
		mColors.add(fromHexToInt("#7f007f"));//31 purple2
		mColors.add(fromHexToInt("#8fbc8f"));//32 darkseagreen
		mColors.add(fromHexToInt("#b03060"));//33 maroon3
		mColors.add(fromHexToInt("#66cdaa"));//34 mediumaquamarine
		mColors.add(fromHexToInt("#9932cc"));//35 darkorchid
		mColors.add(fromHexToInt("#ff0000"));//36 red
		mColors.add(fromHexToInt("#00ced1"));//37 darkturquoise
		mColors.add(fromHexToInt("#808000"));//38 olive
		mColors.add(fromHexToInt("#f1c232"));//39
		mColors.add(fromHexToInt("#ffd700"));//40 gold
		mColors.add(fromHexToInt("#6a5acd"));//41 slateblue
		mColors.add(fromHexToInt("#ffff00"));//42 yellow
		mColors.add(fromHexToInt("#c71585"));//43 mediumvioletred
		mColors.add(fromHexToInt("#0000cd"));//44 mediumblue
		mColors.add(fromHexToInt("#deb887"));//45 burlywood
		mColors.add(fromHexToInt("#40e0d0"));//46 turquoise
		mColors.add(fromHexToInt("#00ff00"));//47 lime
		mColors.add(fromHexToInt("#9400d3"));//48 darkviolet
		mColors.add(fromHexToInt("#ba55d3"));//49 mediumorchid
		
		mColors.add(fromHexToInt("#fa8072"));//75 salmon
		mColors.add(fromHexToInt("#eee8aa"));//76 palegoldenrod
		mColors.add(fromHexToInt("#134f5c"));//77
		mColors.add(fromHexToInt("#6495ed"));//78 cornflower
		mColors.add(fromHexToInt("#dda0dd"));//79 plum
		mColors.add(fromHexToInt("#add8e6"));//80 lightblue
		mColors.add(fromHexToInt("#87ceeb"));//81 skyblue
		mColors.add(fromHexToInt("#ff1493"));//82 deeppink
		mColors.add(fromHexToInt("#6b8e23"));//83 olivedrab
		mColors.add(fromHexToInt("#7b68ee"));//84 mediumslateblue
		mColors.add(fromHexToInt("#ffa07a"));//85 lighsalmon
		mColors.add(fromHexToInt("#afeeee"));//86 paleturquoise
		mColors.add(fromHexToInt("#ee82ee"));//87 violet
		mColors.add(fromHexToInt("#98fb98"));//88 palegreen
		mColors.add(fromHexToInt("#560000"));//89
		mColors.add(fromHexToInt("#7fffd4"));//90 aquamarine
		mColors.add(fromHexToInt("#ffdead"));//91 navajowhite
		mColors.add(fromHexToInt("#808080"));//92 gray
		mColors.add(fromHexToInt("#dd7e6b"));//93
		mColors.add(fromHexToInt("#ff69b4"));//94 hotpink
		mColors.add(fromHexToInt("#a52a2a"));//95 brown
		mColors.add(fromHexToInt("#ffb6c1"));//96 lightpink		
		mColors.add(fromHexToInt("#696969"));//97 dimgray
		mColors.add(fromHexToInt("#7fff00"));//98 chartreuse
		mColors.add(fromHexToInt("#c0c0c0"));//99 silver
		return mColors;
	}
	static private int rgbToInt(int R, int G, int B) { 
		if (scale!=1.0) {
			R = (int) ((double)R * scale);
			G = (int) ((double)G * scale);
			B = (int) ((double)B * scale);
		}
		return (R<<16) + (G<<8) + B;
	}
	static private int fromHexToInt(String hexCode) {
		String hex = (hexCode.startsWith("#")) ? hexCode.substring(1) : hexCode;
		int r = Integer.valueOf(hex.substring(0, 2), 16);
	    int g = Integer.valueOf(hex.substring(2, 4), 16);
	    int b = Integer.valueOf(hex.substring(4, 6), 16);
	    return rgbToInt(r,g,b);
	}
}
