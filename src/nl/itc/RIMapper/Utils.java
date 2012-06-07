/**
 * 
 */
package nl.itc.RIMapper;

import org.apache.xerces.dom.TextImpl;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/**
 * Set of (static) utility classes for RIMapperWMS. 
 * Currently, mainl for XML file parsing and Error handling
 *
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl"
 *         target="_blank">kobben@itc.nl</a>
 * @version 1.0 [December 2006]
 */
// Major changes:
// 1.0 [Dec 2006] - first released version
public class Utils {

	private static final String LF = "\n"; 

	public final static String XHTML_DOCTYPE = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"; 

	public final static String OGC_EXCEPTION_DOCTYPE = "<!DOCTYPE ServiceExceptionReport SYSTEM \"http://schemas.opengeospatial.net/wms/1.1.1/WMS_exception_1_1_1.dtd\">"; 

	public final static String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"; 

	public final static String XHTML_NAMESPACE = " xmlns=\"http://www.w3.org/1999/xhtml\" "; 

  public final static String XLINK_NAMESPACE = "xmlns:xlink=\"http://www.w3.org/1999/xlink\" ";   
  
  public final static String XMLEVENTS_NAMESPACE = "xmlns:ev=\"http://www.w3.org/2001/xml-events\" ";     
	
	public final static String SVG_NAMESPACE = " xmlns=\"http://www.w3.org/2000/svg\" "; 

	public final static String RIM_NAMESPACE = " xmlns:rim=\"http://kartoweb.itc.nl/RIMapper\" "; 

	public static final String XHTML_MIME_TYPE = "text/html"; 

  public static final String SVG_MIME_TYPE = "image/svg+xml"; 

  public static final String PNG_MIME_TYPE = "image/png"; 

  public static final String JPEG_MIME_TYPE = "image/jpeg"; 

	public static final String CAPABILITIES_MIME_TYPE = "application/vnd.ogc.wms_xml"; 

	public static final String GML_MIME_TYPE = "application/vnd.ogc.gml"; 

	public static final String OGC_EXCEPTION_XML_MIME_TYPE = "application/vnd.ogc.se_xml"; 

	public static final String OGC_EXCEPTION_INIMAGE_MIME_TYPE = "application/vnd.ogc.se_inimage"; 

	public static final String PLAINXML_MIME_TYPE = "text/xml"; 

  private static final int contentTypeSVG = 0;
  private static final int contentTypeXML = 1;
  private static final int contentTypeXHTML = 2;

	
	/**
	 * Parses XML template file and returns the document tree.
	 * 
	 * @param XMLfile
	 *          the URL or file path
	 * @param validate
	 *          sets DTD validation on/off returns the document tree
	 */
	public static Document getXMLtree(String XMLfile, boolean validate) throws RIMapperException {

		String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.DOMParser";
		String VALIDATION_URL = "http://xml.org/sax/features/validation";

		ErrorHandler parseErrorHandler = new ErrorHandler() {
			// inner classes to register Parser errorhandler
			public void warning(SAXParseException e) throws SAXParseException {
				throw e;
			}

			public void error(SAXParseException e) throws SAXParseException {
				throw e;
			}

			public void fatalError(SAXParseException e) throws SAXParseException {
				throw e;
			}
		};

		try {
			DOMParser parser = (DOMParser) Class.forName(DEFAULT_PARSER_NAME).newInstance();
			if (validate) {
				parser.setFeature(VALIDATION_URL, true);
			} else {
				parser.setFeature(VALIDATION_URL, false);
			}
			parser.setErrorHandler(parseErrorHandler);
			parser.parse(XMLfile);
			return parser.getDocument();
		} catch (SAXParseException e) {
			throw new RIMapperException("[XML PARSER ERROR] " + e.getMessage());
		} catch (org.xml.sax.SAXException e) {
			throw new RIMapperException("[SAX ERROR] " + e.getMessage());
		} catch (Exception e) {
			throw new RIMapperException("[UNEXPECTED ERROR] " + e.toString());
		}
	} // of method getXMLtree
	
	
	/**
	 * Gets the content (TEXT or CDATA readable text) out of an XML node.
	 * 
	 * @param node
	 *          the XML node
	 * @return a String with the text
	 */
	private static String readString(Node node) {
		StringBuffer S = new StringBuffer();
		NodeList children = node.getChildNodes();
		if (children != null) {
			int len = children.getLength();
			for (int i = 0; i < len; i++) {
				Node n = children.item(i);
				switch (n.getNodeType()) {
				case Node.CDATA_SECTION_NODE: {
					S.append(n.getNodeValue());
					break;
				}
				case Node.TEXT_NODE: {
					if (n instanceof TextImpl) {
						if (!(((TextImpl) n).isIgnorableWhitespace())) {
							S.append(n.getNodeValue());
						}
					} else {
						S.append(n.getNodeValue());
					}
				}
				}
			}
		}
		return S.toString();
	}
	
	
	/**
	 * Gets the value (CDATA or Text node) of a Tag out of an XML document.
	 * @param myXML the XML Document object
	 * @param myTag the Tag whose value we want
	 * @return a String with the tag value
	 * @throws RIMapperException
	 */
	public static String getTagFromXML(Document myXML, String myTag) throws RIMapperException {
		NodeList Elements = null;
		Node n = null;
		Elements = myXML.getElementsByTagName(myTag);
		if (Elements == null) throw new RIMapperException("[getTagFromXML] Cannot get '"+ myTag + "' from XML.");
		Elements.getLength(); // should only be 1
		n = Elements.item(0);
	  if (n == null) throw new RIMapperException("[getTagFromXML] Cannot get '"+ myTag + "' from XML.");
		return readString(n);
	}
	
	private static StringBuffer MakeErrorContent(int contentType, String ErrorLocation,
      String ErrorMessage)  {
    StringBuffer S = new StringBuffer();
    ErrorMessage = ErrorMessage.replaceAll(">", "&gt;");
    ErrorMessage = ErrorMessage.replaceAll("<", "&lt;"); //to avoid malformed XML
    if (contentType == contentTypeSVG) {
      S.append(XML_HEADER + LF);
      S.append("<svg " + SVG_NAMESPACE);
      S.append(" viewBox=\"0 0 320 320\" width=\"320\" height=\"320\"  ");
      S.append("preserveAspectRatio=\"none\" >");
      // to fit PDA proportioned screens
      S.append("<title>");
      S.append(Messages.getString("Utils.RIMapperWMSError"));
      S.append("</title>" + LF);
      S.append("<rect x=\"1\" y=\"0\" width=\"319\" height=\"320\" ");
      S.append("fill=\"white\" stroke=\"black\" stroke-width=\"1\"/>" + LF);
      S.append("<rect x=\"0\" y=\"0\" width=\"320\" height=\"45\" ");
      S.append("fill=\"#444444\" />" + LF);
      S.append("<text><tspan style=\"font-family:Tahoma,Courier,sans-serif;");
      S.append("fill:white;font-size:18px;\" x=\"6\" y=\"20\" > ");
      S.append(Messages.getString("Utils.RIMapperWMSError"));
      S.append("</tspan>" + LF);
      S.append("<tspan style=\"font-family:Tahoma,Courier,sans-serif; ");
      S.append("fill:white;font-size:12px;\" x=\"6\" y=\"36\">");
      S.append(Messages.getString("Utils.ExceptionIn"));
      S.append(ErrorLocation + "</tspan>" + LF);
      S.append("</text>");
      S.append("<text style=\"font-family:Courier,Courier New, mono; ");
      S.append("fill:black;font-size:12px;\">" + LF);
      S.append("<tspan x=\"4\" y=\"55\" >");
      int z = 0, j = 0;
      for (int i = 0; (i < ErrorMessage.length()); i++) {
        S.append(ErrorMessage.substring(i, i + 1));
        j++;
        if (j > 42) {
          j = 0;
          z++;
          S.append("</tspan>" + LF);
          S.append("<tspan x=\"4\" y=\"" + (55 + (z * 13)) + "\">");
        }
      }
      S.append("</tspan>" + LF + "</text>" + LF);
      S.append("</svg>");
    } else if (contentType == contentTypeXML) {
      S.append(XML_HEADER + LF);
      S.append(OGC_EXCEPTION_DOCTYPE + LF);
      S.append("<ServiceExceptionReport version = \"1.1.1\">" + LF); 
      S.append("  <ServiceException>" + LF); 
      S.append("     " + ErrorLocation + " " + ErrorMessage + LF); 
      S.append("  </ServiceException>" + LF); 
      S.append("</ServiceExceptionReport>" + LF); 
    } else if (contentType == contentTypeXHTML) {
      S.append(XML_HEADER + LF);
      S.append(XHTML_DOCTYPE + LF);
      S.append("<html " + XHTML_NAMESPACE + ">" + LF); 
      S.append("<head>" + LF + "<title>"); 
      S.append(Messages.getString("Utils.RIMapperWMSError")); 
      S.append("</title>" + LF + "<style type=\"text/css\">" + LF); 
      S
          .append("H1 {font-family:Tahoma,Courier,sans-serif;color:white;background-color:#444444;font-size:22px;}" 
              + LF);
      S
          .append("P {font-family:Courier,Tahoma,mono;color:black;background-color:white;;font-size:14px;}" 
              + LF);
      S.append("--></style></head>" + LF); 
      S.append("<body bgcolor=\"#FFFFFF\">"); 
      S.append("<H1>&nbsp;"); 
      S.append(Messages.getString("Utils.RIMapperWMSError")); 
      S.append("</H1><BR/>"); 
      S.append("<font size=\"-1\">&nbsp;&nbsp;"); 
      S.append(Messages.getString("Utils.ExceptionIn")); 
      S.append(ErrorLocation  + "<HR/></font>" + LF); 
      S.append("<P>" + ErrorMessage + "</P>" + LF); 
      S.append("</body></html>" + LF); 
    } else { //unknown/wrong!
      S.append("UNKNOWN CONTENT TYPE ("+contentType +") in function MakeErrorContent() in class Utils!");
    }
    return S;
	}
	
	
	/**
	 * Generates ErrorMessage as complete XHTML, SVG, or other file. <br/> <br/>For
	 * SVG, fills screens with MonoTyped text in 25 char lines. <br/>For X(HT)ML,
	 * lets browser do the textflow.
	 * 
	 * @param ErrorLocation the class that the error occured in
	 * @param ErrorMessage the message to be displayed
   * @param ExceptionResponseType the MIME type of exceptions
   * @param ResponseType the MIME type of the WMS response:<br/>
   * Error output will be:<br/>
   *   "application/vnd.ogc.se_xml" for output in OGC Error XML <br/>
   *   "application/vnd.ogc.se_xml_inimage" for output in the ResponseType:<br/>
   *      - if ResponseType = "image/svg+xml" => SVG output<br/>
   *      - if ResponseType = "image/png" or if "image/jpeg" => image output<br/>
   *      - else (eg. Capabilities XML) => HTML output
	 * @return a StringBuffer with the error output.
	 */
	public static StringBuffer MakeError(String ErrorLocation,
			String ErrorMessage, String ResponseType, String ExceptionResponseType) {
		StringBuffer S = new StringBuffer();
		try {
			if (ExceptionResponseType.equalsIgnoreCase(OGC_EXCEPTION_XML_MIME_TYPE)) {
			  S.append(MakeErrorContent(contentTypeXML, ErrorLocation,ErrorMessage));
			} else if (ExceptionResponseType.equalsIgnoreCase(OGC_EXCEPTION_INIMAGE_MIME_TYPE)) {
				if (ResponseType.equalsIgnoreCase(Utils.SVG_MIME_TYPE)) {
          S.append(MakeErrorContent(contentTypeSVG, ErrorLocation,ErrorMessage));
				} else if (ResponseType.equalsIgnoreCase(Utils.PNG_MIME_TYPE) 
				    || ResponseType.equalsIgnoreCase(Utils.JPEG_MIME_TYPE)) {
          S.append(MakeErrorContent(contentTypeSVG, ErrorLocation,ErrorMessage));
          
        } else { //its HTML, Capabilities XML or unexpected
          S.append(MakeErrorContent(contentTypeXHTML, ErrorLocation,ErrorMessage));
        } 
			} else { //ExceptionResponseType = Capabilities XML, unknown or wrong => force XHTML 
        S.append(MakeErrorContent(contentTypeXHTML, ErrorLocation,ErrorMessage));
      } 
		} catch (RuntimeException e) {
			S.append(e); // desperate dump, might not work if S==null, or not be visible
			// if responsetype is not readable text...
			e.printStackTrace();
    }
		return S;
	}

}
