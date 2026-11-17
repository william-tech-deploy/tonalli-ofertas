<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Reportes y estadísticas" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<%-- Reportes y estadísticas del sitio. SecurityConfig exige rol ADMIN para todo /admin/**.
     Todos los valores salen con <c:out>, que escapa el HTML: un nombre de categoría escrito por un
     usuario no puede inyectar etiquetas en esta página. Los gráficos los pinta /js/graficos.js leyendo
     la API, y también escribe con textContent por el mismo motivo. --%>

<p class="meta"><a href="<c:url value='/admin'/>">← Administración</a></p>
<h1>Reportes y estadísticas</h1>
<p>Indicadores del sitio, siete gráficos de actividad y descarga de reportes en CSV.</p>

<h2>Indicadores</h2>
<div class="cifras">
    <div class="cifra"><strong><c:out value="${estadisticas.ofertasActivas}"/></strong><span>Ofertas activas</span></div>
    <div class="cifra"><strong><c:out value="${estadisticas.ofertasUltimos30Dias}"/></strong><span>Publicadas en 30 días</span></div>
    <div class="cifra"><strong><c:out value="${estadisticas.descuentoPromedio}"/>%</strong><span>Descuento promedio</span></div>
    <div class="cifra"><strong>$<c:out value="${estadisticas.precioPromedio}"/></strong><span>Precio promedio</span></div>
    <div class="cifra"><strong><c:out value="${estadisticas.temperaturaPromedio}"/>°</strong><span>Temperatura promedio</span></div>
    <div class="cifra"><strong><c:out value="${estadisticas.usuarios}"/></strong><span>Usuarios registrados</span></div>
    <div class="cifra"><strong><c:out value="${estadisticas.tasaVerificacion}"/>%</strong><span>Correos verificados</span></div>
    <div class="cifra"><strong><c:out value="${estadisticas.votos}"/></strong><span>Votos emitidos</span></div>
    <div class="cifra"><strong><c:out value="${estadisticas.votosPorOferta}"/></strong><span>Votos por oferta activa</span></div>
    <div class="cifra"><strong><c:out value="${estadisticas.comentarios}"/></strong><span>Comentarios</span></div>
    <div class="cifra ${estadisticas.reportesAbiertos > 0 ? 'pendiente' : ''}"><strong><c:out value="${estadisticas.reportesAbiertos}"/></strong><span>Reportes sin revisar</span></div>
    <div class="cifra"><strong><c:out value="${estadisticas.ofertasOcultas}"/></strong><span>Ocultas por moderación</span></div>
</div>
<p class="meta">
    <c:out value="${estadisticas.ofertasConDescuento}"/> de las <c:out value="${estadisticas.ofertasActivas}"/>
    ofertas activas declaran precio anterior · <c:out value="${estadisticas.reportesTotales}"/> reportes recibidos en total
</p>

<h2>Gráficos</h2>
<p class="meta">Los gráficos muestran el estado actual del sitio. El rango de fechas de abajo se aplica a los archivos que descargues, no a los gráficos.</p>

<div class="graficos">
    <section class="tarjeta">
        <h3>Ofertas publicadas por mes</h3>
        <canvas width="520" height="320" role="img"
                aria-label="Gráfico de barras: ofertas publicadas en cada uno de los últimos doce meses"
                data-url="<c:url value='/api/estadisticas/ofertas-por-mes'/>"
                data-tipo="barras" data-tabla="tabla-meses"></canvas>
        <table class="tabla" id="tabla-meses">
            <caption class="oculto-visual">Ofertas publicadas por mes</caption>
            <thead><tr><th>Mes</th><th>Ofertas</th></tr></thead><tbody></tbody>
        </table>
    </section>

    <section class="tarjeta">
        <h3>Ofertas activas por categoría</h3>
        <canvas width="520" height="320" role="img"
                aria-label="Gráfico de barras: ofertas activas por categoría"
                data-url="<c:url value='/api/estadisticas/ofertas-por-categoria'/>"
                data-tipo="barras" data-tabla="tabla-categorias"></canvas>
        <table class="tabla" id="tabla-categorias">
            <caption class="oculto-visual">Ofertas activas por categoría</caption>
            <thead><tr><th>Categoría</th><th>Ofertas</th></tr></thead><tbody></tbody>
        </table>
    </section>

    <section class="tarjeta">
        <h3>Temperatura de las ofertas activas</h3>
        <canvas width="520" height="320" role="img"
                aria-label="Gráfico de barras: cuántas ofertas activas están frías, tibias o calientes"
                data-url="<c:url value='/api/estadisticas/temperatura'/>"
                data-tipo="barras" data-tabla="tabla-temperatura"></canvas>
        <table class="tabla" id="tabla-temperatura">
            <caption class="oculto-visual">Ofertas activas por tramo de temperatura</caption>
            <thead><tr><th>Tramo</th><th>Ofertas</th></tr></thead><tbody></tbody>
        </table>
    </section>

    <section class="tarjeta">
        <h3>Descuento promedio por categoría</h3>
        <canvas width="520" height="320" role="img"
                aria-label="Gráfico de barras: descuento promedio en porcentaje por categoría"
                data-url="<c:url value='/api/estadisticas/descuento-por-categoria'/>"
                data-tipo="barras" data-tabla="tabla-descuentos" data-sufijo="%"></canvas>
        <table class="tabla" id="tabla-descuentos">
            <caption class="oculto-visual">Descuento promedio por categoría</caption>
            <thead><tr><th>Categoría</th><th>Descuento</th></tr></thead><tbody></tbody>
        </table>
    </section>

    <section class="tarjeta">
        <h3>Usuarios por rol</h3>
        <canvas width="520" height="320" role="img"
                aria-label="Gráfico circular: usuarios por rol"
                data-url="<c:url value='/api/estadisticas/usuarios-por-rol'/>"
                data-tipo="pastel" data-tabla="tabla-roles"></canvas>
        <table class="tabla" id="tabla-roles">
            <caption class="oculto-visual">Usuarios por rol</caption>
            <thead><tr><th>Rol</th><th>Usuarios</th></tr></thead><tbody></tbody>
        </table>
    </section>

    <section class="tarjeta">
        <h3>Reportes por motivo</h3>
        <canvas width="520" height="320" role="img"
                aria-label="Gráfico circular: reportes por motivo"
                data-url="<c:url value='/api/estadisticas/reportes-por-motivo'/>"
                data-tipo="pastel" data-tabla="tabla-motivos"></canvas>
        <table class="tabla" id="tabla-motivos">
            <caption class="oculto-visual">Reportes por motivo</caption>
            <thead><tr><th>Motivo</th><th>Reportes</th></tr></thead><tbody></tbody>
        </table>
    </section>

    <section class="tarjeta">
        <h3>Reportes por estado</h3>
        <canvas width="520" height="320" role="img"
                aria-label="Gráfico de barras: reportes abiertos, resueltos y descartados"
                data-url="<c:url value='/api/estadisticas/reportes-por-estado'/>"
                data-tipo="barras" data-tabla="tabla-estados"></canvas>
        <table class="tabla" id="tabla-estados">
            <caption class="oculto-visual">Reportes por estado</caption>
            <thead><tr><th>Estado</th><th>Reportes</th></tr></thead><tbody></tbody>
        </table>
    </section>
</div>

<h2>Descargar un reporte</h2>
<%-- Es un GET y no un POST porque la descarga no cambia nada en el servidor: el enlace se puede
     compartir y reabrir. Por eso tampoco lleva token CSRF. --%>
<form method="get" action="<c:url value='/admin/informes/descargar'/>" class="filtros">
    <div class="filtros-campos">
        <div class="campo">
            <label for="tipo">Reporte</label>
            <select id="tipo" name="tipo">
                <c:forEach var="t" items="${tipos}">
                    <option value="<c:out value='${t}'/>"><c:out value="${t.etiqueta}"/></option>
                </c:forEach>
            </select>
        </div>
        <div class="campo">
            <label for="desde">Desde</label>
            <input type="date" id="desde" name="desde" value="<c:out value='${rango.desdeUsado}'/>">
        </div>
        <div class="campo">
            <label for="hasta">Hasta</label>
            <input type="date" id="hasta" name="hasta" value="<c:out value='${rango.hastaUsado}'/>">
        </div>
    </div>
    <div class="filtros-acciones">
        <button type="submit">Descargar CSV</button>
        <span class="meta">Hasta <c:out value="${maxFilas}"/> filas por archivo</span>
    </div>
</form>

<ul class="lista">
    <li><strong>Ofertas publicadas:</strong> una fila por oferta, con precios, descuento, estado, autor y temperatura.</li>
    <li><strong>Usuarios registrados:</strong> altas del periodo, con rol, karma y si verificaron el correo.
        Contiene datos personales: cada descarga queda registrada en la auditoría del servidor.</li>
    <li><strong>Reportes de moderación:</strong> reportes recibidos y en qué quedaron. No incluye quién reportó.</li>
    <li><strong>Resumen estadístico:</strong> los indicadores de esta página y los datos de los siete gráficos en una sola hoja.</li>
</ul>
<p class="meta">
    El archivo sale en CSV con separador de coma y codificación UTF-8, así que Excel, LibreOffice y
    Google Sheets lo abren sin configurar nada. Si no eliges fechas, el periodo son los últimos
    <c:out value="${diasPorOmision}"/> días.
</p>

<script src="<c:url value='/js/graficos.js'/>"></script>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
