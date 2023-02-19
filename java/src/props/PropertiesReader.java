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
 * Add comments and removed dead methods 
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class PropertiesReader extends Properties {
	
	// backend.SyProps extends PropertiesReader; sets properties from this file
	public PropertiesReader() {
		super();
	}
	// called from 2D (sequence.java, etc) to display colors from the properties directory
	public PropertiesReader(URL url) {
		super();
		try {
			load(url.openStream());
			fixProps();
		} catch (IOException e) {  
			ErrorReport.print(e, "PropertiesReader unable to load "+url);
		}
	}
	// ProjectManagerFrameCommon: read symap.config
	// Project: get and update project params file
	// PropertyFrame: read pair params file
	public PropertiesReader(File file) {
		super();
		try {
			load(new FileInputStream(file));
			fixProps();
		} catch (IOException e) {  
			ErrorReport.print(e, "PropertiesReader unable to load "+file);
		}
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
	public String getString(String key) {
		String s = (String)getProperty(key);
		if (s != null) s = s.trim();
		if (s != null) s = s.intern();
		return s;
	}
	
	//CAS507 check for blank. Remove methods getInts and getDoubles -- not used
	public int getInt(String key) {
		String val = (String)getProperty(key).trim();
		if (val.contentEquals("")) {
			// System.out.println("Warning: No value for '" + key + "'. Using 0."); CAS513 - assume they mean 0
			return 0;
		}
		return Integer.parseInt(val);
	}

	public float getFloat(String key) {
		String val = (String)getProperty(key).trim();
		if (val.contentEquals("")) {
			//System.out.println("Warning: No value for '" + key + "'. Using 0.0.");
			return 0;
		}
		return Float.parseFloat(val);
	}

	public Color getColor(String key) {
		return parseColor((String)getProperty(key));
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

	public boolean getBoolean(String key) {
		String value = getProperty(key);
		if (value != null && (value.equals("1") || value.toLowerCase().equals("true")))
			return true;
		return false;
	}
}
