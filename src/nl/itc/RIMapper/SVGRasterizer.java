package nl.itc.RIMapper;

import java.awt.Color;
import java.io.ByteArrayInputStream;

import javax.servlet.ServletOutputStream;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;



/**
 * Transcodes SVG streams  to PNG/JPEG for WMS compatibility. <br/>
 * Uses Batik SVG toolkit for transcoding  (<a
 * href="http://xmlgraphics.apache.org/batik/"
 * target="_blank">http://xmlgraphics.apache.org/batik/</a>) <br/>
 * 
 * &copy;2010 International Institute for Geo-information Science and Earth
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
// 2.0 [Sep 2010] first released version
public class SVGRasterizer {

  private String errorMessage = "";

  // constructor
  public SVGRasterizer() {
     super();
  } // constructor

  // constructor
  /**
   * Transcodes SVG streams  to PNG/JPEG for WMS compatibility. <br/>
   * Uses Batik SVG toolkit for transcoding  (<a
   * href="http://xmlgraphics.apache.org/batik/"
   * target="_blank">http://xmlgraphics.apache.org/batik/</a>) <br/>
   * @param inputSVG - the input SVG as a StringBuffer
   * @param theType - uses MIME type to distinguish: "image/png" or "image/jpeg"
   * @param theW - width in pixels
   * @param theH - height in pixels
   * @param myOut - Output stream (usually the WMS servlets ServletOutputStream)
   * @throws RIMapperException
   */
  public void doSVGRasterize(StringBuffer inputSVG, String theType, int theW, int theH, ServletOutputStream myOut)
      throws RIMapperException {
    try {
      if (theType.equalsIgnoreCase(Utils.PNG_MIME_TYPE)) {
        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.white);
        // Set the width and height of the generated image
        transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, new Float(theW));
        transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, new Float(theH));
        // Configure the input and output
        TranscoderInput tIn = new TranscoderInput(new ByteArrayInputStream(inputSVG.toString().getBytes()));
        TranscoderOutput tOut = new TranscoderOutput(myOut);
        // Invoke the transcoder
        transcoder.transcode(tIn, tOut);
      } else if (theType.equalsIgnoreCase(Utils.JPEG_MIME_TYPE)) {
        JPEGTranscoder transcoder = new JPEGTranscoder();
        transcoder.addTranscodingHint(JPEGTranscoder.KEY_QUALITY, new Float(1.0)); 
        // Set the width and height of the generated image
        transcoder.addTranscodingHint(JPEGTranscoder.KEY_WIDTH, new Float(theW));
        transcoder.addTranscodingHint(JPEGTranscoder.KEY_HEIGHT, new Float(theH));
        // Configure the input and output
        TranscoderInput tIn = new TranscoderInput(new ByteArrayInputStream(inputSVG.toString().getBytes()));
        TranscoderOutput tOut = new TranscoderOutput(myOut);
        // Invoke the transcoder
        transcoder.transcode(tIn, tOut);
      } 
    } catch (TranscoderException e) {
      errorMessage = "[SVGRasterizer] Error Transcoding SVG: " + e.toString();
      throw new RIMapperException(errorMessage);
    } catch (RuntimeException e) {
      errorMessage = "[SVGRasterizer] **RuntimeException**: " + e.toString();
      throw new RIMapperException(errorMessage);
    } catch (Exception e) {
      errorMessage = "[SVGRasterizer] **Unexpected Exception**: " + e.toString();
      throw new RIMapperException(errorMessage);
    }
  } // constructor

}
