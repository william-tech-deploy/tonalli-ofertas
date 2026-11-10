<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Revisa tu correo" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Revisa tu correo</h1>
<p>Si los datos son válidos, te enviamos un enlace para confirmar tu cuenta. El enlace vale 24 horas.</p>
<p><a href="<c:url value='/login'/>">Ir a iniciar sesión</a></p>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
