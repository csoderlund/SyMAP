package symap.mapper;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import symap.SyMAPConstants;
import symap.marker.MarkerTrack;
import symap.marker.Marker;
import symap.contig.Clone;
import symap.contig.Contig;
import symap.block.Block;

//public class FPCFPCHits extends AbstractFPCFPCHits implements Hits, SyMAPConstants { // mdb removed 7/10/09 #134
public class FPCFPCHits extends AbstractHitData implements Hits, SyMAPConstants { // mdb added 7/10/09 #134
	private Mapper mapper;
	private MarkerTrack mt1, mt2;
	private FPHit[] fpHits;
	private SharedMarkerHit[] mrkHits;

	public FPCFPCHits(int p1, int c1, int p2, int c2) {
		super(p1,c1,p2,c2,Mapper.FPC2FPC,false); // mdb changed 7/10/09 #134
		fpHits = null;
		mrkHits = null;
	}

	public FPCFPCHits(Mapper mapper, MarkerTrack mt1, MarkerTrack mt2, FPCFPCData data, RepetitiveMarkerFilterData smfd) {
		this(data.getProject1(),data.getContig1(),data.getProject2(),data.getContig2());
		this.mapper = mapper;
		this.mt1 = mt1;
		this.mt2 = mt2;
		setHits(data,smfd);
	}

	public FPCFPCData.FPHitData[] getFPHitData() {
		FPCFPCData.FPHitData[] d = new FPCFPCData.FPHitData[fpHits == null ? 0 : fpHits.length];
		for (int i = 0; i < d.length; i++) d[i] = fpHits[i].data;
		return d;
	}

	public boolean setHits(FPCFPCData data, RepetitiveMarkerFilterData smfd) {
		if (upgradeHitContent(data.getHitContent())) {
			FPCFPCData.FPHitData[] fpHitData = data.getFPHitData();
			fpHits = new FPHit[fpHitData.length];
			for (int i = 0; i < fpHits.length; i++) {
				fpHits[i] = new FPHit(fpHitData[i]);
				fpHits[i].set();
			}
			fpHitData = null;

			Marker[] markers1 = mt1.getMarkers(getContig1());
			Marker[] markers2 = mt2.getMarkers(getContig2());
			Arrays.sort(markers2);

			List<SharedMarkerHit> mrkHitList = new ArrayList<SharedMarkerHit>();
			int ind;
			if (getHitContent() == MapInfo.ONLY_BLOCK_HITS) {
				for (int i = 0; i < markers1.length; i++) {
					ind = Arrays.binarySearch(markers2,markers1[i]);
					if (ind >= 0) {
						markers1[i].setShared(true);
						markers2[ind].setShared(true);
						if (data.isBlockHitMarker(markers1[i].getName()))
							mrkHitList.add(new SharedMarkerHit(markers1[i],markers2[ind],false,true));
					}
				}
			}
			else {
				for (int i = 0; i < markers1.length; i++) {
					ind = Arrays.binarySearch(markers2,markers1[i]);
					if (ind >= 0) {
						markers1[i].setShared(true);
						markers2[ind].setShared(true);
						mrkHitList.add(new SharedMarkerHit(markers1[i],markers2[ind],smfd.isRepetitive(markers1[i].getName()),
								data.isBlockHitMarker(markers1[i].getName())));
					}
				}
			}

			mrkHits = (SharedMarkerHit[])mrkHitList.toArray(new SharedMarkerHit[mrkHitList.size()]);
			mrkHitList = null;        

			return true;
		}
		return false;
	}

	public boolean addHits(int newHitContent, Collection fpHitData, FPCFPCData data, RepetitiveMarkerFilterData smfd) {
		if (upgradeHitContent(newHitContent)) {
			if (fpHits == null) fpHits = new FPHit[0];
			if (fpHitData == null) fpHitData = new LinkedList();

			FPHit tempFPHits[] = new FPHit[fpHits.length + fpHitData.size()];
			System.arraycopy(fpHits,0,tempFPHits,0,fpHits.length);
			int i = fpHits.length;
			fpHits = tempFPHits;
			for (Iterator iter = fpHitData.iterator(); i < fpHits.length; i++) {
				fpHits[i] = new FPHit((FPCFPCData.FPHitData)iter.next());
				fpHits[i].set();
			}

			Marker[] markers1 = mt1.getMarkers(getContig1());
			Marker[] markers2 = mt2.getMarkers(getContig2());
			Arrays.sort(markers2);
			List<SharedMarkerHit> mrkHitList = new ArrayList<SharedMarkerHit>();
			int ind;
			if (getHitContent() == MapInfo.ONLY_BLOCK_HITS) {
				for (i = 0; i < markers1.length; i++) {
					ind = Arrays.binarySearch(markers2,markers1[i]);
					if (ind >= 0) {
						markers1[i].setShared(true);
						markers2[ind].setShared(true);
						mrkHitList.add(new SharedMarkerHit(markers1[i],markers2[ind],smfd.isRepetitive(markers1[i].getName()),
								data.isBlockHitMarker(markers1[i].getName())));
					}
				}
			}
			else {
				for (i = 0; i < markers1.length; i++) {
					ind = Arrays.binarySearch(markers2,markers1[i]);
					if (ind >= 0) {
						markers1[i].setShared(true);
						markers2[ind].setShared(true);
						mrkHitList.add(new SharedMarkerHit(markers1[i],markers2[ind],smfd.isRepetitive(markers1[i].getName()),
								data.isBlockHitMarker(markers1[i].getName())));
					}
				}
			}

			mrkHits = (SharedMarkerHit[])mrkHitList.toArray(new SharedMarkerHit[mrkHitList.size()]);
			mrkHitList = null;

			return true;
		}
		return false;
	}

	public void clear() {
		if (fpHits != null)
			for (int i = 0; i < fpHits.length; i++)
				fpHits[i].clear();
		if (mrkHits != null)
			for (int i = 0; i < mrkHits.length; i++)
				mrkHits[i].clear();
	}

	public void set(MarkerTrack t1, MarkerTrack t2) {
		this.mt1 = t1;
		this.mt2 = t2;
		if (fpHits != null)
			for (int i = 0; i < fpHits.length; i++)
				fpHits[i].set();
		if (mrkHits != null)
			for (int i = 0; i < mrkHits.length; i++)
				mrkHits[i].set();
	}

	public void setMinMax(HitFilter hf) {
		if (fpHits != null)
			for (int i = 0; i < fpHits.length; i++)
				fpHits[i].setMinMax(hf);	
	}

	public void paintComponent(Graphics2D g2) { 
		int orient1 = mapper.getOrientation(mt1);
		int orient2 = mapper.getOrientation(mt2);
		Point loc1 = mt1.getLocation();
		Point loc2 = mt2.getLocation();
		boolean showJoinDot = mapper.getHitFilter().getShowJoinDot();
		if (fpHits != null)
			for (int i = 0; i < fpHits.length; i++)
				fpHits[i].paintComponent(g2,orient1,orient2,loc1,loc2);
		if (mrkHits != null)
			for (int i = 0; i < mrkHits.length; i++)
				mrkHits[i].paintComponent(g2,orient1,orient2,loc1,loc2,showJoinDot);

	}

	public void mouseMoved(MouseEvent e) { 
		if (mrkHits != null)
			for (int i = 0; i < mrkHits.length; i++)
				mrkHits[i].mouseMoved(e);
	}
	
	public void mouseExited(MouseEvent e) { } // mdb added 3/1/07 #100

	class FPHit implements Hit {
		private FPCFPCData.FPHitData data;
		private Clone clone1, clone2;

		public FPHit(FPCFPCData.FPHitData data) {
			this.data = data;
			clone1 = null;
			clone2 = null;
		}

		public void setMinMax(HitFilter hf) {
			hf.condSetFpEvalue(data.getScore(),data.getScore());
		}

		public void set() {
			if (clone1 != null) {
				clone1.removeHit(this);
				clone1 = null;
			}
			if (clone2 != null) {
				clone2.removeHit(this);
				clone2 = null;
			}
			if (mt1 instanceof Contig) {
				clone1 = ((Contig)mt1).getClone(data.getClone1());
				clone1.addHit(this);
			}
			if (mt2 instanceof Contig) {
				clone2 = ((Contig)mt2).getClone(data.getClone2());
				clone2.addHit(this);
			}
		}

		public String getType() {
			return FINGERPRINT_TYPE;
		}

		public boolean isBlockHit() {
			return data.isBlockHit();
		}

		public boolean isFiltered() {
			HitFilter hitfilter = mapper.getHitFilter();
			return (hitfilter.getBlock() && !data.isBlockHit())
					|| (hitfilter.getNonRepetitive() && data.isRepetitiveHit() && !data
							.isBlockHit())
					|| hitfilter.getFpHide()
					|| hitfilter.getFpEvalue() < data.getScore()
					|| (clone1 != null && !clone1.isInRange())
					|| (clone2 != null && !clone2.isInRange())
					|| (mt1 instanceof Block && ((Block) mt1).getPoint(data
							.getPos1(), LEFT_ORIENT, getContig1()) == null)
					|| (mt2 instanceof Block && ((Block) mt2).getPoint(data
							.getPos2(), LEFT_ORIENT, getContig2()) == null);
		}

		public boolean isHighlighted() {
			return (clone1 != null && clone1.isHover()) || (clone2 != null && clone2.isHover());
		}

		public boolean isVisible() {
			boolean v1 = false, v2 = false;
			if (clone1 != null) v1 = clone1.isVisible();
			else v1 = ((Block)mt1).isContigVisible(getContig1());
			if (clone2 != null) v2 = clone2.isVisible();
			else v2 = ((Block)mt2).isContigVisible(getContig2());
			return v1 && v2;
		}

		public void paintComponent(Graphics2D g2, int orient1, int orient2, Point loc1, Point loc2) { 
			if (isVisible() && !isFiltered()) {
				Point2D p1 = null, p2 = null;
				if (mt1 instanceof Block) 
					p1 = ((Block)mt1).getPoint(data.getPos1(),orient1,getContig1());
				else if (clone1 != null && clone1.isVisible())
					p1 = (Point2D)clone1.getMidPoint().clone();

				if (mt2 instanceof Block)
					p2 = ((Block)mt2).getPoint(data.getPos2(),orient2,getContig2());
				else if (clone2 != null && clone2.isVisible())
					p2 = (Point2D)clone2.getMidPoint().clone();

				if (p1 != null && p2 != null) {
					p1.setLocation(p1.getX()+loc1.getX(), p1.getY()+loc1.getY());
					p2.setLocation(p2.getX()+loc2.getX(), p2.getY()+loc2.getY());
					Line2D.Double line = new Line2D.Double(p1,p2);
					p1 = null;
					p2 = null;
					g2.setPaint(isHighlighted() ? Mapper.cloneLineHighlightColor : Mapper.cloneLineColor);
					g2.draw(line);
				}
			}
		}

		public void clear() { 
			if (clone1 != null) clone1.removeHit(this);
			if (clone2 != null) clone2.removeHit(this);
		}

		public void mouseMoved(MouseEvent e) { }
	}

	class SharedMarkerHit implements Hit {
		private String name1,name2;
		private MarkerHit mh1, mh2;
		private boolean repetitive;
		private boolean block;

		public SharedMarkerHit(Marker marker1, Marker marker2, boolean repetitive, boolean isBlockHit) {
			this.name1 = marker1.getName();
			this.name2 = marker2.getName();
			mh1 = new MarkerHit();
			mh2 = new MarkerHit();
			mh1.set(this,marker1);
			mh2.set(this,marker2);
			this.repetitive = repetitive;
			this.block = isBlockHit;
		}

		public String toString() {
			return "Shared Marker Hit "+name1+","+name2;
		}

		public void set() {
			mh1.set(this,mt1,name1,getContig1());
			mh2.set(this,mt2,name2,getContig2());
		}

		public boolean isBlockHit() {
			return block;
		}

		public String getType() {
			return MRK_TYPE;
		}

		public boolean isFiltered() {
			HitFilter hitfilter = mapper.getHitFilter();
			return (hitfilter.getBlock() && !block) || (hitfilter.getNonRepetitive() && repetitive && !block) ||
			hitfilter.getMrkHide() || 
			mh1.isFiltered(mt1,hitfilter.getOnlyShared()) || 
			mh2.isFiltered(mt2,hitfilter.getOnlyShared());
		}

		public boolean isHighlighted() {
			return mh1.isHighlighted() || mh2.isHighlighted();
		}

		public boolean isShowing(Object obj) {
			if (mh1.getMarker() != obj) return mh1.isVisible(mt1);
			return mh2.isVisible(mt2);
		}

		public boolean isVisible() {
			return mh1.isVisible(mt1) && mh2.isVisible(mt2);
		}

		public void paintComponent(Graphics2D g2, int orient1, int orient2, Point loc1, Point loc2, boolean showJoinDot) { 
			if (isVisible() && !isFiltered()) {
				Point2D mp1 = mh1.getCPoint(mt1,orient1,loc1,showJoinDot);
				Point2D mp2 = mh2.getCPoint(mt2,orient2,loc2,showJoinDot);
				mh1.paintComponent(g2,mt1,loc1,mp2,showJoinDot,false,true);
				mh2.paintComponent(g2,mt2,loc2,mp1,showJoinDot,false,true);
				if (mp1 != null && mp2 != null) {
					if (mh1.isHighlighted() || mh2.isHighlighted()) g2.setPaint(Mapper.markerLineHighlightColor);
					g2.setPaint(mh1.isHighlighted() || mh2.isHighlighted() ? 
							Mapper.markerLineHighlightColor : Mapper.sharedMarkerLineColor);
					g2.draw(new Line2D.Double(mp1,mp2));
				}
			}
		}

		public void clear() { 
			mh1.clear(this);
			mh2.clear(this);
		}

		public void mouseMoved(MouseEvent e) { 
			if (mh1.isVisible(mt1)) mh1.mouseMoved(e);
			if (mh2.isVisible(mt2)) mh2.mouseMoved(e);
		}
	}
}
