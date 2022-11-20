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
import util.ErrorReport;
//import util.HelpHandler;
import util.PropertiesReader;

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
 * CAS517 made many changes for readability 
 * CAS520 properties reads in random order. So the alpha in colors.properties was replaced with order number
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ColorDialog extends JDialog implements ActionListener {
	private static final String TAB = "tab";
	private static final String VAR_SEP = ":";

	protected PersistentProps persistentProps = null;
	protected PropertiesReader props = null;
	protected boolean hasFPC;

	private JTabbedPane tabbedPane;
	private JButton okButton, cancelButton, defaultButton;
	
	/**
	 * @param props - java/src/properties/color.properties - static 
	 * @param cookie - user/.symap_saved_props - changes to colors are stored here
	 */
	public ColorDialog(PropertiesReader props, PersistentProps cookie, boolean hasFPC) {
		super();

		setModal(true);
		setTitle("SyMAP Color Editor"); 

		this.props = props;
		if (cookie != null) persistentProps = cookie.copy("SyMapColors");
		this.hasFPC = hasFPC;

		Dimension iconDim = new Dimension(35,20);

		tabbedPane = new JTabbedPane(JTabbedPane.TOP,JTabbedPane.SCROLL_TAB_LAYOUT);

		HashMap<String,String>  tabMap = 		new HashMap<String,String>(); // initial structure of tabs
		Vector<ColorVariable>   cvarsVec = 		new Vector<ColorVariable>();  // displayname/colors
		
		String name, pvalue, dn, desc;
		int ind, cInd, c2Ind;
		int nOrder=0;
		
		// Read symap lines - does not retain input order
		Enumeration <?> propertyNames = props.propertyNames();
		while (propertyNames.hasMoreElements()) {
			name = (String)propertyNames.nextElement();
			if (!name.startsWith("symap"))  continue;
			
			pvalue = props.getString(name);

			ind = name.indexOf('@');
			if (ind < 0) {					// e.g. symap.sequence.Sequence=Track
				tabMap.put(name,pvalue);
			}
			else {							// e.g. symap.sequence.Sequence@unitColor=Ruler,The color of the ruler text
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
				if (hasFPC || desc==null || !desc.startsWith("FPC")) {
					String path = name.substring(0,ind);
					String var =  name.substring(ind+1);
					cvarsVec.add(new ColorVariable(path,var,dn,desc,iconDim,nOrder));
				}
			}
		}		

// Tabs
		// read tab lines, 					e.g. tab2=Sequence Track
		HashMap<String,Integer> tabOrderMap = 	new HashMap<String,Integer>();
		for (ind = 1; ; ind++) {
			name = (String)props.getString(TAB+ind);
			if (name == null) break;
			name = name.trim();

			if (hasFPC || !name.contentEquals("FPC")) 
				tabOrderMap.put(name, ind); // CAS512 tabOrderMap.put(name,new Integer(ind));
		}
		// Build tabs in order
		Vector<ColorTab>    tabVec = 		new Vector<ColorTab>(); 
		
		for (ColorVariable colorVar : cvarsVec) {
			name = (String)tabMap.get(colorVar.className);
			ind = tabOrderMap.get(name) == null ? Integer.MAX_VALUE : ((Integer)tabOrderMap.get(name)).intValue();
			
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

		okButton = new JButton("Ok");
		cancelButton = new JButton("Cancel");
		defaultButton = new JButton("Default");

		okButton.addActionListener(this);
		cancelButton.addActionListener(this);
		defaultButton.addActionListener(this);

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(okButton);
		buttonPanel.add(cancelButton);
		buttonPanel.add(defaultButton);

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
	/*************************************************************
	 * SyMapColors=symap.mapper.Mapper.pseudoLineColorNN\=153,0,153,255
	 */
	public void setColors() {
		String cookie = null;
		if (persistentProps != null) cookie = persistentProps.getProp();
		if (cookie == null || cookie.length() == 0) {return;}
		
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
						changeColor(new ColorVariable(cn,vn,new Color(r,g,b,a)));
					}
				}
			}
			catch (Exception e) {
				ErrorReport.print(e, "Exception Parsing Color Variable ["+variables[i]+"]!!!");
			}
		}
	}

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
			if (comps[i] instanceof ColorTab) {
				((ColorTab)comps[i]).cancel();
			}
		}
	}

	protected void okAction() {
		Component[] comps = tabbedPane.getComponents();
		for (int i = 0; i < comps.length; i++) {
			if (comps[i] instanceof ColorTab) {
				((ColorTab)comps[i]).commit();
			}
		}
	}

	protected void defaultAction() {
		Component[] comps = tabbedPane.getComponents();
		for (int i = 0; i < comps.length; i++) {
			if (comps[i] instanceof ColorTab) {
				((ColorTab)comps[i]).setDefault();
			}
		}
	}

	private void changeColor(ColorVariable cv) {
		Component[] comps = tabbedPane.getComponents();
		for (int i = 0; i < comps.length; i++) {
			if (comps[i] instanceof ColorTab) {
				if (((ColorTab)comps[i]).changeColor(cv)) break;
			}
		}
	}
	/* write change to user.home/.symap_prop */
	private void setCookie() {
		if (persistentProps != null) {
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
			if (v.size() > 0) persistentProps.setProp(cookie.toString());
			else persistentProps.deleteProp();
		}
	}
	/********************************************************
	 * Writes to static color variables in the specified file
	 */
	protected static boolean setColor(String className, String variableName, Color color) {
		try {
			Class c = Class.forName(className);
			Field f;
			int lind = variableName.indexOf('[');
			if (lind < 0) {
				f = c.getField(variableName);
				f.set(null,color);
			}
			else {
				// CAS512 int ind = new Integer(variableName.substring(lind+1,variableName.indexOf(']'))).intValue();		
				int ind = Integer.parseInt(variableName.substring(lind+1,variableName.indexOf(']')));		
				f = c.getField(variableName.substring(0,lind));
				Vector colors = (Vector)f.get(null);
				colors.set(ind, color);
			}
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "set color"); return false;}
	}

	protected static Color getColor(String className, String variableName) {
		Color color = null;
		try {
			Class c = Class.forName(className);
			Field f;
			int lind = variableName.indexOf('[');
			if (lind < 0) {
				f = c.getField(variableName);
				color = (Color)f.get(null);
			}
			else {
				// CAS512 int ind = new Integer(variableName.substring(lind+1,variableName.indexOf(']'))).intValue();		
				int ind = Integer.parseInt(variableName.substring(lind+1,variableName.indexOf(']')));		
				f = c.getField(variableName.substring(0,lind));
				Vector colors = (Vector)f.get(null);
				color = (Color)colors.get(ind);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "get color"); }
		return color;
	}
}
