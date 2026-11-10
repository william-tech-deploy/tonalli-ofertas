<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Usuarios" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Usuarios</h1>
<p>Al cambiar el rol de un usuario se cierran sus sesiones abiertas.</p>

<table class="tabla">
    <thead><tr><th>Nombre</th><th>Correo</th><th>Verificado</th><th>Rol</th></tr></thead>
    <tbody>
    <c:forEach var="u" items="${pagina.content}">
        <tr>
            <td><c:out value="${u.nombre}"/></td>
            <td><c:out value="${u.correo}"/></td>
            <td><c:out value="${u.verificado ? 'Sí' : 'No'}"/></td>
            <td>
                <form method="post" action="<c:url value='/admin/usuarios/${u.id}/rol'/>" class="en-linea">
                    <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                    <select name="rol" aria-label="Rol de <c:out value='${u.nombre}'/>">
                        <c:forEach var="r" items="${roles}">
                            <option value="<c:out value='${r}'/>" <c:if test="${r == u.rol}">selected</c:if>><c:out value="${r}"/></option>
                        </c:forEach>
                    </select>
                    <button type="submit" class="secundario">Guardar</button>
                </form>
            </td>
        </tr>
    </c:forEach>
    </tbody>
</table>

<nav class="paginas" aria-label="Páginas">
    <c:if test="${pagina.number > 0}">
        <a href="<c:url value='/admin/usuarios'><c:param name='pagina' value='${pagina.number - 1}'/></c:url>">← Anterior</a>
    </c:if>
    <span>Página <c:out value="${pagina.number + 1}"/> de <c:out value="${pagina.totalPages}"/></span>
    <c:if test="${pagina.number + 1 < pagina.totalPages}">
        <a href="<c:url value='/admin/usuarios'><c:param name='pagina' value='${pagina.number + 1}'/></c:url>">Siguiente →</a>
    </c:if>
</nav>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
