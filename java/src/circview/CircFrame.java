package circview;

import javax.swing.*;

import java.awt.*;
import java.sql.*;

import symap.frame.HelpBar;
import util.DatabaseReader;

import java.util.TreeSet;
import java.util.Vector;
import java.util.TreeMap;

import java.applet.Applet;

// Draw circle view block display.
// Considerations:
// 1. Allow more than two projects
// 2. Allow subsets of groups from each project
// 3. Allow self-alignments. 
//		For the last, we assign each group a locally generated id, different from its database idx 
public class CircFrame extends JFrame
{
	private static final long serialVersionUID = 2371747762964367253L;

	String refName, Name2;
	DatabaseReader mDB;
	Vector<Integer> mColors;
	final int maxColors = 100;
	TreeMap<Integer,String> mRefNames, mGrp2Names;
	TreeMap<Integer,Integer> mRefSizes;
	Vector<Integer> mRefOrder;
	TreeMap<Integer,Integer> grpColorOrder;
	Vector<Integer> mGrps2;	
	
	int mPairIdx;
	Font chromeFont,legendFont, chromFontVert;
	
	Vector<Project> allProj;
	Statement s;
	public CircPanel cp;
	
	int width, height;

    JButton plusButton, minusButton;
    HelpBar hb;
    boolean extHelpBar = false;
    ControlPanelCirc controls;
    
    // This one is called from the project manager
	public CircFrame(DatabaseReader dbReader, int projXIdx, int projYIdx) 
	{
		super("SyMAP Circle View");
		int[] pidxList = {projXIdx, projYIdx};
		doConstruct(null,dbReader,pidxList,null,null);
	}
	// Called by the chromosome explorer (both standalone and applet)
	public CircFrame(Applet a, DatabaseReader dbReader, int[] pidxList, TreeSet<Integer> shownGroups,HelpBar hb) 
	{
		super("SyMAP Circle View");
		doConstruct(a,dbReader,pidxList,shownGroups, hb);
	}

	// This is the generic constructor, which is actually only called from the other constructors
	public void doConstruct(Applet applet, DatabaseReader dbReader, int[] projIdxList, TreeSet<Integer> shownGroups, HelpBar _hb)
	{
		allProj = new Vector<Project>();
		mDB = dbReader;
		if (projIdxList.length == 0) return;
		
		try
		{
			s = mDB.getConnection().createStatement();
			for (int i = 0; i < projIdxList.length; i++)
			{
				if (projIdxList[i] > 0)
				{
					addProject(projIdxList[i]);
				}
			}

			hb = _hb;
			if (hb == null) 
			{
				hb = new HelpBar(-1, 17, true, false, false);
			}
			else
			{
				extHelpBar = true;
			}
						
			cp = new CircPanel(dbReader,projIdxList,shownGroups, hb);
			controls = new ControlPanelCirc(cp,hb,applet);
			controls.selfCheckbox.setSelected(true); //allProj.size() == 1);

			init();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}	
	public void addProject(int idx) throws Exception
	{
		ResultSet rs;
		rs = s.executeQuery("select name,type from projects where idx=" + idx);
		if (rs.first())
		{
			Project proj = new Project(idx, rs.getString("name"), rs.getString("type").equals("fpc"), s);
			allProj.add(proj);
		}
		else
		{
			System.out.println("Can't find proj_idx=" + idx);
		}
	}

	public void init() 
	{		
		
		try
		{
			setPreferredSize(new Dimension(1000,900));
			setMinimumSize(new Dimension(1000,900));

			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			setLayout( new BorderLayout() );
			add( controls, BorderLayout.NORTH );
			add( cp.getScrollPane(),BorderLayout.CENTER );
			if (!extHelpBar)
			{
				add( hb, BorderLayout.SOUTH );
			}			
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return;
		}		

	}


	class Project
	{
		int idx;
		String name;
		boolean fpc;
		
		public Project(int _idx, String _name, boolean _fpc, Statement s) throws Exception
		{
			idx = _idx;
			name = _name;
			fpc = _fpc;

		}


	}
}