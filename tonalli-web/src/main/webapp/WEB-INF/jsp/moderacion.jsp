<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Moderación" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Moderación</h1>

<h2>Reportes abiertos</h2>
<c:if test="${empty reportes}"><p>No hay reportes abiertos.</p></c:if>
<c:if test="${not empty reportes}">
    <table class="tabla">
        <thead><tr><th>Oferta</th><th>Motivo</th><th>Reportó</th><th>Fecha</th><th>Acciones</th></tr></thead>
        <tbody>
        <c:forEach var="r" items="${reportes}">
            <tr>
                <td><a href="<c:url value='/ofertas/${r.ofertaId}'/>"><c:out value="${r.ofertaTitulo}"/></a>
                    <c:if test="${r.ofertaEstado != 'ACTIVA'}"> (<c:out value="${r.ofertaEstado}"/>)</c:if></td>
                <td><c:out value="${r.motivo}"/></td>
                <td><c:out value="${r.reportante}"/></td>
                <td><c:out value="${r.creado}"/></td>
                <td>
                    <form method="post" action="<c:url value='/moderacion/reportes/${r.id}/ocultar'/>" class="en-linea">
                        <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                        <button type="submit">Ocultar oferta</button>
                    </form>
                    <form method="post" action="<c:url value='/moderacion/reportes/${r.id}/descartar'/>" class="en-linea">
                        <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                        <button type="submit" class="secundario">Descartar</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</c:if>

<h2>Ofertas ocultas</h2>
<c:if test="${empty ocultas}"><p>No hay ofertas ocultas.</p></c:if>
<c:if test="${not empty ocultas}">
    <table class="tabla">
        <thead><tr><th>Oferta</th><th>Tienda</th><th>Autor</th><th>Acciones</th></tr></thead>
        <tbody>
        <c:forEach var="o" items="${ocultas}">
            <tr>
                <td><a href="<c:url value='/ofertas/${o.id}'/>"><c:out value="${o.titulo}"/></a></td>
                <td><c:out value="${o.tienda}"/></td>
                <td><c:out value="${o.autor}"/></td>
                <td>
                    <form method="post" action="<c:url value='/moderacion/ofertas/${o.id}/restaurar'/>" class="en-linea">
                        <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                        <button type="submit">Restaurar</button>
                    </form>
                    <form method="post" action="<c:url value='/moderacion/ofertas/${o.id}/eliminar'/>" class="en-linea">
                        <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                        <button type="submit" class="peligro">Eliminar</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</c:if>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
