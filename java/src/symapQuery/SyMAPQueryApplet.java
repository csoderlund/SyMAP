package symapQuery;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.pool.DatabaseUser;
import util.Utilities;
import util.DatabaseReader;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SyMAPQueryApplet extends JApplet {
	private SyMAPQueryFrame symapQ = null;
	private DatabaseReader dbReader = null;
	
	public SyMAPQueryApplet() {
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
	        

	        symapQ = new SyMAPQueryFrame(this,dbReader, false);
			if (!SyMAP.checkJavaSupported(symapQ))
				return;

			Utilities.setResClass(this.getClass());
			Utilities.setHelpParentFrame(symapQ);

	
	        // Load projects
	        String strProject, strType, strContent;
			int i = 1;
			int numProjects = 0;
			do {
				strProject = getParameter("project" + i);
				strType    = getParameter("type"    + i);
				strContent = getParameter("content" + i);
				
				if (strType == null || strType.equals(""))
				{
					strType = "pseudo";
				}
				
				if ( !Utilities.isStringEmpty(strProject) ) {
					System.out.println(i + ": project="+strProject+" type="+strType+" content="+strContent);				
		
					if (!symapQ.hasProject(strProject, strType)) {
						if ( !symapQ.addProject(strProject, strType) ) {
							// Project not found, addProject() will print an error message
							symapQ = null;
							return;
						}
						numProjects++;
					}
				}
				
				i++;
			} while (i <= SyMAP.MAX_PROJECTS);
			
			if (numProjects == 0) {
				System.err.println("No projects specified!");
				symapQ = null;
				return;
			}
			
			symapQ.build();
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
		if (symapQ != null) {
			symapQ.build();
			
			// mdb added 12/31/09 #208
			//setContentPane(symapQ.getContentPane());
			
			//validate(); // needed to make visible
			symapQ.toFront();
			symapQ.setVisible(true);
		}
	}
	
	public void stop() {
		if (symapQ != null) symapQ.setVisible(false);
	}
	
	public void destroy() {
		if (symapQ != null) {
			symapQ.setVisible(false);
			symapQ.dispose();
			symapQ = null;
		}
		dbReader.close();
		dbReader = null;
		super.destroy();
	}
}
