package util;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import org.freehep.graphicsbase.util.export.ExportDialog;

import org.freehep.graphicsio.pdf.PDFExportFileType;
import org.freehep.graphicsio.ps.PSExportFileType;
import org.freehep.graphicsio.ps.EPSExportFileType;
import org.freehep.graphicsio.svg.SVGExportFileType;

public class ImageViewer {

    public ImageViewer() { }

    /**
     * Method <code>showImage</code> prompts the user to save the file to disk
     * and then opens the image in a web browser window. The url of the new image is returned.
     *
     * @param comp a <code>Component</code> value of the component to be made into an image
     * @return an <code>URL</code> value of the URL of the new image or null if there was any problems or the image was not saved.
     * CAS42 1/5/18 This quite working with Java V9
     * CAS500 changed to catch error in case it does work with some non 1.n versions
     */
    public static void showImage(Component comp) {
		try
		{	
	        ExportDialog export = new ExportDialog();
            export.addExportFileType(new PDFExportFileType());
            export.addExportFileType(new EPSExportFileType());
            export.addExportFileType(new PSExportFileType());
            export.addExportFileType(new SVGExportFileType());      

	        export.showExportDialog( comp, "Export view as ...", comp, "export" );
		    System.err.println("Image save complete");
		}
		catch(Exception e){
			String version = System.getProperty("java.version");
			String[] subs = version.split("\\.");
			
			if (!subs[0].equals("1")) {
				String m = "The installed Java version is " + version + 
						"\nThis feature only works with Java v1.n";
				System.err.println(m);
				System.err.println("Try using the Linux/Mac screen capture");
				JOptionPane.showMessageDialog(null, m, 
					"Version incompatiablity", JOptionPane.PLAIN_MESSAGE);
			}
			ErrorReport.print(e,"Image viewer failed - it does not work with all Java versions");
		}
    }
 
    /**
     * Method <code>getImage</code> returns the image object
     *
     * @param url an <code>URL</code> value of the url of the image
     * @return an <code>Image</code> value
     */
    public Image getImage(URL url) {
    		return Toolkit.getDefaultToolkit().getImage(url);
    }

    /**
     * Method <code>getIcon</code> returns an icon.
     *
     * This method is equivalent to <code>new ImageIcon(getImage(url))</code>
     * 
     * @param url an <code>URL</code> value
     * @return an <code>Icon</code> value
     */
    public Icon getIcon(URL url) {
		Image image = getImage(url);
		if (image != null) return new ImageIcon(image);
		return null;
    }
    
	public static ImageIcon getImageIcon(String strImagePath) {
	    java.net.URL imgURL = ImageViewer.class.getResource(strImagePath);
	    if (imgURL != null)
	    		return new ImageIcon(imgURL);
	    else {
	    		//System.err.println("Couldn't find icon: "+strImagePath);
	    		return null;
	    }
	}

    /**
     * Method <code>createImage</code> creates an image from the component <code>comp</code>.
     *
     * @param comp a <code>Component</code> value
     * @return a <code>BufferedImage</code> value
     */
    public static BufferedImage createImage(Component comp) {
		Dimension size = comp.getSize();
		BufferedImage bimg = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);

		Graphics2D g2 = bimg.createGraphics();
	        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setBackground(comp.getBackground());
		g2.setPaint(comp.getBackground());
		g2.fill(new Rectangle(0,0,size.width,size.height));
		comp.paint(g2);
		return bimg;
    }
}
