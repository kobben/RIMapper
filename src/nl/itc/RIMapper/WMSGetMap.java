/**
 * 
 */
package nl.itc.RIMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Handles a WMS 1.1.1 GetMap request.<br/>
 * Employs MakeSVG class to generate SVG document from a WMS request, using OGC
 * Simple Features data.<br/>
 * <br/>
 * &copy;2006-2011 International Institute for Geo-information Science and Earth
 * Observation (ITC) <br/>
 * Licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 2.5
 * License. see <a href="http://creativecommons.org/licenses/by-nc-sa/2.5/"
 * target="_blank"> http://creativecommons.org/licenses/by-nc-sa/2.5/</a>
 * 
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl" target="_blank">
 *         kobben@itc.nl </a>
 * @version 2.2 [Sep 2011]
 */
// 
// Major changes:
// 1.0 b: first public beta version version based on old XML2SVG servlet code
// 1.1 : added GUI stuff (Bram Ton)
// 2.0 : using instantiated iso static WMSCapabilities
// 2.2 : Cascaded layers possible (png/jpeg)
//    + made SQL functions standard, eg. ST_intersects() iso intersects()
//    + using ST_asBinary() iso ST_asText() for gettinge geometry
//    + Get selections based on TIME dimension (if present) -- for static and animated layers
// 
public class WMSGetMap {

  private WMSCapabilities myWMSCapabilities;

  /**
   * Constructor
   */
  public WMSGetMap(WMSCapabilities theWMSCapabilities) {
    myWMSCapabilities = theWMSCapabilities;
    // constructor
  }
  

  /**
   * @param myDBconn
   *          the Database connection class (from {@link DBconn}) to be used
   * @param myWMSRequests
   *          the {@link WMSRequest}
   * @return a StringBuffer with the SVG xml document
   * @throws RIMapperException
   */
  public StringBuffer doGetMap(DBconn myDBconn, WMSRequest myWMSRequests) throws RIMapperException {

    StringBuffer S = new StringBuffer();
    StringBuffer headerBuf = new StringBuffer();
    StringBuffer footerBuf = new StringBuffer();
    int NumLayers = myWMSRequests.getNumLayers();
    StringBuffer[] layerBuffs = new StringBuffer[NumLayers];
    String SQL = ""; //$NON-NLS-1$

    boolean RelativeMode = true;
    Double Xmin = Double.MAX_VALUE;
    Double Ymin = Double.MAX_VALUE;
    Double Xmax = Double.MIN_VALUE;
    Double Ymax = Double.MIN_VALUE;
    String MapExtentPolygonStr = ""; //$NON-NLS-1$
    
    boolean animLayerPresent = false;

    try {
      MakeSVG mySVG = new MakeSVG(myDBconn, myWMSRequests.getGetGUI());
      if (mySVG != null) {
        String[] PkeyStr = new String[NumLayers];
        String[] GeomColStr = new String[NumLayers];
        if (myWMSRequests.isDefaultExtent()) { // calculate full extent
          Xmin = 0.0;
          Ymin = 0.0;
          Xmax = 0.0;
          Ymax = 0.0;
          MapExtentPolygonStr = ""; //$NON-NLS-1$
        } else { // use BBOX extent provided
          Xmin = myWMSRequests.getBBOX().getMinX();
          Ymin = myWMSRequests.getBBOX().getMinY();
          Xmax = myWMSRequests.getBBOX().getMaxX();
          Ymax = myWMSRequests.getBBOX().getMaxY();
          MapExtentPolygonStr = "GeomFromText('POLYGON((" + Xmin + " " + Ymin + "," //$NON-NLS-3$
              + Xmin + " " + Ymax + "," + Xmax + " " + Ymax + "," + Xmax + " " + Ymin //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
              + "," + Xmin + " " + Ymin + "))'," + myWMSRequests.getSRS() + ")"; //$NON-NLS-3$ //$NON-NLS-4$
        }
        Double FalseOriginX = 0.0;
        Double FalseOriginY = 0.0;
        int Precision = 0;
        
        int LayerIndex = 0;

        // for all layers...
        for (int curLayer = 0; curLayer < myWMSRequests.getNumLayers(); curLayer++) {
          
          // search which type it is:
          String curLayerType = "";
          String curLayerTime_column = "";
          String curLayerTimeObjectid = "";
          String curLayerTitle = "";
          String curLayerAbstract = "";
          String curLayerName = myWMSRequests.getLayers()[curLayer];
          for (int j = 0; j < myWMSCapabilities.getNumLayers(); j++) {
            if (curLayerName.equals(myWMSCapabilities.getLayer_name()[j])) {
              curLayerType = myWMSCapabilities.getLayer_type()[j];
              curLayerTime_column = myWMSCapabilities.getLayer_dimension_column()[j]; 
            }
          }
          if (curLayerType.equals("SVG")) {

            // ++++++++++  SVG layer +++++++++++++++

            SQL = "SELECT pkey,geom_col,title,abstract FROM wms_layers WHERE name= '" //$NON-NLS-1$
                + myWMSRequests.getLayers()[curLayer] + "'"; //$NON-NLS-1$
            ResultSet rs = myDBconn.Query(SQL);
            if (rs == null) {
              throw new RIMapperException(Messages.getString("WMSGetMap.17") + SQL); //$NON-NLS-1$
            } else {
              while (rs.next()) { // should normally be one!
                PkeyStr[curLayer] = rs.getString("pkey");
                GeomColStr[curLayer] = rs.getString("geom_col");
                curLayerTitle = rs.getString("title");
                curLayerAbstract = rs.getString("abstract");
              } // end while
            }
            // gather and handle styles stuff
            String[] Classnames = null;
            String[] SVGstyles = null; 
            boolean SVGmakeTrack = false;
            String SVGtrackStyle = null;
            long animDurationFromDB = 0;
            long animDurationFromURL = myWMSRequests.getAnimDur();
            String LayerStyle = myWMSRequests.getStyles()[curLayer];
            String LayerStyleType = "single"; //$NON-NLS-1$
            String LayerStyleAttributeList = "";
            String TmpStr;
            int numSVGstyles = 0; // 1 if type=single
            if (LayerStyle.equalsIgnoreCase("defStyle")) { //$NON-NLS-1$
              Classnames = new String[1];
              Classnames[0] = "defStyle"; //$NON-NLS-1$
              SVGstyles = new String[1];
              SVGstyles[0] = "fill=\"none\" stroke=\"rgb(0,0,0)\" stroke-width=\"0.5\""; //$NON-NLS-1$
            } else {
              SQL = "SELECT styletype,styleattribute,classes,svgstyles FROM wms_styles " //$NON-NLS-1$
                  + "WHERE name='" + LayerStyle + "'";
              rs = myDBconn.Query(SQL);
              if (rs == null) {
                throw new RIMapperException(Messages.getString("WMSGetMap.17") + SQL); //$NON-NLS-1$
              } else {
                if (rs.next()) { // should normally be 1 only!
                  LayerStyleType = rs.getString("styletype"); //$NON-NLS-1$
                  LayerStyleAttributeList = rs.getString("styleattribute"); //$NON-NLS-1$
                  if (LayerStyleAttributeList == null) LayerStyleAttributeList = "";
                  TmpStr = rs.getString("svgstyles"); //$NON-NLS-1$
                  if (TmpStr == null) {
                  } else {
                    SVGstyles = TmpStr.split(";"); //$NON-NLS-1$
                    numSVGstyles = SVGstyles.length;
                    if (numSVGstyles > 1) {
                      Classnames = rs.getString("classes").split(";");
                    } else { // type=single
                      Classnames = new String[1];
                      Classnames[0] = ""; //$NON-NLS-1$
                    }
                  }
                  boolean SVGstyleExists;
                  for (int j = 0; j < numSVGstyles; j++) {
                    SVGstyleExists = false;
                    SVGstyles[j] = SVGstyles[j].trim();
                    Classnames[j] = Classnames[j].trim();
                    for (int z = 0; z < myWMSCapabilities.getNumSVGStyles(); z++) {
                      if (myWMSCapabilities.getSvg_style_name()[z].equalsIgnoreCase(SVGstyles[j])) {
                        SVGstyleExists = true;
                        SVGstyles[j] = myWMSCapabilities.getSvg_style()[z]; 
                        if (LayerStyleType.equalsIgnoreCase("animate")) {
                          SVGmakeTrack = myWMSCapabilities.getSvg_style_maketrack()[z]; 
                          SVGtrackStyle = myWMSCapabilities.getSvg_style_trackstyle()[z];
                          animDurationFromDB = myWMSCapabilities.getSvg_style_animduration()[z];
                        }
                        // actual style params
                      }
                    }
                    if (!SVGstyleExists) {
                      throw new RIMapperException(Messages.getString("WMSGetMap.33") + SVGstyles[j] //$NON-NLS-1$
                          + Messages.getString("WMSGetMap.34") + LayerStyle //$NON-NLS-1$
                          + Messages.getString("WMSGetMap.35")); //$NON-NLS-1$
                    }
                  }
                } else {// style doesnt exist - should have been caught in WMSRequest
                  throw new RIMapperException(Messages.getString("WMSGetMap.36") + LayerStyle //$NON-NLS-1$
                      + Messages.getString("WMSGetMap.37")); //$NON-NLS-1$
                }
              }
            } // end if defStyle     
            

            //Make SQL for retrieving the geometries:            
            SQL = "SELECT ";
            // first the pkey and the geometry:
            SQL += PkeyStr[curLayer] + ", ST_AsBinary(" + GeomColStr[curLayer] + ")";
            
            // then the attributes requested in the WMSstyle table:
            if (!LayerStyleAttributeList.equalsIgnoreCase("")) {
              SQL += ", " + LayerStyleAttributeList;
            }
            //then, if TIME, get time identifier and the timecolumn (as epochSeconds)  again
            if (myWMSRequests.getTime().isTimeRequested()) { // TIME  requested
              //get object identifier from attribute list TODO: better way..?
              curLayerTimeObjectid = LayerStyleAttributeList.split(",")[0];
              SQL += ", " + curLayerTimeObjectid + " AS time_object_identifier";
              SQL += ", " + WMSTime.seconds(curLayerTime_column) + " AS time_seconds";
              String FT = "'" + myWMSRequests.getTime().getFromDateTimeISO() + "'";
              String TT = "'" + myWMSRequests.getTime().getToDateTimeISO() + "'";
              SQL += ", " + WMSTime.seconds(FT) + " AS time_from_seconds";
              SQL += ", " + WMSTime.seconds(TT) + " AS time_to_seconds";
            }
            
            //then the FROM statement
            SQL += " FROM " + myWMSRequests.getLayers()[curLayer];

            // then BBox extent in WHERE statement
            if (!myWMSRequests.isDefaultExtent()) { 
              SQL += " WHERE ST_intersects(" + GeomColStr[curLayer] + "," + MapExtentPolygonStr + ")"; //$NON-NLS-3$
            }

            //then handle TIME and add to WHERE statement
            if (myWMSRequests.getTime().isTimeRequested()) { // TIME  requested
              if (myWMSRequests.isDefaultExtent()) {
                SQL += " WHERE ("; //WHERE still needed
              } else {
                SQL += " AND ("; //WHERE already supplied by BBOX
              }
              SQL += WMSTime.timestamp(curLayerTime_column);
              SQL += " >= ";
              SQL += WMSTime.timestamp("'" + myWMSRequests.getTime().getFromDateTimeISO() + "'");
              SQL += " AND ";
              SQL += WMSTime.timestamp(curLayerTime_column);
              SQL += " <= ";
              SQL += WMSTime.timestamp("'" + myWMSRequests.getTime().getToDateTimeISO() + "'");              
              SQL +=  ") ";            
              SQL +=  " ORDER BY " + curLayerTimeObjectid + "," + curLayerTime_column;
            } else {
              if (LayerStyleType.equalsIgnoreCase("animate")) {
                // animate layer but no TIME requested
                throw new RIMapperException(Messages.getString("WMSGetMap.23"));
              }
            } //end handle TIME

            SQL +=  ";";
            
            layerBuffs[curLayer] = new StringBuffer();
            
            // start wrapper group for GUI elements such as layerswitcher, etc..
            // used later by gui.js Init function
            
            layerBuffs[curLayer].append("\n<!-- START GUI wrapper of layer " + curLayerName + " -->");
            layerBuffs[curLayer].append("\n<g id='GUIlayer_" + LayerIndex + "'");
            layerBuffs[curLayer].append("\n  rim:attribs='" + LayerStyleAttributeList + "'");
            layerBuffs[curLayer].append("\n  rim:title='" + curLayerTitle + "'");
            layerBuffs[curLayer].append("\n  rim:abstract='" + curLayerAbstract + "'");
            
            if (LayerStyleType.equalsIgnoreCase("animate")) {
              animLayerPresent = true;
              long animDuration = 0;
              //if URL request for AnimDuration, it overrides the default set in DB
              if (animDurationFromURL > 0) {
                animDuration = animDurationFromURL;
              } else {
                animDuration = animDurationFromDB;
              }
              layerBuffs[curLayer].append("\n  rim:type='SVGanimated'");
              layerBuffs[curLayer].append("\n  rim:anim_attrib='" + curLayerTime_column + "'");
              layerBuffs[curLayer].append("\n  rim:anim_period='" + myWMSRequests.getTime().getTimeRequestISO() + "'");
              layerBuffs[curLayer].append("\n > ");
              layerBuffs[curLayer].append(mySVG.makeLayerAnimated(myWMSRequests.getLayers()[curLayer], PkeyStr[curLayer], 0,
                  null, null, LayerStyleType, Classnames, SVGstyles, SVGmakeTrack, SVGtrackStyle, animDuration, LayerStyleAttributeList, RelativeMode, FalseOriginX,
                  FalseOriginY, Precision, SQL, myWMSRequests.getTime()));
            } else {
              layerBuffs[curLayer].append("\n  rim:type='SVG'");
              layerBuffs[curLayer].append("\n > ");
              layerBuffs[curLayer].append(mySVG.makeLayer(myWMSRequests.getLayers()[curLayer], PkeyStr[curLayer], 0,
                  null, null, LayerStyleType, Classnames, SVGstyles, LayerStyleAttributeList, RelativeMode, FalseOriginX,
                  FalseOriginY, Precision, SQL));
            }
        
            layerBuffs[curLayer].append("\n</g> <!-- END GUI wrapper of layer " + curLayerName + " -->");

            // ++++++++++  end SVG layer +++++++++++++++
            
          } else if (curLayerType.equals("WMS")) {
            
            // ++++++++++  cascaded WMS layer +++++++++++++++

            SQL = "SELECT name,title,abstract,remote_url,remote_layers,remote_styles FROM wms_cascaded_layers WHERE name= '" //$NON-NLS-1$
                + myWMSRequests.getLayers()[curLayer] + "'"; //$NON-NLS-1$
            ResultSet rs = myDBconn.Query(SQL);
            if (rs == null) {
              throw new RIMapperException(Messages.getString("WMSGetMap.17") + SQL); //$NON-NLS-1$
            } else {
              while (rs.next()) { // should normally be one!
                layerBuffs[curLayer] = new StringBuffer();
                // start wrapper group for GUI element
                // used later by gui.js Init function
                layerBuffs[curLayer].append("\n<!-- START GUI wrapper of layer-->");
                layerBuffs[curLayer].append("\n<g id='GUIlayer_" + LayerIndex + "'");
                layerBuffs[curLayer].append("\n  rim:type='WMS'");
                layerBuffs[curLayer].append("\n  rim:attribs='remote_url'");
                layerBuffs[curLayer].append("\n  rim:title='" + rs.getString("title") + "'");
                layerBuffs[curLayer].append("\n  rim:abstract='" + rs.getString("abstract") + "'");
                layerBuffs[curLayer].append("\n > ");

                String curStyles;
                if (rs.getString("remote_styles") == null) {
                  curStyles = ""; // empty for default...
                } else {
                  curStyles = rs.getString("remote_styles");
                }
                String myUrlString = rs.getString("remote_url") 
                    + "SERVICE=" + myWMSRequests.getService()
                    + "&amp;VERSION=" + myWMSRequests.getVersion() 
                    + "&amp;REQUEST=" + "GetMap" 
                    + "&amp;BBOX=" + myWMSRequests.getBBOXstr() 
                    + "&amp;SRS=EPSG:" + myWMSRequests.getSRS() 
                    + "&amp;WIDTH=" + myWMSRequests.getWidth() 
                    + "&amp;HEIGHT=" + myWMSRequests.getHeight()
                    + "&amp;LAYERS=" + rs.getString("remote_layers")
                    + "&amp;STYLES=" + curStyles 
                    + "&amp;FORMAT=image/png";
       
                layerBuffs[curLayer].append("\n<image id=\"" + rs.getString("name") + "\"");
                layerBuffs[curLayer].append(" x=\"" + Xmin + "\"");
                layerBuffs[curLayer].append(" y=\"" + -Ymax + "\"");
                layerBuffs[curLayer].append(" width=\"" + (Xmax - Xmin) + "\"");
                layerBuffs[curLayer].append(" height=\"" + (Ymax - Ymin) + "\"");
                layerBuffs[curLayer].append(" xlink:href=\"" + myUrlString + "\"");
                layerBuffs[curLayer].append(" rim:remote_url=\"" + myUrlString + "\"");
                layerBuffs[curLayer].append("\n />");

                layerBuffs[curLayer].append("\n</g>");
                layerBuffs[curLayer].append("\n<!-- END GUI wrapper of layer " + rs.getString("name") + "-->");
              } // end while
            }
          // ++++++++++  end cascaded WMS layer +++++++++++++++
            
          } else {
            throw new RIMapperException(Messages.getString("[WMSGetMap] Layer type [") + curLayerType
                + Messages.getString("WMSRequest.66"));
          }
          
          LayerIndex++;

        } // ...for all layers

        if (myWMSRequests.getGetGUI()) {
          if (animLayerPresent) {
            headerBuf = mySVG.makeHeader(myWMSRequests.isDefaultExtent(), Xmin, Ymin, Xmax, Ymax, 0.0, 0.0, 0,
                "svgAnimGUIheader", null, myWMSRequests.getWidth(), //$NON-NLS-1$
                myWMSRequests.getHeight());            
          } else {
            headerBuf = mySVG.makeHeader(myWMSRequests.isDefaultExtent(), Xmin, Ymin, Xmax, Ymax, 0.0, 0.0, 0,
                "svgGUIheader", null, myWMSRequests.getWidth(), //$NON-NLS-1$
                myWMSRequests.getHeight());            
          }
        } else {
          headerBuf = mySVG.makeHeader(myWMSRequests.isDefaultExtent(), Xmin, Ymin, Xmax, Ymax, 0.0, 0.0, 0,
              "noGUIheader", null, myWMSRequests.getWidth(), //$NON-NLS-1$
              myWMSRequests.getHeight());
        }

        // write WMS state into WMSdata
        // TODO: BUG: when automatic extents, no BBOX here!
        //       Note: automatic extents violate WMS spec anyway...
        headerBuf.append("<desc id=\"WMSData\" " + "\n");
        headerBuf.append("WMShost=\"" + myWMSRequests.getWMShost() + "\"\n");
        headerBuf.append("SERVICE=\"" + myWMSRequests.getService() + "\"\n");
        headerBuf.append("VERSION=\"" + myWMSRequests.getVersion() + "\"\n");
        headerBuf.append("REQUEST=\"" + myWMSRequests.getRequest() + "\"\n");
        headerBuf.append("SRS=\"EPSG:" + myWMSRequests.getSRS() + "\"\n");
        headerBuf.append("BBOX=\"" + myWMSRequests.getBBOX().getMinX() + "," 
            + myWMSRequests.getBBOX().getMinY() + "," 
            + myWMSRequests.getBBOX().getMaxX() + "," 
            + myWMSRequests.getBBOX().getMaxY() + "\"\n");
        headerBuf.append("LAYERS=\"" + myWMSRequests.getLayersList() + "\"\n");
        headerBuf.append("STYLES=\"" + myWMSRequests.getStylesList() + "\"\n");
        if (myWMSRequests.getTime().isTimeRequested()) {
          headerBuf.append("TIME=\"" + myWMSRequests.getTime().getTimeRequestISO() + "\"\n");
        }
        headerBuf.append("FORMAT=\"" + myWMSRequests.getFormat() + "\"\n");
        headerBuf.append("WIDTH=\"" + myWMSRequests.getWidth() + "\"\n");
        headerBuf.append("HEIGHT=\"" + myWMSRequests.getHeight() + "\"\n");
        headerBuf.append("EXCEPTIONS=\"" + myWMSRequests.getExceptionsFormat() + "\"\n");
        if (myWMSRequests.getGetGUI()) {
          headerBuf.append("GETGUI=\"true\"\n"); //$NON-NLS-1$	
        } else {
          headerBuf.append("GETGUI=\"false\"\n"); //$NON-NLS-1$
        }
        headerBuf.append(">" + "\nTITLE = " + "OGC:WMS" 
            + "\nAUTHOR = " + "RIMapperWMS" 
            + "\n</desc>\n"); //$NON-NLS-1$
        
        headerBuf.append(mySVG.makeScripts(null, null, null));
        // empty scripts for now
        headerBuf.append(mySVG.makeDefs(null, null, null));
        // empty defs for now
        
        if (myWMSRequests.getGetGUI()) {
          if (animLayerPresent) {
            String[] SVGfooter = {"svgAnimGUIfooter"}; //$NON-NLS-1$
            footerBuf = mySVG.makeFooter(SVGfooter, null, null);            
          } else {
            String[] SVGfooter = {"svgGUIfooter"}; //$NON-NLS-1$
            footerBuf = mySVG.makeFooter(SVGfooter, null, null);
          }
        } else {
          String[] SVGfooter = {"noGUIfooter"}; //$NON-NLS-1$
          footerBuf = mySVG.makeFooter(SVGfooter, null, null);
        }

        // all succesful, now write out
        S.append(headerBuf);
        for (int i = 0; i < myWMSRequests.getNumLayers(); i++) {
          S.append(layerBuffs[i]);
        }
        S.append(footerBuf);
      }
    } catch (SQLException e) {
      throw new RIMapperException(Messages.getString("WMSGetMap.87") + e.getMessage() //$NON-NLS-1$
          + Messages.getString("WMSGetMap.88") + SQL + ")"); //$NON-NLS-1$
    } catch (RuntimeException e) {
      throw new RIMapperException("[WMSGetMap] **RuntimeException**: " + e.toString()); //$NON-NLS-1$
    } catch (OutOfMemoryError e) {
      throw new RIMapperException("[WMSGetMap] " + Messages.getString("WMSGetMap.90") + e.toString()); //$NON-NLS-1$
    }
    return S;
  }
}