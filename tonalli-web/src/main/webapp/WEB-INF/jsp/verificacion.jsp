<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Verificación de correo" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Verificación de correo</h1>
<c:choose>
    <c:when test="${verificado}">
        <p class="aviso ok" role="status">¡Listo! Tu correo quedó verificado.</p>
        <p><a href="<c:url value='/login'/>">Iniciar sesión</a></p>
    </c:when>
    <c:otherwise>
        <p class="aviso error" role="alert">El enlace no es válido o ya venció. Si necesitas uno nuevo, regístrate de nuevo o pide ayuda al administrador.</p>
    </c:otherwise>
</c:choose>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
