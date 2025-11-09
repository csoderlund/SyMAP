package props;

import java.awt.Color;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;

import util.ErrorReport;

/**
 * Class PropertiesReader handles getting values from properties files
 */
public class PropertiesReader extends Properties {
	private static final long serialVersionUID = 1L;

	// colorDialog
	public PropertiesReader(URL url) {
		super();
		try {
			load(url.openStream());
			fixProps();
		} catch (IOException e) {  
			ErrorReport.print(e, "PropertiesReader unable to load "+url);
		}
	}
	// ManagerFrame: read symap.config
	// Mproject: get and update project params file
	// Mpair: read pair params file
	public PropertiesReader(File file) {
		super();
		try {
			load(new FileInputStream(file));
			fixProps();
		} catch (IOException e) {  
			ErrorReport.print(e, "PropertiesReader unable to load "+file);
		}
	}
	
	public String getString(String key) {
		String s = (String)getProperty(key);
		if (s != null) s = s.trim();
		if (s != null) s = s.intern();
		return s;
	}

	public Color getColor(String key) {
		return parseColor((String)getProperty(key));
	}
	
	private void fixProps() {
		for (Enumeration<?> e = propertyNames(); e.hasMoreElements(); ) {
			String propName = e.nextElement().toString();
			String val = getProperty(propName);
			if (val.contains("#")) {
				val = val.replaceAll("#.*","");
				setProperty(propName,val);
			}
		}
	}
	private Color parseColor(String colorString) {
		StringTokenizer st = new StringTokenizer(colorString, ",");
		int r = Integer.decode(st.nextToken()).intValue();
		int g = Integer.decode(st.nextToken()).intValue();
		int b = Integer.decode(st.nextToken()).intValue();
		int a = 255;
		if (st.hasMoreTokens())
			a = Integer.decode(st.nextToken()).intValue();
		return new Color(r, g, b, a);
	}
}
