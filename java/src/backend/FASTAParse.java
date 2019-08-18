package backend;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;

// Yet another fasta parse class....
// Read in the fasta sequences one at a time

public class FASTAParse 
{
	Pattern mpHeader;
	Pattern mpEmpty;
	Pattern mpSpace;
	File mFile;
	String mLine = "";
	BufferedReader mBR;
	
	public FASTAParse(File f) throws FileNotFoundException
	{
		mFile = f;
		mpHeader = Pattern.compile("^>(\\S+).*");	
		mpEmpty = Pattern.compile("\\s*");	
		mpSpace = Pattern.compile("\\s+");	
		
		mBR = new BufferedReader( new FileReader(mFile));
	}
	
	public boolean nextSeq(FASTASequence seq) throws Exception
	{
		// first, do we have a valid header in the current line?
		boolean foundHeader = false;
		Matcher m = mpHeader.matcher(mLine); 
		if (m.matches())
		{
			seq.name = m.group(1);	
			foundHeader = true;
		}
		
		// scan further in the file. 
		// if we don't have a header, we need to find that first.
		// after that, we read to the next header, or an empty line.
		while (mBR.ready())
		{
			mLine = mBR.readLine();
			if (!foundHeader) 
			{
				if (mpEmpty.matcher(mLine).matches())
					continue;
				else // this needs to be a header
				{
					Matcher m2 = mpHeader.matcher(mLine); 
					if (m2.matches())
					{
						seq.name = m2.group(1);	
						foundHeader = true;
					}
					else
						throw(new Exception("Parse error in BES file " + mFile.getName() + "\n" + mLine));							
				}
			}
			else
			{
				// if it's a header, or an empty line we're done with this seq section
				Matcher m2 = mpHeader.matcher(mLine); 
				if (m2.matches() || mpEmpty.matcher(mLine).matches())
				{
					if (seq.seq.length() == 0)
						throw(new Exception("Empty fasta sequence " + seq.name));
					return true;
				}
				else // it appears to be a line of sequence so remove spaces and append
					seq.seq += mpSpace.matcher(mLine).replaceAll("");							
			}
		}

		// file is over - do we have something?
		return (seq.seq.length() > 0);
	}
}
