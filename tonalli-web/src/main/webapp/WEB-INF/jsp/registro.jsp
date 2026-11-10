<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="titulo" value="Crear cuenta" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Crear cuenta</h1>

<form:form cssClass="formulario" method="post" modelAttribute="form" servletRelativeAction="/registro">
    <label for="nombre">Nombre</label>
    <form:input id="nombre" path="nombre" maxlength="60" autocomplete="name"/>
    <form:errors path="nombre" cssClass="campo-error" element="p"/>

    <label for="correo">Correo</label>
    <form:input id="correo" path="correo" type="email" maxlength="120" autocomplete="username"/>
    <form:errors path="correo" cssClass="campo-error" element="p"/>

    <label for="password">Contraseña (10 a 64 caracteres)</label>
    <form:password id="password" path="password" maxlength="64" autocomplete="new-password"/>
    <form:errors path="password" cssClass="campo-error" element="p"/>

    <label for="confirmar">Repite la contraseña</label>
    <form:password id="confirmar" path="confirmar" maxlength="64" autocomplete="new-password"/>
    <form:errors path="confirmar" cssClass="campo-error" element="p"/>

    <button type="submit">Registrarme</button>
</form:form>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
