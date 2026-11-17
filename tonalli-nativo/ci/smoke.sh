#!/usr/bin/env bash
# Prueba de humo en un emulador: instala la app, abre cada pantalla, toma capturas
# y falla si Android registra un cierre inesperado (FATAL EXCEPTION).
set -u
PKG=app.tonalli.nativo
mkdir -p capturas
adb root >/dev/null 2>&1; sleep 3
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION || true
adb shell pm grant $PKG android.permission.CAMERA || true
adb logcat -c
captura() { sleep "${2:-5}"; adb exec-out screencap -p > "capturas/$1.png"; }
adb shell am start -W -n $PKG/.MainActivity;                         captura 1-lista 8
adb shell input keyevent 82;                                         captura 2-menu 3
adb shell input keyevent 4
adb shell am start -W -n $PKG/.DetalleActivity --el oferta_id 1;     captura 3-detalle
adb shell am start -W -n $PKG/.PublicarActivity;                     captura 4-publicar
adb shell am start -W -n $PKG/.AlertasActivity;                      captura 5-alertas
adb logcat -d > capturas/logcat.txt
if grep -q "FATAL EXCEPTION" capturas/logcat.txt; then
  echo "LA APP SE CERRÓ:"; grep -A 25 "FATAL EXCEPTION" capturas/logcat.txt | head -80; exit 1
fi
echo "Sin cierres inesperados en las 4 pantallas."
