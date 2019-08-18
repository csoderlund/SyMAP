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
//import util.HelpHandler;
import util.PropertiesReader;

/**
 * Class <code>ColorDialog</code> is used for editing the colors in a dialog box.
 *
 * The colors and tabs are dynamically determined by the properties in the PropertyReader object.
 * It is important that any variable that is found in the properties file
 * is made public in the class.
 *
 * ColorDialog works by changing the specified public static variables in the specified
 * classes.  The caller is responsible for putting the changes in affect. 
 * <br><br>
 * An example of a valid properties file is:<br>
 *<br>
 * # The title of the dialog box<br>
 * title=SyMAP Color Editor<br>
 * # The help id to use for the help button if applicable<br>
 * help=colors<br>
 * # string - the cookie name, don't set to have cookies not set<br>
 * cookie=SyMapColors<br>
 * # The number of columns of colors in each tab<br>
 * columns=3<br>
 * # dimension - The color icon dimensions<br>
 * iconDim=35,20<br>
 * # string - the base package to verify that the property is one of the below<br>
 * basePackage=edu<br>
 *<br>
 * # This class holds the information for all of the variables that<br>
 * # should be configurable by the user. The class must be part of a package<br>
 * # with the packages being seperated by '.' (i.e. colordialog.ColorDialog)<br>
 * # and the first package equaling the value of the basePackage property to seperate the<br>
 * # property from others.<br>
 * #<br>
 * # All variables listed must be declared public within the class.<br>
 * #<br>
 * # this properties file holds the following form (except for the colorChooserButtonImage variable):<br>
 * # &lt;class name&gt;=&lt;display name&gt;<br>
 * # or<br>
 * # &lt;class name&gt;@&lt;variable name&gt;=&lt;display name&gt;[,&lt;description&gt;[,&lt;alpha adjustable {true|false} 
 *                                                                                        defaults to true&gt;]]<br>
 * #  where all &lt;class name&gt; has an associated &lt;display name&gt;<br>
 *<br>
 * example.ClassObject=Title on Tab<br>
 * example.AnotherObject=Second Tab<br>
 * example.DifObject=Title on Tab<br>
 * <br>
 * example.ClassObject@colorVariableName=The Display Name<br>
 * example.ClassObject@colorVar=Display Name,The long description<br>
 * example.AnotherObject@colorVar=DN,Another long description,true<br>
 * example.DifObject@var[0]=Var at index 0 name<br>
 * example.DifObject@var[1]=Var at index 1 name<br>
 *  ...<br>
 * <br>
 * Tabs can be ordered by giving the variable name tab#, where # starts at 1, with the value of the tab name (i.e. tab1=Title on Tab).
 *<br><br>
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see JDialog
 * @see ActionListener
 * @see ColorDialogHandler
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ColorDialog extends JDialog implements ActionListener {
	private static final String TAB = "tab";

	private static final boolean DEFAULT_ALPHA_ENABLED = true;
	private static final String VAR_SEP = ":";

	protected PersistentProps persistentProps = null;
	//protected HelpHandler helpHandler = null; // mdb removed 4/30/09 #162
	protected PropertiesReader props = null;

	private JTabbedPane tabbedPane;
	private JButton okButton, cancelButton, defaultButton;
	
	/**
	 * Creates a new <code>ColorDialog</code> instance.
	 *
	 * @param props a <code>PropertiesReader</code> containing the properties to set up the dialog
	 * @param cookie a <code>PersistentProps</code> value of the persistent props handler to set and get the color cookie from. If null
	 *               or the cookie name is not set in the props file than no cookies will be set.
	 * @param help a <code>HelpHandler</code> value of the help handler to add a help button (optional)
	 */
	public ColorDialog(PropertiesReader props, 
			PersistentProps cookie) 
			//HelpHandler help) // mdb removed 4/30/09 #162 
	{
		super();

		setModal(true);
		setTitle(/*props.getString("title")*/"SyMAP Color Editor"); // mdb changed 1/29/09 #159

		this.props = props;

		// mdb changed 1/29/09 #159
		if (cookie != null /*&& props.getString("cookie") != null*/) 
			persistentProps = cookie.copy("SyMapColors"/*props.getString("cookie")*/);

		int columns = 3;//props.getInt("columns"); // mdb changed 1/29/09 #159
		Dimension iconDim = new Dimension(35,20);//props.getDimension("iconDim"); // mdb changed 1/29/09 #159

		String basePackage = "symap";//props.getString("basePackage"); // mdb changed 1/29/09 #159

		tabbedPane = new JTabbedPane(JTabbedPane.TOP,JTabbedPane.SCROLL_TAB_LAYOUT);

		Vector<ColorTab> tabs = new Vector<ColorTab>();
		HashMap<String,String> tabMap = new HashMap<String,String>();
		HashMap<String,Integer> tabOrderMap = new HashMap<String,Integer>();
		Vector<ColorVariable> cvars = new Vector<ColorVariable>();
		Enumeration propertyNames = props.propertyNames();
		String name, pvalue, dn, des;
		boolean a;
		int ind, cInd, c2Ind;
		while (propertyNames.hasMoreElements()) {
			name = (String)propertyNames.nextElement();
			if (name.startsWith(basePackage)) {
				pvalue = props.getString(name);
				ind = name.indexOf('@');
				if (ind < 0) {
					tabMap.put(name,pvalue);
				}
				else {
					cInd = pvalue.indexOf(',');
					c2Ind = pvalue.lastIndexOf(',');

					dn = pvalue;
					des = null;
					a = DEFAULT_ALPHA_ENABLED;

					if (cInd > 0) {
						dn = pvalue.substring(0,cInd);
						if (c2Ind != cInd) {
							des = pvalue.substring(cInd+1,c2Ind);
							a = new Boolean(pvalue.substring(c2Ind+1)).booleanValue();
						}
						else des = pvalue.substring(cInd+1);
					}
					cvars.add(new ColorVariable(name.substring(0,ind),name.substring(ind+1),dn,des,iconDim,a));
				}
			}
		}		

		for (ind = 1; ; ind++) {
			name = (String)props.getString(TAB+ind);
			if (name == null) break;
			tabOrderMap.put(name,new Integer(ind));
		}

		for (ColorVariable colorVar : cvars) {
			name = (String)tabMap.get(colorVar.className);
			ind = tabOrderMap.get(name) == null ? Integer.MAX_VALUE : ((Integer)tabOrderMap.get(name)).intValue();
			ColorTab colorTab = new ColorTab(name,columns,ind);
			ind = tabs.indexOf(colorTab);
			if (ind < 0) {
				tabs.add(colorTab);
			}
			else {
				colorTab = (ColorTab)tabs.get(ind);
			}
			colorTab.addVariable(colorVar);
		}

		Collections.sort(tabs);

		for (ColorTab colorTab : tabs) {
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

// mdb removed 4/30/09 #162
//		if (help != null) {
//			JButton helpButton = new JButton("Help");
//			help.enableHelpOnButton(helpButton,props.getString("help"));
//			buttonPanel.add(helpButton);
//		}

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(tabbedPane,BorderLayout.CENTER);
		getContentPane().add(buttonPanel,BorderLayout.SOUTH);

		pack();
	}

	public void show() {
		/*
		 * Cancel other changes that may have happend if the user closes
		 * the dialog some other way.
		 */
		cancelAction();

		super.show();
	}

	public void setColors() {
		String cookie = null;
		if (persistentProps != null) cookie = persistentProps.getProp();
		if (cookie != null && cookie.length() > 0) {
			String[] variables = cookie.split(VAR_SEP);
			String cn, vn, c, cvars[];
			int pind, eind, r, g, b, a;
			for (int i = 0; i < variables.length; i++) {
				try {
					pind = variables[i].lastIndexOf('.');
					eind = variables[i].indexOf('=');
					if (pind < 0 || eind < pind) {
						System.out.println("Illegal Variable Found ["+variables[i]+"]!!!!");
					}
					else {
						cn = variables[i].substring(0,pind);
						vn = variables[i].substring(pind+1,eind);
						c  = variables[i].substring(eind+1);
						cvars = c.split(",");
						if (cvars.length < 3 || cvars.length > 4) {
							System.out.println("Invalid Color Variable: ["+c+"]");
						}
						else {
							r = new Integer(cvars[0]).intValue();
							g = new Integer(cvars[1]).intValue();
							b = new Integer(cvars[2]).intValue();
							if (cvars.length == 4) a = new Integer(cvars[3]).intValue();
							else a = 255;
							changeColor(new ColorVariable(cn,vn,new Color(r,g,b,a)));
						}
					}
				}
				catch (Exception e) {
					System.out.println("Exception Parsing Color Variable ["+variables[i]+"]!!!");
					e.printStackTrace();
				}
			}
		}
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == okButton) {
			okAction();
			setCookie();
			//analyzer.hide(); // mdb removed 6/29/07 #118
			setVisible(false); // mdb added 6/29/07 #118
		}
		else if (e.getSource() == cancelButton) {
			cancelAction();
			//analyzer.hide(); // mdb removed 6/29/07 #118
			setVisible(false); // mdb added 6/29/07 #118
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

	protected static boolean setColor(String className, String variableName, Color color) {
		boolean success = false;
		try {
			Class c = Class.forName(className);
			Field f;
			int lind = variableName.indexOf('[');
			if (lind < 0) {
				f = c.getField(variableName);
				f.set(null,color);
			}
			else {
				int ind = new Integer(variableName.substring(lind+1,variableName.indexOf(']'))).intValue();		
				f = c.getField(variableName.substring(0,lind));
				Vector colors = (Vector)f.get(null);
				colors.set(ind,color);
			}
			success = true;
		}
		catch (Exception exc) {
			exc.printStackTrace();
		}
		return success;
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
				int ind = new Integer(variableName.substring(lind+1,variableName.indexOf(']'))).intValue();		
				f = c.getField(variableName.substring(0,lind));
				Vector colors = (Vector)f.get(null);
				color = (Color)colors.get(ind);
			}
		}
		catch (Exception exc) {
			exc.printStackTrace();
		}
		return color;
	}
}
