package backend.anchor2;

import java.util.TreeSet;
import java.util.Vector;

import util.ErrorReport;

public class GeneTree {	
	protected Tree tree = new Tree();
	protected int cntGenes=0;
	private String title=""; // chr
	private int cntDupStart=0;
	
	protected GeneTree(String title) {this.title=title;}
	
	protected void addGene(int start, Gene hitObj) {
		cntGenes++;
		
		Node startNode = tree.findStartNode(tree.root, start);
		
		if (startNode!=null) {
			cntDupStart++;
			startNode.add(hitObj);
		}
		else  {
			tree.root = tree.insertNode(tree.root, hitObj);	
		}
	}
	protected Gene [] getSortedGeneArray() {
		//tree.printTree();
		Vector <Gene> inorder = new Vector <Gene> ();
		
		tree.tree2array(tree.root, inorder);
		
		return inorder.toArray(new Gene[0]);
	}
	
	protected TreeSet <Gene> findOlapSet(int hstart, int hend, boolean bAll) {
		return tree.findOverlapSet(hstart, hend, bAll); // if false, only return longest gene with start
	}
	protected void finish() {
		tree.iAssignMaxEnd(tree.root);
	}
	protected void clear() {
		tree.clear(tree.root);
		tree.root = null;
		cntDupStart=cntGenes=0;
	}
	
	protected String toResults() {
		return String.format("Tree: %-20s Genes: %,6d  DupStart %,4d", title, cntGenes, cntDupStart);}
	public String toString() {return toResults();}
	
	/***********************************************************/
	protected class Node implements Comparable <Node> {
	   private Gene itvObj; 				 // interval that encompasses startSet intervals
	   private int start, end;  				 // same as itvObj.start, itvObj.end
	   private TreeSet <Gene> startSet=null; // intervals with same start, contained in itvObj
	   
	   private int maxEnd=0, height=1;
	   private Node left, right;
	   
	   private Node (Gene itv) {
		  this.start = itv.gStart;
		  this.end   = itv.gEnd;
		  itvObj = itv;
		  maxEnd = end;
	   }
	   private void add(Gene gn) {
		  if (startSet==null) startSet = new  TreeSet <Gene> ();
		  
		  if (gn.gEnd > end) {
			  startSet.add(itvObj);
			  itvObj = gn;
			  maxEnd = end = gn.gEnd;
		  }
		  else startSet.add(gn);
	   }
	   public int compareTo(Node n) {
           if (this.start < n.start) return -1;
           else if (this.start == n.start) return this.end > n.end ? -1 : 1;
           else return 1;
     }
	   public String toString() {
		   String msg = (startSet!=null) ? "Set " + startSet.size() : "0";
		   return "[" + start + "," + end + "] Max(" + maxEnd + ") " + msg;
	   }
	   protected void clear() { // node
		   if (startSet!=null) {
			   startSet.clear();
			   startSet=null;
		   }
		   itvObj=null;
		   left=right=null;
		   start=end=0;
	   }
	}
	
	private class Tree {
		   protected Node root=null;
		   		
		   protected Node insertNode(Node node, Gene gn) {
		      if (node == null) return new Node(gn);
		      
		      int hStart = gn.gStart;
		      if (hStart < node.start)      node.left =  insertNode (node.left, gn);
		      else if (hStart > node.start) node.right = insertNode (node.right, gn);
		      else die("Duplicate start " + gn.gStart);
		    
		      node.height = 1 + Math.max(iHeight(node.left), iHeight(node.right));
		      
		      int balance = iBalance(node);
		      if (balance >= -1 && balance <= 1) return node;
		      
		      if (balance > 1 && hStart < node.left.start) {
		         return iRightRotate(node);
		      }
		      if (balance < -1 && hStart > node.right.start) {
		         return iLeftRotate(node);
		      }
		      if (balance > 1 && hStart > node.left.start) {
		         node.left = iLeftRotate (node.left); // here
		         return iRightRotate(node);
		      }
		      if (balance < -1 && hStart < node.right.start) {
		         node.right = iRightRotate(node.right);
		         return iLeftRotate(node);
		      }
		      return node;
		   }
		   private Node iRightRotate(Node y) {
			   try {
				   Node x = y.left;
				   Node T2 = x.right;
				   x.right = y;
				   y.left = T2;
		      
				   y.height = Math.max(iHeight (y.left), iHeight (y.right)) + 1;
				   x.height = Math.max(iHeight (x.left), iHeight (x.right)) + 1;
				   return x;
			   }
		      catch (Exception e) { ErrorReport.print(e, "GeneTree right rotate"); return null;}
		   }
		   private Node iLeftRotate(Node x) {
		      try {
			      Node y = x.right;
			      Node T2 = y.left; // here
			      y.left = x;
			      x.right = T2;
			      
			      x.height = Math.max(iHeight(x.left), iHeight(x.right)) + 1;
			      y.height = Math.max(iHeight(y.left), iHeight(y.right)) + 1;
			      return y;
		      }
		      catch (Exception e)  { ErrorReport.print(e, "GeneTree left rotate"); return null;}
		   }
		   private int iHeight (Node N) {
			   if (N == null) return 0;
			   return N.height;
		   }
		   private int iBalance(Node N) {
			  if (N == null) return 0;
			  return iHeight(N.left) - iHeight(N.right);
		   }
		   // Find node with start; allows adding AnnoData to node.startSet
		   protected Node findStartNode(Node node, int start) {
			   if (node==null) return null;
			  
			   if (start < node.start)    return findStartNode(node.left, start);
			   else if (start>node.start) return findStartNode(node.right, start);
			   else return node;
		   }
		   
		  // this can only be done when the tree is complete as it expects init maxEnd=end 
		   protected int iAssignMaxEnd(Node tmpNode) {
			   int maxL = (tmpNode.left!=null) ? iAssignMaxEnd(tmpNode.left) : 0;
			   int maxR = (tmpNode.right!=null) ? iAssignMaxEnd(tmpNode.right) : 0;
			   if (tmpNode.maxEnd < maxL) tmpNode.maxEnd = maxL;
			   if (tmpNode.maxEnd < maxR) tmpNode.maxEnd = maxR;
			   return tmpNode.maxEnd;
		   }
		   protected void clear(Node tmpNode) {
			   if (tmpNode == null) return;
			   clear(tmpNode.left);
			   clear(tmpNode.right);
			   tmpNode.clear();
		   }
		   
		  /** Access tree methods *******************************************************/
		  
		// Search for overlap or contained; uses maxEnd
		   protected TreeSet <Gene> findOverlapSet(int start, int end, boolean checkAll) {
			   TreeSet <Gene> set = new TreeSet <Gene> ();
			   findOverlap(root, start, end, set, checkAll);
			   return set;
		   }
		   private void findOverlap(Node tmpNode, int hstart, int hend, TreeSet<Gene> annoSet, boolean checkAll) {
		        if (tmpNode == null) return;
		        
		        if ( !(tmpNode.start > hend || tmpNode.end < hstart) )  {
		            annoSet.add(tmpNode.itvObj);
		            if (checkAll && tmpNode.startSet!=null) 
		            	for (Gene ad : tmpNode.startSet) {
		            		if ( !(ad.gStart > hend || ad.gEnd < hstart) )  
		            			annoSet.add(ad);
		            	}
		        }
		
		        if (tmpNode.left != null && tmpNode.left.maxEnd >= hstart) 
		            this.findOverlap(tmpNode.left, hstart, hend, annoSet, checkAll);
		        
		        this.findOverlap(tmpNode.right, hstart, hend, annoSet, checkAll);
		   }
		   
		   protected void tree2array(Node tmpNode,  Vector <Gene> inorder){
			   if (tmpNode == null) return;
				   
			   tree2array(tmpNode.left, inorder);
				  
			   inorder.add(tmpNode.itvObj);
			   if (tmpNode.startSet!=null) {
				   for (Gene gn : tmpNode.startSet) {
					   inorder.add(gn);
				   }
			   }
				
			   tree2array(tmpNode.right, inorder);
		   }
		   
		 // Print
		   private boolean bPrtTree=false;
		   private int cntNode=0, cntLevel=0;
		   protected void printTree() {
			   prt("PrintTree ");
			   printTree(root, 0, "C");
			   prt("#Node " + cntNode + " level " + cntLevel);
		   }
		   private void printTree(Node root, int level, String d ){
			  if (root == null) return;
			   
			  if (level>cntLevel) cntLevel=level;
			  cntNode++;
			  
			  printTree(root.left, level+1, "L");
			  if (bPrtTree) {
			      String sp=" ";
			      for (int i=0; i<level; i++) sp += "  ";
			      String list="";
			      if (root.startSet!=null) {
			    	  list = " {";
			    	  for (Gene ad : root.startSet) list += ad.toString() + " ";
			    	  list += "}";
			      }
			     System.out.println(level + sp + d + " " + root.toString() + list);
			  }
		      printTree(root.right, level+1, "R");
		   }
	}
	private void prt(String msg) {System.out.println(String.format("TR %15s ",title)+ msg);}
	private void die(String msg) {prt(msg); System.exit(0);}
}
