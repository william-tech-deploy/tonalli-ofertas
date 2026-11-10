<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1><c:out value="${titulo}"/></h1>
<p><c:out value="${mensaje}"/></p>
<p><a href="<c:url value='/'/>">Volver al inicio</a></p>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
