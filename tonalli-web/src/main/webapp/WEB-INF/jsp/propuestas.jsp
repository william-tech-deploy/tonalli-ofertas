<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="titulo" value="Votación de categorías" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Votación de categorías</h1>
<p>Las categorías nuevas las decide la comunidad: una propuesta con <strong><c:out value="${umbral}"/> votos</strong>
    se convierte sola en categoría, sin que la apruebe nadie más. Puedes retirar tu voto cuando quieras.</p>

<h2>En votación</h2>
<c:if test="${empty propuestas}"><p>No hay propuestas en votación. Propón la primera más abajo.</p></c:if>

<ul class="propuestas">
    <c:forEach var="p" items="${propuestas}">
        <li class="propuesta">
            <div class="propuesta-datos">
                <h3><c:out value="${p.nombre}"/></h3>
                <p class="meta">Propuesta de <c:out value="${p.autor}"/></p>
                <%-- <progress> dibuja la barra con sus propios atributos: no hacen falta estilos en línea,
                     que la CSP prohíbe, y los lectores de pantalla ya anuncian el avance. --%>
                <progress class="barra" max="${p.umbral}" value="${p.apoyos}"></progress>
                <p class="meta">
                    <c:out value="${p.apoyos}"/> de <c:out value="${p.umbral}"/> votos
                    <c:choose>
                        <c:when test="${p.faltan == 0}">· lista para crearse</c:when>
                        <c:when test="${p.faltan == 1}">· falta 1 voto</c:when>
                        <c:otherwise>· faltan <c:out value="${p.faltan}"/> votos</c:otherwise>
                    </c:choose>
                </p>
            </div>
            <form method="post" action="<c:url value='/propuestas/${p.id}/apoyar'/>" class="en-linea">
                <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                <c:choose>
                    <c:when test="${p.yaApoyada}">
                        <button type="submit" class="secundario" aria-label="Retirar mi voto a <c:out value='${p.nombre}'/>">Quitar mi voto</button>
                    </c:when>
                    <c:otherwise>
                        <button type="submit" aria-label="Votar por <c:out value='${p.nombre}'/>">Votar</button>
                    </c:otherwise>
                </c:choose>
            </form>
        </li>
    </c:forEach>
</ul>

<c:if test="${not empty aprobadas}">
    <h2>Categorías creadas por votación</h2>
    <ul class="lista">
        <c:forEach var="a" items="${aprobadas}">
            <li><strong><c:out value="${a.nombre}"/></strong> · propuesta de <c:out value="${a.autor}"/>
                · <c:out value="${a.apoyos}"/> votos</li>
        </c:forEach>
    </ul>
</c:if>

<h2>Proponer una categoría</h2>
<form:form cssClass="formulario" method="post" modelAttribute="form" servletRelativeAction="/propuestas">
    <label for="nombre">Nombre de la nueva categoría</label>
    <form:input id="nombre" path="nombre" maxlength="40"/>
    <form:errors path="nombre" cssClass="campo-error" element="p"/>
    <p class="meta">Tu propuesta empieza con tu propio voto. Puedes tener hasta 3 en votación a la vez.</p>
    <button type="submit">Proponer</button>
</form:form>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
