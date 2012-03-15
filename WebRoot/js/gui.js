/**
 * JavaScript for GUI of RIMapper (m)apps <br/>&copy;2006-2011 International
 * Institute for Geo-information Science and Earth Observation (ITC)
 * <br/>Licensed under a Creative Commons Attribution-NonCommercial-ShareAlike
 * 3.0 License. see <a href="http://creativecommons.org/licenses/by-nc-sa/3.0/"
 * target="_blank"> http://creativecommons.org/licenses/by-nc-sa/3.0/</a>
 * 
 * @author Barend K&ouml;bben <a href="mailto:kobben@itc.nl"
 *         target="_blank">kobben@itc.nl</a>
 * 
 * @version 2.1.3 [Sep 2010]
 */
// Major changes:
// 1.0 [Dec 2006] - first released version
// 2.0.002 b [Feb 2009] - new setup with clear separation of GUI and layers
// 2.1.2 [Sep 2009]
// - error reporting (SVG layers)
// - using display none/inline iso. visiblity visible/hidden for layer switcher
// (old sys did not work for animated layer for some strange reason..)
// -> now storing rim:status on/off for general type metadata
// - difference in layer type made
// type = "SVGanimated" (only one allowed)
// type = "SVG" for normal layers
// type = "WMS" for standard (Raster) WMS layers - treated as <image> in SVG
// Init these variables as Global
// 2.1.3 [Sep 2010]
// - made GUI draggable
// - changed layerswitcher code to allow for GetGUi=true (in-built GUI)
// - Information tool now also shows abstract/title of layer
// - layer Title used in layerSwitcher
// - WMSdata and LayerData in compound objects

var GUIdoc = null;
var SVGRoot = null;
var myMap = null;
var myMapData = null;
var myMapApp = null;

var zoomFactor = parseFloat(0.66); // 33% zoom in/out, fixed for now

var myWMSdata = {
  theWMShost : "",
  theSRS : "",
  theBBOX : "",
  theLAYERS : new Array(),
  theSTYLES : new Array(),
  theTIME : "",
  theFORMAT : "",
  theWIDTH : 0,
  theHEIGHT : 0,
  theEXCEPTIONS : "",
  xmin : 0,
  ymin : 0,
  xmax : 0,
  ymax : 0,
  width : 0,
  height : 0,
  aspectRatio : 0
};

var myUrlString = null;

// global vars for GUI etc.
var GUIroot = null;
var theURL = "";
var gui_buttons = [ "none", "zoomIn", "zoomOut", "pan", "layers", "info" ];
var NO_BTN = 0;
var ZOOM_IN_BTN = 1;
var ZOOM_OUT_BTN = 2;
var PAN_BTN = 3;
var LAYERS_BTN = 4;
var INFO_BTN = 5;
var clickPoint = null;
var gui_numButtons = gui_buttons.length;
var gui_button_circle = new Array(gui_numButtons);
var gui_active_button = 0;
var GUIopened = null;
var GUILayerSwitcher = null;
var theObjectClicked = null;
// ******* GUI dragging vars
var guiX = 0; // gui position
var guiY = 0;
var diffX = 0; // offset mouse-gui
var diffY = 0;

// global variables necessary to create & get elements in these namespaces
var rimNS = "http://kartoweb.itc.nl/RIMapper";
// others (svgNS, xlinkNS,cartoNS,attribNS,batikNS ) are already declared
// in helper_functions.js

// ** globals for layerloading code
function aLayer(id, name, title, abstract, type, layer, style, attribs, status) {
  this.id = id;
  this.name = name;
  this.title = title;
  this.abstract = abstract;
  this.type = type;
  this.layer = layer;
  this.style = style;
  this.attribs = attribs;
  this.status = status;
}
var myLayers = new Array(); // of aLayer
var numMyLayers = 0;
var layerLoadingMessage = null;


function init(evt) {
 
  GUIdoc = evt.target.ownerDocument;
  GUIroot = GUIdoc.getElementById("GUI");
  for ( var i = 1; i < gui_numButtons; i++) {
    gui_button_circle[i] = GUIdoc.getElementById("GUI_" + gui_buttons[i]
        + "_circle");
  }
  GUILayerSwitcher = GUIdoc.getElementById("GUI_layerSwitcher");
  myMap = GUIdoc.getElementById("theMap"); // get svg root of map
  layerLoadingMessage = GUIdoc.getElementById("loadingData");
  myMapData = GUIdoc.getElementById("WMSData");
  // get common WMSrequest params from mainMap object (in svg <desc> element)
  myWMSdata.theWMShost = myMapData.getAttributeNS(null, "WMShost");
  myWMSdata.theSRS = myMapData.getAttributeNS(null, "SRS");
  myWMSdata.theBBOX = myMapData.getAttributeNS(null, "BBOX");
  var BBOXarray = myWMSdata.theBBOX.split(",");
  myWMSdata.xmin = parseFloat(BBOXarray[0]);
  myWMSdata.ymin = parseFloat(BBOXarray[1]);
  myWMSdata.xmax = parseFloat(BBOXarray[2]);
  myWMSdata.ymax = parseFloat(BBOXarray[3]);
  myWMSdata.width = myWMSdata.xmax - myWMSdata.xmin;
  myWMSdata.height = myWMSdata.ymax - myWMSdata.ymin;
  myWMSdata.aspectRatio = myWMSdata.width / myWMSdata.height;
  myWMSdata.theLAYERS = myMapData.getAttributeNS(null, "LAYERS").split(",");
  numMyLayers = myWMSdata.theLAYERS.length;
  myWMSdata.theSTYLES = myMapData.getAttributeNS(null, "STYLES").split(",");
  myWMSdata.theTIME = myMapData.getAttributeNS(null, "TIME");
  myWMSdata.theFORMAT = myMapData.getAttributeNS(null, "FORMAT");
  myWMSdata.theWIDTH = myMapData.getAttributeNS(null, "WIDTH");
  myWMSdata.theHEIGHT = myMapData.getAttributeNS(null, "HEIGHT");
  myWMSdata.theEXCEPTIONS = myMapData.getAttributeNS(null, "EXCEPTIONS");

  // init the Carto:Net mapApp
  myMapApp = new mapApp(false, undefined);
  // WMS state: will be retrieved from desc element of map svg

  // *******retrieve WMSlayer metadata from SVG file...
  // id=theMap should be the enclosing SVG chunk...
  if (myMap != null) {
    // setting layer data (from WMS metadata):
    for ( var i = 0; i < numMyLayers; i++) {
      var tmpID = "GUIlayer_" + i;
      myLayers[i] = new aLayer(tmpID, // id
          myWMSdata.theLAYERS[i], // name
          GUIdoc.getElementById(tmpID).getAttributeNS(rimNS, "title"), // title
          GUIdoc.getElementById(tmpID).getAttributeNS(rimNS, "abstract"), // abstract
          GUIdoc.getElementById(tmpID).getAttributeNS(rimNS, "type"), // type
          myWMSdata.theLAYERS[i], // layer
          myWMSdata.theSTYLES[i], // style
          GUIdoc.getElementById(tmpID).getAttributeNS(rimNS, "attribs"), // attribs
          "on" // status, always start ON
      );
      if (myLayers[i].title == null || myLayers[i].title == "null"
          || myLayers[i].title == "")
        myLayers[i].title = myLayers[i].name;
      if (myLayers[i].abstract == null || myLayers[i].abstract == "null")
        myLayers[i].abstract = "";

    }
  } else {
    alert("ERROR building layer switcher: No SVG layer holder found...");
  }

  var Wstr = myMap.getAttributeNS(null, "width");
  var Hstr = myMap.getAttributeNS(null, "height");
  var W = Wstr.substring(0, Wstr.length - Wstr.indexOf("px") + 1);
  var H = Hstr.substring(0, Hstr.length - Hstr.indexOf("px") + 1);
  var translateToCenter = "translate(" + (W / 2) + "," + (H / 2) + ")";
  layerLoadingMessage.setAttributeNS(null, "transform", translateToCenter);

  // Create layerSwitcher;
  // code adapted from original by Bram Ton
  var maxLabelLength = 0;
  for ( var i = 0; i < numMyLayers; i++) {
    var currLayer = numMyLayers - i - 1; // reverse order: highest first!
    // Create checkboxes
    var theBox = document.createElementNS(svgNS, "circle");
    theBox.setAttributeNS(null, "id", "layerBox_" + (currLayer));
    theBox.setAttributeNS(null, "cx", 65);
    theBox.setAttributeNS(null, "cy", -15 + i * 15);
    theBox.setAttributeNS(null, "r", 5);
    // alert ("myLayers[currLayer].status = " + myLayers[currLayer].status);

    if (myLayers[currLayer].status == "on") {
      theBox.setAttributeNS(null, "fill", "rgb(255,0,0)");
    } else {
      theBox.setAttributeNS(null, "fill", "rgb(177,199,255)");
    }
    theBox.setAttributeNS(null, "stroke", "rgb(210,210,255)");
    theBox.setAttributeNS(null, "stroke-width", "1");
    theBox.setAttributeNS(null, "onclick", "toggleLayer(" + currLayer + ")");
    GUILayerSwitcher.appendChild(theBox);

    // Create label next to checkbox.
    var label = document.createElementNS(svgNS, "text");
    label.setAttributeNS(null, "id", "layerText_" + currLayer);
    label.setAttributeNS(null, "x", 75);
    label.setAttributeNS(null, "y", -12 + i * 15);
    label.setAttributeNS(null, "font-family", "Helvetica,Verdana,sans-serif");
    label.setAttributeNS(null, "font-size", "10px");
    var text_node = document.createTextNode(myLayers[currLayer].title);
    label.appendChild(text_node);
    GUILayerSwitcher.appendChild(label);
    // getComputedTextLength() only implemented in Opera,FireFox,Safari
    if (label.getComputedTextLength() > maxLabelLength)
      maxLabelLength = label.getComputedTextLength();
  }
  layerSwitcherbackground = GUIdoc
      .getElementById("GUI_layerSwitcherbackground");
  layerSwitcherbackground.setAttributeNS(null, "height", 5 + 15 * numMyLayers);
  if (maxLabelLength > 0) { // getComputedTextLength worked
    // (Opera,FireFox,Safari)
    layerSwitcherbackground.setAttributeNS(null, "width", maxLabelLength + 85);
  } else { // getComputedTextLength did not work, use default width
    // DEBUG:
    alert("hardcoded layerSwitcherbackground width...");
    layerSwitcherbackground.setAttributeNS(null, "width", 250);
  }
  // *******end retrieve layer data

  InitDragGUI(); // Init GUI dragging stuff

} // init()

// ******* GUI dragging stuff

function InitDragGUI() {
  document.onmousedown = OnMouseDown;
  document.onmouseup = OnMouseUp;
  _T = GUIroot.getAttribute("transform");
  guiX = _T.substring(_T.indexOf("(", 0) + 1, _T.indexOf(",", 0));
  guiY = _T.substring(_T.indexOf(",", 0) + 1, _T.indexOf(")", 0));
}

function OnMouseDown(e) {
  // IE is retarded and doesn't pass the event object
  if (e == null)
    e = window.event;
  // IE uses srcElement, others use target
  var target = e.target != null ? e.target : e.srcElement;
  if (target.id.substring(0, 4) == 'GUI_') {// its the GUI
    // for IE, left click == 1
    // for Firefox, left click == 0
    if ((e.button == 1 && window.event != null || e.button == 0)) {
      // grab the current GUI position
      _T = GUIroot.getAttribute("transform");
      guiX = _T.substring(_T.indexOf("(", 0) + 1, _T.indexOf(",", 0));
      guiY = _T.substring(_T.indexOf(",", 0) + 2, _T.indexOf(")", 0));
      // grab the mouse position and calc diff
      diffX = guiX - e.clientX;
      diffY = guiY - e.clientY;
      // tell our code to start moving the element with the mouse
      document.onmousemove = OnMouseMove;
      // prevent text selection in IE
      document.onselectstart = function() {
        return false;
      };
      // prevent IE from trying to drag an image
      target.ondragstart = function() {
        return false;
      };
      // prevent text selection (except IE)
      return false;
    }
  }
}
function OnMouseMove(e) {
  if (e == null)
    var e = window.event;
  // this is the actual "drag code"
  guiX = e.clientX + diffX;
  guiY = e.clientY + diffY;
  // alert(_newX + " -- " + _newY);
  _T = "translate(" + guiX + ", " + guiY + ")";
  GUIroot.setAttribute("transform", _T);
}
function OnMouseUp(e) {
  if (GUIroot != null) {
    // we're done with these events until the next OnMouseDown
    document.onmousemove = null;
    document.onselectstart = null;
  }
}
// ******* end of GUI dragging stuff

// get myMap, refresh layers all at once
function getMyMap() {

  showLayerLoadingMessage(true);
  var baseHost = document.location.href;
  var tmp1 = baseHost.indexOf("http://", 0) + 7;
  var tmp2 = baseHost.indexOf("/", tmp1);
  baseHost = baseHost.substring(tmp1, tmp2);
  var ajaxHost = myWMSdata.theWMShost;
  tmp1 = ajaxHost.indexOf("http://", 0) + 7;
  tmp2 = ajaxHost.indexOf("/", tmp1);
  ajaxHost = ajaxHost.substring(tmp1, tmp2);
    // if (baseHost != ajaxHost) { TODO: for now no test!!
    if (baseHost != baseHost) {
    alert("Security Exception!\nWMS requested from  a domain [" + ajaxHost
        + "] that is different from the domain [" + baseHost
        + "] this request originated from.\nChange host or use Proxy...");
    showLayerLoadingMessage(false);
  } else {
    myUrlString = myWMSdata.theWMShost 
        + "&SERVICE=WMS" + "&VERSION=1.1.1"
        + "&REQUEST=GetMap" 
        + "&BBOX=" + myWMSdata.theBBOX 
        + "&SRS="+ myWMSdata.theSRS 
        + "&LAYERS=" + myWMSdata.theLAYERS 
        + "&STYLES=" + myWMSdata.theSTYLES 
        + "&TIME=" + myWMSdata.theTIME
        + "&FORMAT=image/svg+xml" 
        + "&WIDTH=" + myWMSdata.theWIDTH 
        + "&HEIGHT=" + myWMSdata.theHEIGHT
        + "&EXCEPTIONS=" + myWMSdata.theEXCEPTIONS;
    // alert(myUrlString);

    // use XMLHttpRequest() if available
    if (window.XMLHttpRequest) {
      var xmlRequest = null;
      xmlRequest = new XMLHttpRequest();
      xmlRequest.open("GET", myUrlString, true);

      // use inline function for callback
      xmlRequest.onreadystatechange = function() {
        if (xmlRequest.readyState == 4) {
          if (xmlRequest.status == 200) {
            // find content-type from header
            var myContentType = xmlRequest.getResponseHeader("Content-Type");
            myContentType = myContentType.substring(0, 13);
            // alert("|" + myContentType + "|");
            // error handling
            if (myContentType == "image/svg+xml") {
              // its SVG, so an SVG map or SVG-formatted error...
              var importedNode = document.importNode(
                  xmlRequest.responseXML.documentElement, true);
              // call function addGeomAll if error: handled there...
              addGeomAll(importedNode);
            } else {
              // its not SVG, try to show as error on new page
              var errMSG = "Unexpected data (not SVG) retrieved! -- IGNORED";
              errMSG += "\n[myContentType = " + myContentType + "]";
              errMSG += "\nWill try to dump response content...";
              alert(errMSG);
              // window.location.href = myUrlString;
              alert(xmlRequest.responseText);
              showLayerLoadingMessage(false);
            }
          }
        }
      } ;// end inline function for callback;
      xmlRequest.send(null);
    } else if (window.getURL) { // else use getURL() if available
      getURL(myUrlString, addGeomGetURLAll);
    } else {
      // write an error message if neither method is available
      alert("ERROR: can not retrieve SVG layer."
          + "\nYour browser or svg viewer supports neither getURL nor XMLHttpRequest!");
      showLayerLoadingMessage(false);
    }
  }
}

// this function is only necessary for getURL()
function addGeomGetURLAll(data) {
  // check if data has a success property
  if (data.success) {
    // parse content of the XML format to the variable "node"
    var node = parseXML(data.content, document);
    addGeomAll(node);
  } else {
    alert("ERROR [addGeomGetURL()]: dynamic loading of geometry failed...");
    showLayerLoadingMessage(false);
  }
}

// add the geometry to the application
// note that GUI_layer wrapper groups with matching IDs in the main-map group
// of the SVG have to exist
function addGeomAll(node) {
  var dataToInsert = null;
  var mapToAttachTo = null;
  var isSVGMap = false;
  var IncomingSVGMapID;

  // debugObj = node;
  if (node.tagName == "svg") {
    dataToInsert = node;
    IncomingSVGMapID = node.getAttribute("id");
    // alert("IncomingSVGMapID: " + IncomingSVGMapID + "\n myMapID: " +
    // myMap.getAttribute("id"));
    if (IncomingSVGMapID == myMap.getAttribute("id")) {
      mapToAttachTo = myMap;
    }
  }

  // alert("mapToAttachTo = " + mapToAttachTo);
  if (mapToAttachTo == "" || mapToAttachTo == null || dataToInsert == ""
      || dataToInsert == null) { // not found:
    // its not RIMapper SVG, try to show as error on new page
    alert("Unexpected data structure  (could not match existing map object with incoming data) !"
        + "\nWill try to put Request Response in browser window...");
    showLayerLoadingMessage(false);
    window.location.href = myUrlString;
  } else { // seems to be real RIMapper SVG layer

    mapToAttachTo.parentNode.replaceChild(dataToInsert, mapToAttachTo);

    // make layer status reflect in display on/off:
    for ( var i = 0; i < numMyLayers; i++) {
      if (myLayers[i].status == "on") {
        GUIdoc.getElementById(myLayers[i].id).setAttributeNS(null, "display",
            "inline");
      } else {
        GUIdoc.getElementById(myLayers[i].id).setAttributeNS(null, "display",
            "none");
      }
    }

    showLayerLoadingMessage(false);

  }
}

// this function toggles the display of a map layer
function toggleLayer(i) {
  var layerCheckBox = document.getElementById("layerBox_" + i);
  var mapLayerElem = document.getElementById(myLayers[i].id);
  if (myLayers[i].status == "on") {
    mapLayerElem.setAttributeNS(null, "display", "none");
    // alert(mapLayer + " : "+ mapLayer.getAttributeNS(null, "display"));
    myLayers[i].status = "off";
    layerCheckBox.setAttributeNS(null, "fill", "rgb(177,199,255)");
  } else { // myLayer[i].status == "off"
    mapLayerElem.setAttributeNS(null, "display", "inline");
    myLayers[i].status = "on";
    layerCheckBox.setAttributeNS(null, "fill", "rgb(255,0,0)");
  }
}
/**
 * This is for the ACTION that has to take place when someone 
 * clicks in the map: check which function was selcted (zoom/pane/etc)
 * and then trigger the action
 * @param evt
 * @return
 */
function doAction(evt) {
  if (gui_active_button != NO_BTN) {
    // use the calcCoord from the Carto:Net mapApp script to
    // get XY in coords of the map:
    myMap = GUIdoc.getElementById("theMap"); // (re)get svg root of map
    clickPoint = myMapApp.calcCoord(evt, myMap);
    // undo SVG Y-axis negation:
    clickPoint.y = -clickPoint.y;

    if (gui_active_button == ZOOM_IN_BTN) { // zoomInButton
      // now zoom in:
      myWMSdata.width = myWMSdata.width * zoomFactor;
      myWMSdata.height = myWMSdata.width / myWMSdata.aspectRatio;
      // force ratio to be maintained;
      changeView();
    }
    if (gui_active_button == ZOOM_OUT_BTN) { // zoomOutButton
      // now zoom out:
      myWMSdata.width = myWMSdata.width / zoomFactor;
      myWMSdata.height = myWMSdata.width / myWMSdata.aspectRatio;
      // force ratio to be maintained;
      changeView();
    }
    if (gui_active_button == PAN_BTN) { // panButton
      changeView();
    }
    if (gui_active_button == INFO_BTN) { // infoButton
      showInfo(evt.target);
    } // info_BTN
    // other buttons require no map action...
  }
} // doAction()


function showInfo(theObjectClicked) {
  var theObjectClickedID = "";
  var infoRetrieved = "";
  var infoToReport = "";
  var layerInfoToReport = "";
  var attribInfoToReport = "";
  theObjectClickedID = theObjectClicked.getAttributeNS(null, "id");
  if (theObjectClickedID != "clickMask") {

    //alert(theObjectClickedID);
    for ( var i = 0; i < numMyLayers; i++) {
      //alert(theObjectClickedID.indexOf(myLayers[i].name));
      if (theObjectClickedID.indexOf(myLayers[i].name) != -1) {
        infoToReport += "Layer: " + myLayers[i].name;
        infoToReport += "\n    Title = " + myLayers[i].title;
        infoToReport += "\n    Abstract = " + myLayers[i].abstract;
        infoToReport += "\n\nObject: " + theObjectClickedID;
        if (myLayers[i].attribs != null && myLayers[i].attribs != "null"
            && myLayers[i].attribs != "") {
          var attribList = myLayers[i].attribs.split(",");
          for ( var j = 0; j < attribList.length; j++) {
            infoRetrieved = theObjectClicked.getAttributeNS(rimNS,
                attribList[j]);
            if (infoRetrieved != "" && infoRetrieved != null
                && infoRetrieved != "null") {
              infoToReport += "\n    " + attribList[j] + " = " + infoRetrieved;
            }
          }
        }
        break; //do not continue, only report first...
      }
    } // for i
    alert(infoToReport);
  }
}

function changeView() {
  // calculate new LowerLeft & UpperRight points from click & width+height:
  myWMSdata.xmin = clickPoint.x - (myWMSdata.width / 2);
  myWMSdata.ymin = clickPoint.y - (myWMSdata.height / 2);
  myWMSdata.xmax = clickPoint.x + (myWMSdata.width / 2);
  myWMSdata.ymax = clickPoint.y + (myWMSdata.height / 2);
  // alert ("xmin,ymin = " + myWMSdata.xmin + ", " + myWMSdata.ymin + "\n
  // xmax,ymax= " + myWMSdata.xmax + ", " + myWMSdata.ymax);

  // update mapdata in main map svg
  var newBBOX = (myWMSdata.xmin + "," + myWMSdata.ymin + "," + myWMSdata.xmax
      + "," + myWMSdata.ymax);
  myMapData.setAttributeNS(null, "BBOX", newBBOX);
  myWMSdata.theBBOX = newBBOX;

  // update viewbox: exchange ymin/ymax and negate because of negative y-space!
  var viewBoxStr = myWMSdata.xmin + " " + (-myWMSdata.ymax) + " "
      + myWMSdata.width + " " + myWMSdata.height;
  myMap.setAttributeNS(null, "viewBox", viewBoxStr);

  getMyMap();

}

function openGUI(evt) {
  GUIopened = GUIdoc.getElementById("GUI_Open");
  GUIopened.setAttributeNS(null, "visibility", "visible");
  if (gui_active_button == LAYERS_BTN) {
    GUILayerSwitcher.setAttributeNS(null, "visibility", "visible");
  }
}

function closeGUI(evt) {
  GUIopened = GUIdoc.getElementById("GUI_Open");
  GUIopened.setAttributeNS(null, "visibility", "hidden");
  GUILayerSwitcher.setAttributeNS(null, "visibility", "hidden");
}

/**
 * Triggered in SVG code in svgAnimGUIfooter
 * 
 * @param gui_buttonClicked the Button clicked
 * @return
 */
function toggleButton(gui_buttonClicked) {
  if (gui_active_button == gui_buttonClicked) {
    gui_active_button = 0;
    gui_button_circle[gui_buttonClicked].setAttributeNS(null, "fill",
        "url(#buttonGradient)");
    if (gui_buttonClicked == LAYERS_BTN) {
      GUILayerSwitcher.setAttributeNS(null, "visibility", "hidden");
    }
  } else {
    gui_active_button = gui_buttonClicked;
    for ( var i = 1; i < gui_numButtons; i++) {
      gui_button_circle[i].setAttributeNS(null, "fill", "url(#buttonGradient)");
    }
    gui_button_circle[gui_buttonClicked].setAttributeNS(null, "fill",
        "url(#buttonGradientRed)");
    if (gui_buttonClicked == LAYERS_BTN) {
      GUILayerSwitcher.setAttributeNS(null, "visibility", "visible");
    } else {
      GUILayerSwitcher.setAttributeNS(null, "visibility", "hidden");
    }
  }
} // toggleButton

function showLayerLoadingMessage(turnOn) {
  if (turnOn) { // set layerloading message...
    layerLoadingMessage.setAttributeNS(null, "display", "inline");
  } else { // off
    layerLoadingMessage.setAttributeNS(null, "display", "none");
  }
}// showLayerLoadingMessage
