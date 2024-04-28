package props;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import util.ErrorReport;
import util.Utilities;

/*************************************************************
 * This reads/writes from user.dir/.symap_saved_props
 *   ColorDialog.setColors() getProp; setCookie() setProp and deleteProp; name=SyMapColors
 *   symapQuery.FieldData setColumnDefaults(), saveColumnDefaults(); name=SyMapColumns1 and name=SyMapColumns2
 *   util.SizedJFrame and SyMAPframe, name=SyMAPDisplayPosition
 * 
 * util.PropertiesReader reads from /properties
 * 
 * CAS521 this was a stupid interface with 3 basically useless files i/f to it. Reduced to one simple file.
 */
public class PersistentProps {
	private Properties props;
    private File file;
    private String name;
    
    public static final  String PERSISTENT_PROPS_FILE = symap.Globals.PERSISTENT_PROPS_FILE; // CAS553 access Globals
	private static final String PP_HEADER = "SyMAP Saved Properties. Do not modify.";
    
	public PersistentProps(Properties props, File file, String name) {
		this.file = file;
		this.props = props;
		this.name = name;
	}
	public PersistentProps() {
		file = Utilities.getFile(PERSISTENT_PROPS_FILE,true);
		props = new Properties();
		readProps(props,file);
	}
    public PersistentProps copy(String name) {
    	return new PersistentProps(props, file, name);
    };
    public boolean equals(Object obj) {
		if (obj instanceof PersistentProps) {
		    PersistentProps ch = (PersistentProps)obj;
		    return (ch.file==file && ch.props==props && ch.name==name);
		}
		return false;
    }
  
    public String getName() {return name;}
    public void setName(String name) {this.name = name;}
    
    public String getProp() {
		synchronized (props) {
		    return props.getProperty(name);
		}
    }

    public void setProp(String value) {
		synchronized (props) {
		    props.setProperty(name,value);
		    writeProps(props,file);
		}
    }

    public void deleteProp() {
		synchronized (props) {
		    props.remove(name);
		    writeProps(props,file);
		}
    }

    private synchronized static void readProps(Properties props, File file) {
		try {
		    if (file.isFile())
		    	props.load(new FileInputStream(file));
		}
		catch (Exception e) {
			ErrorReport.print(e, "Reading " + PERSISTENT_PROPS_FILE);
		}
    }

    private synchronized static void writeProps(Properties props, File file) {
		try {
		    props.store(new FileOutputStream(file),PP_HEADER);
		}
		catch (Exception e) {
			ErrorReport.print(e, "Writing " + PERSISTENT_PROPS_FILE);
		}
    }
}

