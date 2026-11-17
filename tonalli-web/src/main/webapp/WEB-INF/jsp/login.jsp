<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="titulo" value="Iniciar sesión" scope="request"/>
<%@ include file="/WEB-INF/jsp/fragmentos/cabecera.jspf" %>

<h1>Iniciar sesión</h1>

<c:if test="${param.error != null}">
    <p class="aviso error" role="alert">Correo o contraseña incorrectos, o la cuenta está bloqueada temporalmente. Inténtalo de nuevo en unos minutos.</p>
</c:if>
<c:if test="${param.noverificado != null}">
    <p class="aviso error" role="alert">Tu correo aún no está verificado. Revisa el mensaje que te enviamos al registrarte.</p>
</c:if>
<c:if test="${param.salio != null}">
    <p class="aviso ok" role="status">Cerraste sesión.</p>
</c:if>

<form class="formulario" method="post" action="<c:url value='/login'/>">
    <%@ include file="/WEB-INF/jsp/fragmentos/csrf.jspf" %>
    <label for="correo">Correo</label>
    <input id="correo" type="email" name="correo" maxlength="120" required autocomplete="username">
    <label for="password">Contraseña</label>
    <input id="password" type="password" name="password" maxlength="64" required autocomplete="current-password">
    <button type="submit">Entrar</button>
</form>
<p>¿No tienes cuenta? <a href="<c:url value='/registro'/>">Regístrate</a>.</p>

<c:if test="${demoActivo}">
    <%-- Solo con TONALLI_DEMO=true. Muestra los correos de prueba, nunca la contraseña. --%>
    <section class="tarjeta demo">
        <h2>Modo demostración</h2>
        <p>Usuarios de ejemplo: <code>demo@tonalli.local</code>, <code>ana.martinez@tonalli.local</code>,
            <code>carlos.rivas@tonalli.local</code>, <code>sofia.hernandez@tonalli.local</code> y
            <code>diego.flores@tonalli.local</code>.</p>
        <p class="meta">Todos usan la misma contraseña: la variable <code>TONALLI_DEMO_PASSWORD</code> del archivo <code>.env</code>.</p>
    </section>
</c:if>

<%@ include file="/WEB-INF/jsp/fragmentos/pie.jspf" %>
