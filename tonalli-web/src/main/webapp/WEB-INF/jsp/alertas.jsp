<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="titulo" value="Mis alertas" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Mis alertas</h1>
<p>Escribe una palabra clave y, si quieres, un precio máximo. Aquí verás las ofertas activas que coinciden.</p>

<form:form cssClass="formulario" method="post" modelAttribute="form" servletRelativeAction="/alertas">
    <label for="palabraClave">Palabra clave</label>
    <form:input id="palabraClave" path="palabraClave" maxlength="60"/>
    <form:errors path="palabraClave" cssClass="campo-error" element="p"/>

    <label for="precioMax">Precio máximo (USD, opcional)</label>
    <form:input id="precioMax" path="precioMax" inputmode="decimal" maxlength="10"/>
    <form:errors path="precioMax" cssClass="campo-error" element="p"/>

    <button type="submit">Crear alerta</button>
</form:form>

<c:forEach var="a" items="${alertas}">
    <section class="tarjeta">
        <h2>
            «<c:out value="${a.palabraClave}"/>»
            <c:if test="${a.precioMax != null}">
                hasta <fmt:formatNumber value="${a.precioMax}" type="currency" currencyCode="USD"/>
            </c:if>
        </h2>
        <c:choose>
            <c:when test="${empty a.coincidencias}"><p>Sin coincidencias por ahora.</p></c:when>
            <c:otherwise>
                <ul>
                    <c:forEach var="o" items="${a.coincidencias}">
                        <li>
                            <a href="<c:url value='/ofertas/${o.id}'/>"><c:out value="${o.titulo}"/></a>
                            · <fmt:formatNumber value="${o.precio}" type="currency" currencyCode="USD"/>
                            · <c:out value="${o.tienda}"/>
                        </li>
                    </c:forEach>
                </ul>
            </c:otherwise>
        </c:choose>
        <form method="post" action="<c:url value='/alertas/${a.id}/eliminar'/>">
            <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
            <button type="submit" class="secundario">Eliminar alerta</button>
        </form>
    </section>
</c:forEach>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
