<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="titulo" value="Propuestas de categorías" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Propuestas de categorías</h1>

<form:form cssClass="formulario" method="post" modelAttribute="form" servletRelativeAction="/propuestas">
    <label for="nombre">Nombre de la nueva categoría</label>
    <form:input id="nombre" path="nombre" maxlength="40"/>
    <form:errors path="nombre" cssClass="campo-error" element="p"/>
    <button type="submit">Proponer</button>
</form:form>

<h2>Pendientes de aprobación</h2>
<c:if test="${empty propuestas}"><p>No hay propuestas pendientes.</p></c:if>
<ul class="lista">
    <c:forEach var="p" items="${propuestas}">
        <li>
            <strong><c:out value="${p.nombre}"/></strong>
            · propuesta de <c:out value="${p.autor}"/>
            · <c:out value="${p.apoyos}"/> apoyo(s)
            <c:choose>
                <c:when test="${p.yaApoyada}"><em>(ya la apoyaste)</em></c:when>
                <c:otherwise>
                    <form method="post" action="<c:url value='/propuestas/${p.id}/apoyar'/>" class="en-linea">
                        <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                        <button type="submit" class="secundario">Apoyar</button>
                    </form>
                </c:otherwise>
            </c:choose>
        </li>
    </c:forEach>
</ul>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
