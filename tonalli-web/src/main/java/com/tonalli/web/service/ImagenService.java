package com.tonalli.web.service;

import com.tonalli.web.dto.ImagenProcesada;
import com.tonalli.web.exception.OperacionInvalidaException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Valida y limpia la foto que sube un usuario. Sigue la guía de OWASP para subida de archivos
 * (File Upload Cheat Sheet):
 *
 * 1. Tamaño máximo antes de leer nada (2 MB).
 * 2. El tipo se decide por el CONTENIDO, no por el nombre ni por la cabecera Content-Type que manda el
 *    navegador (ambos los controla quien sube). Solo se aceptan JPEG y PNG; SVG nunca, porque puede llevar
 *    código.
 * 3. Las medidas se leen de la cabecera ANTES de decodificar: una imagen diminuta en bytes puede declarar
 *    50 000 × 50 000 píxeles y agotar la memoria al abrirla ("bomba de descompresión").
 * 4. La imagen se vuelve a dibujar y se guarda como un JPEG nuevo de 1000 px como máximo. Eso descarta
 *    cualquier contenido escondido en el archivo original y borra los metadatos EXIF, entre ellos la
 *    ubicación GPS de la foto: la privacidad del usuario queda protegida aunque él no lo sepa.
 * 5. El archivo nunca se guarda con el nombre que le puso el usuario ni en una carpeta pública.
 */
@Service
public class ImagenService {

    static final long MAX_BYTES = 2L * 1024 * 1024;
    static final int MAX_LADO_ORIGINAL = 8000;
    static final long MAX_PIXELES_ORIGINAL = 24_000_000L;
    static final int LADO_FINAL = 1000;
    private static final Set<String> FORMATOS = Set.of("jpeg", "png");

    /** Devuelve null si no se subió ninguna foto (la foto es opcional). */
    public ImagenProcesada procesar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new OperacionInvalidaException("La foto supera los 2 MB.");
        }
        try {
            return procesar(archivo.getBytes());
        } catch (IOException e) {
            throw new OperacionInvalidaException("No se pudo leer la foto.");
        }
    }

    ImagenProcesada procesar(byte[] bytes) {
        if (bytes.length > MAX_BYTES) {
            throw new OperacionInvalidaException("La foto supera los 2 MB.");
        }
        BufferedImage original = leerConLimites(bytes);
        return reescribir(original);
    }

    private BufferedImage leerConLimites(byte[] bytes) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> lectores = entrada == null ? null : ImageIO.getImageReaders(entrada);
            if (lectores == null || !lectores.hasNext()) {
                throw new OperacionInvalidaException("El archivo no es una foto JPEG o PNG.");
            }
            ImageReader lector = lectores.next();
            try {
                String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
                if (!FORMATOS.contains(formato)) {
                    throw new OperacionInvalidaException("Solo se aceptan fotos JPEG o PNG.");
                }
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho < 1 || alto < 1 || ancho > MAX_LADO_ORIGINAL || alto > MAX_LADO_ORIGINAL
                        || (long) ancho * alto > MAX_PIXELES_ORIGINAL) {
                    throw new OperacionInvalidaException("La foto es demasiado grande en píxeles.");
                }
                BufferedImage imagen = lector.read(0);
                if (imagen == null) {
                    throw new OperacionInvalidaException("El archivo no es una foto válida.");
                }
                return imagen;
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof OperacionInvalidaException propia) {
                throw propia;
            }
            // Un archivo dañado o manipulado puede hacer fallar al decodificador de muchas formas.
            throw new OperacionInvalidaException("El archivo no es una foto válida.");
        }
    }

    private ImagenProcesada reescribir(BufferedImage original) {
        double escala = Math.min(1.0, (double) LADO_FINAL / Math.max(original.getWidth(), original.getHeight()));
        int ancho = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(original.getHeight() * escala));

        BufferedImage limpia = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = limpia.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE); // fondo para las PNG con transparencia (JPEG no la admite)
            g.fillRect(0, 0, ancho, alto);
            g.drawImage(original, 0, 0, ancho, alto, null);
        } finally {
            g.dispose();
        }

        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream destino = ImageIO.createImageOutputStream(salida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(0.85f);
            escritor.setOutput(destino);
            // Sin metadatos: el JPEG nuevo no hereda EXIF, GPS ni comentarios del original.
            escritor.write(null, new IIOImage(limpia, null, null), parametros);
        } catch (IOException e) {
            throw new OperacionInvalidaException("No se pudo procesar la foto.");
        } finally {
            escritor.dispose();
        }
        return new ImagenProcesada(salida.toByteArray(), ancho, alto);
    }
}
