package log4j2JText;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;


public class FlexibleLayout extends Layout {

	private String NL = System.getProperty("line.separator");
	
	@Override
	public void activateOptions() {
		// TODO Auto-generated method stub

	}

	@Override
	public String format(LoggingEvent event) {
		StringBuffer sb = new StringBuffer();
		sb.append(event.getLevel().toString()).append(": ");
//		sb.append("[")
//		.append(event.getLocationInformation().fullInfo)
//		.append("] :");
		
		sb.append(event.getMessage()).append(NL);
		if (event.getThrowableInformation() != null){
			String[] s = event.getThrowableStrRep();
			for (int i=0;i<s.length;i++){
				sb.append(s[i]).append(NL);
			}			
		}
	    
	    return sb.toString();
	}

	@Override
	public boolean ignoresThrowable() {		
		return false;
	}

}
