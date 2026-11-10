# Tonalli — Manual de marca

Tonalli es una app móvil de ahorro colaborativo donde la comunidad vota qué tan "caliente" (real) es cada oferta. Toda pieza de la marca debe sentirse cercana, clara y honesta: nunca estridente, nunca milagrosa.

## Esencia

- El nombre viene de *tonalli*, palabra náhuatl asociada al calor del sol y a la fuerza vital. Úsalo como idea de **temperatura**: una oferta se mide, no se promete.
- El símbolo es una **T-termómetro**: el travesaño es la T y la etiqueta de precio; el tubo y el bulbo son el medidor del Consenso de Temperatura.
- Lema: **Ofertas con temperatura real.** (EN: *Real deals, real temperature.* PT: *Ofertas com temperatura real.*). El lema en Náhuat lo redacta y valida un hablante; no lo traduzcas con herramientas automáticas.

## Voz y contenido

- Escribe en tú neutro; la variante es-SV puede usar voseo ("publicá", "mirá") solo si el equipo lo aprueba para toda la app.
- Frases cortas, verbos de acción, cifras concretas. Habla de precios y votos, no de "ofertas imperdibles".
- Usa los nombres del producto siempre igual: **Consenso de Temperatura**, **Rastreador** (quien consulta), **Cazador** (quien publica), **brasas** (la reputación o karma), niveles **Chispa, Brasa, Fogata, Hoguera**.
- Ejemplos de texto real:
  - "Esta oferta está caliente: 86 % de la comunidad la confirmó."
  - "Aún tibia. Faltan votos para confirmar el precio."
  - "Fría: la comunidad no pudo verificar este descuento."
  - "Gracias, Cazador. Tu aviso suma 5 brasas."
- Escribe "Tonalli" con T mayúscula inicial en texto corrido. No uses emojis como adorno, ni signos de exclamación dobles, ni mayúsculas sostenidas.
- Nunca afirmes un ahorro que la comunidad no haya confirmado; muestra siempre el estado de temperatura junto al descuento.

## Logo

- Usa `tonalli-horizontal-color.svg` sobre `background` claro y `tonalli-horizontal-sobre-oscuro.svg` sobre `brand-tinta` o fondos oscuros.
- Usa el símbolo solo (`tonalli-simbolo-*.svg`) en espacios cuadrados: avatar, icono de notificación, favicon. Tamaño mínimo 24 px de ancho.
- Deja alrededor del logo un margen libre igual al ancho del bulbo (aprox. la altura de la "o" del nombre). No lo estires, no cambies sus colores, no le añadas sombras ni contornos.
- En una sola tinta usa `tonalli-simbolo-mono-tinta.svg` (tinta `#14213D`) o `tonalli-simbolo-mono-blanco.svg` (blanco) sobre fotografías o sellos.
- El icono de app es `tonalli-icono-app.svg` (1024 px, fondo `brand-tinta`). Para Android usa el par adaptativo `android-adaptive-primer-plano.svg` y `android-adaptive-fondo.svg`.

## Color

- Fondo de pantalla `background`; tarjetas `surface`; texto `ink`, secundario `ink-muted`.
- El único color de acción es `primary` (botón principal, enlaces, iconos activos). `brand-brasa` es el naranja del logo y de ilustraciones; no lo uses como fondo de botón ni de texto, porque no alcanza 4.5:1 con texto blanco.
- `secondary` (tinta) es el botón secundario sólido. `tertiary` se reserva para información y filtros activos.
- El naranja y el rojo conviven: `primary` es naranja quemado y `error` es rojo profundo. Un error siempre lleva icono y mensaje en texto; el color nunca es la única señal.
- Los tres estados de temperatura usan sus pares `hot-bg`/`hot-fg`, `warm-bg`/`warm-fg`, `cold-bg`/`cold-fg`, y cada uno lleva palabra e icono (llama, termómetro, copo). Jamás indiques el estado solo con color.
- Los tokens `brand-*` son para gráficos, bloques e ilustración; ninguno lleva texto encima.
- Todo texto cumple 4.5:1 y todo borde, foco o icono con significado 3:1, en tema claro y oscuro. El informe de contraste completo viene en el kit descargable de la marca.

## Tipografía

- Titulares, precios y marca en `display` (Bricolage Grotesque, 700 u 800). Todo lo demás en `body` (Plus Jakarta Sans).
- Estilos: `display` 32/40, `headline` 24/32, `price-xl` 28/32, `title` 18/24, `body` 16/24, `body-small` 14/20, `label` 12/16.
- Muestra el precio siempre en `price-xl`; el precio anterior va tachado en `body-small` y `ink-muted`.
- Nada por debajo de 12 px; el texto debe escalar con el tamaño de fuente del sistema sin recortarse. Una etiqueta compacta se mantiene en una línea y, si hay que acortarla, el texto completo está disponible por teclado y toque.

## Espaciado y forma

- Usa la escala `space-1` a `space-8` (4, 8, 12, 16, 24, 32, 48, 64). Márgenes laterales de pantalla `space-4`; padding de tarjeta `space-4`; hueco mínimo entre controles táctiles `space-2`.
- Todo control táctil mide al menos 48×48 dp; si el icono es más pequeño, amplía el área de toque.
- Esquinas: botones y etiquetas `radius-pill`; campos `radius-field`; tarjetas `radius-card`; hojas y diálogos `radius-sheet`.
- Elevación mínima con `shadow-card`; la jerarquía se logra con `surface` sobre `background`, no con sombras profundas.

## Iconografía

- Iconos propios de trazo (2 px, extremos y uniones redondeados, cuadrícula de 24 px) en un solo estilo de contorno: llama, termómetro, copo, flecha arriba y abajo. Si falta uno, dibújalo igual o usa Phosphor en peso Regular.
- Los iconos decorativos junto a texto visible llevan `aria-hidden`; los que están solos llevan nombre accesible.
- No uses emojis como iconos estructurales.

## Movimiento

- Duraciones `120 ms` (toque), `200 ms` (cambio de estado), `320 ms` (hojas y transiciones de pantalla), con curva suave de salida.
- El voto anima el termómetro subiendo o bajando; si el sistema reduce las animaciones, muestra el estado final sin movimiento.
- Las interacciones rápidas pueden cancelar la animación, pero el estado, el foco y el contenido finales deben ser correctos.

## Estados de temperatura

- **Caliente**: la comunidad confirmó el precio. **Tibia**: aún en verificación. **Fría**: no verificada o rechazada.
- Los umbrales de votos para cada estado los define el producto; el diseño exige solo que existan los tres estados, con palabra, icono y color.


# Guía: por qué, estándares, cómo aplicar y publicar

## Por qué esta identidad

**Concepto.** La app resuelve incertidumbre sobre precios engañosos. La marca convierte esa incertidumbre en una metáfora que cualquiera entiende: la **temperatura**. El Consenso de Temperatura (votos +1 / -1) ya es un termómetro; el logo, el nombre y los estados de color lo hacen visible.

**Nombre.** *Tonalli* (náhuatl: calor del sol, fuerza vital) es corto, se pronuncia igual en español, inglés y portugués, y conecta con el idioma Náhuat que la app ya incluye. Es una propuesta de trabajo: antes de publicarla verifica disponibilidad del nombre y del dominio y registra la marca en tu país (en El Salvador, el Registro de la Propiedad Intelectual del CNR). Existe una marca de fitness llamada *Tonal*; no es el mismo nombre ni rubro, pero conviene pedir asesoría legal. Alternativas si el nombre no está libre: *Tunal* (forma pipil de sol y día, con el riesgo de confundirse con la planta de tuna) o *Chispa* (muy usado).

**Símbolo.** Una T construida como termómetro: travesaño (etiqueta de precio), tubo con degradado ámbar a naranja (el calor sube) y bulbo naranja. Es una forma simple, legible a 24 px, que no depende de texto y que funciona en una sola tinta.

**Paleta.**
- *Tinta* `#14213D` transmite confianza y sobriedad en un rubro lleno de rojo y amarillo chillón.
- *Brasa* `#E8501F` y *Sol* `#F5A524` aportan el calor del nombre; *Frío* `#1AA6B7` completa la escala de temperatura.
- *Papel* `#FFF8EF` como fondo cálido evita el blanco clínico.
- Cada color de texto o botón se oscurece hasta cumplir contraste (por eso `primary` es `#C73E14` y no el naranja del logo).

**Tipografía.** Bricolage Grotesque aporta personalidad en titulares y precios; Plus Jakarta Sans es muy legible en tamaños pequeños y cubre los caracteres del español, inglés y portugués. Ambas son Google Fonts con licencia abierta (OFL).

**Escalera de reputación.** Chispa, Brasa, Fogata y Hoguera dan nombre a los niveles del sistema de karma y encajan con el módulo de gobernanza (los niveles altos pueden proponer categorías). Los umbrales numéricos son decisión del producto.

## Estándares y rúbrica que se siguió

| Área | Estándar | Cómo se cumple |
|---|---|---|
| Contraste | WCAG 2.2 criterio 1.4.3 (texto ≥ 4.5:1) y 1.4.11 (no texto ≥ 3:1) | 34 pares verificados por cálculo en tema claro y oscuro; todos cumplen |
| Color no único | WCAG 1.4.1 | Cada estado de temperatura lleva palabra e icono además de color |
| Tamaño de toque | Material Design 48 dp y Apple HIG 44 pt | Todo control mide 48×48 dp, con 8 dp de separación |
| Texto | Tamaños en sp, mínimo 12 | Escala de 7 estilos; escalan con el sistema |
| Icono Android | Iconos adaptativos: lienzo 108 dp, zona segura 66 dp | El primer plano está dentro de la zona segura |
| Tokens | Material 3 (ColorScheme) | `Color.kt` mapea los tokens a `lightColorScheme` y `darkColorScheme` |
| Movimiento | Respeto de la escala de animación del sistema | Estado final sin animación cuando se reduce el movimiento |

## Cómo aplicarlo en Android Studio

1. Copia `Color.kt` al paquete de tema (`ui/theme`) y ajusta el `package`.
2. En `Theme.kt`, usa `TonalliLightColors` y `TonalliDarkColors` en `MaterialTheme(colorScheme = …)`. Si trabajas con vistas XML, copia `colors.xml` a `values/` y `colors-night.xml` a `values-night/colors.xml`.
3. Fuentes: agrega Bricolage Grotesque y Plus Jakarta Sans con *Downloadable Fonts* (Resource Manager → Font → Google Fonts) y define los estilos de la escala tipográfica.
4. Icono: *New → Image Asset → Launcher Icons (Adaptive and Legacy)*, capa de primer plano con `android-adaptive-primer-plano.svg` y capa de fondo con `android-adaptive-fondo.svg`.
5. Usa siempre los colores por su nombre semántico (`primary`, `surface`…) y nunca valores hexadecimales sueltos en las pantallas.

## Cómo publicarlo

- **Google Play:** icono de 512×512 PNG (`tonalli-icono-play-store-512.png`) y gráfico de funciones de 1024×500 (pendiente de diseñar con capturas reales de la app).
- **Repositorio:** guarda `tokens/` y `logos/` en la carpeta `design/` del proyecto para que el equipo use la misma fuente.
- **Sitio web (segundo avance):** importa `tonalli-tokens.css` y usa el logo horizontal.

## Decisiones que quedan abiertas

- Confirmar el nombre tras la búsqueda de disponibilidad y registro.
- Textos en Náhuat (lema y etiquetas de temperatura) con un hablante.
- Umbrales de votos para Caliente, Tibia y Fría y niveles de brasas.
- Diseñar las 10 pantallas aplicando este sistema.
