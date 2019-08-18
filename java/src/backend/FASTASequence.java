package backend;

public class FASTASequence 
{
	String name = ""; 	// section name
	String seq = "";	// sequence data
	int start, end;
	
	public FASTASequence() { }
	
	public FASTASequence(int start, int end, String name)
	{
		if (start > end) {
			int temp = start;
			start = end;
			end = temp;
		}
		
		this.start = start;
		this.end = end;
		this.name = name;
	}
	
	public void clear()
	{
		name = "";
		seq = "";
	}
}
