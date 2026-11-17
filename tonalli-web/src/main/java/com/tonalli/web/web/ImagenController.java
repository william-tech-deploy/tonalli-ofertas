package com.tonalli.web.web;

import com.tonalli.web.model.Rol;
import com.tonalli.web.security.UsuarioActual;
import com.tonalli.web.service.OfertaService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Sirve las fotos de las ofertas. Siempre como image/jpeg, porque el servidor las re-codificó a JPEG:
 * nunca se devuelve el archivo tal como lo subió el usuario. La cabecera X-Content-Type-Options: nosniff
 * (puesta por Spring Security) impide que el navegador intente interpretarlas como otra cosa.
 */
@Controller
public class ImagenController {

    private final OfertaService ofertas;

    public ImagenController(OfertaService ofertas) {
        this.ofertas = ofertas;
    }

    @GetMapping("/imagenes/{id:\\d+}")
    public ResponseEntity<byte[]> foto(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual usuario) {
        boolean esModerador = usuario != null && usuario.getRol() != Rol.USER;
        byte[] datos = ofertas.foto(id, esModerador);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                // Privada: si un moderador oculta la oferta, una caché compartida no seguiría sirviendo la foto.
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(datos);
    }
}
