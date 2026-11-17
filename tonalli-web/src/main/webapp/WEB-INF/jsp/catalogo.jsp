<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Ofertas" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Ofertas</h1>

<form class="busqueda" method="get" action="<c:url value='/'/>">
    <%-- Un solo formulario GET: los filtros quedan en la URL, así la búsqueda se puede compartir y volver atrás. --%>
    <div class="busqueda-fila">
        <input type="search" name="q" value="<c:out value='${q}'/>" maxlength="60" placeholder="Buscar ofertas" aria-label="Buscar">
        <select name="categoria" aria-label="Categoría">
            <option value="">Todas las categorías</option>
            <c:forEach var="c" items="${categorias}">
                <option value="${c.id}" <c:if test="${c.id == filtro.categoriaId}">selected</c:if>><c:out value="${c.nombre}"/></option>
            </c:forEach>
        </select>
        <button type="submit">Buscar</button>
    </div>

    <details class="filtros" <c:if test="${filtro.hayFiltros}">open</c:if>>
        <summary>Más filtros</summary>
        <div class="filtros-campos">
            <div class="campo">
                <label for="precioMin">Precio desde (USD)</label>
                <input id="precioMin" name="precioMin" inputmode="decimal" maxlength="10"
                       value="<c:if test='${filtro.precioMin != null}'><fmt:formatNumber value='${filtro.precioMin}' maxFractionDigits='2' groupingUsed='false'/></c:if>">
            </div>
            <div class="campo">
                <label for="precioMax">Precio hasta (USD)</label>
                <input id="precioMax" name="precioMax" inputmode="decimal" maxlength="10"
                       value="<c:if test='${filtro.precioMax != null}'><fmt:formatNumber value='${filtro.precioMax}' maxFractionDigits='2' groupingUsed='false'/></c:if>">
            </div>
            <div class="campo">
                <label for="temperatura">Temperatura</label>
                <select id="temperatura" name="temperatura">
                    <c:forEach var="t" items="${temperaturas}">
                        <option value="${t}" <c:if test="${t == filtro.temperatura}">selected</c:if>><c:out value="${t.etiqueta}"/></option>
                    </c:forEach>
                </select>
            </div>
            <div class="campo">
                <label for="orden">Ordenar por</label>
                <select id="orden" name="orden">
                    <c:forEach var="o" items="${ordenes}">
                        <option value="${o}" <c:if test="${o == filtro.orden}">selected</c:if>><c:out value="${o.etiqueta}"/></option>
                    </c:forEach>
                </select>
            </div>
            <div class="campo campo-casilla">
                <input id="conDescuento" type="checkbox" name="conDescuento" value="1" <c:if test="${filtro.soloConDescuento}">checked</c:if>>
                <label for="conDescuento">Solo con precio anterior</label>
            </div>
        </div>
        <div class="filtros-acciones">
            <button type="submit">Aplicar filtros</button>
            <c:if test="${filtro.hayFiltros}">
                <a class="boton-tienda" href="<c:url value='/'/>">Quitar filtros</a>
            </c:if>
        </div>
    </details>
</form>

<p class="meta resultado">
    <c:out value="${pagina.totalElements}"/>
    <c:choose><c:when test="${pagina.totalElements == 1}"> oferta encontrada</c:when><c:otherwise> ofertas encontradas</c:otherwise></c:choose>
    · ordenadas por <c:out value="${filtro.orden.etiqueta}"/>
</p>

<c:choose>
    <c:when test="${empty pagina.content}">
        <p>No hay ofertas que coincidan con tu búsqueda.
            <c:if test="${filtro.hayFiltros}"><a href="<c:url value='/'/>">Quitar los filtros</a>.</c:if>
        </p>
    </c:when>
    <c:otherwise>
        <ul class="ofertas">
            <c:forEach var="o" items="${pagina.content}">
                <li class="oferta">
                    <%-- Foto subida por el usuario o, si no hay, la ilustración de la categoría (decorativa: alt vacío). --%>
                    <img class="miniatura" src="<c:url value='${o.imagenUrl}'/>" width="88" height="88" loading="lazy"
                         alt="<c:out value='${o.tieneFoto ? o.titulo : ""}'/>">
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

        <%-- Los enlaces de página arrastran los mismos filtros, para no perderlos al avanzar. --%>
        <nav class="paginas" aria-label="Páginas">
            <c:if test="${pagina.number > 0}">
                <c:url var="anterior" value="/">
                    <c:param name="q" value="${q}"/>
                    <c:param name="categoria" value="${filtro.categoriaId}"/>
                    <c:param name="precioMin" value="${filtro.precioMin}"/>
                    <c:param name="precioMax" value="${filtro.precioMax}"/>
                    <c:if test="${filtro.soloConDescuento}"><c:param name="conDescuento" value="1"/></c:if>
                    <c:param name="temperatura" value="${filtro.temperatura}"/>
                    <c:param name="orden" value="${filtro.orden}"/>
                    <c:param name="pagina" value="${pagina.number - 1}"/>
                </c:url>
                <a href="<c:out value='${anterior}'/>">← Anterior</a>
            </c:if>
            <span>Página <c:out value="${pagina.number + 1}"/> de <c:out value="${pagina.totalPages}"/></span>
            <c:if test="${pagina.number + 1 < pagina.totalPages}">
                <c:url var="siguiente" value="/">
                    <c:param name="q" value="${q}"/>
                    <c:param name="categoria" value="${filtro.categoriaId}"/>
                    <c:param name="precioMin" value="${filtro.precioMin}"/>
                    <c:param name="precioMax" value="${filtro.precioMax}"/>
                    <c:if test="${filtro.soloConDescuento}"><c:param name="conDescuento" value="1"/></c:if>
                    <c:param name="temperatura" value="${filtro.temperatura}"/>
                    <c:param name="orden" value="${filtro.orden}"/>
                    <c:param name="pagina" value="${pagina.number + 1}"/>
                </c:url>
                <a href="<c:out value='${siguiente}'/>">Siguiente →</a>
            </c:if>
        </nav>
    </c:otherwise>
</c:choose>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
