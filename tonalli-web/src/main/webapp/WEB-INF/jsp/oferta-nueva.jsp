<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="titulo" value="Publicar oferta" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Publicar una oferta</h1>

<form:form cssClass="formulario" method="post" modelAttribute="form" servletRelativeAction="/ofertas/nueva" enctype="multipart/form-data">
    <label for="titulo">Título</label>
    <form:input id="titulo" path="titulo" maxlength="120"/>
    <form:errors path="titulo" cssClass="campo-error" element="p"/>

    <label for="tienda">Tienda</label>
    <form:input id="tienda" path="tienda" maxlength="80"/>
    <form:errors path="tienda" cssClass="campo-error" element="p"/>

    <label for="precio">Precio actual (USD)</label>
    <form:input id="precio" path="precio" inputmode="decimal" maxlength="10"/>
    <form:errors path="precio" cssClass="campo-error" element="p"/>

    <label for="precioAnterior">Precio anterior (USD, opcional)</label>
    <form:input id="precioAnterior" path="precioAnterior" inputmode="decimal" maxlength="10"/>
    <form:errors path="precioAnterior" cssClass="campo-error" element="p"/>

    <label for="enlace">Enlace a la oferta en la tienda (opcional, https://)</label>
    <form:input id="enlace" path="enlace" type="url" maxlength="300" placeholder="https://"/>
    <form:errors path="enlace" cssClass="campo-error" element="p"/>

    <label for="foto">Foto del producto (opcional)</label>
    <input id="foto" type="file" name="foto" accept="image/jpeg,image/png" aria-describedby="foto-ayuda">
    <p id="foto-ayuda" class="meta">JPEG o PNG, hasta 2 MB. Usa una foto tuya: la del producto en la tienda o en el estante.
        Se reduce a 1000 px y se le quitan los datos ocultos, como la ubicación GPS.</p>
    <p id="foto-aviso" class="campo-error" role="alert" hidden>La foto supera los 2 MB. Elige una más pequeña.</p>

    <label for="categoriaId">Categoría</label>
    <form:select id="categoriaId" path="categoriaId">
        <form:option value="" label="Elige una categoría"/>
        <form:options items="${categorias}" itemValue="id" itemLabel="nombre"/>
    </form:select>
    <form:errors path="categoriaId" cssClass="campo-error" element="p"/>

    <button type="submit">Publicar</button>
</form:form>
<script src="<c:url value='/js/foto.js'/>"></script>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
