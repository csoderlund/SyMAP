package blockview;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;
import java.awt.BorderLayout;
import java.awt.Container;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;

import dotplot.Data;
import symap.frame.HelpBar;
import symap.pool.DatabaseUser;
import symap.SyMAP;
import symap.SyMAPConstants;
import util.DatabaseReader;
import util.Utilities;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class BlockApplet extends JApplet
{
	public static final String DATABASE_URL  = "database";
	public static final String USERNAME      = "username";
	public static final String PASSWORD      = "password";

	public void init() {
		super.init();

		try 
		{
			SyMAP.printVersion();
				
			// mdb added 4/8/08
			if (!SyMAP.checkJavaSupported(this))
				return;
	
			// Get parameters - mdb rewritten 12/16/09 #205
			final Vector<String> projects = new Vector<String>();
			for (int i = 1;  i <= SyMAP.MAX_PROJECTS;  i++) {
				String projName = getParameter("PROJECT" + i);
				if (!Utilities.isStringEmpty(projName))
					projects.add(projName);
			}
			if (projects.size() != 2)
			{
				System.err.println("Block view can only take two projects!");
				System.exit(0);
			}
			String projName1 = projects.get(0);
			String projName2 = projects.get(1); 
			
			
			DatabaseReader db = getDatabaseReader();
			ResultSet rs;
			Statement s = db.getConnection().createStatement();
			
			rs = s.executeQuery("select idx from projects where name='" + projName1 + "'");
			rs.first();
			int projIdx1 = rs.getInt(1);
			rs = s.executeQuery("select idx from projects where name='" + projName2 + "'");
			rs.first();
			int projIdx2 = rs.getInt(1);
			
			Container cp = null; //getContentPane();
			BlockViewFrame bvframe = new BlockViewFrame(db,projIdx1,projIdx2);
			bvframe.toFront();
			bvframe.setVisible(true);
	
			new Thread(new Runnable() {
				public void run() {
					repaint();
				}
			}).start();
	
			//cp.add(bvframe.getContentPane() ,			 BorderLayout.NORTH);
			
			System.out.println("Initialization done, applet is ready.");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public void fit() { // called from javascript
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				validate();
			}
		});
	}

	
	
	private DatabaseReader getDatabaseReader() {
		return DatabaseUser.getDatabaseReader(SyMAPConstants.DB_CONNECTION_SYMAP_APPLET,
			getParameter(DATABASE_URL),
			getParameter(USERNAME),
			getParameter(PASSWORD),
			Utilities.getHost(this));
}

}
