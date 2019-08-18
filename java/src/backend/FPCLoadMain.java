package backend;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Vector;

import symap.pool.DatabaseUser;
import util.ErrorCount;
import util.Logger;
import util.Utilities;
import backend.Project;

/*
 *  Parse FPC file and load data into database
 */

public class FPCLoadMain 
{
	public static boolean run(UpdatePool pool, Logger log, String projName) throws Exception {
		long startTime = System.currentTimeMillis();
		
		log.msg("Loading FPC project " + projName);
		
		// Load properties for FPC project
		String projDir = "data/fpc/" + projName;
		SyProps props = new SyProps(log, new File(projDir + "/params"));
		if (props.getProperty("display_name") == null)
			props.setProperty("display_name", projName);
		if (props.getProperty("name") == null)
			props.setProperty("name", projName);
		if (props.getProperty("category") == null)
			props.setProperty("category", "Uncategorized");
//		if (props.getProperty("group") != null && !props.getProperty("group").equals(""))
//			props.setProperty("category", props.getProperty("group"));		
		if (props.getProperty("description") == null)
			props.setProperty("description", "");
		
		// Create FPC project in database
		int projIdx = pool.getProjIdx(projName, ProjType.fpc);
		if (projIdx > 0) // delete existing project
			pool.deleteProject(projIdx);
		
		if (pool.projectExists(projName)) {
			System.out.println("A PSEUDO project with the same exists, please remove it first\n" +
					"or select a different name for this project.");
			return false;
		}
		
		pool.createProject(projName,ProjType.fpc);
		projIdx = pool.getProjIdx(projName, ProjType.fpc);
		props.uploadProjProps(pool, projIdx, 
				new String[] { "name", "display_name", "category", "description", 
				"grp_prefix", "grp_sort", "grp_type", "cbsize", 
				"max_mrk_ctgs_hit", "min_mrk_clones_hit" } );

		// Locate the FPC file
		File fpcFile = null;
		if (!props.containsKey("fpc_file") || props.getProperty("fpc_file").trim().equals(""))
		{
			File sdf = new File(projDir);
			File[] seqFiles = null;
			if (sdf.exists() && sdf.isDirectory())
				seqFiles = sdf.listFiles(new ExtensionFilter(".fpc"));
			else {
				log.msg("Can't find fpc directory " + projDir);
				ErrorCount.inc();
				return false;
			}
			if (seqFiles.length == 0) {
				log.msg("Can't find fpc file in  directory " + projDir);
				ErrorCount.inc();
				return false;
			}
			else if (seqFiles.length > 1) {
				log.msg("More than one fpc file in  directory " + projDir);
				ErrorCount.inc();
				return false;
			}
			fpcFile = seqFiles[0];
		}
		else
		{
			if (props.containsKey("fpc_file"))
			{
				fpcFile = new File(props.getProperty("fpc_file"));
			}

		}
		if (fpcFile == null || !fpcFile.isFile())
		{
			log.msg("Can't find fpc file ");
			return false;
		}
		String prefix = props.getProperty("grp_prefix");
		
		Vector<File> besFiles = new Vector<File>();
		Vector<File> mrkFiles = new Vector<File>();
		addFiles(props,"bes_files",besFiles, log);
		addFiles(props,"marker_files",mrkFiles, log);
		
		
		// Set up the sort order of the groups based on param file setting
		GroupSorter gs = null;
		if (!props.getProperty("grp_order").equals("")) {
			String[] grpOrder = props.getProperty("grp_order").split(",");
			gs = new GroupSorter(grpOrder);
		}
		else if (props.getProperty("grp_sort").equals("numeric"))
			gs = new GroupSorter(GrpSortType.Numeric);
		else
			gs = new GroupSorter(GrpSortType.Alpha);
		
		String seqdir = projDir + "/sequence";
		int maxctgs = Integer.parseInt(props.getProperty("max_mrk_ctgs_hit"));
		FPCData mFPC;
		try
		{
			mFPC = new FPCData(pool, log, fpcFile, projIdx, prefix, gs, seqdir,maxctgs,besFiles, mrkFiles);
		}
		catch (Exception e)
		{
			log.msg("Failed to parse the FPC file");
			ErrorCount.inc();
			return false;
		}
		try
		{
			mFPC.doUpload();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			log.msg("Failed to load the FPC file");
			ErrorCount.inc();
			return false;			
		}
		log.msg("Done:  " + Utilities.getDurationString(System.currentTimeMillis()-startTime) + "\n");
		
		return true;
	}
	private static void addFiles(Properties props, String key, Vector<File> files, Logger log)
	{
		if (!props.containsKey(key)) return;
		
		String[] fileList = props.getProperty(key).split(",");
		int i = 0;
		for (String filstr : fileList)
		{
			if (filstr == null) continue;
			if (filstr.trim().equals("")) continue;
			File f = new File(filstr);
			if (!f.exists())
			{
				log.msg("Can't find sequence file " + filstr);
			}
			else if (f.isDirectory())
			{
				for (File f2 : f.listFiles())
				{
					files.add(f2);
					i++;
				}
			}
			else
			{
				files.add(f);
				i++;
			}
		}
	}
	public static void main(String[] args) 
	{
		try {
			FileInputStream propFH = new FileInputStream(Utils.getParamsName());
			Properties mDBProps = new Properties();
			mDBProps.load(propFH);

			String dbstr = DatabaseUser.getDatabaseURL(mDBProps.getProperty("db_server"), mDBProps.getProperty("db_name"));
			UpdatePool pool = new UpdatePool(dbstr,
				mDBProps.getProperty("db_adminuser"), 
				mDBProps.getProperty("db_adminpasswd"));
		
			run( pool, new Log("symap.log"), args[0] );
			DatabaseUser.shutdown();
		}
		catch (Exception e) {
			e.printStackTrace();
			DatabaseUser.shutdown();
			System.exit(-1);
		}
	}
}
