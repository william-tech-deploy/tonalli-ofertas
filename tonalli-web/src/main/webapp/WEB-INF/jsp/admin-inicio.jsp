<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="titulo" value="Administración" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<%-- Panel de inicio del administrador. SecurityConfig exige el rol ADMIN para todo /admin/**. --%>
<h1>Administración</h1>
<p>Resumen del sitio en este momento.</p>

<div class="cifras">
    <div class="cifra"><strong><c:out value="${resumen.usuarios}"/></strong><span>Usuarios registrados</span></div>
    <div class="cifra"><strong><c:out value="${resumen.ofertasActivas}"/></strong><span>Ofertas activas</span></div>
    <div class="cifra ${resumen.reportesAbiertos > 0 ? 'pendiente' : ''}"><strong><c:out value="${resumen.reportesAbiertos}"/></strong><span>Reportes por revisar</span></div>
    <div class="cifra ${resumen.propuestasPendientes > 0 ? 'pendiente' : ''}"><strong><c:out value="${resumen.propuestasPendientes}"/></strong><span>Propuestas de categoría</span></div>
</div>
<p class="meta">
    <c:out value="${resumen.administradores}"/> administradores · <c:out value="${resumen.moderadores}"/> moderadores ·
    <c:out value="${resumen.ofertasOcultas}"/> ofertas ocultas por moderación
</p>

<nav class="accesos" aria-label="Secciones de administración">
    <a class="acceso" href="<c:url value='/moderacion'/>"><strong>Moderación</strong><span>Revisa reportes, oculta o restaura ofertas.</span></a>
    <a class="acceso" href="<c:url value='/admin/usuarios'/>"><strong>Usuarios y roles</strong><span>Asigna Usuario, Moderador o Administrador.</span></a>
    <a class="acceso" href="<c:url value='/admin/categorias'/>"><strong>Categorías y propuestas</strong><span>Crea categorías y resuelve propuestas.</span></a>
    <a class="acceso" href="<c:url value='/admin/informes'/>"><strong>Reportes y estadísticas</strong><span>Indicadores, siete gráficos y descarga de reportes en CSV.</span></a>
</nav>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
