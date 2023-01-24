package util;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/*************************************************
 * Write image - used from the Printer Icon
 * CAS533 it was using org.freehep for images, which did not work past Java v8.
 * Even though it does not use org.freehep explicitly, it still needs 
 * 		org.freehep.graphicsio.raw.RawImageWriterSpi 
 */

public class ImageViewer extends JDialog {
	private static final long serialVersionUID = 1L;
	private static String [] typeExt = {"jpeg", "png", "gif", "bmp"};
	private static String typeStr = ".png .gif .jpeg .bmp";
	
	public static void showImage(String name, JPanel comp) {
		String f = getFile(name + ".png");
		if (f==null) return;
		
		String type=null;
		for (String t : typeExt) {
			if (f.endsWith(t)) {
				type=t;
				break;
			}
		}
		if (type==null) {
			Utilities.showWarningMessage("Illegal File: " + f + "\nMust end in: " + typeStr);
			return;
		}
		saveImage(comp, type, f);
	}
	
    private static String getFile(String fname) {
		try {	
			String saveDir = System.getProperty("user.dir") + "/exports/";
			File temp = new File(saveDir);
			if(!temp.exists()) {
				System.out.println("Create " + saveDir);
				temp.mkdir();
			}
			
			JFileChooser fc = new JFileChooser(saveDir);
			FileNameExtensionFilter filter = new FileNameExtensionFilter(typeStr, typeExt);
			fc.setFileFilter(filter);
			fc.setSelectedFile(new File(fname));
			
			int rc = fc.showSaveDialog(null);
			if (rc!= JFileChooser.APPROVE_OPTION) return null;
			
			File f = fc.getSelectedFile();
			if (f.exists()) {
				if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(null,"The file exists, do you want to overwrite it?", 
						"File exists",JOptionPane.YES_NO_OPTION)) return null;
				f.delete();
			}
			return f.getAbsolutePath();
		}
		catch(Exception e){ErrorReport.print(e,"Failed getting file"); return null;}
    }
    
	static private void saveImage(JPanel dPanel, String type, String path) {
		BufferedImage bImg = new BufferedImage(dPanel.getWidth(), dPanel.getHeight(), BufferedImage.TYPE_INT_RGB);
	    Graphics2D cg = bImg.createGraphics();
	    dPanel.paintAll(cg);
	    try {
            if (ImageIO.write(bImg, type, new File(path)))
                 System.out.println("Saved " + type + " image to " + path);
            else System.out.println("Failure: Could not write to " + path);
	    } catch (IOException e) { ErrorReport.print(e, "Write image to " + path);} 
    }
	 
  // Called to create the image (works for print, Home, etc)
	public static ImageIcon getImageIcon(String strImagePath) {
	    java.net.URL imgURL = ImageViewer.class.getResource(strImagePath);
	    if (imgURL != null) return new ImageIcon(imgURL);
	    else return null;
	}
}
