// Aviso inmediato si la foto pesa más de 2 MB. Es solo comodidad: la regla real la aplica el servidor
// (ImagenService), porque cualquier comprobación en el navegador se puede saltar.
(function () {
    var campo = document.getElementById('foto');
    var aviso = document.getElementById('foto-aviso');
    if (!campo || !aviso) { return; }
    campo.addEventListener('change', function () {
        var archivo = campo.files && campo.files[0];
        var grande = !!archivo && archivo.size > 2 * 1024 * 1024;
        aviso.hidden = !grande;
        if (grande) { campo.value = ''; }
    });
}());
