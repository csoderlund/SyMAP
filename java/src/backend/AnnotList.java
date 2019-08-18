package backend;
import java.util.HashSet;


class AnnotList
{
	HashSet<AnnotElem> mList;
	
	public AnnotList()
	{
		mList = new HashSet<AnnotElem>();		
	}
	
	public void add(AnnotElem ae)
	{
		mList.add(ae);
	}
	public void remove(AnnotElem ae)
	{
		mList.remove(ae);
	}	
}