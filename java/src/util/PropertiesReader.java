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
 * Class <code>PropertiesReader</code> handles getting values from properties
 * files
 * 
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Properties
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class PropertiesReader extends Properties {
	/**
	 * Creates a new empty <code>PropertiesReader</code> instance.
	 */
	public PropertiesReader() {
		super();
	}

	/**
	 * Creates a new <code>PropertiesReader</code> instance loading in the properties from <code>url</code>.
	 * If an IOException occurs, the load fails and an error message is printed to standard error.
	 *
	 * @param url an <code>URL</code> value
	 */
	public PropertiesReader(URL url) {
		super();
		try {
			load(url.openStream());
			fixProps();
		} catch (IOException e) {  
			System.err.println("PropertiesReader unable to load "+url);
			e.printStackTrace();
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
	private void fixProps() 
	{
		for (Enumeration<?> e = propertyNames(); e.hasMoreElements(); )
		{
			String propName = e.nextElement().toString();
			String val = getProperty(propName);
			if (val.contains("#"))
			{
				val = val.replaceAll("#.*","");
				setProperty(propName,val);
			}
		}
	}

	/**
	 * Method <code>getString</code> returns the string value of the property interned.
	 * 
	 * @param key a <code>String</code> value of the property key.
	 * @return a <code>String</code> value for the key o null if it's not found.
	 */
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

	public int[] getInts(String prop) {
		Vector<Integer> list = new Vector<Integer>();
		String p = getString(prop+1);
		for (int i = 2; p != null; i++) {
			list.add(new Integer(p));
			p = getString(prop+i);
		}
		int[] r = new int[list.size()];
		for (int i = 0; i < r.length; i++)
			r[i] = ((Integer)list.get(i)).intValue();
		return r;
	}

	public double[] getDoubles(String prop) {
		Vector<Double> list = new Vector<Double>();
		String p = getString(prop+1);
		for (int i = 2; p != null; i++) {
			list.add(new Double(p));
			p = getString(prop+i);
		}
		double[] r = new double[list.size()];
		for (int i = 0; i < r.length; i++)
			r[i] = ((Double)list.get(i)).doubleValue();
		return r;
	}

	/**
	 * Method <code>getInt</code>
	 * 
	 * An exception of some sort will be thrown if the property doesn't exist.
	 * 
	 * @param key a <code>String</code> value of the property key.
	 * @return an <code>int</code> value for the integer corresponding to key.
	 */
	public int getInt(String key) {
		return Integer.parseInt((String)getProperty(key));
	}

	/**
	 * Method <code>getLong</code>
	 * 
	 * An exception of some sort will be thrown if the property doesn't exist.
	 * 
	 * @param key a <code>String</code> value of the property key.
	 * @return an <code>long</code> value for the integer coorisponding to key.
	 */
	public long getLong(String key) {
		return Long.parseLong((String)getProperty(key));
	}

	public float getFloat(String key) {
		return Float.parseFloat((String)getProperty(key));
	}

	/**
	 * Method <code>getDouble</code>
	 * 
	 * An exception of some sort will be thrown if the property doesn't exist.
	 * 
	 * @param key a <code>String</code> value of the property key.
	 * @return a <code>double</code> value for the double coorisponding to key.
	 */
	public double getDouble(String key) {
		return Double.parseDouble((String)getProperty(key));
	}

	/**
	 * Method <code>getColor</code>
	 * 
	 * @param key a <code>String</code> value
	 * @return a <code>Color</code> value
	 */
	public Color getColor(String key) {
		return parseColor((String)getProperty(key));
	}

	/**
	 * Method <code>getColors</code>
	 * 
	 * @param key a <code>String</code> value
	 * @return a <code>Vector</code> value
	 */
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

	/**
	 * Method <code>getFont</code>
	 * 
	 * @param key a <code>String</code> value
	 * @return a <code>Font</code> value
	 */
	public Font getFont(String key) {
		StringTokenizer st = new StringTokenizer((String)getProperty(key), ",");
		String name = st.nextToken();
		int style = Integer.parseInt(st.nextToken());
		int size = Integer.parseInt(st.nextToken());
		return new Font(name, style, size);
	}

	/**
	 * Method <code>getPoint</code>
	 * 
	 * @param key a <code>String</code> value
	 * @return a <code>Point2D</code> value
	 */
	public Point2D getPoint(String key) {
		StringTokenizer st = new StringTokenizer((String)getProperty(key), ",");
		double x = Double.parseDouble(st.nextToken());
		double y = Double.parseDouble(st.nextToken());
		return new Point2D.Double(x, y);
	}

	/**
	 * Method <code>getDimension</code> returns the dimension
	 *
	 * @param key a <code>String</code> value
	 * @return a <code>Dimension</code> value
	 */
	public Dimension getDimension(String key) {
		StringTokenizer st = new StringTokenizer((String)getProperty(key), ",");
		int width = (int)Double.parseDouble(st.nextToken());
		int height = (int)Double.parseDouble(st.nextToken());
		return new Dimension(width,height);
	}

	/**
	 * Method <code>getDoubleDimension</code> returns a Dimension2D that stores its dimension in double precision.
	 *
	 * @param key a <code>String</code> value
	 * @return a <code>DoubleDimension</code> value
	 */
	public DoubleDimension getDoubleDimension(String key) {
		StringTokenizer st = new StringTokenizer((String)getProperty(key), ",");
		double width =  Double.parseDouble(st.nextToken());
		double height = Double.parseDouble(st.nextToken());
		return new DoubleDimension(width,height);	
	}

	/**
	 * Method <code>getBoolean</code> returns true if the property is found and is equal to true ignoring case.
	 *
	 * @param key a <code>String</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean getBoolean(String key) {
		//return Boolean.valueOf(getString(key)).booleanValue(); // mdb removed 5/12/09
		
		// mdb added 5/12/09
		String value = getProperty(key);
		if (value != null && (value.equals("1") || value.toLowerCase().equals("true")))
			return true;
		return false;
	}

	/**
	 * Method <code>getFile</code> return the file. If fromHome the home directory is acquired
	 * by <code>System.getProperty("user.home")</code> and the File constructor taking two string is
	 * invoked (i.e. <code>new File(homeDir,propValue)<code>).  Otherwise, the file is created by
	 * <code>new File(propValue)</code>
	 *
	 * @param name a <code>String</code> value of property name
	 * @param fromHome a <code>boolean</code> value of true to set the path from the home directory
	 * @return a <code>File</code> value
	 * @see Utilities#getFile(String,boolean)
	 */
	public File getFile(String name, boolean fromHome) {
		return Utilities.getFile(getString(name),fromHome);
	}

	/**
	 * Method <code>getURL</code> returns the url or null if any problems occur.
	 *
	 * @param key a <code>String</code> value
	 * @return an <code>URL</code> value
	 */
	public URL getURL(String key) {
		URL url = null;
		try {
			url = new URL(getString(key));
		} catch (Exception e) { }
		return url;
	}
}
