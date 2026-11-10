<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="titulo" value="Publicar oferta" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Publicar una oferta</h1>

<form:form cssClass="formulario" method="post" modelAttribute="form" servletRelativeAction="/ofertas/nueva">
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

    <label for="categoriaId">Categoría</label>
    <form:select id="categoriaId" path="categoriaId">
        <form:option value="" label="Elige una categoría"/>
        <form:options items="${categorias}" itemValue="id" itemLabel="nombre"/>
    </form:select>
    <form:errors path="categoriaId" cssClass="campo-error" element="p"/>

    <button type="submit">Publicar</button>
</form:form>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
