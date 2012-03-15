<?xml version="1.0" encoding="UTF-8" ?><%@ page language="java" contentType="text/xml; charset=ISO-8859-1"
  pageEncoding="ISO-8859-1"%><%@ page import="nl.itc.RIMapper.Crypt"%><%@ page import="nl.itc.RIMapper.RIMapperException"%><%
    	String ErrorStr = "";
    	//String OutputStr = "";
    	try {
    		//get DB connection params from WMS.xml instance XML file
    		Crypt myCrypt = new Crypt("myRIMapperKey"); //Instanciate Crypt class;\
    		if (request.getParameter("mySFS") == null) {
    			throw new RIMapperException("No Spatial Feature Server specified...");
    		}
    		String mySFS = request.getParameterValues("mySFS")[0];
        if (request.getParameter("myDB") == null) {
          throw new RIMapperException("No database specified...");
        }
        String myDB = request.getParameterValues("myDB")[0];
        if (request.getParameter("myUN") == null) {
          throw new RIMapperException("No user specified...");
        }
        String myUN = request.getParameterValues("myUN")[0];
        if (request.getParameter("myPW") == null) {
          throw new RIMapperException("No password specified...");
        }
    		String myPWPlain = request.getParameterValues("myPW")[0];
    		String myPWEnc = myCrypt.encrypt(myPWPlain); //encrypt PW %>
<!-- NOTE: this wms_instance.xml provides DB connection data to the
 RIMapperWMS server application:
 1 DB with the RIMapperWMS tables + 1 RIMapper webapp = 1 WMS instance-->
<WMS>
   <SFSServer><%=mySFS%></SFSServer>
   <database><%=myDB%></database>
   <username><%=myUN%></username>
   <!-- password is encrypted using app-provided encryption key-->
   <password><%=myPWEnc%></password>
</WMS>
    <%
    	} catch (RIMapperException e) {
    		ErrorStr = "[WMS]" + e.getMessage();
    	} catch (RuntimeException e) {
    		ErrorStr = "[WMS] **RuntimeException**: " + e.toString();
    	} catch (Exception e) {
    		ErrorStr = "[WMS] **Unexpected Exception**: " + e.toString();
    	} finally {
    		// generic output block
    %>
    <%=ErrorStr%>
    <%
    	}
    %>