package nl.itc.RIMapper;

import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.*;

/**
 * Converts SFS OGC geometry columns to Scalable Vector Graphics (SVG 1.1). <br/>
 * Uses Java Topology Suite (JTS 1.12) (<a
 * href="http://sourceforge.net/projects/jts-topo-suite/"
 * target="_blank">http://sourceforge.net/projects/jts-topo-suite/</a>)<br/>
 * <br/>
 * &copy;2004-2011 ITC - University of Twente, Faculty of Geo-Information
 * Science and Earth Observation<br/>
 * Licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 2.5
 * License. see <a href="http://creativecommons.org/licenses/by-nc-sa/2.5/"
 * target="_blank"> http://creativecommons.org/licenses/by-nc-sa/2.5/</a>
 * 
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl"
 *         target="_blank">kobben@itc.nl</a>
 * @version 2.2 [September 2011]
 */

// Major changes:
// 1.0 [15 June 2004] - first released version
// 1.1 [July 2004] - removed some bugs and cleaned up debug code
// 1.2 [10 Sep 2004] - changed some behaviours to accomodate calling from
// MySQL2SVG servlet
// 1.3 [30 Sep 2004] -SQL statement for MakeLayers methods now fully coming from
// calling code
// 1.4 [june 2005] - Accepts MapExtent; Accomodate UsePrecision and
// UseFalseOriginX/Y in getSVGPoint to avoid ASV Precision errors
// 1.5 [April 2006] - Catch and report empty and invalid Geometries in MakeLayer
// class
// 1.6 [Dec 2006] Making WMS compatible:
// - use DBConn class
// - width & height parameters
// - default AspectRatio=none
// - use presentation attribs styling iso. CSS, from wms_styles table
// 2.0 [Sep 2010] MAJOR OVERHAUL:
// - enabled better GUI from gui.js
// - Upgraded to JTS 1.11 (migrated from VividSolutions to SourceForge)
// - wrapped layers in GUIlayer_ wrapper groups for use by GUI layer switcher
//
// 2.2 [Sep 2011] Adding TimeMapper functionality:
// - Further additions and changes:
// - - Points radius now parsed from rim:radius="x.x", a 'fake' css style in svg_styles table
// - - Upgraded to JTS 1.12 + using Well Known Binary (WKBReader) iso Well Known Text
// TODO: - ??? Using AS_SVG from DB instead of private functions...??
// -> Note: this means dropping MySQL support

public class MakeSVG {

  // SVG DocType now deprecated!
  // + "<!DOCTYPE svg PUBLIC '-//W3C//DTD SVG 1.0//EN'
  // 'http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd'>";
  // public final static String SVG_NAMESPACE_DEF =
  // " xmlns='http://www.w3.org/2000/svg' ";

  // public final static String RIM_NAMESPACE_DEF =
  // " xmlns:rim='http://kartoweb.itc.nl/RIMapper' ";

  public final static String RIM_NAMESPACE = "rim:";

  private final static String FRAGMENTS_TABLE = "fragments";

  private final static String FRAGMENT_CODE_COLUMN = "code";

  private DBconn myDBconn;

  private String SQL = "";

  private ResultSet rs = null;

  public double bboxHeight = 768;

  public double bboxWidth = 1024; // just initiate arbitrarily

  public double xMin = Double.MAX_VALUE;

  public double yMin = Double.MAX_VALUE;

  public double xMax = -(Double.MAX_VALUE);

  public double yMax = -(Double.MAX_VALUE);

  public double xMinMap = Double.MAX_VALUE;

  public double yMinMap = Double.MAX_VALUE;

  public double xMaxMap = -(Double.MAX_VALUE);

  public double yMaxMap = -(Double.MAX_VALUE);

  public String errorMessage;

  private boolean withGUI;

  /**
   * method to instantiate a MakeSVG class.
   * 
   * @param theDBconn
   *          Database connection class (from {@link DBconn}) to be used
   */
  public MakeSVG(DBconn theDBconn, boolean getGUI) { // constructor method
    bboxHeight = 0.0;
    bboxWidth = 0.0;
    errorMessage = "NO_ERROR";
    myDBconn = theDBconn;
    withGUI = getGUI;
  }

  /**
   * checks 'current' xy against existing SVGBBox and sets if necessary
   * 
   * @param XY
   *          as found in {@link #makeLayer makeLayer}
   */
  private void setSVGBBox(Coordinate XY) {
    if (XY.x < xMin)
      xMin = XY.x;
    if (XY.y < yMin)
      yMin = XY.y;
    if (XY.x > xMax)
      xMax = XY.x;
    if (XY.y > yMax)
      yMax = XY.y;
  } // end setSVGBBox()

  /**
   * @param MapExtentDefault
   * @param BBxMin
   * @param BByMin
   * @param BBxMax
   * @param BByMax
   * @param FalseOriginX
   * @param FalseOriginY
   * @param Precision
   * @param SVGRootFragmentName
   * @param SVGRootFragment
   * @param Width
   *          (if 0, set to 100%)
   * @param Height
   *          (if 0, set to 100%)
   * @return StringBuffer with SVG code for Header
   * @throws RIMapperException
   */
  public StringBuffer makeHeader(
      boolean MapExtentDefault,
      double BBxMin,
      double BByMin,
      double BBxMax,
      double BByMax,
      double FalseOriginX,
      double FalseOriginY,
      long Precision,
      String SVGRootFragmentName,
      String SVGRootFragment,
      int Width,
      int Height) throws RIMapperException {
    StringBuffer S = new StringBuffer();
    StringBuffer SVGRoot = new StringBuffer();

    try {
      // S.append(Utils.XML_HEADER);
      if (SVGRootFragmentName.equals("none")) { // get from XML style
        if (SVGRootFragment == null) {
          SVGRoot.append("");
        } else {
          SVGRoot.append(SVGRootFragment);
        }
      } else { // get from DB
        rs = null;
        boolean gotSQLResult = false;
        String SQL = null;
        SQL = "SELECT " + FRAGMENT_CODE_COLUMN + " FROM " + FRAGMENTS_TABLE + " where name=\'" + SVGRootFragmentName
            + "\'";
        rs = myDBconn.Query(SQL);
        while (rs.next()) { // loop through records found (should be
          // only one), if more take last
          gotSQLResult = true;
          SVGRoot.append(rs.getString(1));
        } // end while loop through records found
        if (!gotSQLResult) {
          errorMessage = (Messages.getString("MakeSVG.0") + SQL); //$NON-NLS-1$
          throw new RIMapperException(errorMessage);
        }
      }

      if (!MapExtentDefault) { // take BBox arguments, calculate SVG
        // equivalent 'manually'...
        Coordinate LL = new Coordinate(BBxMin, BByMin);
        Point LLp = new GeometryFactory().createPoint(LL);
        LL = getSVGPoint(LLp, FalseOriginX, FalseOriginY, Precision);
        Coordinate UR = new Coordinate(BBxMax, BByMax);
        Point URp = new GeometryFactory().createPoint(UR);
        UR = getSVGPoint(URp, FalseOriginX, FalseOriginY, Precision);
        xMin = LL.x;
        yMin = UR.y; // exchange LL.y and UR.y because of negative SVG Y-space!
        xMax = UR.x;
        yMax = LL.y;
        bboxWidth = xMax - xMin;
        bboxHeight = (yMax - yMin); // undo negation by GetSVGPoint!
      } else { // take calculated extents by setBBOX
        bboxWidth = xMax - xMin;
        bboxHeight = (yMax - yMin);
      }
      if (Precision != 0) {
        bboxWidth *= Precision;
        bboxHeight *= Precision;
        bboxWidth = Math.round(bboxWidth);
        bboxHeight = Math.round(bboxHeight);
        bboxWidth /= Precision;
        bboxHeight /= Precision;
      }
      String WidthStr, HeightStr;
      if (Width == 0) { // make 100%
        WidthStr = "100%";
      } else { // width in px, as asked in params
        WidthStr = Width + "px";
      }
      if (Height == 0) { // make 100%
        HeightStr = "100%";
      } else { // width in px, as asked in params
        HeightStr = Height + "px";
      }
      S.append(SVGRoot); // will have guiHeader if getGUI=true
      S.append("\n<svg id='theMap' ");
      S.append(" onclick='doAction(evt);' ");
      // }
      S.append(" preserveAspectRatio='none' ");
      String VBstr = xMin + " " + yMin + " " + bboxWidth + " " + bboxHeight;
      S.append("\nwidth='" + WidthStr + "' height='" + HeightStr + "' viewBox='" + VBstr + "' " + Utils.SVG_NAMESPACE
          + Utils.RIM_NAMESPACE + Utils.XLINK_NAMESPACE + ">\n");

      // need a background rect to catch click events. Must have fill,
      // but can be fully transparent
      S.append("\n<rect id='clickMask' ");
      S.append(" x='" + xMin + "'");
      S.append(" y='" + yMin + "'");
      S.append(" width='" + bboxWidth + "'");
      S.append(" height='" + bboxHeight + "'");
      S.append(" fill='rgb(255,255,255)' stroke='none' fill-opacity='0' ");
      S.append(" />");

      return S;
    } catch (java.sql.SQLException ex) {
      errorMessage = Messages.getString("MakeSVG.1") + ex.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } catch (RuntimeException e) {
      errorMessage = Messages.getString("MakeSVG.2") + e.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    }

  } // end makeHeader()

  /**
   * Creates the svg <defs> section, from dbase table FRAGMENTs and/or RIM XML
   * nodes. <br/>
   * The <defs> section contains:
   * <ul>
   * <LI>SVG_FILTERs
   * <LI>SVG_SYMBOLs
   * </ul>
   * 
   * @param fragmentNames
   * @param fragmentTypes
   * @param fragments
   * @return StringBuffer depicting the <defs> section
   * @throws RIMapperException
   */
  public StringBuffer makeDefs(String[] fragmentNames, String[] fragmentTypes, String[] fragments) throws RIMapperException {

    StringBuffer S = new StringBuffer();

    try {
      S.append("\n<defs>");

      if (fragmentNames != null) {
        for (int i = 0; i < fragmentNames.length; i++) {
          if (fragmentTypes[i].equals("SVG_FILTER") || fragmentTypes[i].equals("SVG_SYMBOL")) {
            if (fragments[i] != null) { // get from XML style
              S.append("\n" + fragments[i] + "\n");
            } else { // get from DB
              rs = null;
              boolean gotSQLResult = false;
              SQL = "SELECT " + FRAGMENT_CODE_COLUMN + " FROM " + FRAGMENTS_TABLE + " where name=\'" + fragmentNames[i]
                  + "\'";
              rs = myDBconn.Query(SQL);
              while (rs.next()) { // loop through records found
                gotSQLResult = true;
                S.append("\n" + rs.getString(1) + "\n");
              } // end while loop through records found
              if (!gotSQLResult) {
                errorMessage = (Messages.getString("MakeSVG.3") + SQL); //$NON-NLS-1$
                throw new RIMapperException(errorMessage);
              }
            }
          }
        }
      }
      S.append("\n</defs>");
      return S;
    } catch (java.sql.SQLException ex) {
      errorMessage = Messages.getString("MakeSVG.4") + ex.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } catch (RuntimeException e) {
      errorMessage = Messages.getString("MakeSVG.5") + e.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    }

  } // end makeDefs()

  /**
   * Creates the ecmascript section, from dbase table FRAGMENTs and/or RIM XML
   * nodes.
   * 
   * @param fragmentNames
   * @param fragmentTypes
   * @param fragments
   * @return StringBuffer depicting the ECMAscripts
   * @throws RIMapperException
   */
  public StringBuffer makeScripts(String[] fragmentNames, String[] fragmentTypes, String[] fragments) throws RIMapperException {
    StringBuffer S = new StringBuffer();

    try {
      if (fragmentNames != null) {
        S.append("\n<script  language='ECMAScript' type='text/ecmascript'><![CDATA[");
        for (int i = 0; i < fragmentNames.length; i++) {
          if (fragmentTypes[i].equals("ECMASCRIPT")) {
            if (fragments == null) { // get from DB
              rs = null;
              boolean gotSQLResult = false;
              SQL = "SELECT " + FRAGMENT_CODE_COLUMN + " FROM " + FRAGMENTS_TABLE + " where name=\'" + fragmentNames[i]
                  + "\'";
              rs = myDBconn.Query(SQL);
              while (rs.next()) { // loop through records found
                gotSQLResult = true;
                S.append("\n" + rs.getString(1) + "\n");
              } // end while loop through records found
              if (!gotSQLResult) {
                errorMessage = (Messages.getString("MakeSVG.6") + SQL); //$NON-NLS-1$
                throw new RIMapperException(errorMessage);
              }
            } else {
              if (fragments[i] != null) { // get from XML style
                S.append("\n" + fragments[i] + "\n");
              }
            }
          }
        }
        S.append("]]></script>");
      }
      return S;
    } catch (java.sql.SQLException ex) {
      errorMessage = Messages.getString("MakeSVG.7") + ex.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } catch (RuntimeException e) {
      errorMessage = Messages.getString("MakeSVG.8") + e.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    }

  } // end makeSscripts()

  /**
   * Simple SVG footer.
   * 
   * @return A StringBuffer with the SVG footer
   * @throws RIMapperException
   */
  public StringBuffer makeFooter(String[] fragmentNames, String[] fragmentTypes, String[] fragments) throws RIMapperException {
    try {
      StringBuffer S = new StringBuffer();

      if (fragmentNames == null) {
        S.append("\n</svg>");
      } else { // XML or DB constructed footer
        for (int i = 0; i < fragmentNames.length; i++) {
          if (fragments != null && fragments[i] != null) { // get from XML style
            S.append("\n" + fragments[i] + "\n");
          } else { // get from DB
            rs = null;
            boolean gotSQLResult = false;
            SQL = "SELECT " + FRAGMENT_CODE_COLUMN + " FROM " + FRAGMENTS_TABLE + " where name=\'" + fragmentNames[i]
                + "\'";
            rs = myDBconn.Query(SQL);
            while (rs.next()) { // loop through records found
              gotSQLResult = true;
              S.append("\n </svg> \n" + rs.getString(1) + "\n");
            } // end while loop through records found
            if (!gotSQLResult) {
              errorMessage = (Messages.getString("MakeSVG.9") + SQL); //$NON-NLS-1$
              throw new RIMapperException(errorMessage);
            }
          }
        }
      }
      return S;

    } catch (java.sql.SQLException ex) {
      errorMessage = Messages.getString("MakeSVG.10") + ex.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } catch (RuntimeException e) {
      errorMessage = Messages.getString("MakeSVG.11") + e.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    }
  } // end makeFooter()

  /**
   * gets given JTS Point and constructs SVG point, negating y and setting the
   * boundingbox
   * 
   * @return A Coordinate for the SVG point
   */
  private Coordinate getSVGPoint(Point p, double FalseOriginX, double FalseOriginY, long Precision) {
    Coordinate XY = new Coordinate();
    XY.x = p.getX();
    XY.y = -(p.getY()); // negate to fit SVG coord space
    XY.x = (XY.x - FalseOriginX);
    XY.y = (XY.y + FalseOriginY); // negate to fit SVG coord space
    if (Precision != 0) {
      XY.x *= Precision;
      XY.y *= Precision;
      XY.x = Math.round(XY.x);
      XY.y = Math.round(XY.y);
      XY.x /= Precision;
      XY.y /= Precision;
    }
    setSVGBBox(XY);
    return XY;
  } // end getSVGPoint()

  /**
   * @param attrib
   *          the attribute name (column in DB)
   * @param layer
   *          the layer table from whcih to get the attribute column
   * @param FID
   *          the FID of the row to get the attribute from
   * @return String with SVG fragment
   * @throws RIMapperException
   * @throws SQLException
   * @throws UnsupportedEncodingException
   */
  private String getAttribFromDB(String attrib, String layer, String PkeyStr, String FID) throws RIMapperException,
      SQLException {
    String S = "";
    if (attrib == null) { // no attribs.
      return S;
    } else if (attrib.equalsIgnoreCase("")) { // no attribs.
      return S;
    }

    rs = null;
    boolean gotSQLResult = false;
    SQL = "SELECT " + attrib + " FROM " + layer + " WHERE " + PkeyStr + "=" + FID;
    rs = myDBconn.Query(SQL);
    while (rs.next()) { // loop through records found - should be 1 only!
      gotSQLResult = true;
      S = rs.getString(1);
    } // end while loop through records found
    if (!gotSQLResult) {
      errorMessage = (Messages.getString("MakeSVG.12") + SQL); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    }
    return S;
  }

  private String makeAttribsFromDB(String attribs, String layer, String PkeyStr, String FID) throws RIMapperException,
      SQLException {
    String S = "";
    if (attribs == null) { // no attribs.
      return S;
    } else if (attribs.equalsIgnoreCase("")) { // no attribs.
      return S;
    }
    String[] attribList;
    attribList = attribs.split(",");
    rs = null;
    boolean gotSQLResult = false;
    SQL = "SELECT " + attribs + " FROM " + layer + " WHERE " + PkeyStr + "=" + FID;
    rs = myDBconn.Query(SQL);
    while (rs.next()) { // loop through recs found - could be more
      gotSQLResult = true;
      for (int i = 0; i < attribList.length; i++) {
        String attribValue = rs.getString(i + 1);
        if (attribValue == null)
          attribValue = "";
        // encode & char, otherwise XML parsing errors in client...
        attribValue = attribValue.replace("&", "&amp;");
        // same for '
        attribValue = attribValue.replace("'", "&apos;");

        S += " " + RIM_NAMESPACE + attribList[i].trim() + "='" + attribValue + "' ";
      }
    } // end while loop through records found
    if (!gotSQLResult) {
      errorMessage = (Messages.getString("MakeSVG.13") + SQL); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    }
    return S;
  }

  /**
   * Loops through given Linestring to retrieve points and construct SVG path.
   * 
   * @return A StringBuffer with the SVG path data (d= "")
   */
  private StringBuffer getSVGPath(
      LineString ls,
      boolean relativeMode,
      double FalseOriginX,
      double FalseOriginY,
      long Precision) {
    StringBuffer path = new StringBuffer();
    int i = 0;
    int numPoints = ls.getNumPoints();
    double x = 0.0, y = 0.0, xPrev = 0.0, yPrev = 0.0, xRel = 0.0, yRel = 0.0;

    Coordinate XY = getSVGPoint(ls.getPointN(0), FalseOriginX, FalseOriginY, Precision); // start
    // point
    x = XY.x;
    y = XY.y;
    path.append("M " + x + " " + y + " "); // ***SVG absolute MoveTo
    xPrev = x;
    yPrev = y;
    for (i = 1; i < numPoints; i++) { // loop through subsequent coords
      XY = getSVGPoint(ls.getPointN(i), FalseOriginX, FalseOriginY, Precision);
      x = XY.x;
      y = XY.y;
      if (relativeMode) {
        xRel = x - xPrev;
        yRel = y - yPrev;
        path.append("l " + xRel + " " + yRel + " "); // ***SVG
        // relative
        // lineTo
        xPrev = x;
        yPrev = y;
      } else {
        path.append("L " + x + " " + y + " "); // ***SVG absolute
        // lineTo
      }
    } // end loop numpoints
    return path;
  } // end getSVGPath()

  /**
   * Uses JTS WKBreader to extract OGC Simple features of Layer layername and
   * converts to SVG.
   * 
   * @param layerName
   *          the name (as used in wms_layers table)
   * @param PkeyStr
   * @param nrLayerActions
   * @param layerActionScopes
   * @param layerActions
   * @param layerType
   *          "single" or "chorochromatic" or "animated"
   * @param Classnames
   *          Array of classes, "defStyle" if style=single or no style requested
   * @param SVGstyles
   *          Array of style names, 1 if style=single, "defStyle" if no style
   *          requested
   * @param styleAttrib
   *          attributes for style(s): ALL attribs are added to SVG (rim:attrib)
   *          AND IF styletype = chorochromatic => 1st atrib = classifying
   *          attrib IF styletype = animate => 1st attrib = object identifier,
   *          2nd = time attrib
   * @param relativeMode
   *          true: use L=absolute lineto's (more precise); false: l=relative
   *          lineto's (more compact)
   * @param FalseOriginX
   * @param FalseOriginY
   * @param Precision
   * @param layerSQL
   *          The SQL for extracting layer data from the table
   * @return A StringBuffer containing the SVG fragment for the layer
   * @throws RIMapperException
   */
  public StringBuffer makeLayer(
      String layerName,
      String PkeyStr,
      int nrLayerActions,
      String[] layerActionScopes,
      String[] layerActions,
      String layerType,
      String[] Classnames,
      String[] SVGstyles,
      String styleAttrib,
      boolean relativeMode,
      double FalseOriginX,
      double FalseOriginY,
      long Precision,
      String layerSQL) throws RIMapperException {

    StringBuffer S = new StringBuffer();
    String featureAction = "";
    String featureAttrib = "";
    ResultSet rs1 = null;
    SQL = layerSQL;
    String FIDStr = null;
    byte[] WKB = null;
    int numGeoms = 0;

    try {

      S.append("\n<g id='" + layerName + "'");

      if (layerType.equals("single")) {
        if (SVGstyles[0] != null) {
          S.append(" " + SVGstyles[0]);
        }
      }

      for (int i = 0; i < nrLayerActions; i++) {
        if (layerActionScopes[i] != null) {
          if (layerActionScopes[i].equals("layer")) {
            S.append(" " + layerActions[i]);
          } else { // scope = feature
            featureAction = featureAction + " " + layerActions[i];
          }
        }
      }

      S.append(">");
      rs1 = myDBconn.Query(SQL);

      while (rs1.next()) { // loop through records found
        // gotSQLResult = true;
        FIDStr = rs1.getString(1); // get FIDStr
        WKB = rs1.getBytes(2); // get OGC WellKnownBinary
        if (WKB == null) {
          errorMessage = Messages.getString("MakeSVG.14") + layerName + "(" //$NON-NLS-1$
              + PkeyStr + "=" + FIDStr + Messages.getString("MakeSVG.17"); //$NON-NLS-2$
          throw new RIMapperException(errorMessage);
        }
        Geometry g = new WKBReader().read(WKB);
        if (g.isEmpty()) {
          errorMessage = Messages.getString("MakeSVG.15") + layerName + "(" //$NON-NLS-1$
              + PkeyStr + "=" + FIDStr + Messages.getString("MakeSVG.16"); //$NON-NLS-2$
          throw new RIMapperException(errorMessage);
        } else if (!g.isValid()) {
          errorMessage = "[MakeSVG.MakeLayers] WKTReader: layer=" + layerName + "(" + PkeyStr
              + "=" + FIDStr + Messages.getString("MakeSVG.18"); //$NON-NLS-2$
          throw new RIMapperException(errorMessage);
        }

        if (g instanceof Point) {

          Point p = (Point) g;
          String circleRadius = "0";

          featureAttrib = makeAttribsFromDB(styleAttrib, layerName, PkeyStr, FIDStr);

          S.append("\n<circle ");

          if (layerType.equals("chorochromatic")) {
            String AttribValue = getAttribFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
            for (int z = 0; z < Classnames.length; z++) {
              if (Classnames[z].equalsIgnoreCase(AttribValue)) {
                S.append(" " + SVGstyles[z]);
                circleRadius = extractRadius(SVGstyles[z]);
              }
            }
          } else { // layertype = "single"
            circleRadius = extractRadius(SVGstyles[0]);
          }
          // the code above uses a trick to get radius of circle from css-type
          // svg style.
          // radius in svg-style table defined as rim:radius="x.x"; ignored as
          // css style
          // but parsed out and used for svg circle object r

          S.append(featureAction + featureAttrib + " id='" + layerName + "_" + FIDStr + "' cx='"
              + getSVGPoint(p, FalseOriginX, FalseOriginY, Precision).x + "' cy='"
              + getSVGPoint(p, FalseOriginX, FalseOriginY, Precision).y + "' r='" + circleRadius + "' />");

        } else if (g instanceof MultiPoint) {
          MultiPoint mp = (MultiPoint) g;
          numGeoms = mp.getNumGeometries();
          S.append("\n<g id='" + layerName + "_" + FIDStr + "'>");

          String circleRadius = "0";

          featureAttrib = makeAttribsFromDB(styleAttrib, layerName, PkeyStr, FIDStr);

          // ***SVG start group to collect multipoint
          for (int j = 0; j < numGeoms; j++) { // loop through NumGeomsPoint
            S.append("\n<circle ");

            if (layerType.equals("chorochromatic")) {
              String AttribValue = getAttribFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
              for (int z = 0; z < Classnames.length; z++) {
                if (Classnames[z].equalsIgnoreCase(AttribValue)) {
                  S.append(" " + SVGstyles[z]);
                  circleRadius = extractRadius(SVGstyles[z]);
                }
              }
            } else { // layertype = "single"
              circleRadius = extractRadius(SVGstyles[0]);
            }
            // the code above uses a trick to get radius of circle from css-type
            // svg style.
            // radius in svg-style table defined as rim:radius="x.x"; ignored as
            // css style
            // but parsed out and used for svg circle object r

            S.append(featureAction + featureAttrib + " id='" + layerName + "_" + FIDStr + "_" + j + "' cx='"
                + getSVGPoint((Point) mp.getGeometryN(j), FalseOriginX, FalseOriginY, Precision).x + "' cy='"
                + getSVGPoint((Point) mp.getGeometryN(j), FalseOriginX, FalseOriginY, Precision).y + "' r='"
                + circleRadius + "' />");

          } // for numGeoms
          S.append("\n</g>"); // *** SVG end group to collect
          // multipoint
        } else if (g instanceof LineString) {
          featureAttrib = makeAttribsFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
          S.append("\n<path ");
          if (layerType.equals("chorochromatic")) {
            String AttribValue = getAttribFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
            for (int j = 0; j < Classnames.length; j++) {
              if (Classnames[j].equalsIgnoreCase(AttribValue)) {
                S.append(" " + SVGstyles[j]);
              }
            }
          }
          S.append(featureAction + featureAttrib + " id='" + layerName + "_" + FIDStr + "' d='");
          S.append(getSVGPath((LineString) g, relativeMode, FalseOriginX, FalseOriginY, Precision));
          S.append("'/>"); // ***SVG end path
        } else if (g instanceof MultiLineString) {
          MultiLineString mls = (MultiLineString) g;
          featureAttrib = makeAttribsFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
          numGeoms = mls.getNumGeometries();
          S.append("\n<path ");
          if (layerType.equals("chorochromatic")) {
            String AttribValue = getAttribFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
            for (int j = 0; j < Classnames.length; j++) {
              if (Classnames[j].equalsIgnoreCase(AttribValue)) {
                S.append(" " + SVGstyles[j]);
              }
            }
          }
          S.append(featureAction + featureAttrib + " id='" + layerName + "_" + FIDStr + "' d='");
          for (int j = 0; j < numGeoms; j++) { // loop through
            // NumGeoms
            S.append(getSVGPath((LineString) mls.getGeometryN(j), relativeMode, FalseOriginX, FalseOriginY, Precision));
          } // for numGeoms
          S.append("'/>"); // ***SVG end path
        } else if (g instanceof Polygon) {
          g.normalize();
          // to make sure CW-CCW rule is obeyed,necessary for SVG
          Polygon pg = (Polygon) g;
          featureAttrib = makeAttribsFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
          S.append("\n<path ");
          if (layerType.equals("chorochromatic")) {
            String AttribValue = getAttribFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
            for (int z = 0; z < Classnames.length; z++) {
              if (Classnames[z].equalsIgnoreCase(AttribValue)) {
                S.append(" " + SVGstyles[z]);
              }
            }
          }
          S.append(featureAction + featureAttrib + " id='" + layerName + "_" + FIDStr + "' d='\n");
          // ***SVG start path
          S.append(getSVGPath(pg.getExteriorRing(), relativeMode, FalseOriginX, FalseOriginY, Precision));
          S.append("z "); // ***SVG close ring
          int NumHoles = pg.getNumInteriorRing();
          if (NumHoles > 0) {
            for (int i = 0; i < NumHoles; i++) { // loop
              // through
              // NumHoles
              S.append(getSVGPath(pg.getInteriorRingN(i), relativeMode, FalseOriginX, FalseOriginY, Precision));
              S.append("z "); // ***SVG close ring
            } // for numHoles
          } // if numHoles > 1
          S.append("\n'/>"); // ***SVG end path
        } else if (g instanceof MultiPolygon) {
          g.normalize();
          // to make sure CW-CCW rule is obeyed,necessary for SVG
          MultiPolygon mp = (MultiPolygon) g;
          numGeoms = mp.getNumGeometries();
          S.append("\n<g id='" + layerName + "_" + FIDStr + "'>");
          // ***SVG start group to collect multipolygon
          for (int j = 0; j < numGeoms; j++) { // loop through
            // NumGeoms
            featureAttrib = makeAttribsFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
            S.append("\n<path ");
            if (layerType.equals("chorochromatic")) {
              String AttribValue = getAttribFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
              for (int z = 0; z < Classnames.length; z++) {
                if (Classnames[z].equalsIgnoreCase(AttribValue)) {
                  S.append(" " + SVGstyles[z]);
                }
              }
            }
            S.append(featureAction + featureAttrib + " id='" + layerName + "_" + FIDStr + "_" + j + "' d='\n");
            // ***SVG start sub_group Id = GroupID_geomNum
            Polygon pg = (Polygon) mp.getGeometryN(j);
            S.append(getSVGPath(pg.getExteriorRing(), relativeMode, FalseOriginX, FalseOriginY, Precision));
            S.append("z "); // ***SVG close sub_group
            int NumHoles = pg.getNumInteriorRing();
            if (NumHoles > 0) {
              for (int i = 0; i < NumHoles; i++) { // loop through NumHoles
                S.append(getSVGPath(pg.getInteriorRingN(i), relativeMode, FalseOriginX, FalseOriginY, Precision));
                // ccw to create hole
                S.append("z "); // ***SVG close path
              } // for numHoles
            } // if numHoles > 1
            S.append("\n'/>"); // ***SVG end sub_group
          } // for numGeoms
          S.append("\n</g>"); // *** SVG end group to collect
          // multipolygon
        } else if (g instanceof GeometryCollection) {
          errorMessage = ("[MakeSVG.MakeLayer] " + Messages.getString("MakeSVG.19") + layerName + " (PK=" + FIDStr + Messages.getString("MakeSVG.20")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
          throw new RIMapperException(errorMessage);
        } else {
          errorMessage = ("[MakeSVG.MakeLayer] " + Messages.getString("MakeSVG.19") + layerName + " (PK=" + FIDStr //$NON-NLS-1$
              + Messages.getString("MakeSVG.22") + g.getGeometryType()); //$NON-NLS-1$
          throw new RIMapperException(errorMessage);
        }
      } // while loop through records found

      S.append("\n</g>");

    } catch (ParseException e) { // catch WKTreader exception
      errorMessage = "[MakeSVG.MakeLayer] " + Messages.getString("MakeSVG.19") + layerName 
          + " (PK=" + FIDStr //$NON-NLS-1$
          + Messages.getString("MakeSVG.24") + e.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } 
    catch (SQLException e) {
      errorMessage = "[MakeSVG.MakeLayer] " + Messages.getString("MakeSVG.25") + e.getMessage()
          + "; SQL =  [" + SQL + "] "; //$NON-NLS-1$;
      throw new RIMapperException(errorMessage);
    } catch (RuntimeException e) {
      errorMessage = "[MakeSVG.MakeLayer] " + Messages.getString("MakeSVG.19") + layerName 
          + " (PK=" + FIDStr //$NON-NLS-1$
          + Messages.getString("MakeSVG.27") + e.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } catch (OutOfMemoryError e) {
      throw new RIMapperException(Messages.getString("WMSGetMap.90") + e.toString()); //$NON-NLS-1$
    }

    return S;
  } // end function makeLayer()

  private String extractRadius(String styleStr) throws RIMapperException {
    String R = "-1";
    int bPos, ePos;
    String quoteCh;
    bPos = styleStr.indexOf("rim:radius=") + 11;
    if (bPos != -1) {
      quoteCh = styleStr.substring(bPos, bPos + 1);
      ePos = styleStr.indexOf(quoteCh, bPos + 1);
      if (ePos != -1) {
        String radiusStr = styleStr.substring(bPos + 1, ePos);
        R = radiusStr;
      }
    }
    if (R.equalsIgnoreCase("-1")) {
      throw new RIMapperException("[MakeSVG] " + Messages.getString("MakeSVG.28") + styleStr);
    } else {
      return R;
    }
  }

  /**
   * ################################################### 
   * Uses JTS WKBreader to extract OGC Simple features of Layer layername 
   * and converts to SVG. Special case of MakeLayer for animated layers, 
   * only works for POINT data at present! 
   * ###################################################
   * 
   * @param layerName
   *          the name (as used in wms_layers table)
   * @param PkeyStr
   * @param nrLayerActions
   * @param layerActionScopes
   * @param layerActions
   * @param layerType
   *          "anim"
   * @param Classnames
   *          Array of classes, "defStyle" if style=single or no style requested
   * @param SVGstyles
   *          Array of style names, 1 if style=single, "defStyle" if no style
   *          requested
   * @param SVGmakeTrack
   *          boolean true=include tracks, false = do not
   * @param SVGtrackStyle
   *          String with SVG style for tracks
   * @param SVGanimDuration
   *          int with initial animation duration (in seconds)
   * @param styleAttrib
   *          attributes for style(s): ALL attribs are added to SVG (rim:attrib)
   *          AND IF styletype = chorochromatic => 1st atrib = classifying
   *          attrib IF styletype = animate => 1st attrib = object identifier,
   *          2nd = time attrib
   * @param relativeMode
   *          true: use L=absolute lineto's (more precise); false: l=relative
   *          lineto's (more compact)
   * @param FalseOriginX
   * @param FalseOriginY
   * @param Precision
   * @param layerSQL
   *          The SQL for extracting layer data from the table
   * @return A StringBuffer containing the SVG fragment for the layer
   * @param myWMSTime
   *          The WMSTime Object (period requested, etc)
   * @throws RIMapperException
   */
  public StringBuffer makeLayerAnimated(
      String layerName,
      String PkeyStr,
      int nrLayerActions,
      String[] layerActionScopes,
      String[] layerActions,
      String layerType,
      String[] Classnames,
      String[] SVGstyles,
      boolean SVGmakeTrack,
      String SVGtrackStyle,
      long SVGanimDuration,
      String styleAttrib,
      boolean relativeMode,
      double FalseOriginX,
      double FalseOriginY,
      long Precision,
      String layerSQL,
      WMSTime myWMSTime) throws RIMapperException {

    StringBuffer S = new StringBuffer();
    String featureAction = "";
    String featureAttrib = "";
    ResultSet rs1 = null;
    SQL = layerSQL;
    String FIDStr = null;
    byte[] WKB = null;

    Boolean AnimStarted = false;
    String currAnimatedObject = "none";
    String RWStartTimeISO = myWMSTime.getFromDateTimeISO();
    String RWEndTimeISO = myWMSTime.getToDateTimeISO();
    int animObjectCount = 0;
    int animStepsCount = 0;
    int maxAnimSteps = 1024; // ***TMP fixed for now;
    int[] animObjectSteps = new int[maxAnimSteps];
    String[] animObjectNames = new String[maxAnimSteps];
    Point p_prev = null;
    StringBuffer objectAnimSVG = new StringBuffer();
    StringBuffer trackAnimSVG = new StringBuffer();

    String ObjectIdentifier = null;
    String ObjectIdentifier_prev = null;

    long RWStartTimeSeconds = 0;
    long RWEndTimeSeconds = 0;
    long RWTimeOffset = 0;
    long RWDurationSeconds = 0;
    double TimeScale = 0;
    long AnimStartTime = 0;
    long AnimEndTime = 0;
    long AnimDuration = SVGanimDuration; // default duration in seconds

    double PrevObjectTime = 0;
    double ObjectTime = 0;

    try {

      rs1 = myDBconn.Query(SQL);
      while (rs1.next()) { // loop through records found
        // gotSQLResult = true;
        FIDStr = rs1.getString(1); // get FIDStr
        WKB = rs1.getBytes(2); // get OGC WellKnownBinary

        RWStartTimeSeconds = rs1.getLong("time_from_seconds"); // ...and the
                                                               // start time in
                                                               // epoch secs
        RWEndTimeSeconds = rs1.getLong("time_to_seconds"); // ...and end time in
                                                           // epoch secs
        RWTimeOffset = RWStartTimeSeconds;
        RWDurationSeconds = RWEndTimeSeconds - RWStartTimeSeconds;
        // now calculate with offset so it starts at 0:
        RWStartTimeSeconds = RWStartTimeSeconds - RWTimeOffset; // should be 0
        RWEndTimeSeconds = RWEndTimeSeconds - RWTimeOffset; // reset with offset
        TimeScale = (double) AnimDuration / (double) RWDurationSeconds;
        AnimStartTime = 0;
        AnimEndTime = (AnimStartTime + AnimDuration);

        ObjectIdentifier = rs1.getString("time_object_identifier"); // get
                                                                    // objectidentifier...
        ObjectTime = rs1.getLong("time_seconds"); // ...and the object time in
                                                  // epoch secs
        ObjectTime = (ObjectTime - RWTimeOffset) * TimeScale;

        if (AnimStarted) { // creating an animation...
          if (!currAnimatedObject.equals(ObjectIdentifier)) {

            // Close PREVIOUS 'running' Animation Object

            if (PrevObjectTime < AnimEndTime) { // only remove object if
              objectAnimSVG.append("<set id='RemoveAnim_" + ObjectIdentifier_prev + "'" + " attributeName='visibility'"
                  + " begin='" + (PrevObjectTime) + "' " + " from='visible'" + " to='hidden'" + " fill='freeze' />");
            }

            objectAnimSVG.append("</circle>");

            AnimStarted = false;
            animObjectSteps[animObjectCount] = animStepsCount;
            animObjectNames[animObjectCount] = ObjectIdentifier_prev;
            animObjectCount++;
            animStepsCount = 0;
            ObjectIdentifier_prev = ObjectIdentifier;
          }
        }

        if (WKB == null) {
          errorMessage = Messages.getString("MakeSVG.14") + layerName + "(" //$NON-NLS-1$
              + PkeyStr + "=" + FIDStr + Messages.getString("MakeSVG.17"); //$NON-NLS-2$
          throw new RIMapperException(errorMessage);
        }

        if (AnimStarted) { // Animation of Object is 'running'

          // add ANIMATEs:

          Geometry g = new WKBReader().read(WKB);
          if (g.isEmpty()) {
            errorMessage = Messages.getString("MakeSVG.15") + layerName + "(" //$NON-NLS-1$
                + PkeyStr + "=" + FIDStr + Messages.getString("MakeSVG.16"); //$NON-NLS-2$
            throw new RIMapperException(errorMessage);
          } else if (!g.isValid()) {
            errorMessage = "[MakeSVG.MakeLayers] WKTReader: layer=" + layerName + "(" + PkeyStr
                + "=" + FIDStr + Messages.getString("MakeSVG.18"); //$NON-NLS-2$
            throw new RIMapperException(errorMessage);
          }

          if (g instanceof Point) { // ***using small SVG circle to depict point
            // TODO: make Point visualisation more generic/better
            Point p = (Point) g;

            Coordinate p1 = getSVGPoint(p_prev, FalseOriginX, FalseOriginY, Precision);
            Coordinate p2 = getSVGPoint(p, FalseOriginX, FalseOriginY, Precision);

            objectAnimSVG.append("<animate ");
            objectAnimSVG.append(" id='Xanim_" + ObjectIdentifier + "_" + animStepsCount + "' attributeName='cx' "
                + " begin='" + (PrevObjectTime) + "' " + " from='" + p1.x + "'" + " to='" + p2.x + "'" + " dur='"
                + ((ObjectTime - PrevObjectTime)) + "' " + " calcMode='linear' fill='freeze' />");
            objectAnimSVG.append("<animate ");
            objectAnimSVG.append(" id='Yanim_" + ObjectIdentifier + "_" + animStepsCount + "' attributeName='cy'"
                + " begin='" + (PrevObjectTime) + "' " + " from='" + p1.y + "'" + " to='" + p2.y + "'" + " dur='"
                + ((ObjectTime - PrevObjectTime)) + "' " + " calcMode='linear' fill='freeze' />");

            if (SVGmakeTrack) {
              // make line and calculate length to use in dashoffset animation
              Coordinate[] CA = {p1, p2};
              LineString trackLine = new GeometryFactory().createLineString(CA);
              double trackLength = trackLine.getLength();
              trackAnimSVG.append("<path id='" + ObjectIdentifier + "_track_" + animStepsCount + "' d='M" + p1.x + ","
                  + p1.y + " L" + p2.x + "," + p2.y + "'" + " stroke-dasharray='" + trackLength + "," + trackLength
                  + "' stroke-dashoffset='" + trackLength + "'" + " >");
              trackAnimSVG.append("<animate id='dashAnim_" + ObjectIdentifier + "_" + animStepsCount
                  + "' attributeName='stroke-dashoffset'" + " begin='" + (PrevObjectTime) + "' " + " from='"
                  + trackLength + "'" + " to='0'" + " dur='" + ((ObjectTime - PrevObjectTime)) + "' "
                  + " calcMode='linear' fill='freeze' />");
              trackAnimSVG.append("</path>");
            }
            

            // store for next steps:
            PrevObjectTime = ObjectTime;
            p_prev = p;

          } else {
            errorMessage = " [" + Messages.getString("MakeSVG.19") + layerName + " - type " + g.getGeometryType()
                + "] " + Messages.getString("MakeSVG.21"); //$NON-NLS-1$
            throw new RIMapperException(errorMessage);
          }
          if (animStepsCount <= maxAnimSteps) {
            animStepsCount++;
          } else {
            throw new RIMapperException("Too many animation objects! [currently limited to " + maxAnimSteps + "] "); // TODO:
                                                                                                                    // better
          }

        } else { // ****** Anim Not Started

          // Start Animation object:
          AnimStarted = true;
          currAnimatedObject = ObjectIdentifier;
          animStepsCount = 0;
          ObjectIdentifier_prev = ObjectIdentifier;

          Geometry g = new WKBReader().read(WKB);
          if (g.isEmpty()) {
            errorMessage = Messages.getString("MakeSVG.15") + layerName + "(" //$NON-NLS-1$
                + PkeyStr + "=" + FIDStr + Messages.getString("MakeSVG.16"); //$NON-NLS-2$
            throw new RIMapperException(errorMessage);
          } else if (!g.isValid()) {
            errorMessage = "[MakeSVG.MakeLayers] WKTReader: layer=" + layerName + "(" + PkeyStr
                + "=" + FIDStr + Messages.getString("MakeSVG.18"); //$NON-NLS-2$
            throw new RIMapperException(errorMessage);
          }

          if (g instanceof Point) { // ***using SVG circle to depict point
            Point p = (Point) g;
            p_prev = p;

            String circleRadius = "0";

            featureAttrib = makeAttribsFromDB(styleAttrib, layerName, PkeyStr, FIDStr);

            objectAnimSVG.append("<circle ");
            if (layerType.equals("chorochromatic")) {
              String AttribValue = getAttribFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
              for (int z = 0; z < Classnames.length; z++) {
                if (Classnames[z].equalsIgnoreCase(AttribValue)) {
                  objectAnimSVG.append(" " + SVGstyles[z]);
                  circleRadius = extractRadius(SVGstyles[z]);
                }
              }
            } else { // layertype = "single"
              circleRadius = extractRadius(SVGstyles[0]);
            }
            // this uses a trick to get radius of circle from css-type svg style.
            // radius in svg-style table defined as radius="x.x"; ignored as css
            // style
            // but parsed out and used for svg circle object r
            // TODO: make Point visualisation more generic/better

            objectAnimSVG.append(featureAction + featureAttrib + " id='" + layerName + "_" + ObjectIdentifier
                + "' cx='" + getSVGPoint(p, FalseOriginX, FalseOriginY, Precision).x 
                + "' cy='" + getSVGPoint(p, FalseOriginX, FalseOriginY, Precision).y 
                + "' r='" + circleRadius + "'");

            if (ObjectTime > 0.0) { // only do if obj appears after anim start
              objectAnimSVG.append(" visibility='hidden' >" // hide at first
                  + "<set id='ShowAnim_" + ObjectIdentifier + "'" + " attributeName='visibility'"
                  + " begin='"
                  + (ObjectTime) + "' " + " from='hidden'" + " to='visible'" + " fill='freeze' />");
            } else { // show from beginning
              objectAnimSVG.append(" visibility='visible' >");
            }

            PrevObjectTime = ObjectTime; // store for next step

          } else {
            errorMessage = " [" + Messages.getString("MakeSVG.19") + layerName + " - type " + g.getGeometryType()
                + "] " + Messages.getString("MakeSVG.21"); //$NON-NLS-1$
            throw new RIMapperException(errorMessage);
          }

        } // NOT animStarted
      } // while loop through records found

      if (AnimStarted) { // finish last animated element

        if (PrevObjectTime < AnimEndTime) { // only do if obj dissapears befor
                                            // anim end
          objectAnimSVG.append("<set id='RemoveAnim_" + ObjectIdentifier_prev + "'" + " attributeName='visibility'"
              + " begin='" + (PrevObjectTime) + "' " + " from='visible'" + " to='hidden'" + " fill='freeze' />");
        }

        objectAnimSVG.append("</circle>");

        AnimStarted = false;
        animObjectSteps[animObjectCount] = animStepsCount;
        animObjectNames[animObjectCount] = ObjectIdentifier_prev;
        animObjectCount++;
        animStepsCount = 0;
      }

      // now write the animations we have built:

      if (SVGmakeTrack) { // ony make if maketrack = TRUE in svg_styles table
        S.append("\n<g id='Track_Animation' ");
        S.append(" " + SVGtrackStyle);
        S.append(" >");
        S.append(trackAnimSVG);
        S.append("</g> ");
      }

      S.append("\n<g id='Object_Animation' ");
      if (SVGstyles[0] != null) {
        S.append(" " + SVGstyles[0]);
      }
      S.append("\n rim:RWStartTimeISO = '" + RWStartTimeISO + "'");
      S.append("\n rim:RWEndTimeISO = '" + RWEndTimeISO + "'");
      S.append("\n rim:RWStartTimeSeconds = '" + RWStartTimeSeconds + "'");
      S.append("\n rim:RWDurationSeconds = '" + RWDurationSeconds + "'");
      S.append("\n rim:TimeScale = '" + TimeScale + "'");
      S.append("\n rim:AnimDuration = '" + AnimDuration + "'");
      S.append("\n rim:AnimStartTime = '" + AnimStartTime + "'");
      S.append("\n rim:AnimEndTime = '" + AnimEndTime + "'");

      S.append(" >");
      S.append(objectAnimSVG);
      S.append("</g> ");


    } catch (ParseException ex) {
      errorMessage = "[MakeSVG.MakeLayerAnimated] " + Messages.getString("MakeSVG.19") + layerName + " (PK=" + FIDStr //$NON-NLS-1$
          + Messages.getString("MakeSVG.24") + ex.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } // catch WKTreader exception
    catch (SQLException e) {
      errorMessage = "[MakeSVG.MakeLayerAnimated] " + " [SQL = " + SQL + "] " //$NON-NLS-1$
          + e.getMessage();
      throw new RIMapperException(errorMessage);
    } catch (RuntimeException e) {
      errorMessage = "[MakeSVG.MakeLayerAnimated] " + Messages.getString("MakeSVG.19") + layerName + " (PK=" + FIDStr //$NON-NLS-1$
          + Messages.getString("MakeSVG.27") + e.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } catch (OutOfMemoryError e) {
      throw new RIMapperException(Messages.getString("WMSGetMap.90") + e.toString()); //$NON-NLS-1$
    }

    return S;
  } // end function makeAnimLayer()
 

  /**
   * @return the withGUI
   */
  public boolean isWithGUI() {
    return withGUI;
  } //

  
  /**
   * ################################################### 
   * TEST VERSION FOR ALTERNATIVE ANIMATION CONSTRUCT USING animateMotion
   * Uses JTS WKBreader to extract OGC Simple features of Layer 
   * layername and converts to SVG. Special case of MakeLayer for 
   * animated layers, only works for POINT data at present! 
   * ###################################################
   * 
   * @param layerName
   *          the name (as used in wms_layers table)
   * @param PkeyStr
   * @param nrLayerActions
   * @param layerActionScopes
   * @param layerActions
   * @param layerType
   *          "anim"
   * @param Classnames
   *          Array of classes, "defStyle" if style=single or no style requested
   * @param SVGstyles
   *          Array of style names, 1 if style=single, "defStyle" if no style
   *          requested
   * @param SVGmakeTrack
   *          boolean true=include tracks, false = do not
   * @param SVGtrackStyle
   *          String with SVG style for tracks
   * @param SVGanimDuration
   *          int with initial animation duration (in seconds)
   * @param styleAttrib
   *          attributes for style(s): ALL attribs are added to SVG (rim:attrib)
   *          AND IF styletype = chorochromatic => 1st atrib = classifying
   *          attrib IF styletype = animate => 1st attrib = object identifier,
   *          2nd = time attrib
   * @param relativeMode
   *          true: use L=absolute lineto's (more precise); false: l=relative
   *          lineto's (more compact)
   * @param FalseOriginX
   * @param FalseOriginY
   * @param Precision
   * @param layerSQL
   *          The SQL for extracting layer data from the table
   * @return A StringBuffer containing the SVG fragment for the layer
   * @param myWMSTime
   *          The WMSTime Object (period requested, etc)
   * @throws RIMapperException
   */
  public StringBuffer makeLayerAnimated_animateMotion(
      String layerName,
      String PkeyStr,
      int nrLayerActions,
      String[] layerActionScopes,
      String[] layerActions,
      String layerType,
      String[] Classnames,
      String[] SVGstyles,
      boolean SVGmakeTrack,
      String SVGtrackStyle,
      long SVGanimDuration,
      String styleAttrib,
      boolean relativeMode,
      double FalseOriginX,
      double FalseOriginY,
      long Precision,
      String layerSQL,
      WMSTime myWMSTime) throws RIMapperException {

    StringBuffer S = new StringBuffer();
    String featureAction = "";
    String featureAttrib = "";
    ResultSet rs1 = null;
    SQL = layerSQL;
    String FIDStr = null;
    byte[] WKB = null;

    Boolean AnimStarted = false;
    String currAnimatedObject = "none";
    String RWStartTimeISO = myWMSTime.getFromDateTimeISO();
    String RWEndTimeISO = myWMSTime.getToDateTimeISO();
    int animObjectCount = 0;
    int animStepsCount = 0;
    int maxAnimSteps = 1024; // ***TMP fixed for now;
    int[] animObjectSteps = new int[maxAnimSteps];
    String[] animObjectNames = new String[maxAnimSteps];
    Point p_prev = null;
    StringBuffer objectAnimSVG = new StringBuffer();
    StringBuffer trackAnimSVG = new StringBuffer();

    String ObjectIdentifier = null;
    String ObjectIdentifier_prev = null;

    long RWStartTimeSeconds = 0;
    long RWEndTimeSeconds = 0;
    long RWTimeOffset = 0;
    long RWDurationSeconds = 0;
    double TimeScale = 0;
    long AnimStartTime = 0;
    long AnimEndTime = 0;
    long AnimDuration = SVGanimDuration; // default duration in seconds

    double PrevObjectTime = 0;
    double ObjectTime = 0;

    try {
      
      rs1 = myDBconn.Query(SQL);
      while (rs1.next()) { // loop through records found
        // gotSQLResult = true;
        FIDStr = rs1.getString(1); // get FIDStr
        WKB = rs1.getBytes(2); // get OGC WellKnownBinary

        RWStartTimeSeconds = rs1.getLong("time_from_seconds"); // ...and the
                                                               // start time in
                                                               // epoch secs
        RWEndTimeSeconds = rs1.getLong("time_to_seconds"); // ...and end time in
                                                           // epoch secs
        RWTimeOffset = RWStartTimeSeconds;
        RWDurationSeconds = RWEndTimeSeconds - RWStartTimeSeconds;
        // now calculate with offset so it starts at 0:
        RWStartTimeSeconds = RWStartTimeSeconds - RWTimeOffset; // should be 0
        RWEndTimeSeconds = RWEndTimeSeconds - RWTimeOffset; // reset with offset
        TimeScale = (double) AnimDuration / (double) RWDurationSeconds;
        AnimStartTime = 0;
        AnimEndTime = (AnimStartTime + AnimDuration);

        ObjectIdentifier = rs1.getString("time_object_identifier"); // get
                                                                    // objectidentifier...
        ObjectTime = rs1.getLong("time_seconds"); // ...and the object time in
                                                  // epoch secs
        ObjectTime = (ObjectTime - RWTimeOffset) * TimeScale;

        if (AnimStarted) { // creating an animation...
          if (!currAnimatedObject.equals(ObjectIdentifier)) {

            // Close PREVIOUS 'running' Animation Object

            if (PrevObjectTime < AnimEndTime) { // only remove object if
              objectAnimSVG.append("<set id='RemoveAnim_" + ObjectIdentifier_prev + "'" + " attributeName='visibility'"
                  + " begin='" + (PrevObjectTime) + "' " + " from='visible'" + " to='hidden'" + " fill='freeze' />");
            }

            objectAnimSVG.append("</circle>");

            AnimStarted = false;
            animObjectSteps[animObjectCount] = animStepsCount;
            animObjectNames[animObjectCount] = ObjectIdentifier_prev;
            animObjectCount++;
            animStepsCount = 0;
            ObjectIdentifier_prev = ObjectIdentifier;
          }
        }

        if (WKB == null) {
          errorMessage = Messages.getString("MakeSVG.14") + layerName + "(" //$NON-NLS-1$
              + PkeyStr + "=" + FIDStr + Messages.getString("MakeSVG.17"); //$NON-NLS-2$
          throw new RIMapperException(errorMessage);
        }

        if (AnimStarted) { // Animation of Object is 'running'

          // add ANIMATEs:

          Geometry g = new WKBReader().read(WKB);
          if (g.isEmpty()) {
            errorMessage = Messages.getString("MakeSVG.15") + layerName + "(" //$NON-NLS-1$
                + PkeyStr + "=" + FIDStr + Messages.getString("MakeSVG.16"); //$NON-NLS-2$
            throw new RIMapperException(errorMessage);
          } else if (!g.isValid()) {
            errorMessage = "[MakeSVG.MakeLayers] WKTReader: layer=" + layerName + "(" + PkeyStr
                + "=" + FIDStr + Messages.getString("MakeSVG.18"); //$NON-NLS-2$
            throw new RIMapperException(errorMessage);
          }

          if (g instanceof Point) { // ***using small SVG circle to depict point
            // TODO: make Point visualisation more generic/better
            Point p = (Point) g;

            Coordinate p1 = getSVGPoint(p_prev, FalseOriginX, FalseOriginY, Precision);
            Coordinate p2 = getSVGPoint(p, FalseOriginX, FalseOriginY, Precision);

            objectAnimSVG.append("<animateMotion ");
            objectAnimSVG.append(" id='aniMot_" + ObjectIdentifier + "_" + animStepsCount + "' attributeName='cx' "
                + " begin='" + (PrevObjectTime) + "' " 
                + " path='M" + p1.x + "," + p1.y + "L"  + p2.x + "," + p2.y +"'"
                + " dur='" + ((ObjectTime - PrevObjectTime)) + "' " 
                + " rotate='auto' fill='freeze' />");
           
            if (SVGmakeTrack) {
              // make line and calculate length to use in dashoffset animation
              Coordinate[] CA = {p1, p2};
              LineString trackLine = new GeometryFactory().createLineString(CA);
              double trackLength = trackLine.getLength();
              trackAnimSVG.append("<path id='" + ObjectIdentifier + "_track_" + animStepsCount + "' d='M" + p1.x + ","
                  + p1.y + " L" + p2.x + "," + p2.y + "'" + " stroke-dasharray='" + trackLength + "," + trackLength
                  + "' stroke-dashoffset='" + trackLength + "'" + " >");
              trackAnimSVG.append("<animate id='dashAnim_" + ObjectIdentifier + "_" + animStepsCount
                  + "' attributeName='stroke-dashoffset'" + " begin='" + (PrevObjectTime) + "' " + " from='"
                  + trackLength + "'" + " to='0'" + " dur='" + ((ObjectTime - PrevObjectTime)) + "' "
                  + " calcMode='linear' fill='freeze' />");
              trackAnimSVG.append("</path>");
            }
            

            // store for next steps:
            PrevObjectTime = ObjectTime;
            p_prev = p;

          } else {
            errorMessage = " [" + Messages.getString("MakeSVG.19") + layerName + " - type " + g.getGeometryType()
                + "] " + Messages.getString("MakeSVG.21"); //$NON-NLS-1$
            throw new RIMapperException(errorMessage);
          }
          if (animStepsCount <= maxAnimSteps) {
            animStepsCount++;
          } else {
            throw new RIMapperException("Too many animation objects! [currently limited to " + maxAnimSteps + "] "); // TODO:
                                                                                                                     // make
                                                                                                                     // better
          }

        } else { // ****** Anim Not Started

          // Start Animation object:
          AnimStarted = true;
          currAnimatedObject = ObjectIdentifier;
          animStepsCount = 0;
          ObjectIdentifier_prev = ObjectIdentifier;

          Geometry g = new WKBReader().read(WKB);
          if (g.isEmpty()) {
            errorMessage = Messages.getString("MakeSVG.15") + layerName + "(" //$NON-NLS-1$
                + PkeyStr + "=" + FIDStr + Messages.getString("MakeSVG.16"); //$NON-NLS-2$
            throw new RIMapperException(errorMessage);
          } else if (!g.isValid()) {
            errorMessage = "[MakeSVG.MakeLayers] WKTReader: layer=" + layerName + "(" + PkeyStr
                + "=" + FIDStr + Messages.getString("MakeSVG.18"); //$NON-NLS-2$
            throw new RIMapperException(errorMessage);
          }

          if (g instanceof Point) { // ***using SVG circle to depict point
            Point p = (Point) g;
            p_prev = p;

            String circleRadius = "0";

            featureAttrib = makeAttribsFromDB(styleAttrib, layerName, PkeyStr, FIDStr);

            objectAnimSVG.append("<circle ");
            if (layerType.equals("chorochromatic")) {
              String AttribValue = getAttribFromDB(styleAttrib, layerName, PkeyStr, FIDStr);
              for (int z = 0; z < Classnames.length; z++) {
                if (Classnames[z].equalsIgnoreCase(AttribValue)) {
                  objectAnimSVG.append(" " + SVGstyles[z]);
                  circleRadius = extractRadius(SVGstyles[z]);
                }
              }
            } else { // layertype = "single"
              circleRadius = extractRadius(SVGstyles[0]);
            }
            // this uses a trick to get radius of circle from css-type svg style.
            // radius in svg-style table defined as radius="x.x"; ignored as css
            // style
            // but parsed out and used for svg circle object r
            // TODO: make Point visualisation more generic/better

            objectAnimSVG.append(featureAction + featureAttrib + " id='" + layerName + "_" + ObjectIdentifier
                + "' cx='" + getSVGPoint(p, FalseOriginX, FalseOriginY, Precision).x + "' cy='"
                + getSVGPoint(p, FalseOriginX, FalseOriginY, Precision).y + "' r='" + circleRadius + "'" 
                + " transform='translate(" 
                + (-1*(getSVGPoint(p, FalseOriginX, FalseOriginY, Precision).x))
                + "," + (-1*(getSVGPoint(p, FalseOriginX, FalseOriginY, Precision).y))
                + ")'"
                );

            if (ObjectTime > 0.0) { // only do if obj appears after anim start
              objectAnimSVG.append(" visibility='hidden' >" // hide at first
                  + "<set id='ShowAnim_" + ObjectIdentifier + "'" + " attributeName='visibility'"
                  + " begin='"
                  + (ObjectTime) + "' " + " from='hidden'" + " to='visible'" + " fill='freeze' />");
            } else { // show from beginning
              objectAnimSVG.append(" visibility='visible' >");
            }

            PrevObjectTime = ObjectTime; // store for next step

          } else {
            errorMessage = " [" + Messages.getString("MakeSVG.19") + layerName + " - type " + g.getGeometryType()
                + "] " + Messages.getString("MakeSVG.21"); //$NON-NLS-1$
            throw new RIMapperException(errorMessage);
          }

        } // NOT animStarted
      } // while loop through records found

      if (AnimStarted) { // finish last animated element

        if (PrevObjectTime < AnimEndTime) { // only do if obj dissapears befor
                                            // anim end
          objectAnimSVG.append("<set id='RemoveAnim_" + ObjectIdentifier_prev + "'" + " attributeName='visibility'"
              + " begin='" + (PrevObjectTime) + "' " + " from='visible'" + " to='hidden'" + " fill='freeze' />");
        }

        objectAnimSVG.append("</circle>");

        AnimStarted = false;
        animObjectSteps[animObjectCount] = animStepsCount;
        animObjectNames[animObjectCount] = ObjectIdentifier_prev;
        animObjectCount++;
        animStepsCount = 0;
      }

      // now write the animations we have built:

      if (SVGmakeTrack) { // ony make if maketrack = TRUE in svg_styles table
        S.append("\n<g id='Track_Animation' ");
        S.append(" " + SVGtrackStyle);
        S.append(" >");
        S.append(trackAnimSVG);
        S.append("</g> ");
      }

      S.append("\n<g id='Object_Animation' ");
      if (SVGstyles[0] != null) {
        S.append(" " + SVGstyles[0]);
      }
      S.append("\n rim:RWStartTimeISO = '" + RWStartTimeISO + "'");
      S.append("\n rim:RWEndTimeISO = '" + RWEndTimeISO + "'");
      S.append("\n rim:RWStartTimeSeconds = '" + RWStartTimeSeconds + "'");
      S.append("\n rim:RWDurationSeconds = '" + RWDurationSeconds + "'");
      S.append("\n rim:TimeScale = '" + TimeScale + "'");
      S.append("\n rim:AnimDuration = '" + AnimDuration + "'");
      S.append("\n rim:AnimStartTime = '" + AnimStartTime + "'");
      S.append("\n rim:AnimEndTime = '" + AnimEndTime + "'");

      S.append(" >");
      S.append(objectAnimSVG);
      S.append("</g> ");


    } catch (ParseException ex) {
      errorMessage = "[MakeSVG.MakeLayerAnimated] " + Messages.getString("MakeSVG.19") + layerName + " (PK=" + FIDStr //$NON-NLS-1$
          + Messages.getString("MakeSVG.24") + ex.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } // catch WKTreader exception
    catch (SQLException e) {
      errorMessage = "[MakeSVG.MakeLayerAnimated] " + " [SQL = " + SQL + "] " //$NON-NLS-1$
          + e.getMessage();
      throw new RIMapperException(errorMessage);
    } catch (RuntimeException e) {
      errorMessage = "[MakeSVG.MakeLayerAnimated] " + Messages.getString("MakeSVG.19") + layerName + " (PK=" + FIDStr //$NON-NLS-1$
          + Messages.getString("MakeSVG.27") + e.toString(); //$NON-NLS-1$
      throw new RIMapperException(errorMessage);
    } catch (OutOfMemoryError e) {
      throw new RIMapperException(Messages.getString("WMSGetMap.90") + e.toString()); //$NON-NLS-1$
    }

    return S;
  } // end function makeAnimLayer_2()
  
  
} // end of public class makeSVG
