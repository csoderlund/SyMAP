package colordialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.Collections;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import props.PersistentProps;
import props.PropertiesReader;
import symap.drawingpanel.SyMAP2d;
import util.ErrorReport;

/**
 * Class ColorDialog is used for editing the colors in a dialog box.
 * For example, to add the color for pseudoLineColorPP:
 * 		properties/color.properties
 * 			symap.mapper.Mapper@pseudoLineColorPP
 * 		In symap.mapper.Mapper.java
 * 			it reads /properties/mapper.properties
 * 			add pseudoLineColorPP as a public static variable and read it value
 * 		/properties/mapper.properties
 * 			add it to this file
 * 		Add to appropriate code to use value, in this case, it was in PseudoPseudoHits.java
 * 
 * Tabs are ordered by giving the variable name tab#, where # starts at 1, with the value of the tab name (i.e. tab1=Title on Tab).
 * @see JDialog, ActionListener, ColorDialogHandler
 * 
 * Tabs can be added by:
 * 		Add tab in colors.properties 
 * CAS517 made many changes for readability 
 * CAS520 properties reads in random order. So the alpha in colors.properties was replaced with order number
 * CAS532 fixed bug of defaults not always working by reading pFiles instead of using getColor
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ColorDialog extends JDialog implements ActionListener {
	private static final String TAB = "tab";
	private static final String VAR_SEP = ":";

	private PersistentProps changedProps = null;
	private String propName="SyMapColors";

	private JTabbedPane tabbedPane;
	private JButton okButton, cancelButton, defaultButton;
	
	private final String propsFile = "/properties/colors.properties"; // CAS521 moved from SyMAP.java
	private String [] pFiles = {"annotation", "closeup","mapper", "sequence", "dotplot"}; // CAS532 add to save defaults; CAS541 add dotplot
	
	static private HashMap <String, Color> colorDefs = new HashMap <String, Color> (); // CAS532
		
	/**
	 * @param cookie - user/.symap_saved_props - changes to colors are stored here
	 */
	public ColorDialog(PersistentProps cookie) {
		super();

		setModal(true);
		setTitle("SyMAP Color Editor"); 

		if (cookie != null) changedProps = cookie.copy(propName);
		
		tabbedPane = new JTabbedPane(JTabbedPane.TOP,JTabbedPane.SCROLL_TAB_LAYOUT);

		initDefaultProps(); 
		initPropColors();
		initCookieColors();
		
		okButton = new JButton("Ok");
		cancelButton = new JButton("Cancel");
		defaultButton = new JButton("Default");
		JButton helpButton = util.Jhtml.createHelpIconUserSm(util.Jhtml.colorIcon); // CAS532 add

		okButton.addActionListener(this);
		cancelButton.addActionListener(this);
		defaultButton.addActionListener(this);

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(okButton);
		buttonPanel.add(cancelButton);
		buttonPanel.add(defaultButton);
		buttonPanel.add(helpButton);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(tabbedPane,BorderLayout.CENTER);
		getContentPane().add(buttonPanel,BorderLayout.SOUTH);

		pack();
		setLocationRelativeTo(null); // CAS520
	}
/* CAS512 depreciated, not called
	public void show() {
		 //Cancel other changes that may have happend if the user closes
		 //the dialog some other way.
		cancelAction();

		super.show();
	}
*/
	public void setDotplot() { // CAS541
		tabbedPane.setSelectedIndex(3);
	}

	// read colors.properties
	private void initPropColors() { // CAS532 moved from constructor and make separate method
	try {
		Dimension iconDim = new Dimension(35,20);

		HashMap<String,String>  tabMap = 	new HashMap<String,String>(); // initial structure of tabs
		Vector<ColorVariable>   cvarsVec = 	new Vector<ColorVariable>();  // displayname/colors
		
		String name, pvalue, dn, desc;
		int ind, cInd, c2Ind;
		int nOrder=0;
		
		// Read /properties file, symap lines - does not retain input order
		PropertiesReader defaultProps = new PropertiesReader(SyMAP2d.class.getResource(propsFile));
		
		Enumeration <?> propertyNames = defaultProps.propertyNames();
		while (propertyNames.hasMoreElements()) {
			name = (String)propertyNames.nextElement();
			if (name.startsWith("tab"))  continue; // e.g. tab1=General
			
			pvalue = defaultProps.getString(name);

			ind = name.indexOf('@');
			if (ind < 0) {				// e.g. symap.sequence.Sequence=Track
				tabMap.put(name,pvalue);
			}
			else {				// e.g. symap.sequence.Sequence@unitColor=Ruler,The color of the ruler text,1
				cInd = pvalue.indexOf(',');
				c2Ind = pvalue.lastIndexOf(',');

				dn = pvalue;
				desc = null; // description
				
				if (cInd > 0) {
					dn = pvalue.substring(0,cInd).trim();
					if (c2Ind != cInd) {
						desc = pvalue.substring(cInd+1, c2Ind).trim();
						nOrder = Integer.parseInt(pvalue.substring(c2Ind+1)); // CAS512 a = new Boolean(pvalue.substring(c2Ind+1)).booleanValue();
					}
					else {
						desc = pvalue.substring(cInd+1).trim();
						nOrder=0;
					}
				}
				
				String path = name.substring(0,ind);
				String var =  name.substring(ind+1);				
				cvarsVec.add(new ColorVariable(path,var,dn,desc,iconDim,nOrder));
			}
		}		

// Tabs
		// read tab lines, 					e.g. tab2=Sequence Track
		HashMap<String,Integer> tabOrderMap = 	new HashMap<String,Integer>();
		for (ind = 1; ; ind++) {
			name = (String) defaultProps.getString(TAB+ind);
			if (name == null) break;
			name = name.trim();

			tabOrderMap.put(name, ind); 
		}
		// Build tabs in order
		Vector<ColorTab>    tabVec = 		new Vector<ColorTab>(); 
		
		for (ColorVariable colorVar : cvarsVec) {
			name = (String)tabMap.get(colorVar.className);
			ind = (tabOrderMap.get(name)==null) ? Integer.MAX_VALUE : tabOrderMap.get(name); // CAS532 was Integer conversion
			
			ColorTab colorTab = new ColorTab(name, ind);
			
			ind = tabVec.indexOf(colorTab);
			if (ind < 0) tabVec.add(colorTab);
			else 		 colorTab = (ColorTab)tabVec.get(ind);
			
			colorTab.addVariable(colorVar);
		}

		Collections.sort(tabVec); // sort by order

		for (ColorTab colorTab : tabVec) {
			tabbedPane.add(colorTab);
			colorTab.setup();
		}
	}
	catch (Exception e) {ErrorReport.print(e, "init prop colors");}
	}
	
	private void initDefaultProps() { // CAS532 add
	try {
		for (String f : pFiles) {
			String file = "/properties/" + f + ".properties";
			PropertiesReader defProps = new PropertiesReader(SyMAP2d.class.getResource(file));
			
			Enumeration <?> propertyNames = defProps.propertyNames();
			while (propertyNames.hasMoreElements()) {
				String name = (String)propertyNames.nextElement();
				String pvalue = defProps.getString(name);
				if (pvalue.contains(",")) {
					Color cvalue = defProps.getColor(name);
					colorDefs.put(name, cvalue);
				}
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e, "init prop colors");}
	}
	/*************************************************************
	 * SyMapColors=symap.mapper.Mapper.pseudoLineColorNN\=153,0,153,255
	 */
	private void initCookieColors() {
		String cookie = (changedProps != null) ? changedProps.getProp() : null;
		if (cookie == null || cookie.length() == 0) return;
			
		String[] variables = cookie.split(VAR_SEP);
		String cn, vn, c, cvars[];
		int pind, eind, r, g, b, a;
		for (int i = 0; i < variables.length; i++) {
			try {
				pind = variables[i].lastIndexOf('.'); // name shown beside color box
				eind = variables[i].indexOf('=');
				if (pind < 0 || eind < pind) {
					System.out.println("Illegal Variable Found ["+variables[i]+"]!!!!");
				}
				else {
					cn = variables[i].substring(0,pind); // path
					vn = variables[i].substring(pind+1,eind);
					c  = variables[i].substring(eind+1);
					cvars = c.split(",");
					if (cvars.length < 3 || cvars.length > 4) {
						System.out.println("Invalid Color Variable: ["+c+"]");
					}
					else {
						r = Integer.parseInt(cvars[0]); // CAS512 new Integer(cvars[0]).intValue();
						g = Integer.parseInt(cvars[1]); //new Integer(cvars[1]).intValue();
						b = Integer.parseInt(cvars[2]); //new Integer(cvars[2]).intValue();
						if (cvars.length == 4) a = Integer.parseInt(cvars[3]); //new Integer(cvars[3]).intValue();
						else a = 255;
						
						changeCookieColor(new ColorVariable(cn,vn,new Color(r,g,b,a)));
					}
				}
			}
			catch (Exception e) {
				ErrorReport.print(e, "Exception Parsing Color Variable ["+variables[i]+"]!!!");
			}
		}
	}
	private void changeCookieColor(ColorVariable cv) { // called from right above reading properties
		Component[] comps = tabbedPane.getComponents();
		for (int i = 0; i < comps.length; i++) {
			if (comps[i] instanceof ColorTab) {
				if (((ColorTab)comps[i]).changeColor(cv)) break;
			}
		}
	}
	
	/****************************************************************/
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == okButton) {
			okAction();
			setCookie();
			setVisible(false); 
		}
		else if (e.getSource() == cancelButton) {
			cancelAction();
			setVisible(false); 
		}
		else if (e.getSource() == defaultButton) {
			defaultAction();
		}
	}
	
	protected void cancelAction() {
		Component[] comps = tabbedPane.getComponents();
		for (int i = 0; i < comps.length; i++) {
			if (comps[i] instanceof ColorTab) 
				((ColorTab)comps[i]).cancel();
		}
	}
	protected void okAction() {
		Component[] comps = tabbedPane.getComponents();
		for (int i = 0; i < comps.length; i++) {
			if (comps[i] instanceof ColorTab) 
				((ColorTab)comps[i]).commit();
		}
	}
	/**CAS532 changed to just set defaults for current tab 
	   The getSelectedIndex() gets wrong index on Linux, which adds extra components at the beginning */
	protected void defaultAction() {
		try {
			Component c = tabbedPane.getSelectedComponent();
			((ColorTab) c).setDefault();
		}
		catch (Exception e) {
			ErrorReport.print(e, "This feature does not work on this machine. Setting all defaults. (Please email symap@agcol.arizona.edu)");
			Component[] comps = tabbedPane.getComponents();
			for (int i = 0; i < comps.length; i++) 
				if (comps[i] instanceof ColorTab) 
					((ColorTab)comps[i]).setDefault();
		}
	}
	
	/* write change to user.home/.symap_prop; called after any changed colors; called on Ok */
	private void setCookie() {
		if (changedProps == null) return;
		
		Component[] comps = tabbedPane.getComponents();
		Vector<ColorVariable> v = new Vector<ColorVariable>();
		for (int i = 0; i < comps.length; i++) {
			if (comps[i] instanceof ColorTab) {
				v.addAll(((ColorTab)comps[i]).getChangedVariables());
			}
		}
		Iterator<ColorVariable> iter = v.iterator();
		StringBuffer cookie = new StringBuffer();
		ColorVariable cv;
		for (int i = v.size(); i > 0; i--) {
			cv = iter.next();
			cookie.append(cv.toString());
			if (i > 1) cookie.append(VAR_SEP);
		}
		if (v.size() > 0) 	changedProps.setProp(cookie.toString());
		else 				changedProps.deleteProp();
	}
	/********************************************************
	 * Writes to static color variables in the specified file; called by ColorVariable on change
	 * CAS532 removed some dead code looking for variableName.indexOf('[') which never happens
	 */
	protected static boolean setColor(String className, String variableName, Color color) {
		try {
			Class <?> c = Class.forName(className);
			Field f = c.getField(variableName);
			f.set(null,color);
			
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "set color"); return false;}
	}

	protected static Color getColor(String className, String variableName) {// replaced with getDefault
		Color color = null;
		try {
			Class <?> c = Class.forName(className);
			Field f = c.getField(variableName);
			color = (Color)f.get(null);
		}
		catch (Exception e) {ErrorReport.print(e, "get color"); }
		return color;
	}
	
	protected static Color getDefault(String var) {
		if (!colorDefs.containsKey(var)) {
			System.out.println("Not found " + var);
			return Color.black;
		}
		return colorDefs.get(var);
	}
}
