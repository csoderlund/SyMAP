package symap3D;

import javax.swing.JApplet;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.pool.DatabaseUser;
import util.Utilities;
import util.DatabaseReader;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SyMAPApplet3D extends JApplet {
	private SyMAP3D symap3d = null;
	private SyMAPFrame3D frame = null;
	private DatabaseReader dbReader = null;
	
	public SyMAPApplet3D() {
		super();
	}
	
	public void init() {
		SyMAP.printVersion();
		String osVer = SyMAP.getOSType();
		String j3dVer = SyMAP3D.getInstalledJava3DVersionStr();
		System.out.println("OS " + osVer);
		System.out.println("Java3D Version " + j3dVer);
		
		try {
	        // Connect to database
			String database = getParameter("database");
			String username = getParameter("username");
			String password = getParameter("password");
	        Class.forName("com.mysql.jdbc.Driver");
	        dbReader = DatabaseUser.getDatabaseReader(SyMAPConstants.DB_CONNECTION_SYMAP_APPLET_3D,
	        			database,
	        			username,
	        			password,
	        			Utilities.getHost(this));
	        
	        try {
	        	symap3d = new SyMAP3D(this,dbReader);
		        frame = symap3d.getFrame();
				if (!SyMAP.checkJavaSupported(frame))
					return;
				if (!SyMAP3D.checkJava3DSupported(frame))
					return;
	        }
	        catch (Exception e) {
	        	String detailStr = "";
	        	if (j3dVer != "") {
	        		detailStr = "\nJava 3D is installed as an extension on this computer, " + 
	        			"\nand must be removed in order to use Java 3D applets. ";	
	        		if (osVer.startsWith("Mac")) {
	        			detailStr += "\n(This is a known problem for Mac OSX 10.6 and later.)";	
	        		}
	        	}
	        	JOptionPane.showMessageDialog(null, "Unfortunately, the 3D applet has encountered a problem! " + detailStr);
	        	throw(e);
	        }
	
	        // Load projects
	        String strProject, strType, strContent;
			int i = 1;
			int numProjects = 0;
			do {
				strProject = getParameter("project" + i);
				strType    = getParameter("type"    + i);
				strContent = getParameter("content" + i);
				
				if ( !Utilities.isStringEmpty(strProject) ) {
					System.out.println(i + ": project="+strProject+" type="+strType+" content="+strContent);				
		
					if (!symap3d.hasProject(strProject, strType)) {
						if ( !symap3d.addProject(strProject, strType) ) {
							// Project not found, addProject() will print an error message
							frame = null;
							return;
						}
						numProjects++;
					}
				}
				
				i++;
			} while (i <= SyMAP.MAX_PROJECTS);
			
			if (numProjects == 0) {
				System.err.println("No projects specified!");
				frame = null;
				return;
			}
			
			symap3d.build();
			System.out.println("Initialization done, applet is ready.");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	// mdb added 12/31/09 #208
	public void fit() { // called from javascript
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				validate();
			}
		});
	}
	
	public void start() {
		if (frame != null) {
			frame.build();
			
			frame.toFront();
			frame.setVisible(true);
		}
	}
	
	public void stop() {
		if (frame != null) frame.setVisible(false);
	}
	
	public void destroy() {
		if (frame != null) {
			frame.setVisible(false);
			frame.dispose();
			frame = null;
		}
		symap3d = null;
		dbReader.close();
		dbReader = null;
		super.destroy();
	}
}
