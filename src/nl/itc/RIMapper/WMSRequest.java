/**
 * 
 */
package nl.itc.RIMapper;

import java.util.HashMap;
import java.util.Map;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Class for storing an OGC WMS 1.1.1 request.<br/>
 *  Note that the class holds the data needed for an OGC compatible
 *  GetMap or GetCapabilities, PLUS extras needed for RIMapperWMS, in the 
 *  VendorSpecific GETGUI and ANIMDUR.<br/>
 * &copy;2006-2011 International Institute for Geo-information Science and Earth
 * Observation (ITC) <br/>
 * Licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 2.5
 * License. see <a href="http://creativecommons.org/licenses/by-nc-sa/2.5/"
 * target="_blank"> http://creativecommons.org/licenses/by-nc-sa/2.5/</a>
 * 
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl" target="_blank">
 *         kobben@itc.nl </a>
 * @version 2.2 [September 2011]
 */
//
// Major changes:
// 1.0 b: first public beta version version based on old XML2SVG servlet code
// 2.0 [Sep 2010] - using instantiated iso static WMSCapabilities
// 2.2 [Sep 2011] - deal with optional TIME parameter 
//                - deal with optional ANIMDUR parameter (VendorSpecific)
// 
public class WMSRequest {

  public static final String[] SUPPORTED_WMS_VERSIONS = {"1.1.0", "1.1.1"}; //$NON-NLS-1$ //$NON-NLS-2$

  private int NumLayers;

  private String LayersList;

  private String StylesList;

  private String[] Layers;

  private String[] Styles;

  private String SRS;

  private String WMShost;

  private String Service;

  private String Version;

  private String Request;

  private String ExceptionsFormat;

  private boolean defaultExtent;

  private Envelope BBOX;

  private String BBOXstr;

  private int Width;

  private int Height;

  private String Format;
  
  private WMSTime Time;

  private boolean getGUI;
  
  private long AnimDur;

  private static final String[] requiredGetCapabilitiesParams = new String[]{
    "SERVICE", "REQUEST", "VERSION"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

  private static final String[] requiredGetMapParams = new String[]{
    "SERVICE", "REQUEST", "VERSION", "LAYERS", "STYLES", "SRS", "BBOX", "WIDTH", "HEIGHT", "FORMAT"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$

  private static final String[] optionalGetMapParams = new String[]{
    "GETGUI", "TRANSPARENT", "BGCOLOR", "EXCEPTIONS", "TIME", "ELEVATION", "SLD", "WFS", "ANIMDUR" //$NON-NLS-1$ 
  };

  public HashMap<String, String[]> requestMap = new HashMap<String, String[]>();
  // leaves multiple parameters (eg layers) in a stringList,
  // to be picked apart later...

  private void checkRequiredParams(String[] theParams) throws RIMapperException {
    String K = null;
    // String[] V = null;
    for (int i = 0; i < theParams.length; i++) {
      K = theParams[i];
      if (requestMap.containsKey(K)) {
        // OK
      } else {
        // TODO: CHECK: According to 1.1.1 spec VERSION is required, but many UA
        // (eg. QGIS) don't provide VERSION, so we assume VERSION=1.1.1 if not
        // provided
        if (K.equalsIgnoreCase("VERSION")) {
          requestMap.put("VERSION", new String[]{"1.1.1"});
        } else {
          throw new RIMapperException(Messages.getString("WMSRequest.80") + K); //$NON-NLS-1$
        }
      }
    }
  }
  
  private String checkVersion(String theVersionRequested) throws RIMapperException {
    String tmpStr = ""; //$NON-NLS-1$
    for (int i = 0; i < SUPPORTED_WMS_VERSIONS.length; i++) {
      tmpStr += SUPPORTED_WMS_VERSIONS[i] + " ";
      if (theVersionRequested.equalsIgnoreCase(SUPPORTED_WMS_VERSIONS[i])) {
        return theVersionRequested;
      }
    }
    throw new RIMapperException("'VERSION=" + theVersionRequested //$NON-NLS-1$
        + Messages.getString("WMSRequest.79") + tmpStr); //$NON-NLS-1$ //$NON-NLS-2$
  }

  /**
   * Builds a WMS request object out of the request URL.
   * 
   * @param paramMap
   *          the parameters coming in from the servlet (POST or GET request)
   * @param WMSPath
   *          BaseURL of the WMS instance
   * @throws RIMapperException
   */
  public WMSRequest(Map<?, ?> paramMap, String WMSPath, WMSCapabilities myWMSCapabilities) throws RIMapperException {
    // constructor
    
    String paramCheck = "";

    if (!requestMap.isEmpty()) {
      requestMap.clear();
    }
    try {
      WMShost = WMSPath;
      Object[] myKeys = paramMap.keySet().toArray();
      Object[] myValues = paramMap.values().toArray();
      paramCheck = "";
      String tmpStr = null;
      String K = new String();
      String[] V = new String[25];
      int NumLayersAvailable = 0;
      String layerRequested = null;
      String layerFound = null;
      String[] SRSsfound = null;
      int numSRSs = 0;
      String[] Stylesfound = null;
      int numStyles = 0;
      String SRSrequested = null;

      for (int i = 0; i < myKeys.length; i++) {
        // harmonise on UpperCase because OGC says keys are caseINsensitive:
        K = myKeys[i].toString().toUpperCase();
        V = (String[]) myValues[i];
        requestMap.put(K, V);
      }
      
      paramCheck = "REQUEST";
      if (!requestMap.containsKey("REQUEST")) {
        throw new RIMapperException(Messages.getString("WMSRequest.85") 
            + " [path = " + WMSPath + "]"
            ); 
      } else {
        Request = requestMap.get("REQUEST")[0];
      }

      if (Request.equalsIgnoreCase("GetMap")) { //$NON-NLS-1$

        // ++++++++++++GETMAP+++++++++++++++++++++++++++++++++++++++++++++++++

        checkRequiredParams(requiredGetMapParams);
        // requiredWMSParams:
        // {""SERVICE", "REQUEST", "VERSION", "LAYERS", "STYLES", "SRS", "BBOX", "WIDTH", "HEIGHT", "FORMAT"}
        
        paramCheck = requiredGetMapParams[0];
        Service = requestMap.get(requiredGetMapParams[0])[0]; //$NON-NLS-1$
        if (!Service.equalsIgnoreCase("WMS")) { //$NON-NLS-1$
          throw new RIMapperException("'SERVICE=" + Service //$NON-NLS-1$
              + Messages.getString("WMSRequest.65")); //$NON-NLS-1$
        }
        
        paramCheck = requiredGetMapParams[2];
        Version = checkVersion(requestMap.get(requiredGetMapParams[2])[0]); //$NON-NLS-1$

        paramCheck = requiredGetMapParams[7];
        Width = Integer.parseInt(requestMap.get(requiredGetMapParams[7])[0]);
        
        paramCheck = requiredGetMapParams[8];
        Height = Integer.parseInt(requestMap.get(requiredGetMapParams[8])[0]);
       
        // optionalGetMapParams{ "GETGUI", "TRANSPARENT", "BGCOLOR",
        // "EXCEPTIONS", "TIME", "ELEVATION", "SLD", "WFS", "ANIMDUR"}
        
        // GETGUI param
        paramCheck = optionalGetMapParams[0];
        if (requestMap.get(optionalGetMapParams[0]) != null) {
          tmpStr = requestMap.get(optionalGetMapParams[0])[0];
          if (tmpStr == null) { // no getGUI param
            getGUI = false;
          } else {
            if (tmpStr.equalsIgnoreCase("true")) { //$NON-NLS-1$
              getGUI = true;
            } else {
              getGUI = false;
            }
          }
        }
     // TIME param
        paramCheck = optionalGetMapParams[4];
        if (requestMap.get(optionalGetMapParams[4]) != null) { 
          String RequestedTimeStr = requestMap.get(optionalGetMapParams[4])[0];
          if (RequestedTimeStr == null || RequestedTimeStr.equalsIgnoreCase("")
              || RequestedTimeStr.equalsIgnoreCase("null")) {
            Time = new WMSTime(null); //  TIME param empty   
          } else {
            Time = new WMSTime(RequestedTimeStr);
          }
        } else { // no TIME param
          Time = new WMSTime(null); //  TIME param empty
        }
     // ANIMDUR param
        paramCheck = optionalGetMapParams[8];
        if (requestMap.get(optionalGetMapParams[8]) != null) { 
          String RequestedAnimDur = requestMap.get(optionalGetMapParams[8])[0];
          if (RequestedAnimDur == null || RequestedAnimDur.equalsIgnoreCase("")) {
            AnimDur = 0; //  ANIMDUR param empty   
          } else {
            AnimDur = Long.parseLong(RequestedAnimDur);
            if (AnimDur <= 0) {
              AnimDur = 0; 
              throw new RIMapperException(Messages.getString("WMSRequest.10")); 
            }
          }
        } else { // no ANIMDUR param
          AnimDur = 0; //  ANIMDUR param empty
        }
        

        boolean LayerAvailable, SRSAvailable, StyleAvailable;

        paramCheck = requiredGetMapParams[3];
        LayersList = requestMap.get(requiredGetMapParams[3])[0]; //$NON-NLS-1$
        Layers = LayersList.split(","); //$NON-NLS-1$
        NumLayers = Layers.length;
        if (NumLayers < 1)
          throw new RIMapperException(Messages.getString("WMSRequest.78") + NumLayers //$NON-NLS-1$
              + Messages.getString("WMSRequest.77")); //$NON-NLS-1$
        // rebuild LayersList to normalise (eg. remove trailing comma's)
        LayersList=Layers[0];
        for (int i = 1; i < NumLayers; i++) {
          LayersList += "," + Layers[i]; //  //$NON-NLS-1$
        }

        paramCheck = requiredGetMapParams[5];
        SRS = requestMap.get(requiredGetMapParams[5])[0]; //$NON-NLS-1$
        if (Layers != null) {
          paramCheck = requiredGetMapParams[4];
          StylesList = requestMap.get(requiredGetMapParams[4])[0]; //$NON-NLS-1$
          Styles = StylesList.split(","); //$NON-NLS-1$
          int NumStyles = Styles.length;
          if (NumStyles == 1 && Styles[0] == "") { // no styles, use defaults //$NON-NLS-1$
            Styles = new String[NumLayers];
            for (int i = 0; i < NumLayers; i++) {
              Styles[i] = "defStyle"; //  //$NON-NLS-1$
            }
          } else { // check if styles match layers
            if (NumStyles != NumLayers)
              throw new RIMapperException(Messages.getString("WMSRequest.76")); //$NON-NLS-1$
          }
          String[] SRSparts = SRS.split(":"); //$NON-NLS-1$
          if (!SRSparts[0].equalsIgnoreCase("EPSG")) { //$NON-NLS-1$
            throw new RIMapperException(Messages.getString("WMSRequest.75") + SRSparts[0] //$NON-NLS-1$
                + Messages.getString("WMSRequest.74")); //$NON-NLS-1$
          }
          SRSrequested = SRSparts[1];
          for (int i = 0; i < NumLayers; i++) {
            LayerAvailable = false;
            NumLayersAvailable = myWMSCapabilities.getNumLayers();
            layerRequested = Layers[i];
            for (int j = 0; j < NumLayersAvailable; j++) {
              layerFound = myWMSCapabilities.getLayer_name()[j];
              if (layerFound.equalsIgnoreCase(layerRequested)) {
                LayerAvailable = true;
                SRSAvailable = false;
                SRSsfound = myWMSCapabilities.getLayer_srs_epsg_list()[j];
                numSRSs = SRSsfound.length;
                for (int z = 0; z < numSRSs; z++) {
                  if (SRSsfound[z].equalsIgnoreCase(SRSrequested)) {
                    SRSAvailable = true;
                    SRS = SRSrequested;
                  }
                }
                if (!SRSAvailable)
                  throw new RIMapperException(Messages.getString("WMSRequest.73") + SRSrequested //$NON-NLS-1$
                      + Messages.getString("WMSRequest.72") + Layers[i] + "]."); //$NON-NLS-1$ //$NON-NLS-2$
                StyleAvailable = false;
                Stylesfound = myWMSCapabilities.getLayer_style_list()[j];
                if (Stylesfound != null) { // styles defined
                  numStyles = Stylesfound.length;
                  for (int z = 0; z < numStyles; z++) {
                    if (Stylesfound[z].equalsIgnoreCase(Styles[i]) // style
                        // available
                        || Styles[i].equals("defStyle")) { //defStyle always availabe //$NON-NLS-1$
                      StyleAvailable = true;
                    }
                  }
                }
                if (!StyleAvailable) {
                  throw new RIMapperException(Messages.getString("WMSRequest.71") + Styles[i] //$NON-NLS-1$
                      + Messages.getString("WMSRequest.70") + Layers[i] + ")."); //$NON-NLS-1$ //$NON-NLS-2$
                }
              }
            }
            if (!LayerAvailable)
              throw new RIMapperException(Messages.getString("WMSRequest.69") + Layers[i] //$NON-NLS-1$
                  + Messages.getString("WMSRequest.68")); //$NON-NLS-1$
          } // i=NumLayers
        } // Layers != null

        String[] BBOXparts;
        Double Xmin;
        Double Ymin;
        Double Xmax;
        Double Ymax;
        if (requestMap.get("BBOX")[0] == "") { //$NON-NLS-1$ //$NON-NLS-2$
          // defaultExtent = true; // ols non-WMS code
          // NOTE this is actually non-WMS compliant, so we do not allow any more
          throw new RIMapperException(Messages.getString("WMSRequest.80") + "BBOX");
        } else {
          defaultExtent = false; // indicates BBOX extent is provided
          paramCheck = requiredGetMapParams[6];
          BBOXstr = requestMap.get(requiredGetMapParams[6])[0];
          BBOXparts = BBOXstr.split(","); //$NON-NLS-1$ //$NON-NLS-2$
          if (BBOXparts.length != 4) {
            throw new RIMapperException("BBOX=" + BBOXstr + Messages.getString("WMSRequest.67")); 
          }
          Xmin = Double.parseDouble(BBOXparts[0]);
          Ymin = Double.parseDouble(BBOXparts[1]);
          Xmax = Double.parseDouble(BBOXparts[2]);
          Ymax = Double.parseDouble(BBOXparts[3]);
          if (Xmin >= Xmax || Ymin >= Ymax)
            throw new RIMapperException("BBOX=" //$NON-NLS-1$
                + BBOXparts[0] + "," //$NON-NLS-1$
                + BBOXparts[1] + "," //$NON-NLS-1$
                + BBOXparts[2] + "," //$NON-NLS-1$
                + BBOXparts[3] + Messages.getString("WMSRequest.67")); //$NON-NLS-1$
          BBOX = new Envelope();
          BBOX.init(Xmin, Xmax, Ymin, Ymax);
        }
        paramCheck = requiredGetMapParams[9];
        Format = requestMap.get(requiredGetMapParams[9])[0]; //$NON-NLS-1$
        // TODO: change this debug thing to proper check
        if (Format.equalsIgnoreCase("image/svg xml"))Format = "image/svg+xml"; //$NON-NLS-1$ //$NON-NLS-2$
        if (Format.equalsIgnoreCase(Utils.JPEG_MIME_TYPE) || Format.equals(Utils.PNG_MIME_TYPE)) {
          if (getGUI)
            throw new RIMapperException(
                Messages.getString("WMSRequest.8") + Messages.getString("WMSRequest.9") + Format + Messages.getString("WMSRequest.66")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } else if (Format.equalsIgnoreCase(Utils.SVG_MIME_TYPE)) {
          // OK, go on...
        } else {
          throw new RIMapperException(Messages.getString("WMSRequest.9") + Format + Messages.getString("WMSRequest.66")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (requestMap.containsKey("EXCEPTIONS")) { //$NON-NLS-1$
          paramCheck = optionalGetMapParams[3];
          ExceptionsFormat = requestMap.get(optionalGetMapParams[3])[0]; //$NON-NLS-1$
          if (ExceptionsFormat.equalsIgnoreCase(Utils.OGC_EXCEPTION_XML_MIME_TYPE)
              || ExceptionsFormat.equalsIgnoreCase(Utils.OGC_EXCEPTION_INIMAGE_MIME_TYPE)) {
              // OK, do nothing
          } else {
            throw new RIMapperException("EXCEPTIONS=" + ExceptionsFormat + " not supported.");
          }
        } else {
          ExceptionsFormat = Utils.OGC_EXCEPTION_XML_MIME_TYPE;
          // default exception type
        }

        // ++++++++++++ END GETMAP
        // +++++++++++++++++++++++++++++++++++++++++++++++++

      } else if (Request.equalsIgnoreCase("GetCapabilities")) { //$NON-NLS-1$

        // ++++++++++++ GETCAPABILITIES
        // +++++++++++++++++++++++++++++++++++++++++++++++++

        checkRequiredParams(requiredGetCapabilitiesParams);
        // requiredGetCapabilitiesParams = {"SERVICE", "REQUEST", "VERSION"}
        Service = requestMap.get(requiredGetCapabilitiesParams[0])[0]; //$NON-NLS-1$
        if (!Service.equalsIgnoreCase("WMS")) { //$NON-NLS-1$
          throw new RIMapperException("'SERVICE=" + Service //$NON-NLS-1$
              + Messages.getString("WMSRequest.65")); //$NON-NLS-1$
        }
        Version = checkVersion(requestMap.get(requiredGetCapabilitiesParams[2])[0]); //$NON-NLS-1$
        
        // ++++++++++++ END GETCAPABILITIES
        // +++++++++++++++++++++++++++++++++++++++++++++++++

      } else {
        throw new RIMapperException(Messages.getString("WMSRequest.81") + Request + Messages.getString("WMSRequest.64")); //$NON-NLS-1$ //$NON-NLS-2$
      }
    } catch (NumberFormatException e) {
      throw new RIMapperException(Messages.getString("WMSRequest.83") + e.getMessage() //$NON-NLS-1$
          + " in parameter " + paramCheck + "]."); //$NON-NLS-1$
    } catch (RuntimeException e) {
      throw new RIMapperException("[WMSRequest] While checking request parameter " + paramCheck + " **RuntimeException**: " + e.toString()); //$NON-NLS-1$
    }

  }// end constructor

  // GETTERS
  /**
   * @return the numLayers
   */
  public int getNumLayers() {
    return NumLayers;
  }

  /**
   * @return the bBOX
   */
  public Envelope getBBOX() {
    return BBOX;
  }

  /**
   * @return the format
   */
  public String getFormat() {
    return Format;
  }

  /**
   * @return the Time
   */
  public WMSTime getTime() {
    return Time;
  }
  

  /**
   * @return the getGUI parameter
   */
  public boolean getGetGUI() {
    return getGUI;
  }
  
  /**
   * @return the AnimDur parameter
   */
  public long getAnimDur() {
    return AnimDur;
  }

  /**
   * @return the height
   */
  public int getHeight() {
    return Height;
  }

  /**
   * @return the layers
   */
  public String[] getLayers() {
    return Layers;
  }

  /**
   * @return the layersList
   */
  public String getLayersList() {
    return LayersList;
  }

  /**
   * @return the stylesList
   */
  public String getStylesList() {
    return StylesList;
  }

  /**
   * @return the SRS
   */
  public String getSRS() {
    return SRS;
  }

  /**
   * @return the styles
   */
  public String[] getStyles() {
    return Styles;
  }

  /**
   * @return the width
   */
  public int getWidth() {
    return Width;
  }

  /**
   * @return the defaultExtent
   */
  public boolean isDefaultExtent() {
    return defaultExtent;
  }

  /**
   * @return the exceptionsFormat
   */
  public String getExceptionsFormat() {
    return ExceptionsFormat;
  }

  /**
   * @return the WMShost
   */
  public String getWMShost() {
    return WMShost;
  }

  /**
   * @return the service
   */
  public String getService() {
    return Service;
  }

  /**
   * @return the version
   */
  public String getVersion() {
    return Version;
  }

  /**
   * @return the request
   */
  public String getRequest() {
    return Request;
  }

  /**
   * @return the bBOXstr
   */
  public String getBBOXstr() {
    return BBOXstr;
  }

}
