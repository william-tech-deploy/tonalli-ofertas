package com.tonalli.web.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tonalli.web.dto.ImagenProcesada;
import com.tonalli.web.exception.OperacionInvalidaException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** La foto que sube un usuario se acepta solo si es JPEG o PNG real, y siempre sale como JPEG nuevo. */
class ImagenServiceTest {

    private final ImagenService servicio = new ImagenService();

    private static byte[] imagen(String formato, int ancho, int alto) throws Exception {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, salida);
        return salida.toByteArray();
    }

    @Test
    void sinArchivoNoHayFoto() {
        assertNull(servicio.procesar(new MockMultipartFile("foto", new byte[0])));
        assertNull(servicio.procesar((org.springframework.web.multipart.MultipartFile) null));
    }

    @Test
    void unaPngGrandeSaleComoJpegDeMil() throws Exception {
        ImagenProcesada r = servicio.procesar(imagen("png", 2000, 1000));
        assertEquals(1000, r.getAncho());
        assertEquals(500, r.getAlto());
        // Todo JPEG empieza con los bytes FF D8: el resultado es un JPEG, sea cual sea la entrada.
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xD8}, Arrays.copyOf(r.getJpeg(), 2));
    }

    @Test
    void unaFotoPequenaNoSeAgranda() throws Exception {
        ImagenProcesada r = servicio.procesar(imagen("jpg", 300, 200));
        assertEquals(300, r.getAncho());
        assertEquals(200, r.getAlto());
    }

    @Test
    void unTextoDisfrazadoDeFotoSeRechaza() {
        byte[] falso = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThrows(OperacionInvalidaException.class,
                () -> servicio.procesar(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", falso)));
    }

    @Test
    void otrosFormatosSeRechazan() throws Exception {
        assertThrows(OperacionInvalidaException.class, () -> servicio.procesar(imagen("gif", 10, 10)));
        assertThrows(OperacionInvalidaException.class, () -> servicio.procesar(imagen("bmp", 10, 10)));
    }

    @Test
    void unaImagenDemasiadoGrandeEnPixelesSeRechazaSinAbrirla() throws Exception {
        assertThrows(OperacionInvalidaException.class, () -> servicio.procesar(imagen("png", 9000, 1)));
    }

    @Test
    void unArchivoDeMasDeDosMegasSeRechaza() {
        byte[] grande = new byte[(int) ImagenService.MAX_BYTES + 1];
        assertThrows(OperacionInvalidaException.class,
                () -> servicio.procesar(new MockMultipartFile("foto", "f.jpg", "image/jpeg", grande)));
    }
}
