package nl.itc.RIMapper;

import java.util.*;
import org.w3c.dom.*;
import org.apache.xerces.dom.TextImpl;
import org.apache.xerces.parsers.DOMParser;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/**
 * Parses RIMapper XML files and returns handles to further process them 
 * into SVG output.
 * <br/>
 * NOTE: this is the older way of configuring RIMapper instances (pre-WMS). 
 * 
 * <br/>&copy;2004-6 International Institute for Geo-information Science and Earth
 * Observation (ITC) <br/>Licensed under a Creative Commons
 * Attribution-NonCommercial-ShareAlike 2.5 License. see <a
 * href="http://creativecommons.org/licenses/by-nc-sa/2.5/" target="_blank">
 * http://creativecommons.org/licenses/by-nc-sa/2.5/</a>
 * 
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl"
 *         target="_blank">kobben@itc.nl </a>
 * @version 1.1 [April 2004]
 */
// Major changes: 
// 1.0 [15 June 2004] - first released version 
// 1.1 [April 2006] - supports PostgreSQL/PostGIS + mapExtent, FalseOrigin & Precision
 

public class ParseXML {

	public static final int MAX_ACTIONS_PER_LAYER = 5;

	public static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.DOMParser";

	public static final String VALIDATION_URL = "http://xml.org/sax/features/validation";

	public Document XMLtree;

	public String RIM_Type;

	public String RIM_SFSserver;

	public String RIM_DB;

	public String RIM_UN;

	public String RIM_PW;

	double Xmin = 0.0;

	double Ymin = 0.0;

	double Xmax = 0.0;

	double Ymax = 0.0;

	double FalseOriginX = 0.0;

	double FalseOriginY = 0.0;

	long Precision = 0;

	boolean mapExtentDefault = true;

	public String title;

	public String author;

	public int nrLayers;

	public String[] layerNames;

	public String[] layerAttribs;

	public int[] nrLayerActions;

	public String[][] layerActions;

	public String[][] layerActionScopes;

	public String[] layerStyles;

	public String[] layerStyleTypes;

	public int nrStyles;

	public String[] styleNames;

	public String[] styles;

	public int nrFragments;

	public String[] fragmentNames;

	public String[] fragmentTypes;

	public String[] fragments;

	public String SVGRootFragmentName;

	public String SVGRootFragment;

	public ParseXML() { // constructor method
		XMLtree = null;
		title = new String();
		author = new String();
		RIM_Type = null;
		RIM_DB = null;
		RIM_UN = null;
		RIM_PW = null;
		nrLayers = -1;
		nrStyles = -1;
		nrFragments = -1;
		// layerNames, styleNames and other arrays get initialized
		// later depending on nrLayers, nrStyles, etc. found...
		SVGRootFragment = null;
		SVGRootFragmentName = null;
	}

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
	 * Get the attributes of an XML node and returns them as a Properties list.
	 * 
	 * @param node
	 *          the XML node
	 * @return Properties list
	 */
	private Properties getNodeAttributes(Node node) {
		Properties attribList = new Properties();
		NamedNodeMap attributes = node.getAttributes();
		int attributeCount = attributes.getLength();
		if (attributeCount != 0) {
			for (int j = 0; j < attributeCount; j++) {
				attribList.setProperty(attributes.item(j).getNodeName(), attributes
						.item(j).getNodeValue());
			}
		}
		return attribList;
	}

	/**
	 * Get the attributes of XML RIM node.
	 * 
	 * </br>void,sets instance field {@link #title title}
	 * 
	 * @param XMLtree
	 *          the Document Tree in memory as parsed by
	 *          {@link #getXMLtree getXMLtree}
	 */
	public void getRIM(Document XMLtree) throws RIMapperException {

		try {
			NodeList RIMElements = XMLtree.getElementsByTagName("RIM");
			if (RIMElements.getLength() != 1) {
				throw new RIMapperException("[parseXML] only 1 RIM element allowed.");
			}
			Node n = RIMElements.item(0);
			if (n == null) {
				throw new RIMapperException("[parseXML] Cannot parse RIM.");
			}
			Properties myNodeAttributes = getNodeAttributes(n);
			if (myNodeAttributes.getProperty("TYPE") == null) {
				throw new RIMapperException(
						"[parseXML] Cannot parse TYPE attribute of RIM.");
			}
			RIM_Type = myNodeAttributes.getProperty("TYPE");

			if (myNodeAttributes.getProperty("SFSserver") == null) {
				RIM_SFSserver = "MySQL"; // for backward compatibility
			} else {
				RIM_SFSserver = myNodeAttributes.getProperty("SFSserver");
			}

			if (myNodeAttributes.getProperty("DB") == null) {
				throw new RIMapperException(
						"[parseXML] Cannot parse DB attribute of RIM.");
			}
			RIM_DB = myNodeAttributes.getProperty("DB");
			if (myNodeAttributes.getProperty("UN") == null) {
				RIM_UN = "";
			} else {
				RIM_UN = myNodeAttributes.getProperty("UN");
			}
			if (myNodeAttributes.getProperty("PW") == null) {
				RIM_PW = "";
			} else {
				RIM_PW = myNodeAttributes.getProperty("PW");
			}
			if ((myNodeAttributes.getProperty("BBOX") == null)
					|| (myNodeAttributes.getProperty("BBOX").equals(""))) { // default to
																																	// full extent
				mapExtentDefault = true;
				Xmin = 0;
				Ymin = 0;
				Xmax = 0;
				Ymax = 0;
			} else {
				mapExtentDefault = false;
				String[] BBOX = (myNodeAttributes.getProperty("BBOX")).split(",");
				Xmin = Double.parseDouble(BBOX[0]);
				Ymin = Double.parseDouble(BBOX[1]);
				Xmax = Double.parseDouble(BBOX[2]);
				Ymax = Double.parseDouble(BBOX[3]);
			}

			if ((myNodeAttributes.getProperty("FalseOriginX").equals(""))
					|| (myNodeAttributes.getProperty("FalseOriginX") == null)) { // default
																																				// to
																																				// none
				FalseOriginX = 0;
			} else {
				FalseOriginX = Double.parseDouble(myNodeAttributes
						.getProperty("FalseOriginX"));
			}
			if ((myNodeAttributes.getProperty("FalseOriginY").equals(""))
					|| (myNodeAttributes.getProperty("FalseOriginY") == null)) { // default
																																				// to
																																				// none
				FalseOriginY = 0;
			} else {
				FalseOriginY = Double.parseDouble(myNodeAttributes
						.getProperty("FalseOriginY"));
			}

			if ((myNodeAttributes.getProperty("Precision").equals(""))
					|| (myNodeAttributes.getProperty("Precision") == null)) { // default
																																		// to no
																																		// change
				Precision = 0;
			} else {
				Precision = Long.parseLong(myNodeAttributes.getProperty("Precision"));
			}

		} catch (RuntimeException e) {
			throw new RIMapperException("[ParseXML.getRIM error] " + e.toString());
		}
	}

	/**
	 * Get the names of XML TITLE node (concatenates if more nodes).
	 * 
	 * </br>void,sets instance field {@link #title title}
	 * 
	 * @param XMLtree
	 *          the Document Tree in memory as parsed by
	 *          {@link #getXMLtree getXMLtree}
	 */
	public void getTitle(Document XMLtree) throws RIMapperException {

		try {
			NodeList titleElements = XMLtree.getElementsByTagName("TITLE");
			int nrTitles = titleElements.getLength(); // should be only one, but just
			// concat if more...
			for (int i = 0; i < nrTitles; i++) {
				Node n = titleElements.item(i);
				if (n == null) {
					throw new RIMapperException("[parseXML] Cannot parse TITLE.");
				}
				title = title + readString(n);
			}
		} catch (RuntimeException e) {
			throw new RIMapperException("[getTitle RUNTIME ERROR] " + e.toString());
		}
	}

	/**
	 * Get the names of XML AUTHOR node (concatenates if more nodes).
	 * 
	 * </br>returns nothing,sets instance field {@link #title title}
	 * 
	 * @param XMLtree
	 *          the Document Tree in memory as parsed by
	 *          {@link #getXMLtree getXMLtree}
	 */
	public void getAuthor(Document XMLtree) throws RIMapperException {

		try {
			NodeList authorElements = XMLtree.getElementsByTagName("AUTHOR");
			int nrAuthors = authorElements.getLength();
			// should be only one, but just concat if more...
			for (int i = 0; i < nrAuthors; i++) {
				Node n = authorElements.item(i);
				if (n == null) {
					throw new RIMapperException("[parseXML] Cannot parse AUTHOR.");
				}
				author = author + readString(n);
			}
		} catch (RuntimeException e) {
			throw new RIMapperException("[getTitle RUNTIME ERROR] " + e.toString());
		}
	}

	/**
	 * Get the names and style strings (if in XML) of XML STYLEs node.
	 * 
	 * </br>returns nothing,sets instance fields {@link #nrStyles nrStyles},
	 * {@link #styleNames styleNames},{@link #styles styles}
	 * 
	 * @param XMLtree
	 *          the Document Tree in memory as parsed by
	 *          {@link #getXMLtree getXMLtree}
	 */
	public void getStyles(Document XMLtree) throws RIMapperException {

		try {
			NodeList StyleElements = XMLtree.getElementsByTagName("STYLE");
			nrStyles = StyleElements.getLength();

			if (nrStyles <= 0 || StyleElements == null) {
				throw new RIMapperException("[parseXML] No STYLEs defined in RIM XML. ");
			}
			styleNames = new String[nrStyles];
			styles = new String[nrStyles];
			for (int i = 0; i < nrStyles; i++) {
				Node n = StyleElements.item(i);
				if (n == null) {
					throw new RIMapperException("[parseXML] Cannot parse STYLE "
							+ (i + 1) + ".");
				}
				Properties myNodeAttributes = getNodeAttributes(n);
				if (myNodeAttributes.getProperty("NAME") == null) {
					throw new RIMapperException(
							"[parseXML] Invalid NAME attribute (of STYLE " + (i + 1) + ").");
				}
				styleNames[i] = myNodeAttributes.getProperty("NAME");
				if (myNodeAttributes.getProperty("DBID") != null) {
					if (myNodeAttributes.getProperty("DBID").equals("none")) {
						// get fragment from XML file iso DB
						styles[i] = readString(n);
					}
				}
			}
		} catch (RuntimeException e) {
			throw new RIMapperException("[getStyles RUNTIME ERROR] " + e.toString());
		}
	}

	/**
	 * Get the names and Fragment strings (if in XML) of XML FRAGMENT nodes.
	 * 
	 * </br>returns nothing,sets instance fields {@link #nrFragments nrFragments},
	 * {@link #fragmentNames fragmentNames}, {@link #fragments fragments}
	 * 
	 * @param XMLtree
	 *          the Document Tree in memory as parsed by
	 *          {@link #getXMLtree getXMLtree}
	 */
	public void getFragments(Document XMLtree) throws RIMapperException {

		try {
			NodeList fragmentElements = XMLtree.getElementsByTagName("FRAGMENT");
			nrFragments = fragmentElements.getLength();

			fragmentNames = new String[nrFragments];
			fragments = new String[nrFragments];
			fragmentTypes = new String[nrFragments];
			for (int i = 0; i < nrFragments; i++) {
				Node n = fragmentElements.item(i);
				if (n == null) {
					throw new RIMapperException("[parseXML] Cannot parse FRAGMENT "
							+ (i + 1) + ".");
				}
				Properties myNodeAttributes = getNodeAttributes(n);
				if (myNodeAttributes.getProperty("NAME") == null) {
					throw new RIMapperException(
							"[parseXML] Invalid NAME attribute (of FRAGMENT " + (i + 1)
									+ ").");
				}
				fragmentNames[i] = myNodeAttributes.getProperty("NAME");
				if (myNodeAttributes.getProperty("DBID") != null) {
					if (myNodeAttributes.getProperty("DBID").equals("none")) {
						// get fragment from XML file iso DB
						fragments[i] = readString(n);
					}
				}
				if (myNodeAttributes.getProperty("TYPE") == null) {
					throw new RIMapperException(
							"[parseXML] Invalid TYPE attribute (of FRAGMENT " + (i + 1)
									+ ").");
				}
				fragmentTypes[i] = myNodeAttributes.getProperty("TYPE");
				if (fragmentTypes[i].equals("SVG_ROOT")) {
					SVGRootFragmentName = fragmentNames[i];
					SVGRootFragment = fragments[i];
				}
			} // loop nrFragments
		} catch (RuntimeException e) {
			throw new RIMapperException("[ParseXML.getFragments RUNTIME ERROR] "
					+ e.toString());
		}
	}

	/**
	 * 
	 * 
	 * </br>returns nothing,sets instance fields
	 * {@link #layerActionScopes layerActionScopes},
	 * {@link #layerActions layerActions}
	 * 
	 * @param currentLayer
	 * @param currentLayerChildren
	 * @throws RIMapperException
	 */
	public void getActions(int currentLayer, NodeList currentLayerChildren)
			throws RIMapperException {

		try {
			Node n;
			String nameStr = null;
			String eventStr = null;
			String paramsStr = null;
			nrLayerActions[currentLayer] = 0;

			for (int i = 0; i < currentLayerChildren.getLength(); i++) {
				n = currentLayerChildren.item(i);
				if (n == null) {
					throw new RIMapperException(
							"[parseXML] Cannot parse ACTION in LAYER " + (currentLayer + 1)
									+ ".");
				}
				if (n.getNodeName().equals("ACTION")) {
					nrLayerActions[currentLayer]++;
					if (nrLayerActions[currentLayer] > MAX_ACTIONS_PER_LAYER) {
						throw new RIMapperException("[parseXML] Too many ACTIONs in LAYER "
								+ (currentLayer + 1) + " (max = 5).");
					}
					Properties myNodeAttributes = getNodeAttributes(n);
					if (myNodeAttributes.getProperty("SCOPE") == null) {
						throw new RIMapperException(
								"[parseXML] Invalid SCOPE attribute (of ACTION in LAYER "
										+ (currentLayer + 1) + ".");
					}
					layerActionScopes[currentLayer][nrLayerActions[currentLayer] - 1] = myNodeAttributes
							.getProperty("SCOPE");
					if (myNodeAttributes.getProperty("NAME") == null) {
						throw new RIMapperException(
								"[parseXML] Invalid NAME attribute (of ACTION in LAYER "
										+ (currentLayer + 1) + ".");
					}
					nameStr = myNodeAttributes.getProperty("NAME");
					if (myNodeAttributes.getProperty("EVENT") == null) {
						throw new RIMapperException(
								"[parseXML] Invalid EVENT attribute (of ACTION in LAYER "
										+ (currentLayer + 1) + ".");
					}
					eventStr = myNodeAttributes.getProperty("EVENT");

					if (myNodeAttributes.getProperty("PARAMS") == null) {
						throw new RIMapperException(
								"[parseXML] Invalid PARAMS attribute (of ACTION in LAYER "
										+ (currentLayer + 1) + ".");
					}
					paramsStr = myNodeAttributes.getProperty("PARAMS");

					layerActions[currentLayer][nrLayerActions[currentLayer] - 1] = eventStr
							+ "=\"" + nameStr + "(" + paramsStr + ")\"";
				}
			}
		} catch (RuntimeException e) {
			throw new RIMapperException("[getActions RUNTIME ERROR] " + e.toString());
		}
	}

	/**
	 * Get the names of XML LAYERs node.
	 * 
	 * @param XMLtree
	 *          the Document Tree in memory as parsed by
	 *          {@link #getXMLtree getXMLtree} returns nothing (sets instance
	 *          fields {@link #nrLayers nrLayers}, {@link #layerNames layerNames},
	 *          {@link #layerActionScopes layerActionScopes},
	 *          {@link #layerActions layerActions}
	 *          {@link #layerStyles layerStyles},
	 *          {@link #layerStyleTypes layerStyleTypes})
	 */
	public void getLayers(Document XMLtree) throws RIMapperException {

		try {
			NodeList layerElements = XMLtree.getElementsByTagName("LAYER");
			NodeList layerChildren;
			Node n;
			nrLayers = layerElements.getLength();

			if (nrLayers <= 0 || layerElements == null) {
				throw new RIMapperException("[parseXML] No LAYERs defined in RIM XML. ");
			}
			layerNames = new String[nrLayers];
			nrLayerActions = new int[nrLayers];
			layerActions = new String[nrLayers][MAX_ACTIONS_PER_LAYER];
			layerActionScopes = new String[nrLayers][MAX_ACTIONS_PER_LAYER];
			layerStyles = new String[nrLayers];
			layerStyleTypes = new String[nrLayers];
			layerAttribs = new String[nrLayers];
			for (int i = 0; i < nrLayers; i++) {
				n = layerElements.item(i);
				if (n == null) {
					throw new RIMapperException("[parseXML] Cannot parse LAYER "
							+ (i + 1) + ".");
				}
				Properties myNodeAttributes = getNodeAttributes(n);
				layerStyleTypes[i] = myNodeAttributes.getProperty("STYLETYPE");
				layerStyles[i] = myNodeAttributes.getProperty("STYLE");
				if (layerStyleTypes[i] == null) {
					throw new RIMapperException(
							"[parseXML] Invalid STYLETYPE attribute (of LAYER " + (i + 1)
									+ ").");
				}
				if (layerStyleTypes[i].equals("single")) {
					if (layerStyles[i] == null) {
						throw new RIMapperException(
								"[parseXML] Missing/invalid STYLE attribute (of LAYER "
										+ (i + 1) + ").");
					}
				}

				if (myNodeAttributes.getProperty("NAME") == null) {
					throw new RIMapperException(
							"[parseXML] Invalid NAME attribute (of LAYER " + (i + 1) + ").");
				}
				layerNames[i] = myNodeAttributes.getProperty("NAME");

				if (myNodeAttributes.getProperty("ATTRIBS") != null) {
					layerAttribs[i] = myNodeAttributes.getProperty("ATTRIBS");
				}
				layerChildren = n.getChildNodes();
				if (layerChildren != null) {
					getActions(i, layerChildren);
				}
			}
		} catch (RuntimeException e) {
			throw new RIMapperException("[getlayerNames RUNTIME ERROR] "
					+ e.toString());
		}
	}

	/**
	 * Parses XML file and returns the document tree.
	 * 
	 * @param XMLfile
	 *          the URL or file path
	 * @param validate
	 *          sets DTD validation on/off returns the document tree
	 */
	public void getXMLtree(String XMLfile, boolean validate)
			throws RIMapperException {

		ErrorHandler parseErrorHandler = new ErrorHandler() {
			// inner class to register Parser errorhandler
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

			DOMParser parser = (DOMParser) Class.forName(DEFAULT_PARSER_NAME)
					.newInstance();
			if (validate) {
				parser.setFeature(VALIDATION_URL, true);
			} else {
				parser.setFeature(VALIDATION_URL, false);
			}

			parser.setErrorHandler(parseErrorHandler);
			parser.parse(XMLfile);

			XMLtree = parser.getDocument();
		}

		catch (org.xml.sax.SAXParseException e) {
			throw new RIMapperException("[XML PARSER ERROR] " + e.getMessage());
		} catch (org.xml.sax.SAXException e) {
			throw new RIMapperException("[SAX ERROR] " + e.getMessage());
		} catch (Exception e) {
			throw new RIMapperException("[UNEXPECTED ERROR] " + e.toString());
		}

	} // of method getXMLtree
}
