/*
 * Gráficos del panel de administración, dibujados con la API <canvas> sin librerías externas.
 * Por qué sin Chart.js: así la política CSP "script-src 'self'" no necesita abrirse a una CDN.
 * Para cambiar a Chart.js, ver docs/ y la sección de gráficos del informe.
 *
 * Seguridad: los datos llegan de /api/estadisticas/** (solo ADMIN) y se pintan con textContent y
 * fillText, nunca con innerHTML, así que un nombre de categoría con HTML no se ejecuta.
 */
(function () {
    'use strict';

    var COLORES = ['#C73E14', '#0B6E7D', '#F5A524', '#14213D', '#7656BE', '#2A8F61', '#B93A77', '#80652F'];

    function obtener(url) {
        return fetch(url, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (respuesta) {
                if (!respuesta.ok) {
                    throw new Error('HTTP ' + respuesta.status);
                }
                return respuesta.json();
            });
    }

    function preparar(canvas) {
        var ancho = canvas.width;
        var alto = canvas.height;
        var escala = window.devicePixelRatio || 1;
        canvas.width = Math.round(ancho * escala);
        canvas.height = Math.round(alto * escala);
        canvas.style.width = ancho + 'px';
        canvas.style.height = 'auto';
        var ctx = canvas.getContext('2d');
        ctx.scale(escala, escala);
        ctx.clearRect(0, 0, ancho, alto);
        return { ctx: ctx, ancho: ancho, alto: alto };
    }

    function recortar(ctx, texto, maximo) {
        if (ctx.measureText(texto).width <= maximo) {
            return texto;
        }
        var corto = texto;
        while (corto.length > 1 && ctx.measureText(corto + '…').width > maximo) {
            corto = corto.slice(0, -1);
        }
        return corto + '…';
    }

    function mensajeVacio(lienzo) {
        lienzo.ctx.fillStyle = '#4A5877';
        lienzo.ctx.font = '16px sans-serif';
        lienzo.ctx.textAlign = 'center';
        lienzo.ctx.fillText('Sin datos todavía', lienzo.ancho / 2, lienzo.alto / 2);
    }

    function barras(canvas, etiquetas, valores) {
        var lienzo = preparar(canvas);
        var ctx = lienzo.ctx;
        var maximo = Math.max.apply(null, valores.concat([0]));
        if (maximo === 0) {
            mensajeVacio(lienzo);
            return;
        }
        var izquierda = 40, derecha = 16, arriba = 24, abajo = 56;
        var areaAncho = lienzo.ancho - izquierda - derecha;
        var areaAlto = lienzo.alto - arriba - abajo;
        var paso = Math.max(1, Math.ceil(maximo / 5));
        var tope = Math.ceil(maximo / paso) * paso;

        ctx.font = '12px sans-serif';
        ctx.textAlign = 'right';
        ctx.fillStyle = '#4A5877';
        ctx.strokeStyle = '#E6D8C3';
        ctx.lineWidth = 1;
        for (var v = 0; v <= tope; v += paso) {
            var y = arriba + areaAlto - (v / tope) * areaAlto;
            ctx.beginPath();
            ctx.moveTo(izquierda, y);
            ctx.lineTo(izquierda + areaAncho, y);
            ctx.stroke();
            ctx.fillText(String(v), izquierda - 6, y + 4);
        }

        var ranura = areaAncho / valores.length;
        var anchoBarra = Math.min(56, ranura * 0.6);
        for (var i = 0; i < valores.length; i++) {
            var alto = (valores[i] / tope) * areaAlto;
            var x = izquierda + ranura * i + (ranura - anchoBarra) / 2;
            ctx.fillStyle = COLORES[i % COLORES.length];
            ctx.fillRect(x, arriba + areaAlto - alto, anchoBarra, alto);
            ctx.fillStyle = '#14213D';
            ctx.textAlign = 'center';
            ctx.font = '13px sans-serif';
            ctx.fillText(String(valores[i]), x + anchoBarra / 2, arriba + areaAlto - alto - 6);
            ctx.font = '12px sans-serif';
            ctx.fillStyle = '#4A5877';
            ctx.fillText(recortar(ctx, etiquetas[i], ranura - 4), izquierda + ranura * i + ranura / 2, arriba + areaAlto + 18);
        }
    }

    function pastel(canvas, etiquetas, valores) {
        var lienzo = preparar(canvas);
        var ctx = lienzo.ctx;
        var total = valores.reduce(function (a, b) { return a + b; }, 0);
        if (total === 0) {
            mensajeVacio(lienzo);
            return;
        }
        var radio = Math.min(lienzo.alto / 2 - 16, lienzo.ancho / 5);
        var cx = radio + 24;
        var cy = lienzo.alto / 2;
        var inicio = -Math.PI / 2;
        for (var i = 0; i < valores.length; i++) {
            if (valores[i] === 0) {
                continue;
            }
            var fin = inicio + (valores[i] / total) * Math.PI * 2;
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.arc(cx, cy, radio, inicio, fin);
            ctx.closePath();
            ctx.fillStyle = COLORES[i % COLORES.length];
            ctx.fill();
            inicio = fin;
        }
        ctx.font = '14px sans-serif';
        ctx.textAlign = 'left';
        var x = cx + radio + 28;
        for (var j = 0; j < etiquetas.length; j++) {
            var y = 40 + j * 28;
            ctx.fillStyle = COLORES[j % COLORES.length];
            ctx.fillRect(x, y - 12, 14, 14);
            ctx.fillStyle = '#14213D';
            var porcentaje = Math.round((valores[j] / total) * 100);
            ctx.fillText(recortar(ctx, etiquetas[j], lienzo.ancho - x - 90) + ' (' + porcentaje + '%)', x + 22, y);
        }
    }

    function llenarTabla(idTabla, etiquetas, valores) {
        var cuerpo = document.querySelector('#' + idTabla + ' tbody');
        if (!cuerpo) {
            return;
        }
        while (cuerpo.firstChild) {
            cuerpo.removeChild(cuerpo.firstChild);
        }
        for (var i = 0; i < etiquetas.length; i++) {
            var fila = document.createElement('tr');
            var celdaNombre = document.createElement('td');
            var celdaValor = document.createElement('td');
            celdaNombre.textContent = etiquetas[i];
            celdaValor.textContent = String(valores[i]);
            fila.appendChild(celdaNombre);
            fila.appendChild(celdaValor);
            cuerpo.appendChild(fila);
        }
    }

    function iniciar(idCanvas, idTabla) {
        var canvas = document.getElementById(idCanvas);
        if (!canvas) {
            return;
        }
        obtener(canvas.getAttribute('data-url'))
            .then(function (datos) {
                var etiquetas = Object.keys(datos);
                var valores = etiquetas.map(function (clave) { return Number(datos[clave]) || 0; });
                if (canvas.getAttribute('data-tipo') === 'pastel') {
                    pastel(canvas, etiquetas, valores);
                } else {
                    barras(canvas, etiquetas, valores);
                }
                llenarTabla(idTabla, etiquetas, valores);
            })
            .catch(function () {
                var aviso = document.createElement('p');
                aviso.className = 'aviso error';
                aviso.textContent = 'No se pudieron cargar los datos del gráfico.';
                canvas.parentNode.replaceChild(aviso, canvas);
            });
    }

    iniciar('grafico-categorias', 'tabla-categorias');
    iniciar('grafico-reportes', 'tabla-reportes');
})();
