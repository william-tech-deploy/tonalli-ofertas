<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="No se pudo atender la petición" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>No se pudo atender la petición</h1>
<p>La página no existe, no tienes permiso para verla o tu sesión expiró (código <c:out value="${status}"/>).</p>
<p><a href="<c:url value='/'/>">Volver al inicio</a></p>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
