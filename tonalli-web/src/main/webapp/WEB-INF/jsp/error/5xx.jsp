<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Error del servidor" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Error del servidor</h1>
<p>Ocurrió un problema de nuestro lado. Inténtalo de nuevo más tarde.</p>
<p><a href="<c:url value='/'/>">Volver al inicio</a></p>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
