<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="${oferta.titulo}" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<article class="detalle">
    <h1><c:out value="${oferta.titulo}"/></h1>
    <p class="meta">
        <c:out value="${oferta.tienda}"/> · <c:out value="${oferta.categoria}"/> ·
        por <c:out value="${oferta.autor}"/> · <c:out value="${oferta.creada}"/>
        <c:if test="${oferta.estado != 'ACTIVA'}"> · <strong>Oferta <c:out value="${oferta.estado}"/></strong></c:if>
    </p>
    <p class="precio-grande">
        <strong><fmt:formatNumber value="${oferta.precio}" type="currency" currencyCode="USD"/></strong>
        <c:if test="${oferta.precioAnterior != null}">
            <span class="antes"><fmt:formatNumber value="${oferta.precioAnterior}" type="currency" currencyCode="USD"/></span>
        </c:if>
        <c:if test="${oferta.descuento != null}">
            <span class="descuento">-<c:out value="${oferta.descuento}"/>%</span>
        </c:if>
    </p>
    <p>Temperatura: <span class="temp ${oferta.temperatura >= 100 ? 'caliente' : oferta.temperatura >= 0 ? 'tibia' : 'frio'}"><c:out value="${oferta.temperatura}"/>° <span class="palabra">${oferta.temperatura >= 100 ? 'Caliente' : oferta.temperatura >= 0 ? 'Tibia' : 'Fría'}</span></span></p>

    <c:choose>
        <c:when test="${pageContext.request.userPrincipal != null and oferta.estado == 'ACTIVA'}">
            <div class="votos">
                <form method="post" action="<c:url value='/ofertas/${oferta.id}/voto'/>" class="en-linea">
                    <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                    <input type="hidden" name="valor" value="1">
                    <button type="submit">▲ Caliente</button>
                </form>
                <form method="post" action="<c:url value='/ofertas/${oferta.id}/voto'/>" class="en-linea">
                    <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                    <input type="hidden" name="valor" value="-1">
                    <button type="submit" class="secundario">▼ Frío</button>
                </form>
            </div>
        </c:when>
        <c:when test="${oferta.estado == 'ACTIVA'}">
            <p><a href="<c:url value='/login'/>">Inicia sesión</a> para votar, comentar o reportar.</p>
        </c:when>
    </c:choose>
</article>

<section class="comentarios">
    <h2>Comentarios</h2>
    <c:if test="${pageContext.request.userPrincipal != null and oferta.estado == 'ACTIVA'}">
        <form method="post" action="<c:url value='/ofertas/${oferta.id}/comentarios'/>">
            <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
            <label for="texto">Tu comentario</label>
            <textarea id="texto" name="texto" rows="3" maxlength="500" required></textarea>
            <button type="submit">Comentar</button>
        </form>
    </c:if>
    <c:forEach var="c" items="${comentarios}">
        <div class="comentario">
            <p class="meta"><strong><c:out value="${c.autor}"/></strong> · <c:out value="${c.creado}"/></p>
            <p><c:out value="${c.texto}"/></p>
        </div>
    </c:forEach>
    <c:if test="${empty comentarios}"><p>Aún no hay comentarios.</p></c:if>
</section>

<c:if test="${pageContext.request.userPrincipal != null and oferta.estado == 'ACTIVA'}">
    <section class="reportar">
        <h2>Reportar esta oferta</h2>
        <form method="post" action="<c:url value='/ofertas/${oferta.id}/reportes'/>">
            <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
            <label for="motivo">Motivo</label>
            <select id="motivo" name="motivo" required>
                <option value="">Elige un motivo</option>
                <c:forEach var="m" items="${motivos}">
                    <option value="<c:out value='${m}'/>"><c:out value="${m.etiqueta}"/></option>
                </c:forEach>
            </select>
            <button type="submit" class="secundario">Enviar reporte</button>
        </form>
    </section>
</c:if>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
