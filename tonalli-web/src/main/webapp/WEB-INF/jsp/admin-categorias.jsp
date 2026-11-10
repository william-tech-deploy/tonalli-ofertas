<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="titulo" value="Categorías" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Categorías</h1>

<form:form cssClass="formulario" method="post" modelAttribute="form" servletRelativeAction="/admin/categorias">
    <label for="nombre">Nueva categoría</label>
    <form:input id="nombre" path="nombre" maxlength="40"/>
    <form:errors path="nombre" cssClass="campo-error" element="p"/>
    <button type="submit">Crear</button>
</form:form>

<table class="tabla">
    <thead><tr><th>Nombre</th><th>Tipo</th><th>Acciones</th></tr></thead>
    <tbody>
    <c:forEach var="c" items="${categorias}">
        <tr>
            <td><c:out value="${c.nombre}"/></td>
            <td><c:out value="${c.base ? 'Base' : 'Agregada'}"/></td>
            <td>
                <c:if test="${not c.base}">
                    <form method="post" action="<c:url value='/admin/categorias/${c.id}/eliminar'/>" class="en-linea">
                        <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                        <button type="submit" class="peligro">Eliminar</button>
                    </form>
                </c:if>
            </td>
        </tr>
    </c:forEach>
    </tbody>
</table>

<h2>Propuestas pendientes</h2>
<c:if test="${empty propuestas}"><p>No hay propuestas pendientes.</p></c:if>
<c:if test="${not empty propuestas}">
    <table class="tabla">
        <thead><tr><th>Nombre</th><th>Propuesta de</th><th>Apoyos</th><th>Acciones</th></tr></thead>
        <tbody>
        <c:forEach var="p" items="${propuestas}">
            <tr>
                <td><c:out value="${p.nombre}"/></td>
                <td><c:out value="${p.autor}"/></td>
                <td><c:out value="${p.apoyos}"/></td>
                <td>
                    <form method="post" action="<c:url value='/admin/propuestas/${p.id}/aprobar'/>" class="en-linea">
                        <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                        <button type="submit">Aprobar</button>
                    </form>
                    <form method="post" action="<c:url value='/admin/propuestas/${p.id}/rechazar'/>" class="en-linea">
                        <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                        <button type="submit" class="secundario">Rechazar</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</c:if>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
