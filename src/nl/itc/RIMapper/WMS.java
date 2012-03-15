package nl.itc.RIMapper;

import java.io.IOException;
import java.io.PrintWriter;

//import com.mysql.jdbc.Util;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.w3c.dom.*;

/**
 * OGC compatible Web Map Service. Converts SFS OGC geometry columns (eg from
 * PostgreSQL/PostGIS) to Scalable Vector Graphics (SVG 1.1) map output, with a
 * built-in SVG GUI (if getGUI=true in request). <br/>
 * Can transcode result to PNG/JPEG for WMS compatibility. <br/>
 * Uses Batik SVG toolkit for transcoding (<a
 * href="http://xmlgraphics.apache.org/batik/"
 * target="_blank">http://xmlgraphics.apache.org/batik/</a>) <br/>
 * Uses Java Topology Suite (JTS 1.11) (<a
 * href="http://sourceforge.net/projects/jts-topo-suite/"
 * target="_blank">http://sourceforge.net/projects/jts-topo-suite/</a>)<br/>
 * <br/>
 * &copy;2004-2010 International Institute for Geo-information Science and Earth
 * Observation (ITC) <br/>
 * Licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 2.5
 * License. see <a href="http://creativecommons.org/licenses/by-nc-sa/2.5/"
 * target="_blank"> http://creativecommons.org/licenses/by-nc-sa/2.5/</a>
 * 
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl"
 *         target="_blank">kobben@itc.nl</a>
 * @version 2.0 [Sep 2010]
 */

// Major changes:
// 1.0b [Dec 2006] - first released version (public beta)
// 2.0 [Sep 2010]:
// - using instantiated iso static WMSCapabilities
// - upgrade to JTS 1.11 (now on sourceforge)
// - cleaned up exceptions reporting, to better match WMS 1.1.1 standard
// - output to PNG/JPEG via Batik transcoder
public class WMS extends HttpServlet {

  private String ResponseType = null;
  private String ExceptionsResponseType = null;

  private static final long serialVersionUID = 1L;

  /**
   * Just use constructor of superclass HttpServlet.
   */
  public WMS() {
    super();
  }

  /**
   * Destruction of the servlet. Just puts "destroy" string in log.
   */
  @Override
  public void destroy() {
    super.destroy(); // 
  }

  /**
   * The doGet method of the servlet. <br/>
   * <ol>
   * <li>Gets the WMS instance data from wms_instance.xml
   * <li>Opens a database connection for this instance (using the {@link DBconn}
   * class).
   * <li>builds a Capabilities XML doc for this WMS instance (if needed). <br/>
   * This uses a rather primitive way of finding if it's instantiated already.
   * Eg. fails to detect changes in WMS config when "hotswapping" the DB.
   * <li>calls {@link WMSRequest} to parse parameters
   *<li>asks {@link WMSGetMap} or {@link WMSGetCapabilities} for further
   * processing
   * <li>forwards output to servlet reponse object.
   * </ol>
   * 
   * @param request
   *          the request send by the client to the server
   * @param response
   *          the response send by the server to the client
   * @throws ServletException
   *           if an error occurred
   * @throws IOException
   *           if an error occurred
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    StringBuffer mySVGOutput = new StringBuffer();
    // StringBuffer myOutput = new StringBuffer();
    ResponseType = Utils.XHTML_MIME_TYPE;// default output type
    //the path below will be the 'official' path as advertised by Tomcat 
    //with possible inclusion of proxyName & proxyPort (as set in conf/server.xml)
    String BasePath = request.getScheme() + "://" + request.getServerName() 
      + ":" + request.getServerPort() + request.getContextPath();
    String WMSPath = BasePath + "/WMS?";
    //below is the "real" path (without proxy)
    String LocalBasePath = request.getScheme() + "://" + request.getLocalName() 
      + ":" + request.getLocalPort() + request.getContextPath();

    try {
      // get DB connection params from WMS.xml instance XML file
      String xmlFile = LocalBasePath + "/wms_instance.xml";
      Document myWMSinstance = Utils.getXMLtree(xmlFile, false);
      myWMSinstance.normalize();
      String mySFS = Utils.getTagFromXML(myWMSinstance, "SFSserver");
      String myHOST = Utils.getTagFromXML(myWMSinstance, "host");
      String myDB = Utils.getTagFromXML(myWMSinstance, "database");
      String myUN = Utils.getTagFromXML(myWMSinstance, "username");
      String myPWEnc = Utils.getTagFromXML(myWMSinstance, "password");
      Crypt myCrypt = new Crypt("myRIMapperKey"); // Instanciate Crypt class;
      String myPWPlain = myCrypt.decrypt(myPWEnc); // de-encrypt PW

      ResponseType = Utils.XHTML_MIME_TYPE; // until we find out what FORMAT is
      ExceptionsResponseType = Utils.OGC_EXCEPTION_XML_MIME_TYPE;// same for EXPECTIONSFORMAT

      DBconn myDBconn = new DBconn();
      if (myDBconn.Open(mySFS, myHOST, myDB, myUN, myPWPlain)) {
        // determine Capabilities
        WMSCapabilities myWMSCapabilities = new WMSCapabilities(myDBconn);
        // collect parameters
        WMSRequest myWMSRequest = new WMSRequest(request.getParameterMap(), WMSPath, myWMSCapabilities);
        // perform request
        if (myWMSRequest.getRequest().equalsIgnoreCase("GetMap")) {
          ResponseType = myWMSRequest.getFormat();
          ExceptionsResponseType = myWMSRequest.getExceptionsFormat();
          WMSGetMap myWMSGetMap = new WMSGetMap(myWMSCapabilities);
          mySVGOutput.append(myWMSGetMap.doGetMap(myDBconn, myWMSRequest));
          if (myWMSRequest.getFormat().equalsIgnoreCase(Utils.PNG_MIME_TYPE)
              || myWMSRequest.getFormat().equalsIgnoreCase(Utils.JPEG_MIME_TYPE)) {
            SVGRasterizer myRasterizer = new SVGRasterizer();
            ServletOutputStream outBin = response.getOutputStream();
            myRasterizer.doSVGRasterize(mySVGOutput, myWMSRequest.getFormat(), myWMSRequest.getWidth(), myWMSRequest
                .getHeight(), outBin);
            response.setContentType(ResponseType);
            outBin.flush();
            outBin.close();
          }
        } else if (myWMSRequest.getRequest().equalsIgnoreCase("GetCapabilities")) {
          ResponseType = Utils.CAPABILITIES_MIME_TYPE;
          ExceptionsResponseType = Utils.OGC_EXCEPTION_XML_MIME_TYPE;
          WMSGetCapabilities myWMSGetCapabilities = new WMSGetCapabilities(myWMSCapabilities);
          mySVGOutput.append(myWMSGetCapabilities.doGetCapabilities(LocalBasePath, WMSPath));
        }
        if (myDBconn != null)
          myDBconn.Close(mySFS, myHOST);
      } // if DBconn...

      // / CATCH BLOCK:

    } catch (RIMapperException e) {
      mySVGOutput.delete(0, mySVGOutput.length()); // empty first
      mySVGOutput.append(Utils.MakeError("[WMS]", e.getMessage(), ResponseType, ExceptionsResponseType));
      if (ResponseType.equalsIgnoreCase(Utils.JPEG_MIME_TYPE) || ResponseType.equalsIgnoreCase(Utils.PNG_MIME_TYPE)) {
        if (ExceptionsResponseType.equalsIgnoreCase(Utils.OGC_EXCEPTION_XML_MIME_TYPE)) {
          response.reset(); // get rid of Binary OutputStream
          ResponseType = Utils.OGC_EXCEPTION_XML_MIME_TYPE;
        } else { // else error is returned inImage, ie. the format originally
                 // requested...
          try {
            SVGRasterizer myRasterizer = new SVGRasterizer();
            response.reset(); // get rid of original Binary OutputStream
            ServletOutputStream outBin = response.getOutputStream();
            myRasterizer.doSVGRasterize(mySVGOutput, ResponseType, 320, 320, outBin);
            outBin.flush();
            outBin.close();
          } catch (RIMapperException e1) {
            response.reset(); // get rid of original Binary OutputStream
            response.setContentType(Utils.XHTML_MIME_TYPE);
            mySVGOutput.delete(0, mySVGOutput.length()); // empty first
            mySVGOutput.append(Utils.MakeError("[WMS]",
                "**Unexpected Exception** Failed to generate Exception as Image: " + e1.toString(),
                Utils.XHTML_MIME_TYPE, Utils.OGC_EXCEPTION_XML_MIME_TYPE));
          }
        }
      }
    } catch (RuntimeException e) {
      mySVGOutput.delete(0, mySVGOutput.length()); // empty first
      mySVGOutput.append(Utils.MakeError("[WMS]", "**RuntimeException**: " + e.toString(), ResponseType,
          ExceptionsResponseType));
    } catch (Exception e) {
      mySVGOutput.delete(0, mySVGOutput.length()); // empty first
      mySVGOutput.append(Utils.MakeError("[WMS]", "**Unexpected Exception**: " + e.toString(), ResponseType,
          ExceptionsResponseType));
      // } catch (Throwable e) {
      // WMSOutput.delete(0, WMSOutput.length()); // empty first
      // WMSOutput.append(Utils.MakeError("[WMS]", "**Throwable Exception**: "
      // + e.toString(), ResponseType));
    } finally {
      response.setContentType(ResponseType);
      if (ResponseType.equalsIgnoreCase(Utils.PNG_MIME_TYPE) || ResponseType.equalsIgnoreCase(Utils.JPEG_MIME_TYPE)) {
        // do nothing;
      } else { // XML-XHTML-etcetera
        PrintWriter outText = response.getWriter();
        outText.print(mySVGOutput);
        outText.flush();
        outText.close();
      }
    }
  }

  /**
   * doPost just redirects to doGet <br>
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doGet(request, response);
  }

  /**
   * Initialization of the servlet. <br>
   * 
   * @throws ServletException
   *           if an error occures
   */
  @Override
  public void init() throws ServletException {
    // Put your code here
  }

}
