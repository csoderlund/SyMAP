package util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Class <code>PropertiesReader</code> handles getting values from properties files
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class PropertiesReader extends Properties {
	/**
	 * Creates a new empty <code>PropertiesReader</code> instance.
	 */
	public PropertiesReader() {
		super();
	}

	public PropertiesReader(URL url) {
		super();
		try {
			load(url.openStream());
			fixProps();
		} catch (IOException e) {  
			ErrorReport.print(e, "PropertiesReader unable to load "+url);
		}
	}

	public PropertiesReader(File file) {
		super();
		try {
			load(new FileInputStream(file));
			fixProps();
		} catch (IOException e) {  
			System.err.println("PropertiesReader unable to load "+file);
			e.printStackTrace();
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
		if (s != null) {
			s = s.trim();
		}
		if (s != null) s = s.intern();
		return s;
	}
	public String[] getStrings(String prop) {
		Vector<String> list = new Vector<String>();
		String p = getString(prop+1);
		for (int i = 2; p != null; i++) {
			list.add(p);
			p = getString(prop+i);
		}
		return (String[])list.toArray(new String[list.size()]);
	}

	/**
	 * CAS507 check for blank. Remove methods getInts and getDoubles -- not used.
	 */
	public int getInt(String key) {
		String val = (String)getProperty(key).trim();
		if (val.contentEquals("")) {
			// System.out.println("Warning: No value for '" + key + "'. Using 0."); CAS513 - assume they mean 0
			return 0;
		}
		return Integer.parseInt(val);
	}

	public long getLong(String key) {
		String val = (String)getProperty(key).trim();
		if (val.contentEquals("")) {
			//System.out.println("Warning: No value for '" + key + "'. Using 0.");
			return 0;
		}
		return Long.parseLong(val);
	}

	public float getFloat(String key) {
		String val = (String)getProperty(key).trim();
		if (val.contentEquals("")) {
			//System.out.println("Warning: No value for '" + key + "'. Using 0.0.");
			return 0;
		}
		return Float.parseFloat(val);
	}

	public double getDouble(String key) {
		String val = (String)getProperty(key).trim();
		if (val.contentEquals("")) {
			//System.out.println("Warning: No value for '" + key + "'. Using 0.");
			return 0;
		}
		return Double.parseDouble(val);
	}

	public Color getColor(String key) {
		return parseColor((String)getProperty(key));
	}

	public Vector<Color> getColors(String key) {
		StringTokenizer st = new StringTokenizer((String)getProperty(key), ";");
		Vector<Color> v = new Vector<Color>(st.countTokens());
		while (st.hasMoreTokens())
			v.add(parseColor(st.nextToken()));
		return v;
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

	public Font getFont(String key) {
		StringTokenizer st = new StringTokenizer((String)getProperty(key), ",");
		String name = st.nextToken();
		int style = Integer.parseInt(st.nextToken());
		int size = Integer.parseInt(st.nextToken());
		return new Font(name, style, size);
	}

	public Point2D getPoint(String key) {
		StringTokenizer st = new StringTokenizer((String)getProperty(key), ",");
		double x = Double.parseDouble(st.nextToken());
		double y = Double.parseDouble(st.nextToken());
		return new Point2D.Double(x, y);
	}

	public Dimension getDimension(String key) {
		StringTokenizer st = new StringTokenizer((String)getProperty(key), ",");
		int width = (int)Double.parseDouble(st.nextToken());
		int height = (int)Double.parseDouble(st.nextToken());
		return new Dimension(width,height);
	}

	public DoubleDimension getDoubleDimension(String key) {
		StringTokenizer st = new StringTokenizer((String)getProperty(key), ",");
		double width =  Double.parseDouble(st.nextToken());
		double height = Double.parseDouble(st.nextToken());
		return new DoubleDimension(width,height);	
	}

	public boolean getBoolean(String key) {
		String value = getProperty(key);
		if (value != null && (value.equals("1") || value.toLowerCase().equals("true")))
			return true;
		return false;
	}
	/**
	 * If fromHome the home directory is acquired by System.getProperty("user.home") 
	 * and the File constructor taking two string is invoked (i.e. new File(homeDir,propValue)).  
	 * Otherwise, the file is created by new File(propValue)
	 */
	public File getFile(String name, boolean fromHome) {
		return Utilities.getFile(getString(name),fromHome);
	}

	public URL getURL(String key) {
		URL url = null;
		try {
			url = new URL(getString(key));
		} catch (Exception e) { }
		return url;
	}
}
