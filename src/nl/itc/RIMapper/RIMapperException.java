package nl.itc.RIMapper;

/**
 * simple extended interface to Exception.
 * 
 * <br/>&copy;2004 International Institute for Geo-information Science and Earth
 * Observation (ITC) <br/>Licensed under a Creative Commons
 * Attribution-NonCommercial-ShareAlike 2.5 License. see <a
 * href="http://creativecommons.org/licenses/by-nc-sa/2.5/" target="_blank">
 * http://creativecommons.org/licenses/by-nc-sa/2.5/</a>
 * 
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl"
 *         target="_blank">kobben@itc.nl</a>
 * @version 1.0 [15 June 2004]
 * 
 */
/*
 * 1.0 [15 June 2004] - first released version
 */
public class RIMapperException extends Exception {

	private static final long serialVersionUID = 1L;
	private final String myMessage; 

	public RIMapperException(String message) {
		super(message);
		myMessage = message;
	}

	/**
	 * Simple extended interface to getMessage.
	 * Needed because of a bug (?) in Exception.getMessage() which 
	 * keeps returning the class:detailmessage instead of just
	 * detailmessage...
	 */
	@Override
	public String getMessage() {
		return myMessage;
	}

}
