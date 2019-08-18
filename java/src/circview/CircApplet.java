package circview;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;

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
public class CircApplet extends JApplet
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
			
			HelpBar helpBar = new HelpBar(-1, 17, true, false, false); // mdb added 7/6/09
			
			DatabaseReader db = getDatabaseReader();
			ResultSet rs;
			Statement s = db.getConnection().createStatement();
			
			int[] pIdxList = new int[projects.size()];
			int i = 0;
			for (String proj : projects)
			{
				rs = s.executeQuery("select idx from projects where name='" + proj + "'");
				rs.first();
				int projIdx = rs.getInt(1);
				pIdxList[i] = projIdx;
				i++;
			}
			
			
			CircFrame frame = new CircFrame(null,db,pIdxList,null,helpBar);
			frame.setPreferredSize( new Dimension(900, 1000) );
			frame.setMinimumSize( new Dimension(900, 1000) );
	
			new Thread(new Runnable() {
				public void run() {
					repaint();
				}
			}).start();
	
			//Container cp = getContentPane();
			//cp.add(frame.getContentPane() ,			 BorderLayout.NORTH);
			frame.toFront();
			frame.setVisible(true);
			
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
