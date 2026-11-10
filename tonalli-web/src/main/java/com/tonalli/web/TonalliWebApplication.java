package com.tonalli.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * Punto de entrada. @SpringBootApplication activa tres cosas: configuración, autoconfiguración y
 * escaneo de componentes del paquete com.tonalli.web y sus subpaquetes.
 * VIA_DTO hace que una Page se serialice con un formato JSON estable en la API REST.
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class TonalliWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(TonalliWebApplication.class, args);
    }
}
