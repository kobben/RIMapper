/**
 * 
 */
package nl.itc.RIMapper; 

import java.sql.ResultSet;
import java.sql.SQLException;

import nl.itc.RIMapper.DBconn;
import nl.itc.RIMapper.RIMapperException;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

/**
 * OGC WMS 1.1.1 compatible Web Map Capabilities class.<br/> 
 *  Note that the class holds the data needed for an OGC compatible
 *  Capabilities response, PLUS extras needed for RIMapperWMS specific
 *  capabilities (eg. SVG GUI, animation, etcetera).
 * <br/>&copy;2004-2011
 * International Institute for Geo-information Science and Earth Observation (ITC)
 * <br/>Licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 2.5
 * License. see <a href="http://creativecommons.org/licenses/by-nc-sa/2.5/"
 * target="_blank"> http://creativecommons.org/licenses/by-nc-sa/2.5/</a>
 * 
 * @author Barend K&ouml;bben - <a href="mailto:kobben@itc.nl"
 *         target="_blank">kobben@itc.nl</a>
 * @version 2.2 [September 2011]
 */
// Major changes:
//1.0b [Dec 2006] - first released version (public beta)
//2.0 [Sep 2010] - rewritten to non-static class which will be instatiated
//2.1 [nov 2010]- now puts TIME dimension + extent in Capabilities
//2.2 [sep 2011]- now puts 'cascaded' attribute in layer_cascaded 
//              - gets extra data for animations: SVGmakeTrack, SVGtrackStyle and AnimDuration

public class WMSCapabilities {

	private String metadata_name = "EMPTY"; //$NON-NLS-1$

	private String metadata_title;

	private String metadata_abstract;

	private String[] metadata_keyword_list;

	private String metadata_contact_electronic_mail_address;

	private String metadata_fees;

	private String metadata_access_constraints;

	private int numLayers;

	private int numStyles;

	private int numSVGStyles;

  private String[] layer_name;
  
  private String[] layer_type;

	private String[] layer_title;

	private String[] layer_pkey;

	private String[] layer_geometry_col;

	private String[] layer_abstract;

	private String[][] layer_keyword_list;

  private String[] layer_dimension;

  private String[] layer_dimension_column;

  private String[] layer_extent;

	private String[][] layer_style_list;

	private String[] layer_metadata_url;

	private String[] layer_queryable;

  private String[] layer_opaque;
  
  private String[] layer_cascaded;

	private Envelope[] layer_lat_lon_bounding_box;
	
  private Envelope[][] layer_bounding_box_list;

	private String[][] layer_srs_epsg_list;

	private String[] layer_scale_hint;

  private String[] layer_remote_url;
  
  private String[] layer_remote_layers;
  
  private String[] layer_remote_styles;

	private String[] style_name;

	private String[] style_title;

	private String[] style_abstract;

	private String[] style_legend_url_format;

	private String[] style_legend_url_height;

	private String[] style_legend_url_width;

	private String[] style_legend_url_online_resource;

  private String[] svg_style;

  private String[] svg_style_name;

  private boolean[] svg_style_maketrack;

  private String[] svg_style_trackstyle;
  
  private long[] svg_style_animduration;

	private static String currentSQL; // used for debugging

	
	
	
	private boolean existsInDB(DBconn myDBconn, String checkTable, String checkColumn) throws RIMapperException, SQLException {
    boolean itExists = false;
    if (checkColumn == null) {
      checkColumn = ""; 
    } else {
      checkColumn = " AND t.COLUMN_NAME ='" + checkColumn + "'"; 
    }
    String SQL = 
      "SELECT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS as t WHERE t.TABLE_SCHEMA = 'public' AND t.TABLE_NAME = '" 
      + checkTable + "'" 
      + checkColumn 
      + ") AS table_exists;";
    ResultSet rs = myDBconn.Query(SQL);
    if (rs == null) {
      throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
    } else {
      while (rs.next()) { // loop through records found, should be only one
        itExists = rs.getBoolean("table_exists");
      }
    }
    return itExists;
	}

	/**
	 * Sets the one BBOX item in LatLon per layer, where necessary reprojecting from 'native' SRS to latlon (EPSG:4326).
	 * @param myDBconn
	 *          the DB connection defining the WMS instance.
	 * @param layerNum
	 * 					the current layer
	 * @return a JTS {@link com.vividsolutions.jts.geom.Envelope} geometry
	 * @throws RIMapperException
	 * @throws SQLException
	 */
	private Envelope setLayer_lat_lon_bounding_box(DBconn myDBconn, int layerNum)
			throws RIMapperException, SQLException {

		Envelope myLatLonBoundingBox = new Envelope();
		myLatLonBoundingBox.init(-180.0, 180.0, -90.0, 90.0); // whole world
		String EnvelopeStr = null;

		try {
			String T = getLayer_srs_epsg_list()[layerNum][0];
			if (T.equalsIgnoreCase("-1") || layer_srs_epsg_list[layerNum] == null) { //$NON-NLS-1$
				// No valid SRS -> assume BoundingBox = whole world
			} else {
				// valid SRS -> transform from SRS to latlon in EPSG:4326
				String SQL = "SELECT asText(envelope(extent(transform(" //$NON-NLS-1$
						+ layer_geometry_col[layerNum] + ",4326))))" + " FROM " //$NON-NLS-1$ //$NON-NLS-2$
						+ layer_name[layerNum];
				currentSQL = SQL;
				ResultSet rs = myDBconn.Query(SQL);
				if (rs == null) {
					throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
				} else {
					if (rs.next()) { // get record found
						EnvelopeStr = rs.getString(1);
						Geometry g = new WKTReader().read(EnvelopeStr);
						if (g.isEmpty()) {
							throw new RIMapperException(Messages.getString("WMSCapabilities.6")); //$NON-NLS-1$
						}
						myLatLonBoundingBox = g.getEnvelopeInternal();
					} // end while
				}
			}
		} catch (ParseException e) {
			throw new RIMapperException(
					Messages.getString("WMSCapabilities.7") + EnvelopeStr //$NON-NLS-1$
							+ " ]"); //$NON-NLS-1$
		}
		return myLatLonBoundingBox;
	}

	/**
	 * Sets the BBOX items per layer, one for every SRS supported by this WMS instance. Where necessary reprojecting from 'native' SRS to requested SRS.
	 * @param myDBconn
	 *          the DB connection defining the WMS instance.
	 * @param layerNum
	 * 					the current layer
	 * @param epsg_list
	 * 					the list of SRSes supported by this WMS instance.
	 * @return an array of JTS {@link com.vividsolutions.jts.geom.Envelope} geometries
	 * @throws RIMapperException
	 * @throws SQLException
	 */
	private Envelope[] setLayer_bounding_box_list(DBconn myDBconn, int layerNum,
			String[] epsg_list) throws RIMapperException, SQLException {

		int L = epsg_list.length;
		Envelope[] myBoundingBox = new Envelope[L];

		String EnvelopeStr = null;

		try {
			for (int j = 0; j < L; j++) {
				myBoundingBox[j] = new Envelope();
				myBoundingBox[j].init(-180.0, 180.0, -90.0, 90.0);
				// = whole world in EPSG:4326
				String SQL = null;
				String T = layer_srs_epsg_list[layerNum][0];
				if (T.equalsIgnoreCase("-1") || layer_srs_epsg_list[layerNum] == null) { //$NON-NLS-1$
					// No valid SRS -> do not try to transform, use 'native' coordinates
					SQL = "SELECT asText(envelope(extent(" + layer_geometry_col[layerNum] + ")))" //$NON-NLS-1$ //$NON-NLS-2$
							+ " FROM " + layer_name[layerNum]; //$NON-NLS-1$
				} else {
					// valid SRS -> transform from 'native' SRS to requested SRS
					SQL = "SELECT asText(envelope(extent(transform(" + layer_geometry_col[layerNum] //$NON-NLS-1$
							+ "," + epsg_list[j] + "))))" + " FROM " + layer_name[layerNum]; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				currentSQL = SQL;
				ResultSet rs = myDBconn.Query(SQL);
				if (rs == null) {
					throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
				} else {
					if (rs.next()) { // get record found
						EnvelopeStr = rs.getString(1);
						Geometry g = new WKTReader().read(EnvelopeStr);
						if (g.isEmpty()) {
							throw new RIMapperException(Messages.getString("WMSCapabilities.18")); //$NON-NLS-1$
						}
						myBoundingBox[j] = g.getEnvelopeInternal();
					} // end while
				} // end if
			} // end for
		} catch (ParseException e) {
			throw new RIMapperException(
					Messages.getString("WMSCapabilities.19") + EnvelopeStr //$NON-NLS-1$
							+ Messages.getString("WMSCapabilities.0") + e.toString()); //$NON-NLS-1$
		}
		return myBoundingBox;
	}


  /**
   * Sets the dimension extent per layer, by retrieving from designated column in the DB
   * table. Currently only "time" dimension is supported
   * 
   * @param myDBconn
   *          the DB connection defining the WMS instance.
   * @param layerNum
   *          the current layer
   * @param dimension_column
   *          the name of the column holding the dimension (=time) data
   * @return a String (ISO TIME format) to set the layer_extent[i] to.
   * @throws RIMapperException
   * @throws SQLException
   */
  private String setLayer_dimension_extent(DBconn myDBconn, int layerNum,
      String dimension_column) throws RIMapperException, SQLException {

    String SQL = null;
    String timeStr = null;
    SQL = "SELECT MIN(to_timestamp(" + dimension_column
        + ",'" + WMSTime.ISO_TIME_PATTERN + "')) AS minDate, MAX(to_timestamp(" + dimension_column
        + ",'" + WMSTime.ISO_TIME_PATTERN + "')) AS maxDate FROM " + layer_name[layerNum];
    currentSQL = SQL; // put in global var for Error handling
    ResultSet rs = myDBconn.Query(SQL);
    if (rs == null) {
      throw new RIMapperException("No result from Query: " + SQL);
    } else {
      if (rs.next()) { // get record found
        timeStr = rs.getString("minDate") + "/" + rs.getString("maxDate");
      } // end while
    } // end if
    return timeStr;
  }

  
	/**
	 * Collects the WMS styles from the DB table.
	 * 
	 * @param myDBconn
	 *          the DB connection defining the WMS instance.
	 * @throws RIMapperException
	 */
	private void CollectStyles(DBconn myDBconn) throws RIMapperException {

		String SQL;
		ResultSet rs;

		try {
			int numStyles = 0;
			// below is a stupid way to find&set the array sizes
			// TODO: find better way to find&set the array sizes
			SQL = "SELECT COUNT(id) FROM wms_styles"; //$NON-NLS-1$
			rs = myDBconn.Query(SQL);
			if (rs == null) {
				throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
			} else {
				while (rs.next()) { // should be only 1
				  numStyles = rs.getInt("count");
				} 
			}
			style_name = new String[numStyles];
			style_title = new String[numStyles];
			style_abstract = new String[numStyles];
			style_legend_url_format = new String[numStyles];
			style_legend_url_height = new String[numStyles];
			style_legend_url_width = new String[numStyles];
			style_legend_url_online_resource = new String[numStyles];
			
			SQL = "SELECT * FROM wms_styles"; //$NON-NLS-1$
			rs = myDBconn.Query(SQL);
			if (rs == null) {
				throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
			} else {
			  int i = 0;
				while (rs.next()) { // loop through records
					style_name[i] = rs.getString("name"); //$NON-NLS-1$
					style_title[i] = rs.getString("title"); //$NON-NLS-1$
					style_abstract[i] = rs.getString("abstract"); //$NON-NLS-1$
					style_legend_url_format[i] = rs.getString("legend_url_format"); //$NON-NLS-1$
					style_legend_url_height[i] = rs.getString("legend_url_height"); //$NON-NLS-1$
					style_legend_url_width[i] = rs.getString("legend_url_width"); //$NON-NLS-1$
					style_legend_url_online_resource[i] = rs
							.getString("legend_url_online_resource"); //$NON-NLS-1$
					i++;
				} // end while
			}
		} catch (SQLException e) {
			throw new RIMapperException(Messages.getString("WMSCapabilities.1") + e.getMessage()); //$NON-NLS-1$
		}
	}

	/**
	 * Loops through list of SVG styles available for this WMS instance.<br/> Note that
	 * svg-syles are an extension to the WMS Styles and as such are not part of the WMS
	 * spec. They are therefore NOT added to the GetCapabilities response output, only added
	 * to the Capabilities class to be used internally by RIMapperSVG in responding to
	 * GetMap requests...
	 * 
	 * @param myDBconn
	 *          the DB connection defining the WMS instance.
	 * @throws RIMapperException
	 */
	private void CollectSVGStyles(DBconn myDBconn) throws RIMapperException {

		String SQL;
		ResultSet rs;

		try {
			numSVGStyles= 0;
			// below is a stupid way to find&set the array sizes
			// TODO: find better way to find&set the array sizes
			SQL = "SELECT COUNT(id) FROM svg_styles"; //$NON-NLS-1$
			rs = myDBconn.Query(SQL);
			if (rs == null) {
				throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
			} else {
        while (rs.next()) { // should be only 1
          numSVGStyles = rs.getInt("count");
        } 
      }
			svg_style_name = new String[numSVGStyles];
      svg_style = new String[numSVGStyles];
      svg_style_maketrack = new boolean[numSVGStyles]; 
      svg_style_trackstyle = new String[numSVGStyles];
      svg_style_animduration = new long[numSVGStyles];
      
			if (existsInDB(myDBconn, "svg_styles", "maketrack")) {
			  //new style table with animation style columns
	      SQL = "SELECT name,style,maketrack,trackstyle,animation_duration FROM svg_styles"; //$NON-NLS-1$
	      rs = myDBconn.Query(SQL);
	      if (rs == null) {
	        throw new RIMapperException(Messages.getString("WMSCapabilities.36") + SQL); //$NON-NLS-1$
	      } else {
	        int i = 0;
	        while (rs.next()) { // loop through records 
	          svg_style_name[i] = rs.getString("name"); //$NON-NLS-1$
            svg_style[i] = rs.getString("style"); //$NON-NLS-1$
            svg_style_maketrack[i]  = rs.getBoolean("maketrack");//$NON-NLS-1$
            svg_style_trackstyle[i] = rs.getString("trackstyle"); //$NON-NLS-1$
            svg_style_animduration[i] = rs.getLong("animation_duration"); //$NON-NLS-1$
	          i++;
	        } // end while
	      }
			} else {
        //old style table without animation style columns
	      SQL = "SELECT name,style FROM svg_styles"; //$NON-NLS-1$
	      rs = myDBconn.Query(SQL);
	      if (rs == null) {
	        throw new RIMapperException(Messages.getString("WMSCapabilities.36") + SQL); //$NON-NLS-1$
	      } else {
	        int i = 0;
	        while (rs.next()) { // loop through records 
	          svg_style_name[i] = rs.getString("name"); //$NON-NLS-1$
	          svg_style[i] = rs.getString("style"); //$NON-NLS-1$
	          i++;
	        } // end while
	      }
			}
		} catch (SQLException e) {
			throw new RIMapperException(Messages.getString("WMSCapabilities.39") + e.getMessage()); //$NON-NLS-1$
		}
	}

	/**
	 * Constructs capabilities of a given WMS instance (derived from DB)
	 * and builds up an OGC WMS 1.1.1 compatible Capabilities class.
	 * 
	 * @param myDBconn
	 *          the DB connection defining the WMS instance.
	 * @throws RIMapperException
	 */
	public WMSCapabilities(DBconn myDBconn) throws RIMapperException {

		String SQL;
		ResultSet rs;
		numLayers = 0;
		numStyles = 0;
		String T = new String();
		boolean CascadedLayersTableExists;

		try {
			SQL = "SELECT * FROM service_metadata WHERE name='OGC:WMS'"; //$NON-NLS-1$
			currentSQL = SQL;
			rs = myDBconn.Query(SQL);
			if (rs == null) {
				throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
			} else {
				metadata_name = "OGC:WMS"; // fixed by spec! //$NON-NLS-1$
				if (rs.next()) { // should only be one
					metadata_title = rs.getString("title"); //$NON-NLS-1$
					metadata_abstract = rs.getString("abstract"); //$NON-NLS-1$
					T = rs.getString("keyword_list"); //$NON-NLS-1$
					if (T != null)
						metadata_keyword_list = T.split(";"); //$NON-NLS-1$
					metadata_contact_electronic_mail_address = rs
							.getString("contact_electronic_mail_address"); //$NON-NLS-1$
					metadata_fees = rs.getString("fees"); //$NON-NLS-1$
					metadata_access_constraints = rs.getString("access_constraints"); //$NON-NLS-1$
				} // end if
			}
			
			CollectStyles(myDBconn);
			CollectSVGStyles(myDBconn);			
			
			// below is a stupid way to find&set the array sizes
			// TODO: find better way to find&set the array sizes
		  // FIRST: COUNT SVG LAYERS:
			SQL = "SELECT COUNT(id) FROM wms_layers"; //$NON-NLS-1$
			rs = myDBconn.Query(SQL);
			if (rs == null) {
				throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
			} else {
				while (rs.next()) { // shoudl be only one
				  numLayers = rs.getInt(1);
				} // end while
			}
		  // SECOND: COUNT CASCADED WSM LAYERS:
			// first check if it exist: for backward compatability this table is not obligatory!
      if (existsInDB(myDBconn, "wms_cascaded_layers", null)) { //there is a cascaded layers table
        CascadedLayersTableExists = true;
        SQL = "SELECT COUNT(id) FROM wms_cascaded_layers"; //$NON-NLS-1$
        rs = myDBconn.Query(SQL);
        if (rs == null) {
          throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
        } else {
          while (rs.next()) { // should be only one
            numLayers = numLayers + rs.getInt(1);
          } // end while
        }          
      }  else {
        CascadedLayersTableExists = false;
      }
      
      layer_type = new String[numLayers];
			layer_name = new String[numLayers];
      layer_pkey = new String[numLayers];
			layer_title = new String[numLayers];
			layer_geometry_col = new String[numLayers];
			layer_abstract = new String[numLayers];
			layer_metadata_url = new String[numLayers];
			layer_keyword_list = new String[numLayers][];
      layer_dimension = new String[numLayers];
      layer_dimension_column = new String[numLayers];
      layer_extent = new String[numLayers];
			layer_style_list = new String[numLayers][];
			layer_srs_epsg_list = new String[numLayers][];
			layer_queryable = new String[numLayers];
      layer_opaque = new String[numLayers];
      layer_cascaded = new String[numLayers];
			layer_bounding_box_list = new Envelope[numLayers][];
			layer_lat_lon_bounding_box = new Envelope[numLayers];
			layer_scale_hint = new String[numLayers];
      layer_remote_url = new String[numLayers];
      layer_remote_layers = new String[numLayers];
      layer_remote_styles = new String[numLayers];
			
      // end of TODO_change stupid way

			int i = 0;
			
			// FIRST: COLLECT CAPABILITIES FOR SVG LAYERS:
			SQL = "SELECT * FROM wms_layers"; //$NON-NLS-1$
			currentSQL = SQL;
			rs = myDBconn.Query(SQL);
			if (rs == null) {
				throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
			} else {
				while (rs.next()) { // loop through records found 
				  layer_type[i] = "SVG";
					layer_name[i] = rs.getString("name"); //$NON-NLS-1$
          layer_pkey[i] = rs.getString("pkey"); //$NON-NLS-1$
					layer_title[i] = rs.getString("title"); //$NON-NLS-1$
					layer_geometry_col[i] = rs.getString("geom_col"); //$NON-NLS-1$
					layer_abstract[i] = rs.getString("abstract"); //$NON-NLS-1$
					layer_metadata_url[i] = rs.getString("metadata_url"); //$NON-NLS-1$
					layer_queryable[i] = rs.getString("queryable"); //$NON-NLS-1$
          layer_opaque[i] = rs.getString("opaque"); //$NON-NLS-1$
          layer_cascaded[i] = "0"; //$NON-NLS-1$
					T = rs.getString("keyword_list"); //$NON-NLS-1$
					if (T != null)
						layer_keyword_list[i] = T.split(";"); //$NON-NLS-1$
          layer_dimension[i] = rs.getString("dimension");
          layer_dimension_column[i] = rs.getString("dimension_column");
          if (layer_dimension[i] != null) {
            if (layer_dimension[i].equalsIgnoreCase("time")) { //only time supported for now
              layer_extent[i] = setLayer_dimension_extent(myDBconn, i, layer_dimension_column[i]);
            }
          }
					T = rs.getString("style_list"); //$NON-NLS-1$
					if (T != null)
						layer_style_list[i] = T.split(";"); //$NON-NLS-1$
					T = rs.getString("srs_epsg_list"); //$NON-NLS-1$
					if (T != null) {
						layer_srs_epsg_list[i] = T.split(";"); //$NON-NLS-1$
  						layer_bounding_box_list[i] = setLayer_bounding_box_list(myDBconn, i, layer_srs_epsg_list[i]);
  						layer_lat_lon_bounding_box[i] = setLayer_lat_lon_bounding_box(myDBconn, i);
						}
					layer_scale_hint[i] = rs.getString("scalehint"); //$NON-NLS-1$
					i++;
				} // end while
			}
			
		  // NEXT: COLLECT CAPABILITIES FOR CASCADED WMS LAYERS:
			// first check if its there: for backward compatability this table is not obligatory!
		     if (CascadedLayersTableExists) { //there is a cascaded layers table
            SQL = "SELECT * FROM wms_cascaded_layers"; //$NON-NLS-1$
            currentSQL = SQL;
            rs = myDBconn.Query(SQL);
            if (rs == null) {
              throw new RIMapperException(Messages.getString("WMSCapabilities.5") + SQL); //$NON-NLS-1$
            } else {
              while (rs.next()) { // loop through records found
                layer_type[i] = "WMS"; //$NON-NLS-1$
                layer_name[i] = rs.getString("name"); //$NON-NLS-1$
                layer_title[i] = rs.getString("title"); //$NON-NLS-1$
                layer_abstract[i] = rs.getString("abstract"); //$NON-NLS-1$
                layer_metadata_url[i] = rs.getString("metadata_url"); //$NON-NLS-1$
                layer_queryable[i] = rs.getString("queryable"); //$NON-NLS-1$
                layer_opaque[i] = rs.getString("opaque"); //$NON-NLS-1$
                layer_cascaded[i] = "1"; //$NON-NLS-1$
                T = rs.getString("keyword_list"); //$NON-NLS-1$
                if (T != null)
                  layer_keyword_list[i] = T.split(";"); //$NON-NLS-1$
                T = rs.getString("style_list"); //$NON-NLS-1$
                if (T != null)
                  layer_style_list[i] = T.split(";"); //$NON-NLS-1$
                T = rs.getString("srs_epsg_list"); //$NON-NLS-1$
                if (T != null) {
                  layer_srs_epsg_list[i] = T.split(";"); //$NON-NLS-1$
                    //TODO: FIXED for now => CHANGE!! 
                    int l = layer_srs_epsg_list[i].length;
                    Envelope[] myBB = new Envelope[l];
                    for (int z = 0; z < l; z++) {
                      myBB[z] = new Envelope();
                      myBB[z].init(-180.0, 180.0, -90.0, 90.0); // whole world
                    }
                    layer_bounding_box_list[i] = myBB;
                    // = whole world in EPSG:4326
                    layer_lat_lon_bounding_box[i] = myBB[0];
                  }
                layer_scale_hint[i] = rs.getString("scalehint"); //$NON-NLS-1$
                layer_remote_url[i] = rs.getString("remote_url"); //$NON-NLS-1$
                layer_remote_layers[i] = rs.getString("remote_layers"); //$NON-NLS-1$
                layer_remote_styles[i] = rs.getString("remote_styles"); //$NON-NLS-1$
                i++;
              } // end while
            }
          }     
			
			
			
		} catch (RIMapperException e) {
			throw new RIMapperException(Messages.getString("WMSCapabilities.2") + e.getMessage()); //$NON-NLS-1$
		} catch (SQLException e) {
			throw new RIMapperException(Messages.getString("WMSCapabilities.3") //$NON-NLS-1$
					+ e.getMessage() + " [" + Messages.getString("WMSCapabilities.92") + currentSQL + "]"); //$NON-NLS-1$
		} catch (RuntimeException e) {
			throw new RIMapperException(Messages.getString("WMSCapabilities.93") //$NON-NLS-1$
					+ e.toString());
		} catch (Exception e) {
			throw new RIMapperException(Messages.getString("WMSCapabilities.94") //$NON-NLS-1$
					+ e.toString());
		}
	} // determineCapabilities method

  /**
   * @return the metadata_name
   */
  public String getMetadata_name() {
    return metadata_name;
  }

  /**
   * @return the metadata_title
   */
  public String getMetadata_title() {
    return metadata_title;
  }

  /**
   * @return the metadata_abstract
   */
  public String getMetadata_abstract() {
    return metadata_abstract;
  }

  /**
   * @return the metadata_keyword_list
   */
  public String[] getMetadata_keyword_list() {
    return metadata_keyword_list;
  }

  /**
   * @return the metadata_contact_electronic_mail_address
   */
  public String getMetadata_contact_electronic_mail_address() {
    return metadata_contact_electronic_mail_address;
  }

  /**
   * @return the metadata_fees
   */
  public String getMetadata_fees() {
    return metadata_fees;
  }

  /**
   * @return the metadata_access_constraints
   */
  public String getMetadata_access_constraints() {
    return metadata_access_constraints;
  }

  /**
   * @return the numLayers
   */
  public int getNumLayers() {
    return numLayers;
  }

  /**
   * @return the numStyles
   */
  public int getNumStyles() {
    return numStyles;
  }

  /**
   * @return the numSVGStyles
   */
  public int getNumSVGStyles() {
    return numSVGStyles;
  }

  /**
   * @return the layer_name
   */
  public String[] getLayer_name() {
    return layer_name;
  }

  /**
   * @return the layer_title
   */
  public String[] getLayer_title() {
    return layer_title;
  }

  /**
   * @return the layer_pkey
   */
  public String[] getLayer_pkey() {
    return layer_pkey;
  }

  /**
   * @return the layer_geometry_col
   */
  public String[] getLayer_geometry_col() {
    return layer_geometry_col;
  }

  /**
   * @return the layer_abstract
   */
  public String[] getLayer_abstract() {
    return layer_abstract;
  }

  /**
   * @return the layer_keyword_list
   */
  public String[][] getLayer_keyword_list() {
    return layer_keyword_list;
  }

  /**
   * @return the layer_dimension
   */
  public String[] getLayer_dimension() {
    return layer_dimension;
  }
  
  /**
   * @return the layer_dimension_column
   */
  public String[] getLayer_dimension_column() {
    return layer_dimension_column;
  }

  /**
   * @return the layer_extent
   */
  public String[] getLayer_extent() {
    return layer_extent;
  }

  /**
   * @return the layer_style_list
   */
  public String[][] getLayer_style_list() {
    return layer_style_list;
  }

  /**
   * @return the layer_metadata_url
   */
  public String[] getLayer_metadata_url() {
    return layer_metadata_url;
  }

  /**
   * @return the layer_queryable
   */
  public String[] getLayer_queryable() {
    return layer_queryable;
  }

  /**
   * @return the layer_opaque
   */
  public String[] getLayer_opaque() {
    return layer_opaque;
  }
  
  /**
   * @return the layer_cascaded
   */
  public String[] getLayer_cascaded() {
    return layer_cascaded;
  }

  /**
   * @return the layer_lat_lon_bounding_box
   */
  public Envelope[] getLayer_lat_lon_bounding_box() {
    return layer_lat_lon_bounding_box;
  }

  /**
   * @return the layer_srs_epsg_list
   */
  public String[][] getLayer_srs_epsg_list() {
    return layer_srs_epsg_list;
  }

  /**
   * @return the layer_scale_hint
   */
  public String[] getLayer_scale_hint() {
    return layer_scale_hint;
  }

  /**
   * @return the layer_bounding_box_list
   */
  public Envelope[][] getLayer_bounding_box_list() {
    return layer_bounding_box_list;
  }

  /**
   * @return the style_name
   */
  public String[] getStyle_name() {
    return style_name;
  }

  /**
   * @return the style_title
   */
  public String[] getStyle_title() {
    return style_title;
  }

  /**
   * @return the style_abstract
   */
  public String[] getStyle_abstract() {
    return style_abstract;
  }

  /**
   * @return the style_legend_url_format
   */
  public String[] getStyle_legend_url_format() {
    return style_legend_url_format;
  }

  /**
   * @return the style_legend_url_height
   */
  public String[] getStyle_legend_url_height() {
    return style_legend_url_height;
  }

  /**
   * @return the style_legend_url_width
   */
  public String[] getStyle_legend_url_width() {
    return style_legend_url_width;
  }

  /**
   * @return the style_legend_url_online_resource
   */
  public String[] getStyle_legend_url_online_resource() {
    return style_legend_url_online_resource;
  }

  /**
   * @return the svg_style_name
   */
  public String[] getSvg_style_name() {
    return svg_style_name;
  }

  /**
   * @return the svg_style
   */
  public String[] getSvg_style() {
    return svg_style;
  }

  /**
   * @return the layer_type
   */
  public String[] getLayer_type() {
    return layer_type;
  }

  /**
   * @return the layer_remote_url
   */
  public String[] getLayer_remote_url() {
    return layer_remote_url;
  }

  /**
   * @return the layer_remote_layers
   */
  public String[] getLayer_remote_layers() {
    return layer_remote_layers;
  }

  /**
   * @return the layer_remote_styles
   */
  public String[] getLayer_remote_styles() {
    return layer_remote_styles;
  }

  /**
   * @return the currentSQL
   */
  public static String getCurrentSQL() {
    return currentSQL;
  }

  /**
   * @return the svg_style_maketrack
   */
  public boolean[] getSvg_style_maketrack() {
    return svg_style_maketrack;
  }

  /**
   * @return the svg_style_trackstyle
   */
  public String[] getSvg_style_trackstyle() {
    return svg_style_trackstyle;
  }
  
  /**
   * @return the svg_style_trackstyle
   */
  public long[] getSvg_style_animduration() {
    return svg_style_animduration;
  }

} // WMSgetCapabilities class
