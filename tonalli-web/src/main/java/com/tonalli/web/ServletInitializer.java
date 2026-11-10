package com.tonalli.web;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/** Permite desplegar el mismo WAR en un Tomcat externo (etapa de Servlets/JSP de la materia). */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(TonalliWebApplication.class);
    }
}
