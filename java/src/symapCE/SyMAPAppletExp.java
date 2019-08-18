package symapCE;

import javax.swing.JApplet;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.pool.DatabaseUser;
import symap.projectmanager.common.SyMAPFrameCommon;
import util.Utilities;
import util.DatabaseReader;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SyMAPAppletExp extends JApplet {
	private SyMAPExp symapExp = null;
	private SyMAPFrameCommon frame = null;
	private DatabaseReader dbReader = null;
	
	public SyMAPAppletExp() {
		super();
	}
	
	public void init() {
		SyMAP.printVersion();
		String osVer = SyMAP.getOSType();
		System.out.println("OS " + osVer);
		
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
	        

        	symapExp = new SyMAPExp(this,dbReader);
	        frame = symapExp.getFrame();
			if (!SyMAP.checkJavaSupported(frame))
				return;

	
	        // Load projects
	        String strProject, strType, strContent;
			int i = 1;
			int numProjects = 0;
			do {
				strProject = getParameter("project" + i);
				strType    = getParameter("type"    + i);
				if (strType == null) 
				{
					strType = "pseudo";
				}
				strContent = getParameter("content" + i);
				
				if ( !Utilities.isStringEmpty(strProject) ) {
					System.out.println(i + ": project="+strProject+" type="+strType+" content="+strContent);				
		
					if (!symapExp.hasProject(strProject, strType)) {
						if ( !symapExp.addProject(strProject, strType) ) {
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
			
			symapExp.build();
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
			
			// mdb added 12/31/09 #208
			//setContentPane(frame.getContentPane());
			//validate(); // needed to make visible
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
		symapExp = null;
		dbReader.close();
		dbReader = null;
		super.destroy();
	}
}
