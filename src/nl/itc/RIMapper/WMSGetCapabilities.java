/**
 * 
 */
package nl.itc.RIMapper;

import java.io.IOException;
import java.io.StringWriter;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.vividsolutions.jts.geom.Envelope;

/**
 * 
 * Handles a WMS 1.1.1 GetCapabilities request.<br/>
 * &copy;2004-2011 International Institute for Geo-information Science and Earth
 * Observation (ITC) <br/>Licensed under a Creative Commons
 * Attribution-NonCommercial-ShareAlike 2.5 License. see <a
 * href="http://creativecommons.org/licenses/by-nc-sa/2.5/" target="_blank">
 * http://creativecommons.org/licenses/by-nc-sa/2.5/</a>
 * 
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl"
 *         target="_blank">kobben@itc.nl</a>
 * @version 2.2 [Sep 2011]
 */

// Major changes:
// 1.0b [Dec 2006] - first released version (public beta)
// 2.0 [Sep 2010] - using instantiated iso static WMSCapabilities 
// 2.2 [sep 2011]- now puts cascaded=0||1 in layer attribs

public class WMSGetCapabilities {

	private Document XMLCapabilities;
	
	private WMSCapabilities myWMSCapabilities;

	/**
	 * Constructor
	 */
	public WMSGetCapabilities(WMSCapabilities theWMSCapabilities) {
		XMLCapabilities = null;
		myWMSCapabilities = theWMSCapabilities;
	}

	

	// recursive version, finds & sets all occurences
	public void setXMLValue(Node StartNode, String NodeName, String theValue)
			throws IOException {

		if (StartNode.getNodeName().equals(NodeName)) {
			StartNode.getFirstChild().setNodeValue(theValue);
		}
		if (StartNode.hasChildNodes()) {
			Node firstChild = StartNode.getFirstChild();
			setXMLValue(firstChild, NodeName, theValue);
		}
		Node nextNode = StartNode.getNextSibling();
		if (nextNode != null)
			setXMLValue(nextNode, NodeName, theValue);
	}

	// non-recursive version
	public void setXMLValue(Node thisNode, String theValue) throws IOException {
		thisNode.getFirstChild().setNodeValue(theValue);
	}

	// recursive version, finds & sets all occurences
	public void setXMLAttribute(Node StartNode, String NodeName, String theAttribute,
			String theValue) throws IOException {
		if (StartNode.getNodeName().equals(NodeName)) {
			Element searchElement = (Element) StartNode;
			searchElement.setAttribute(theAttribute, theValue);
		}
		if (StartNode.hasChildNodes()) {
			Node firstChild = StartNode.getFirstChild();
			setXMLAttribute(firstChild, NodeName, theAttribute, theValue);
		}
		Node nextNode = StartNode.getNextSibling();
		if (nextNode != null)
			setXMLAttribute(nextNode, NodeName, theAttribute, theValue);
	}

	// none-recursive version
	public void setXMLAttribute(Node thisNode, String theAttribute, String theValue)
			throws IOException {

		Element searchElement = (Element) thisNode;
		searchElement.setAttribute(theAttribute, theValue);
	}

	private Node getNode(String NodeName) throws RIMapperException {
		NodeList Elements = XMLCapabilities.getElementsByTagName(NodeName);
		Node n = null;
		if (Elements.getLength() == 1) {
			// should only be 1, otherwise use getNode(parentNodename, NodeName)
			n = Elements.item(0);
			if (n == null) {
				throw new RIMapperException("[GetNode] Cannot parse " + NodeName
						+ " from CapabilitiesTemplate.");
			}
		}
		return n;
	}

	private Node addXMLNode(Node startNode, String NodeName, String NodeValue)
			throws DOMException {
		Node newNode = XMLCapabilities.createElement(NodeName);
		startNode.appendChild(newNode);
		if (NodeValue != null) {
			Node newTextNode = XMLCapabilities.createTextNode(NodeValue);
			newNode.appendChild(newTextNode);
		}
		return newNode;
	}

	private void setBBoxAttribs(Node thisNode, Envelope BBox) throws IOException {
		String TS = "" + BBox.getMinX();
		setXMLAttribute(thisNode, "minx", TS);
		TS = "" + BBox.getMinY();
		setXMLAttribute(thisNode, "miny", TS);
		TS = "" + BBox.getMaxX();
		setXMLAttribute(thisNode, "maxx", TS);
		TS = "" + BBox.getMaxY();
		setXMLAttribute(thisNode, "maxy", TS);
	}

	/**
	 * Gets the necessary data out of {@link WMSCapabilities} class and adds the required XML nodes to the 
	 * skeleton capabilitiesTemplate.xml.
	 * @param BasePath the URL of the (TomCat) server
	 * @param WMSPath the URL of the WMS instance 
	 * @return a StringBuffer with the standardized capabilities XML 
	 * @throws RIMapperException
	 */
	public StringBuffer doGetCapabilities(String BasePath, String WMSPath)
			throws RIMapperException {
		StringBuffer S = new StringBuffer();
		int numLayers = 0;
		int L = 0;
		String TS = "";
		String[] T = null;

		try {
			numLayers = myWMSCapabilities.getNumLayers();

			// build DOMtree from template & validate
			String xmlFile = BasePath + "/CapabilitiesTemplate.xml";
			XMLCapabilities = Utils.getXMLtree(xmlFile, false);
			if (XMLCapabilities != null) {
			  XMLCapabilities.normalize();
			} else {
			  throw new RIMapperException("[DEBUG] Cannot parse capabilities template: " + XMLCapabilities);
			}

			Node startNode = null;

			startNode = getNode("Service");
			setXMLValue(startNode, "Name", "OGC:WMS");
			setXMLValue(startNode, "Title", myWMSCapabilities.getMetadata_title());
			setXMLValue(startNode, "Abstract", myWMSCapabilities.getMetadata_abstract());
			setXMLValue(startNode, "Fees", myWMSCapabilities.getMetadata_fees());
			setXMLValue(startNode, "AccessConstraints",
					myWMSCapabilities.getMetadata_access_constraints());
			setXMLValue(startNode, "ContactElectronicMailAddress",
					myWMSCapabilities.getMetadata_contact_electronic_mail_address());
			// sets all 'OnlineResource' occurences
			setXMLAttribute(startNode, "OnlineResource", "xlink:href", WMSPath);

			startNode = getNode("KeywordList");
			String ServiceKeywordList[] = myWMSCapabilities.getMetadata_keyword_list();
			for (int i = 0; i < ServiceKeywordList.length; i++) {
				addXMLNode(startNode, "Keyword", ServiceKeywordList[i]);
			}

			startNode = getNode("Request");
			// (re)sets 'OnlineResource' occurences in Request section only
			setXMLAttribute(startNode, "OnlineResource", "xlink:href", WMSPath);

			startNode = getNode("Layer");// all layers are children of this (empty) root layer
			for (int i = 0; i < numLayers; i++) {
				Node layerNode = null; // use for layer
				Node layerChildNode = null; // use for children
				layerNode = addXMLNode(startNode, "Layer", null);
				setXMLAttribute(layerNode, "queryable", myWMSCapabilities.getLayer_queryable()[i]);
        setXMLAttribute(layerNode, "opaque", myWMSCapabilities.getLayer_opaque()[i]);
        setXMLAttribute(layerNode, "cascaded", myWMSCapabilities.getLayer_cascaded()[i]);
				layerChildNode = addXMLNode(layerNode, "Name", myWMSCapabilities.getLayer_name()[i]);
				layerChildNode = addXMLNode(layerNode, "Title", myWMSCapabilities.getLayer_title()[i]);
				layerChildNode = addXMLNode(layerNode, "Abstract",
						myWMSCapabilities.getLayer_abstract()[i]);
				layerChildNode = addXMLNode(layerNode, "KeywordList", null);
				T = myWMSCapabilities.getLayer_keyword_list()[i];
				if (T != null) {
					L = T.length;
					for (int j = 0; j < L; j++) {
						addXMLNode(layerChildNode, "Keyword",
								myWMSCapabilities.getLayer_keyword_list()[i][j]);
					}
				} // layer_keyword_list
				
				//added 1.1: dimension (time)
        if (myWMSCapabilities.getLayer_dimension()[i] != null) {
          if (myWMSCapabilities.getLayer_dimension()[i].equalsIgnoreCase("time")) { //only time dimension supported for now
            layerChildNode = addXMLNode(layerNode, "Dimension", null);
            setXMLAttribute(layerChildNode, "Dimension", "name",  myWMSCapabilities.getLayer_dimension()[i]);
            setXMLAttribute(layerChildNode, "Dimension",  "units",  "ISO8601");
            layerChildNode = addXMLNode(layerNode, "Extent", myWMSCapabilities.getLayer_extent()[i]);
            setXMLAttribute(layerChildNode, "Extent", "default",  myWMSCapabilities.getLayer_extent()[i]); //default is full extent
          }
        }
        
				T = myWMSCapabilities.getLayer_srs_epsg_list()[i];
				if (T != null) {
					L = T.length;
					for (int j = 0; j < L; j++) {
						layerChildNode = addXMLNode(layerNode, "SRS", "EPSG:"
								+ myWMSCapabilities.getLayer_srs_epsg_list()[i][j]);
					}
					Envelope BBox = myWMSCapabilities.getLayer_lat_lon_bounding_box()[i];
					layerChildNode = addXMLNode(layerNode, "LatLonBoundingBox", null);
					setBBoxAttribs(layerChildNode, BBox);
					for (int j = 0; j < L; j++) {
						BBox = myWMSCapabilities.getLayer_bounding_box_list()[i][j];
						layerChildNode = addXMLNode(layerNode, "BoundingBox", null);
						setXMLAttribute(layerChildNode, "SRS",
								myWMSCapabilities.getLayer_srs_epsg_list()[i][j]);
						setBBoxAttribs(layerChildNode, BBox);
					}
				} // layer_epsg_list
				T = myWMSCapabilities.getLayer_style_list()[i];
				if (T != null) {
					L = T.length;
					for (int j = 0; j < L; j++) {
						layerChildNode = addXMLNode(layerNode, "Style", null);
						TS = myWMSCapabilities.getLayer_style_list()[i][j];
						if (TS != null) {
							for (int z = 0; z < myWMSCapabilities.getStyle_name().length; z++) {
								if (TS.equals(myWMSCapabilities.getStyle_name()[z])) {
									addXMLNode(layerChildNode, "Name", myWMSCapabilities.getStyle_name()[z]);
									addXMLNode(layerChildNode, "Title", myWMSCapabilities.getStyle_title()[z]);
									addXMLNode(layerChildNode, "Abstract",
											myWMSCapabilities.getStyle_abstract()[z]);
									if (myWMSCapabilities.getStyle_legend_url_online_resource()[z] != null) {
										Node legendNode = addXMLNode(layerChildNode, "LegendURL", null);
										setXMLAttribute(legendNode, "width",
												myWMSCapabilities.getStyle_legend_url_width()[z]);
										setXMLAttribute(legendNode, "height",
												myWMSCapabilities.getStyle_legend_url_height()[z]);
										addXMLNode(legendNode, "Format",
												myWMSCapabilities.getStyle_legend_url_format()[z]);
										Node onlineResourceNode = addXMLNode(legendNode, "OnlineResource",
												null);
										setXMLAttribute(onlineResourceNode, "xmlns:xlink",
												"http://www.w3.org/1999/xlink");
										setXMLAttribute(onlineResourceNode, "xlink:type", "simple");
										setXMLAttribute(onlineResourceNode, "xlink:href",
												myWMSCapabilities.getStyle_legend_url_online_resource()[z]);
									}
								}
							}
						}
					}
				} // layer_styles
				TS = myWMSCapabilities.getLayer_scale_hint()[i];
				if (TS != null) {
					T = TS.split(";");
					L = T.length;
					layerChildNode = addXMLNode(layerNode, "ScaleHint", null);
					setXMLAttribute(layerChildNode, "min", T[0]);
					setXMLAttribute(layerChildNode, "max", T[1]);
				} // layer_scale_hint
			}// for numlayers...

			StringWriter out = new StringWriter();
			OutputFormat format = new OutputFormat(XMLCapabilities);
			format.setIndenting(true);
			XMLSerializer output = new XMLSerializer(out, format);
			output.serialize(XMLCapabilities);
			out.flush();
			S = out.getBuffer();
			out.close();
		} catch (IOException e) {
			throw new RIMapperException(
					"[doGetCapabilities] Cannot serialize myWMSCapabilities XML: " + e.getMessage());
		} catch (RuntimeException e) {
			throw new RIMapperException("[doGetCapabilities] **RuntimeException**: "
					+ e.toString());
		}

		return S;
	}
}