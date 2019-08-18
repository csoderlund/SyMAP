package props;

import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class FileProps implements PersistentProps {
    private Properties props;
    private File file;
    private String header;
    private String name;

    public FileProps(File file, String header, String name) {
	this(file,header,name,null);
    }

    protected FileProps(File file, String header, String name, Properties props) {
	if (props == null) {
	    props = new Properties();
	    readProps(props,file);
	}
	this.file = file;
	this.header = header;
	this.name = name;
	this.props = props;
    }

    public PersistentProps copy(String name) {
	return new FileProps(file,header,name,props);
    }

    /**
     * Method <code>equals</code> returns true if obj is an instance of FileProps and the
     * files are equal and the names are equal.
     *
     * @param obj an <code>Object</code> value
     * @return a <code>boolean</code> value
     * @see java.io.File#equals(Object)
     */
    public boolean equals(Object obj) {
	if (obj instanceof FileProps) {
	    FileProps fp = (FileProps)obj;
	    return file.equals(fp.file) && ((name == null && fp.name == null) || name != null && name.equals(fp.name));
	}
	return false;
    }

    public String getName() {
	return name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getProp() {
	synchronized (props) {
	    return props.getProperty(name);
	}
    }

    public void setProp(String value) {
	synchronized (props) {
	    props.setProperty(name,value);
	    writeProps(props,file,header);
	}
    }

    public void deleteProp() {
	synchronized (props) {
	    props.remove(name);
	    writeProps(props,file,header);
	}
    }

    private synchronized static void readProps(Properties props, File file) {
	try {
	    if (file.isFile())
		props.load(new FileInputStream(file));
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }

    private synchronized static void writeProps(Properties props, File file, String header) {
	try {
	    props.store(new FileOutputStream(file),header);
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
