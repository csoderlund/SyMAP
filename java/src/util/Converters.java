package util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

public class Converters 
{
	public static int findIndexOfMax ( int [] intArray )
	{
		if ( intArray.length <= 0 )
			throw new RuntimeException ("The array is empty...  Q: If an empty array falls into a method and nobody cares does it have a max value?  A: The glass is 20.7% empty.");
		
		int nMax = 0;
		for ( int i = 1; i < intArray.length; ++i )
			if ( intArray[i] > nMax )
				nMax = i;
		
		return nMax;
	}
	
	
    public static String getMessageAndAppend ( Exception err )
    {
        // Very common error with very obtuse error message...
        String str = "";
        if ( err.toString().indexOf( "java.security.AccessControlException" ) >= 0 ) // The MYSQL driver throws nested exceptions...
        {
            str += "Note: for security reasons, Java Applets can only connect to a single IP address.";
            str += "\n";
            str += "Therefore all Java GBrowse components (web page, .jar file, configuration file, and database) must be accessed through the same IP address.";
            str += "\n";
        }
        str += err.toString();
        return str;
    }
    
   public static Vector<String> getStackTrace ( Throwable err )
    {
        Vector<String> stringList = new Vector<String> ();
        StackTraceElement stackTrace [] = err.getStackTrace();
        
        for ( int i = 0; i < stackTrace.length; ++i )
        {
            stringList.add( stackTrace [i].toString() );
        }
        
        return stringList;
    }
        
    static public String boolToYesNo ( boolean b )
    {
        if ( b )
            return strYes;
        else
            return strNo;
    }
    
    static public boolean asBool ( int n )
    {
        if ( n == 0 )
            return false;
        else
            return true;
    }

    // This routine first applies the decimal rounding to numDec places.
    // If this returns zero, then it goes to significant digits with numSig places.
    // E.g., for numSig=numDec=2 the results are
    // 45.678 --> 45.68, .00045678 --> .00046
    // This was the original default behavior of TCW (see function decimalString), except it truncated
    static public String roundBoth(double doub, int numSig, int numDec)
    {
    	BigDecimal d = new BigDecimal(doub);
    	int origSig = d.precision(); 
    	numSig = Math.min(origSig, numSig); // can't ask for more sig figs than we started with
    	
    	// First try the decimal 
    	BigDecimal d2 = new BigDecimal(doub).setScale(numDec, RoundingMode.HALF_UP);
    	if (d2.precision() >= numSig) 
    	{
    		// we didn't lose sig figs with the decimal rounding, hence good to go
    		return d2.toString();
    	}
    	return roundToSigFigs(doub, numSig);
    	
    }
    static public String roundToSigFigs (double d, int numSigFigs )
    {
    	BigDecimal bd1 = new BigDecimal(d, new MathContext(numSigFigs));
    	return bd1.toString();
    }
    static public String roundDec(double doub, int numDec)
    {
       	BigDecimal d = new BigDecimal(doub);    	
    	d = d.setScale(numDec, RoundingMode.HALF_UP);   
    	return d.toString();
    }
    // mdb added 4/4/08 - maybe could be simplified
    static public String decimalString (double d, int numSigFigs )
    {
    	if (d == 0) return "0.0";
    	
		String strVal = String.valueOf(d);
		
		int decIndex = strVal.indexOf('.');
		if (decIndex >= 0) 
		{ 
			// has a decimal point
			int expIndex = strVal.indexOf('E');
			
			if (expIndex > 0) 
			{
				// has exponent
				;
			}
			else if (d < 1 && d > -1) 
			{ 
				// count leading zeros (if < 1)
				do { decIndex++; } 
				while (decIndex < strVal.length() && strVal.charAt(decIndex) == '0');
			}
			
			// copy exponent
			String expStr = "";
			if (expIndex > 0) 
			{
				expStr = strVal.substring(expIndex);
				strVal = strVal.substring(0, expIndex);
			}
			
			// truncate value string 
			strVal = strVal.substring(0, Math.min(decIndex+numSigFigs, strVal.length()));
			strVal += expStr;
		}
		
		return strVal;
    }
    
	static public String formatDecimal ( double d )
	{
		if ( numFormat == null )
		{
			numFormat = new DecimalFormat ();
			numFormat.setMinimumIntegerDigits(1);
			numFormat.setMaximumFractionDigits(1);
		}
			
		if ( d == 0 )
			return "0";

		if ( d < 0.1 && d > -0.1 )
			numFormat.applyPattern( "#.#E0" );
		else
			numFormat.applyPattern( "###.#" );
		return numFormat.format(d);
	}
    
    public static void reverse ( int [] intArray )
    {
        int i = 0;
        int j = intArray.length - 1;
        int temp;
        
        while ( i < j )
        {
            // Swap current two elemnts
            temp = intArray[i];
            intArray[i] = intArray[j];
            intArray[j] = temp;
            
            // Both both indexes towards the center
            ++i;
            --j;
        }
    }
    
    // Converts from an int array to a collection of Integer objects
    public static void addIntArray ( int [] fromIntArray, Collection<Integer> toCollection )
    {
        for (int i = 0; i < fromIntArray.length; ++i)
            toCollection.add( new Integer (fromIntArray[i]) );      
    }
    
    public static <T> Vector<T> combineVectors ( Vector<T> prepend, Vector<T> append )
    {
        Vector<T> result = new Vector<T> ( prepend );
        result.addAll( append );
        return result;
    }
    
    public static int [] intCollectionToIntArray ( Collection<Integer> inCollection )
    {
        int outArray [] = new int [ inCollection.size() ];
        
        Iterator<Integer> iter = inCollection.iterator();
        for ( int i = 0; i < inCollection.size(); ++i )
        {   
            Integer aInt = (Integer)iter.next();
            outArray[i] = aInt.intValue();
        }
        
        return outArray;
    }
    
    // Moves all characters in the input stream to the output stream
    public static void InputStreamToOutputStream ( InputStream in, OutputStream out ) throws java.io.IOException
    {
        int n = in.read();
        while ( n != -1 )
        {
            out.write(n);
            n = in.read();
        }
    }
    
    public static boolean compareObjects ( Object a, Object b )
    {
        if ( a == null || b == null )
            return a == b;
        else
            return a.equals( b );
    }

    // returns a deep copy of an object. (The default for clone is a shallow copy.) 
    // I stole from the internet:  http://www.javaworld.com/javaworld/javatips/jw-javatip76-p2.html
    public static Object deepCopy(Object oldObj) throws Exception
    {
          ObjectOutputStream oos = null;
          ObjectInputStream ois = null;
          try
          {
             ByteArrayOutputStream bos =
                   new ByteArrayOutputStream(); // A
             oos = new ObjectOutputStream(bos); // B
             // serialize and pass the object
             oos.writeObject(oldObj);   // C
             oos.flush();               // D
             ByteArrayInputStream bin =
                   new ByteArrayInputStream(bos.toByteArray()); // E
             ois = new ObjectInputStream(bin);                  // F
             // return the new object
             return ois.readObject(); // G
          }
          catch(Exception e)
          {
             System.err.println("Exception in ObjectCloner = " + e);
             throw(e);
          }
          finally
          {
             if ( oos != null )
                oos.close();
             if ( ois != null )
                ois.close();
          }
    }
    
    private Converters () { }; // Don't instantiate
    
    final static private String strYes = "Yes";
    final static private String strNo = "No";
       
	static private DecimalFormat numFormat = null;
}
