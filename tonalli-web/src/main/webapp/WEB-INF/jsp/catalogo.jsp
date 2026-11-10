<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Ofertas" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Ofertas</h1>

<form class="busqueda" method="get" action="<c:url value='/'/>">
    <input type="search" name="q" value="<c:out value='${q}'/>" maxlength="60" placeholder="Buscar ofertas" aria-label="Buscar">
    <select name="categoria" aria-label="Categoría">
        <option value="">Todas las categorías</option>
        <c:forEach var="c" items="${categorias}">
            <option value="${c.id}" <c:if test="${c.id == categoriaActual}">selected</c:if>><c:out value="${c.nombre}"/></option>
        </c:forEach>
    </select>
    <button type="submit">Buscar</button>
</form>

<c:choose>
    <c:when test="${empty pagina.content}">
        <p>No hay ofertas que coincidan con tu búsqueda.</p>
    </c:when>
    <c:otherwise>
        <ul class="ofertas">
            <c:forEach var="o" items="${pagina.content}">
                <li class="oferta">
                    <div class="temp ${o.temperatura >= 100 ? 'caliente' : o.temperatura >= 0 ? 'tibia' : 'frio'}"><c:out value="${o.temperatura}"/>°<span class="palabra">${o.temperatura >= 100 ? 'Caliente' : o.temperatura >= 0 ? 'Tibia' : 'Fría'}</span></div>
                    <div class="cuerpo">
                        <h2><a href="<c:url value='/ofertas/${o.id}'/>"><c:out value="${o.titulo}"/></a></h2>
                        <p class="meta">
                            <c:out value="${o.tienda}"/> · <c:out value="${o.categoria}"/> ·
                            por <c:out value="${o.autor}"/> · <c:out value="${o.creada}"/>
                        </p>
                    </div>
                    <div class="precio">
                        <strong><fmt:formatNumber value="${o.precio}" type="currency" currencyCode="USD"/></strong>
                        <c:if test="${o.precioAnterior != null}">
                            <span class="antes"><fmt:formatNumber value="${o.precioAnterior}" type="currency" currencyCode="USD"/></span>
                        </c:if>
                        <c:if test="${o.descuento != null}">
                            <span class="descuento">-<c:out value="${o.descuento}"/>%</span>
                        </c:if>
                    </div>
                </li>
            </c:forEach>
        </ul>

        <nav class="paginas" aria-label="Páginas">
            <c:if test="${pagina.number > 0}">
                <c:url var="anterior" value="/">
                    <c:param name="q" value="${q}"/>
                    <c:param name="categoria" value="${categoriaActual}"/>
                    <c:param name="pagina" value="${pagina.number - 1}"/>
                </c:url>
                <a href="<c:out value='${anterior}'/>">← Anterior</a>
            </c:if>
            <span>Página <c:out value="${pagina.number + 1}"/> de <c:out value="${pagina.totalPages}"/></span>
            <c:if test="${pagina.number + 1 < pagina.totalPages}">
                <c:url var="siguiente" value="/">
                    <c:param name="q" value="${q}"/>
                    <c:param name="categoria" value="${categoriaActual}"/>
                    <c:param name="pagina" value="${pagina.number + 1}"/>
                </c:url>
                <a href="<c:out value='${siguiente}'/>">Siguiente →</a>
            </c:if>
        </nav>
    </c:otherwise>
</c:choose>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
