<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="titulo" value="Mi cuenta" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<%-- Panel del usuario. Todo lo que muestra sale de la sesión (PanelController); cada dato se escribe con c:out. --%>
<h1>Mi cuenta</h1>

<section class="tarjeta perfil">
    <div>
        <h2><c:out value="${perfil.nombre}"/></h2>
        <p class="meta"><c:out value="${perfil.correo}"/> · miembro desde <c:out value="${perfil.miembroDesde}"/></p>
    </div>
    <span class="chip-rol rol-${perfil.rol}"><c:out value="${perfil.rolTexto}"/></span>
</section>

<div class="cifras">
    <div class="cifra"><strong><c:out value="${perfil.karma}"/></strong><span>Reputación (karma)</span></div>
    <div class="cifra"><strong>×<c:out value="${perfil.pesoVoto}"/></strong><span>Peso de tu voto</span></div>
    <div class="cifra"><strong><c:out value="${fn:length(ofertas)}"/></strong><span>Ofertas publicadas</span></div>
</div>
<p class="meta">Tu voto pesa ×2 desde 10 de karma y ×3 desde 50.</p>

<nav class="accesos" aria-label="Accesos de tu cuenta">
    <a class="acceso" href="<c:url value='/ofertas/nueva'/>"><strong>Publicar una oferta</strong><span>Comparte un precio que encontraste.</span></a>
    <a class="acceso" href="<c:url value='/alertas'/>"><strong>Mis alertas</strong><span>Avisos por palabra clave y precio.</span></a>
    <a class="acceso" href="<c:url value='/propuestas'/>"><strong>Propuestas</strong><span>Pide o apoya nuevas categorías.</span></a>
</nav>

<h2>Mis ofertas</h2>
<c:choose>
    <c:when test="${empty ofertas}">
        <p>Todavía no has publicado ofertas. <a href="<c:url value='/ofertas/nueva'/>">Publica la primera</a>.</p>
    </c:when>
    <c:otherwise>
        <table class="tabla">
            <thead><tr><th>Oferta</th><th>Precio</th><th>Temperatura</th><th>Estado</th><th><span class="oculto-visual">Acción</span></th></tr></thead>
            <tbody>
            <c:forEach var="o" items="${ofertas}">
                <tr>
                    <td>
                        <c:choose>
                            <c:when test="${o.estado == 'ACTIVA'}"><a href="<c:url value='/ofertas/${o.id}'/>"><c:out value="${o.titulo}"/></a></c:when>
                            <c:otherwise><c:out value="${o.titulo}"/></c:otherwise>
                        </c:choose>
                        <br><span class="meta"><c:out value="${o.tienda}"/> · <c:out value="${o.creada}"/></span>
                    </td>
                    <td><fmt:formatNumber value="${o.precio}" type="currency" currencyCode="USD"/></td>
                    <td><c:out value="${o.temperatura}"/>° <c:out value="${o.temperatura >= 100 ? 'Caliente' : o.temperatura >= 0 ? 'Tibia' : 'Fría'}"/></td>
                    <td><c:out value="${o.estado == 'ACTIVA' ? 'Activa' : 'Oculta por moderación'}"/></td>
                    <td>
                        <c:if test="${o.estado == 'ACTIVA'}">
                            <form method="post" action="<c:url value='/cuenta/ofertas/${o.id}/retirar'/>" class="en-linea">
                                <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
                                <button type="submit" class="peligro" aria-label="Retirar <c:out value='${o.titulo}'/>">Retirar</button>
                            </form>
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <p class="meta">Retirar quita la oferta del catálogo para siempre. Úsalo cuando la tienda termine la promoción.</p>
    </c:otherwise>
</c:choose>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
