<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Gráficos de actividad" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Actividad del sitio</h1>

<div class="graficos">
    <section class="tarjeta">
        <h2>Ofertas activas por categoría</h2>
        <canvas id="grafico-categorias" width="520" height="320" role="img"
                aria-label="Gráfico de barras: ofertas activas por categoría"
                data-url="<c:url value='/api/estadisticas/ofertas-por-categoria'/>" data-tipo="barras"></canvas>
        <table class="tabla" id="tabla-categorias"><thead><tr><th>Categoría</th><th>Ofertas</th></tr></thead><tbody></tbody></table>
    </section>
    <section class="tarjeta">
        <h2>Reportes por motivo</h2>
        <canvas id="grafico-reportes" width="520" height="320" role="img"
                aria-label="Gráfico circular: reportes por motivo"
                data-url="<c:url value='/api/estadisticas/reportes-por-motivo'/>" data-tipo="pastel"></canvas>
        <table class="tabla" id="tabla-reportes"><thead><tr><th>Motivo</th><th>Reportes</th></tr></thead><tbody></tbody></table>
    </section>
</div>

<script src="<c:url value='/js/graficos.js'/>"></script>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
