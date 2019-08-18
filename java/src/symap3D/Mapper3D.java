package symap3D;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;

import java.util.Enumeration;
import java.util.Vector;

import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.Bounds;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.ColoringAttributes;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.ImageComponent2D;
import javax.media.j3d.Material;
import javax.media.j3d.PickInfo;
import javax.media.j3d.QuadArray;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.TransparencyAttributes;
import javax.media.j3d.WakeupCriterion;
import javax.media.j3d.WakeupOnAWTEvent;
import javax.media.j3d.WakeupOnBehaviorPost;
import javax.vecmath.Color3f;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector3d;
import javax.vecmath.Matrix4d;

import symap.projectmanager.common.Block;
import symap.projectmanager.common.Mapper;
import symap.projectmanager.common.TrackCom;

import com.sun.j3d.utils.behaviors.mouse.MouseBehavior;
import com.sun.j3d.utils.behaviors.mouse.MouseRotate;
import com.sun.j3d.utils.behaviors.mouse.MouseTranslate;
import com.sun.j3d.utils.behaviors.mouse.MouseZoom;
import com.sun.j3d.utils.behaviors.mouse.MouseWheelZoom;
import com.sun.j3d.utils.geometry.Cylinder;
import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.pickfast.behaviors.PickMouseBehavior;
import com.sun.j3d.utils.universe.SimpleUniverse;

// This class is the 3D-equivalent of the 2D symap.mapper and symap.drawingpanel
// packages combined.
public class Mapper3D extends Mapper {	
	
	private BranchGroup objRoot = null;
	private BranchGroup objShapesBG = null;
	private TransformGroup objRotate;
	private TransformGroup vpTrans;
	private LeftClickMouseRotate mouseRotate;
	private LeftClickMouseTranslate mouseTranslate;
	private LeftClickMouseZoom mouseZoom;
	private SelectBehavior pickObj;
	
	private Transform3D savedCoords = null;
	private Transform3D savedCoords2 = null;
		
	public static final int PICK_DO_NOTHING = 0;
	public static final int PICK_DELETE     = 1;
	private int nPickFunction = PICK_DO_NOTHING;
	
	public static final int NAVIGATION_NONE      = 0;
	public static final int NAVIGATION_ROTATE    = 1;
	public static final int NAVIGATION_TRANSLATE = 2;
	public static final int NAVIGATION_ZOOM      = 3;
	private int nNavigationFunction = NAVIGATION_ROTATE;
	
	private boolean bChanged = false;
	
	public Mapper3D() { 
		super();
	}
	
// mdb removed 2/1/10	
//	public void setFrame(SyMAPFrame3D frame) {
//		this.frame = frame;
//	}
	
/*	public void setTracks(Track[] tracks) {
		this.tracks = tracks;
		reference = tracks[0];
		
		for (Track t : tracks)
			maxBpPerUnit = Math.max(maxBpPerUnit, t.getSizeBP());
	}
	
	public Track[] getTracks(int nProjID) {
		Vector<Track> out = new Vector<Track>();
		
		for (Track t : (Track[])tracks)
			if (t.getProjIdx() == nProjID)
				out.add(t);
		
		return out.toArray(new Track[0]);
	}
*/	
	/*
	public Track getReferenceTrack() { return (Track)reference; }
	public boolean isReference(Track t) { return t == reference; }
	public int getNumTracks() { return tracks.length; }
	
	public int getNumVisibleTracks() { // excluding the reference track
		int n = 0;
		for (Track t : (Track[])tracks)
			if (t.isVisible() && t != reference) n++;
		return n;
	}
	
	public int getNumVisibleTracks(int nProjID) {
		int n = 0;
		for (Track t : (Track[])tracks)
			if (t.isVisible() && t.getProjIdx() == nProjID) n++;
		return n;
	}
	
	public Track[] getVisibleTracks() { // excluding the reference track
		Vector<Track> visible = new Vector<Track>();
		for (Track t : (Track[])tracks)
			if (t.isVisible() && t != reference) visible.add(t);
		return visible.toArray(new Track[0]);
	}
	
	public Track[] getVisibleTracks(int nProjID) {
		Vector<Track> visible = new Vector<Track>();
		for (Track t : (Track[])tracks)
			if (t.isVisible() && t.getProjIdx() == nProjID) visible.add(t);
		return visible.toArray(new Track[0]);
	}
	
	public void hideVisibleTracks() { // excluding the reference
		for (Track t : (Track[])tracks)
			if (t != reference) t.setVisible(false);
		bChanged = true;
	}
	
	public void setTrackVisible(Track t, boolean visible) {
		t.setVisible(visible);
		bChanged = true;
	}
*/	
	public void yRotateScene(int nDegrees) { // rotate scene around y-axis
		Transform3D t = new Transform3D();
		objRotate.getTransform(t);
		t.rotY(2 * Math.PI * nDegrees / 360);
		objRotate.setTransform(t);
	}
	
	public void savePosition() {
		if (objRotate != null && vpTrans != null) { // make sure scenegraph was initialized
			savedCoords = new Transform3D();
			savedCoords2 = new Transform3D();
			objRotate.getTransform(savedCoords);
			vpTrans.getTransform(savedCoords2);
		}
	}
	
	public void restorePosition() {
		if (savedCoords != null && savedCoords2 != null) { // check for prior savePosition() call
			objRotate.setTransform(savedCoords);
			vpTrans.setTransform(savedCoords2);
		}
	}
	
	public void setNavigationFunction(int nNavigationFunction) {
		this.nNavigationFunction = nNavigationFunction;
		
		mouseRotate.setEnable(false);
		mouseTranslate.setEnable(false);
		mouseZoom.setEnable(false);
		
		switch (nNavigationFunction) {
			case NAVIGATION_ROTATE    : mouseRotate.setEnable(true);     break;
			case NAVIGATION_TRANSLATE : mouseTranslate.setEnable(true);  break;
			case NAVIGATION_ZOOM      : mouseZoom.setEnable(true);       break;
		}
	}
	
	public void setPickFunction(int nPickFunction) {
		this.nPickFunction = nPickFunction;
		if (pickObj != null) pickObj.setEnable(nPickFunction != PICK_DO_NOTHING);
	}
	
	public int getPickFunction() { return nPickFunction; }
	
	public BranchGroup createSceneGraph(SimpleUniverse su) {
		bChanged = false;
		
		if (objRoot == null) {
			vpTrans = su.getViewingPlatform().getViewPlatformTransform();
			
			objRoot = new BranchGroup();
			objRoot.setCapability(BranchGroup.ALLOW_CHILDREN_READ);
			objRoot.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
			objRoot.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
			objRoot.setCapability(BranchGroup.ALLOW_DETACH);
			
			objShapesBG = new BranchGroup();
			objShapesBG.setCapability(BranchGroup.ALLOW_CHILDREN_READ);
			objShapesBG.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
			objShapesBG.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
			objShapesBG.setCapability(BranchGroup.ALLOW_DETACH);
			
			Background background = new Background(SyMAP3D.white);
			background.setApplicationBounds(new BoundingSphere());
			objRoot.addChild(background);
			
			Vector3f light1Direction = new Vector3f(-6.0f, 0.0f, -12.0f);
			DirectionalLight light1 = new DirectionalLight(SyMAP3D.white, light1Direction);
			light1.setInfluencingBounds(new BoundingSphere());
			objRoot.addChild(light1);
			
		    mouseRotate = new LeftClickMouseRotate();
		    mouseRotate.setSchedulingBounds(new BoundingSphere());
		    objRoot.addChild(mouseRotate);
		    
		    mouseTranslate = new LeftClickMouseTranslate();
	        mouseTranslate.setTransformGroup(vpTrans);
	        mouseTranslate.setSchedulingBounds(new BoundingSphere());
	        mouseTranslate.setFactor(0.003);
	        mouseTranslate.setEnable(false);
	        objRoot.addChild(mouseTranslate);
	        
	        MouseWheelZoom mouseWheelZoom = new MouseWheelZoom(MouseBehavior.INVERT_INPUT);
	        mouseWheelZoom.setTransformGroup(vpTrans);
	        mouseWheelZoom.setSchedulingBounds(new BoundingSphere());
	        mouseWheelZoom.setFactor(0.05);
	        objRoot.addChild(mouseWheelZoom);
	        
	        mouseZoom = new LeftClickMouseZoom();
	        mouseZoom.setTransformGroup(vpTrans);
	        mouseZoom.setSchedulingBounds(new BoundingSphere());
	        mouseZoom.setFactor(0.01);
	        mouseZoom.setEnable(false);
	        objRoot.addChild(mouseZoom);
		}
		else {
			objShapesBG.detach();
			objShapesBG.removeChild(objRotate);
		}
		
		objRotate = new TransformGroup();
		objRotate.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		objRotate.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		
		reference.setPosition( 0, 0, 0);
		reference.setVisible(true);
		
		int numVisibleTracks = getNumVisibleTracks();
		
		double delta = Math.PI*2/numVisibleTracks;	// angle between
		double start = Math.PI/2;					// start angle
		for (TrackCom t : tracks) {
			if (!t.isVisible()) continue;
			
			if (!isReference(t)) {
				double theta = delta*numVisibleTracks + start;
				
				t.setPosition( 
						(float)Math.sin(theta)*DISPLAY_RADIUS, 
						0, 
						(float)Math.cos(theta)*DISPLAY_RADIUS);
				
				for (Block b : blocks) {
					if (b.isTarget(t.getProjIdx()))
						b = b.swap(); 
					
					if (b.getGroup2Idx() == reference.getGroupIdx()
							&& b.getGroup1Idx() == t.getGroupIdx()) 
					{
						Point3f p0 = new Point3f(reference.getPositionArg0(), reference.getPositionArg1(), reference.getPositionArg2());
						Point3f p3 = new Point3f(reference.getPositionArg0(), reference.getPositionArg1(), reference.getPositionArg2());
						Point3f p1 = new Point3f(t.getPositionArg0(), t.getPositionArg1(), t.getPositionArg2());
						Point3f p2 = new Point3f(t.getPositionArg0(), t.getPositionArg1(), t.getPositionArg2());
						
						// Enlarge block if necessary to make sure it's visible
						long s1 = b.getStart1();
						long s2 = b.getStart2();
						long e1 = b.getEnd1();
						long e2 = b.getEnd2();
						long bpMin = (long)(maxBpPerUnit/500);
						if (Math.abs(e1 - s1) < bpMin)
							e1 = s1 + bpMin;
						if (Math.abs(e2 - s2) < bpMin)
							e2 = s2 + bpMin;
						
						long midp1 = reference.getSizeBP()/2;
						long midp2 = t.getSizeBP()/2;
						p0.y = (midp1 - s2)/maxBpPerUnit;
						p1.y = (midp2 - s1)/maxBpPerUnit;
						p2.y = (midp2 - e1)/maxBpPerUnit;
						p3.y = (midp1 - e2)/maxBpPerUnit;
						
						float offset_x = (float)Math.sin(theta)*CYLINDER_WIDTH*JAGGY_FUDGE;
						float offset_z = (float)Math.cos(theta)*CYLINDER_WIDTH*JAGGY_FUDGE;
						p0.x += offset_x;
						p0.z += offset_z;
						p3.x += offset_x;
						p3.z += offset_z;
						
						offset_x = (float)Math.sin(Math.PI+theta)*CYLINDER_WIDTH*JAGGY_FUDGE;
						offset_z = (float)Math.cos(Math.PI+theta)*CYLINDER_WIDTH*JAGGY_FUDGE;
						p1.x += offset_x;
						p1.z += offset_z;
						p2.x += offset_x;
						p2.z += offset_z;
									
						createPlane3D(objRotate, p0, p1, p2, p3, b.inverted());
					}
				}
				
				numVisibleTracks--;
			}
			//System.out.println(t.getFullName());
			t.setShape3D( 
				createCylinder3D(
					t.getFullName(), 
					objRotate, 
					new Vector3f(new Point3f(t.getPositionArg0(), t.getPositionArg1(), t.getPositionArg2())), 
					Math.max(MIN_CYLINDER_HEIGHT, t.getSizeBP()/maxBpPerUnit), 
					new Color3f(t.getColor()), 
					(t == reference ? Color.red : Color.black),
					t, 
					false));
		}
		//objRotate.addChild(createLabel3D("foobar", 0f, 0f,0f,Color.red , false));
	    mouseRotate.setTransformGroup(objRotate);
		objShapesBG.addChild(objRotate);
		objRoot.addChild(objShapesBG);
		
// mdb removed 6/24/09 - pickClosest() not supported on Java3D 1.3.1 required for Mac.
//      pickObj = new SelectBehavior(su.getCanvas(), objRoot, new BoundingSphere());
//      pickObj.setEnable(nPickFunction != PICK_DO_NOTHING);
//		objRotate.addChild(pickObj); 

		setNavigationFunction(getNavigationFunction()); // for subsequent calls
		
		return objRoot;
	}
	
	static public Primitive createCylinder3D(String strName, Group g, Vector3f v, float height, Color3f c, Color labelColor, Object userData, boolean bTransparent) {
		// Label
		Shape3D text = createLabel3D(strName, v.x, v.y+height/2+0.06f, v.z, labelColor, false);
		g.addChild(text);
	
		// Cylinder 
		TransformGroup tg = new TransformGroup();
		Transform3D transform = new Transform3D();
		
	    Appearance app = new Appearance();	    
	    Material mat = new Material( c, SyMAP3D.black, c, SyMAP3D.black, 1.f ); // ambient, emissive, diffuse, specular, shininess
	    app.setMaterial(mat);
	    app.setCapability(Appearance.ALLOW_TRANSPARENCY_ATTRIBUTES_WRITE);
		if (bTransparent)
			app.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.50f));
	    
		Cylinder cylinder = new Cylinder(CYLINDER_WIDTH, height, app);
		cylinder.getShape(Cylinder.BODY).setUserData(userData); 	// used to identify this shape in picking
		cylinder.getShape(Cylinder.TOP).setUserData(userData); 		// used to identify this shape in picking
		cylinder.getShape(Cylinder.BOTTOM).setUserData(userData); 	// used to identify this shape in picking
		cylinder.setPickable(true);
	    
		transform.setTranslation(v);
		tg.setTransform(transform);
		tg.addChild(cylinder);
		g.addChild(tg);
		
		return cylinder;
	}
	
	static private Shape3D createLabel3D( String strText, float x, float y, float z, Color c, boolean bTransparent )
	{
		BufferedImage img = new BufferedImage( strText.length() * 7 + 6, 14, BufferedImage.TYPE_INT_ARGB ); // FIXME: font dimensions are hardcoded
		Graphics g = img.createGraphics();
		
		g.setColor( Color.WHITE );
		g.fillRect( 0, 0, img.getWidth(), img.getHeight());
		if (c == null) g.setColor( Color.BLACK );
		else g.setColor( c );
		g.drawString( strText, 2, 12 );

		ImageComponent2D imageComponent2D = new ImageComponent2D( ImageComponent2D.FORMAT_RGBA, img );

		javax.media.j3d.Raster renderRaster = new javax.media.j3d.Raster ( 
			new Point3f( x, y, z ),
			javax.media.j3d.Raster.RASTER_COLOR,
			0, 0,
			img.getWidth( ),
			img.getHeight( ),
			imageComponent2D,
			null );

		return new Shape3D( renderRaster );
	}
	
	static private void createPlane3D( Group g, Point3f p0, Point3f p1, Point3f p2, Point3f p3, boolean inverted ) {		
		QuadArray plane = new QuadArray(8, GeometryArray.COORDINATES);
		
		// Side 1
	    plane.setCoordinate(0, p0);
	    plane.setCoordinate(1, p1);
	    plane.setCoordinate(2, p2);
	    plane.setCoordinate(3, p3);
	    
	    // Side 2
	    plane.setCoordinate(4, p3);
	    plane.setCoordinate(5, p2);
	    plane.setCoordinate(6, p1);
	    plane.setCoordinate(7, p0);

	    Appearance app = new Appearance();
	    Color3f pc = (inverted ? SyMAP3D.green : SyMAP3D.red);
	    ColoringAttributes ca = new ColoringAttributes(pc, ColoringAttributes.SHADE_FLAT);
	    app.setColoringAttributes(ca);
	    app.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.60f));
	    
		g.addChild(new Shape3D(plane, app));
	}
	
	// Sub-class
	private class SelectBehavior extends PickMouseBehavior {
		public SelectBehavior(Canvas3D canvas, BranchGroup root, Bounds bounds) {
			super(canvas, root, bounds);
			this.setSchedulingBounds(new BoundingSphere());
		}
		
	    public void initialize() {
	    	super.initialize();
	    }
		
	    public void processStimulus(Enumeration criteria){
	    	//System.out.println("processStimulus " + this.getParent().toString());
	    	super.processStimulus(criteria);
	    }
	    
	    public void updateScene(int xpos, int ypos) {
	    	//System.out.println("updateScene x="+xpos+" y="+ypos);
	    	PickInfo pickResult = null; 
	    	Shape3D pickShape = null;
	     
	    	pickCanvas.setShapeLocation(xpos, ypos);
	    	pickCanvas.setMode(PickInfo.PICK_GEOMETRY); // more precise than default mode
	    	pickResult = pickCanvas.pickClosest();
	    	
	    	if (pickResult != null)
	    		pickShape = (Shape3D) pickResult.getNode();
	    	
	    	if (pickShape != null) {
	    		final TrackCom t = (TrackCom)pickShape.getUserData();
	    		if (!isReference(t) && t != null) {
	    			if (mevent.getClickCount() == 1) {
	    				if (nPickFunction == PICK_DELETE) {
			        		t.setVisible(false);
			        		bChanged = true;
	    				}
	
// mdb removed 2/1/10	    				
//		                EventQueue.invokeLater(new Runnable() {
//		        			public void run() {
//		        				frame.repaint();
//		        			}
//		        		});
		                notifyObservers(); // mdb added 2/1/10
	    			}
	    		}
	    	}
	    }
	}
	
	// Sub-class ///////////////////////////////////////////////////////////////
	// This code copied from 3d library MouseTranslate.java.  Needed to
	// change mouse translate button from right-click to left-click.  Also
	// needed to fix the problem of first mouse-press not being recognized
	// after setEnable(true).
	private class LeftClickMouseTranslate extends MouseTranslate {
		Vector3d translation = new Vector3d();
		
		public LeftClickMouseTranslate() {
			super(MouseBehavior.INVERT_INPUT);
		}
		
	    public void processStimulus(Enumeration criteria) {
	    	WakeupCriterion wakeup;
	    	AWTEvent[] events;
	     	MouseEvent evt;
	        
	    	while (criteria.hasMoreElements()) {
	    	    wakeup = (WakeupCriterion) criteria.nextElement();
	          
	    	    if (wakeup instanceof WakeupOnAWTEvent) {
	    		events = ((WakeupOnAWTEvent)wakeup).getAWTEvent();
		    		if (events.length > 0) {
		    		    evt = (MouseEvent) events[events.length-1];
		    		    doProcess(evt);
		    		}
	    	    }
	    	    else if (wakeup instanceof WakeupOnBehaviorPost) {
		    		while (true) {
		    		    // access to the queue must be synchronized
		    		    synchronized (mouseq) {
			    			if (mouseq.isEmpty()) break;
			    			evt = (MouseEvent)mouseq.remove(0);
			    			// consolodate MOUSE_DRAG events
			    			while ((evt.getID() == MouseEvent.MOUSE_DRAGGED) &&
			    			       !mouseq.isEmpty() &&
			    			       (((MouseEvent)mouseq.get(0)).getID() ==
			    				MouseEvent.MOUSE_DRAGGED)) {
			    			    evt = (MouseEvent)mouseq.remove(0);
			    			}
		    		    }
		    		    doProcess(evt);
		    		}
	    	    }

	    	}
	     	wakeupOn(mouseCriterion);
	    }

	    void doProcess(MouseEvent evt) {
	    	int id;
	    	int dx, dy;
	    	
	    	processMouseEvent(evt);
	    	buttonPress = true; // mdb added 1/27/09
	    	if (((buttonPress)&&((flags & MANUAL_WAKEUP) == 0)) ||
	    	    ((wakeUp)&&((flags & MANUAL_WAKEUP) != 0))){
	    	    id = evt.getID();
	    	    if ((id == MouseEvent.MOUSE_DRAGGED) &&
	    		!evt.isAltDown() && !evt.isMetaDown()) { // <-- mdb: this is where the mouse button is checked
	    		
		    		x = evt.getX();
		    		y = evt.getY();
		    		
		    		dx = x - x_last;
		    		dy = y - y_last;
		    		
		    		if ((!reset) && ((Math.abs(dy) < 50) && (Math.abs(dx) < 50))) {
		    		    transformGroup.getTransform(currXform);
		    		    
		    		    translation.x = dx*getXFactor(); 
		    		    translation.y = -dy*getYFactor();
		    		    
		    		    transformX.set(translation);
		    		    
		    		    if (invert) {
		    		    	currXform.mul(currXform, transformX);
		    		    } else {
		    		    	currXform.mul(transformX, currXform);
		    		    }
		    		    
		    		    transformGroup.setTransform(currXform);
		    		    transformChanged( currXform );
//		    		    if (callback!=null)
//		    		    	callback.transformChanged( MouseBehaviorCallback.TRANSLATE,
//		    						   currXform );
		    		}
		    		else {
		    		    reset = false;
		    		}
		    		x_last = x;
		    		y_last = y;
	    	    }
	    	    else if (id == MouseEvent.MOUSE_PRESSED) {
		    		x_last = evt.getX();
		    		y_last = evt.getY();
	    	    }
	    	}
	    }
	} // LeftClickMouseTranslate
	
	// Sub-class ///////////////////////////////////////////////////////////////
	// This code copied from 3d library MouseRotate.java
	// Needed to fix the problem of first mouse-press not being recognized
	// after setEnable(true).
	private class LeftClickMouseRotate extends MouseRotate {
	    public void processStimulus (Enumeration criteria) {
	    	WakeupCriterion wakeup;
	    	AWTEvent[] events;
	     	MouseEvent evt;
	    	
	    	while (criteria.hasMoreElements()) {
	    	    wakeup = (WakeupCriterion) criteria.nextElement();
	    	    if (wakeup instanceof WakeupOnAWTEvent) {
		    		events = ((WakeupOnAWTEvent)wakeup).getAWTEvent();
		    		if (events.length > 0) {
		    		    evt = (MouseEvent) events[events.length-1];
		    		    doProcess(evt);
		    		}
	    	    }
	    	    else if (wakeup instanceof WakeupOnBehaviorPost) {
		    		while (true) {
		    		    // access to the queue must be synchronized
		    		    synchronized (mouseq) {
			    			if (mouseq.isEmpty()) break;
			    			evt = (MouseEvent)mouseq.remove(0);
			    			// consolidate MOUSE_DRAG events
			    			while ((evt.getID() == MouseEvent.MOUSE_DRAGGED) &&
			    			       !mouseq.isEmpty() &&
			    			       (((MouseEvent)mouseq.get(0)).getID() ==
			    				MouseEvent.MOUSE_DRAGGED)) {
			    			    evt = (MouseEvent)mouseq.remove(0);
			    			}
		    		    }
		    		    doProcess(evt);
		    		}
	    	    }
	    	}
	    	wakeupOn (mouseCriterion);
	    }

	    void doProcess(MouseEvent evt) {
	    	int id;
	    	int dx, dy;

	    	processMouseEvent(evt);
	    	buttonPress = true; // mdb added 1/27/09
	    	if (((buttonPress)&&((flags & MANUAL_WAKEUP) == 0)) ||
	    	    ((wakeUp)&&((flags & MANUAL_WAKEUP) != 0))) {
	    	    id = evt.getID();
	    	    if ((id == MouseEvent.MOUSE_DRAGGED) && 
	    		!evt.isMetaDown() && !evt.isAltDown()){
		    		x = evt.getX();
		    		y = evt.getY();
		    		
		    		dx = x - x_last;
		    		dy = y - y_last;
		    		
		    		if (!reset){
		    		    double x_angle = dy * getYFactor();
		    		    double y_angle = dx * getXFactor();
		    		    
		    		    transformX.rotX(x_angle);
		    		    transformY.rotY(y_angle);
		    		    
		    		    transformGroup.getTransform(currXform);
		    		    
		    		    Matrix4d mat = new Matrix4d();
		    		    // Remember old matrix
		    		    currXform.get(mat);
		    		    
		    		    // Translate to origin
		    		    currXform.setTranslation(new Vector3d(0.0,0.0,0.0));
		    		    if (invert) {
			    			currXform.mul(currXform, transformX);
			    			currXform.mul(currXform, transformY);
		    		    } else {
			    			currXform.mul(transformX, currXform);
			    			currXform.mul(transformY, currXform);
		    		    }
		    		    
		    		    // Set old translation back
		    		    Vector3d translation = new 
		    			Vector3d(mat.m03, mat.m13, mat.m23);
		    		    currXform.setTranslation(translation);
		    		    
		    		    // Update xform
		    		    transformGroup.setTransform(currXform);
		    		    transformChanged( currXform );
//		    		    if (callback!=null)
//		    			callback.transformChanged( MouseBehaviorCallback.ROTATE,
//		    						   currXform );
		    		}
		    		else {
		    		    reset = false;
		    		}
		    		
		    		x_last = x;
		    		y_last = y;
	    	    }
	    	    else if (id == MouseEvent.MOUSE_PRESSED) {
		    		x_last = evt.getX();
		    		y_last = evt.getY();
	    	    }
	    	}
	    }
	} // LeftClickMouseRotate
	
	
	// Sub-class ///////////////////////////////////////////////////////////////
	// This code copied from 3d library MouseZoom.java.  Needed to
	// change mouse zoom button from scroll to left-click.  Also
	// needed to fix the problem of first mouse-press not being recognized
	// after setEnable(true).
	public class LeftClickMouseZoom extends MouseZoom {

	    double z_factor = .04;
	    Vector3d translation = new Vector3d();
	  
	    public void processStimulus (Enumeration criteria) {
			WakeupCriterion wakeup;
			AWTEvent[] events;
		 	MouseEvent evt;
		    
			while (criteria.hasMoreElements()) {
			    wakeup = (WakeupCriterion) criteria.nextElement();
			    if (wakeup instanceof WakeupOnAWTEvent) {
			    	events = ((WakeupOnAWTEvent)wakeup).getAWTEvent();
					if (events.length > 0) {
					    evt = (MouseEvent) events[events.length-1];
					    doProcess(evt);
					}
			    }
	
			    else if (wakeup instanceof WakeupOnBehaviorPost) {
					while (true) {
					    synchronized (mouseq) {
							if (mouseq.isEmpty()) break;
							evt = (MouseEvent)mouseq.remove(0);
							// consolodate MOUSE_DRAG events
							while((evt.getID() == MouseEvent.MOUSE_DRAGGED) &&
							      !mouseq.isEmpty() &&
							      (((MouseEvent)mouseq.get(0)).getID() ==
							       MouseEvent.MOUSE_DRAGGED)) {
							    evt = (MouseEvent)mouseq.remove(0);
							}
					    }
					    doProcess(evt);
					}
			    }
			}
			wakeupOn (mouseCriterion);
	    }

	    void doProcess(MouseEvent evt) {
			processMouseEvent(evt);
			buttonPress = true; // mdb added 6/9/09
			if (((buttonPress)&&((flags & MANUAL_WAKEUP) == 0)) ||
			    ((wakeUp)&&((flags & MANUAL_WAKEUP) != 0)))
			{
			    int id = evt.getID();
			    if ((id == MouseEvent.MOUSE_DRAGGED) &&
			    		!evt.isAltDown() && !evt.isMetaDown())
			    {
					x = evt.getX();
					y = evt.getY();
					
					int dy = y - y_last;
					
					if (!reset){
					    transformGroup.getTransform(currXform);
					    
					    translation.z  = dy*z_factor;
					    
					    transformX.set(translation);
					    
					    if (invert) {
					    	currXform.mul(currXform, transformX);
					    } else {
					    	currXform.mul(transformX, currXform);
					    }
					    
					    transformGroup.setTransform(currXform);
					    
					    transformChanged( currXform );
					    
//					    if (callback!=null)
//					    	callback.transformChanged( MouseBehaviorCallback.ZOOM, currXform );
					}
					else {
					    reset = false;
					}
					
					x_last = x;
					y_last = y;
			    }
			    else if (id == MouseEvent.MOUSE_PRESSED) {
					x_last = evt.getX();
					y_last = evt.getY();
			    }
			}
	    }
	}
}
