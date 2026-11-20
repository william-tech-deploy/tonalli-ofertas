"""Prueba de punta a punta en un emulador contra un Supabase local.
Maneja la app como una persona (toca y escribe con adb) y comprueba en el
servidor que los datos llegaron. Guarda capturas de cada pantalla en capturas/.
Variables: SB_URL (http://127.0.0.1:54321) y SB_KEY (anon key)."""
import json, os, re, subprocess, sys, time, urllib.request, urllib.error
import xml.etree.ElementTree as ET

PKG = "app.tonalli.nativo"
URL, KEY = os.environ["SB_URL"].rstrip("/"), os.environ["SB_KEY"]
os.makedirs("capturas", exist_ok=True)
fallos = []


def adb(*a, salida=False):
    r = subprocess.run(["adb", *a], capture_output=True, text=not salida, timeout=90)
    return r.stdout


def captura(nombre, espera=2.5):
    time.sleep(espera)
    with open(f"capturas/{nombre}.png", "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", salida=True))


def nodos():
    for _ in range(3):  # el volcado falla si la pantalla todavía se está animando
        adb("shell", "rm", "-f", "/sdcard/ui.xml")
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        try:
            return list(ET.fromstring(xml[xml.index("<"):]).iter("node"))
        except Exception:
            time.sleep(1)
    return []


def centro(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def buscar(id_=None, texto=None, desplazar=True, intentos=6):
    for i in range(intentos):
        for n in nodos():
            if id_ and n.get("resource-id") == f"{PKG}:id/{id_}":
                return n
            if texto and texto in (n.get("text") or ""):
                return n
        if desplazar:
            adb("shell", "input", "swipe", "500", "1500", "500", "700", "300")
        time.sleep(1)
    return None


def tocar(id_=None, texto=None, desplazar=True):
    n = buscar(id_, texto, desplazar)
    if n is None:
        raise RuntimeError(f"no encontré {id_ or texto}")
    x, y = centro(n)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.8)


def escribir(id_, valor):
    tocar(id_)
    adb("shell", "input", "text", valor.replace(" ", "%s"))
    time.sleep(0.4)
    ocultar_teclado()


def ocultar_teclado():
    # Atrás cierra el teclado; solo se pulsa si el teclado está abierto (si no, saldría de la pantalla).
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.8)


def abrir(actividad, *extras):
    # NEW_TASK | CLEAR_TASK: cada pantalla se abre sola, sin las anteriores encima
    adb("shell", "am", "start", "-W", "-f", "0x10008000", "-n", f"{PKG}/.{actividad}", *extras)
    time.sleep(2)


def api(metodo, ruta, cuerpo=None, token=None):
    h = {"apikey": KEY, "Content-Type": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(URL + ruta, data=json.dumps(cuerpo).encode() if cuerpo else None, headers=h, method=metodo)
    try:
        with urllib.request.urlopen(req) as r:
            t = r.read().decode()
            return r.status, (json.loads(t) if t else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def ok(nombre, cond, extra=""):
    print(("OK    " if cond else "FALLA ") + nombre, "" if cond else extra, flush=True)
    if not cond:
        fallos.append(nombre)


def paso(nombre, f):
    try:
        f()
    except Exception as e:
        ok(nombre, False, repr(e))
        captura("error-" + re.sub(r"\W+", "-", nombre), 0.5)


# ---------------------------------------------------------------- preparación
adb("root"); time.sleep(3)
adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
for p in ["POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "CAMERA"]:
    adb("shell", "pm", "grant", PKG, "android.permission." + p)
adb("logcat", "-c")

# Otra persona publica una oferta directo en el servidor (para votarla desde la app).
s, r = api("POST", "/auth/v1/signup", {"email": "otra@example.com", "password": "frase larga de prueba", "data": {"usuario": "otra_persona"}})
tok_otra = r["access_token"]
s, r = api("POST", "/rest/v1/ofertas", {"titulo": "Aceite vegetal 750 ml", "tienda": "Súper Familiar", "categoria": "Supermercado",
                                        "precio_antes": 3.25, "precio_ahora": 2.60, "ean": "7412345000660"}, tok_otra)
ok("oferta de otra persona creada en el servidor", s == 201, (s, r))

# ---------------------------------------------------------------- pantallas
abrir("MainActivity"); captura("01-inicio", 6)
tocar("btnNuevas", desplazar=False); captura("02-nuevas", 1.5)
ok("la lista trae la oferta de Supabase", buscar(texto="Aceite vegetal", desplazar=False) is not None)
tocar("btnCalientes", desplazar=False)

abrir("PerfilActivity"); captura("03-perfil-sin-sesion")


def registrar():
    abrir("LoginActivity", "--ez", "registro", "true"); captura("04-registro", 1)
    escribir("etCorreo", "emu@example.com")
    escribir("etUsuario", "emu_cazador")
    escribir("etClave", "frase larga emulador")
    ocultar_teclado()
    tocar("btnEnviar")
    time.sleep(4)
    s, r = api("GET", "/rest/v1/perfiles?select=usuario&usuario=eq.emu_cazador")
    ok("registro desde la app crea el perfil en Supabase", s == 200 and len(r) == 1, (s, r))


paso("registro", registrar)
abrir("PerfilActivity"); captura("05-perfil", 4)
ok("el perfil muestra el usuario", buscar(texto="emu_cazador", desplazar=False) is not None)


def publicar():
    abrir("PublicarActivity"); captura("06-publicar", 1)
    tocar(texto="Tecnología", desplazar=False)
    escribir("etTitulo", "Cargador USB-C 25 W")
    escribir("etTienda", "TecnoPlaza")
    escribir("etAntes", "18")
    escribir("etAhora", "12.50")
    escribir("etCodigo", "7412345000592")
    ocultar_teclado()
    captura("07-publicar-lleno", 0.5)
    tocar("btnGuardar")
    time.sleep(4)
    s, r = api("GET", "/rest/v1/ofertas?select=titulo,categoria,ean,autor,perfiles!ofertas_autor_fkey(usuario)&titulo=eq.Cargador%20USB-C%2025%20W")
    ok("publicar desde la app guarda en Supabase con su autor", s == 200 and len(r) == 1
       and r[0]["perfiles"]["usuario"] == "emu_cazador" and r[0]["ean"] == "7412345000592", (s, r))


paso("publicar", publicar)


def votar():
    abrir("MainActivity"); time.sleep(4)
    tocar("btnNuevas", desplazar=False)
    tocar(texto="Aceite vegetal", desplazar=False)
    captura("08-detalle", 1.5)
    tocar("btnSubir")
    time.sleep(3)
    s, r = api("GET", "/rest/v1/ofertas?select=votos_up&titulo=eq.Aceite%20vegetal%20750%20ml")
    ok("votar desde la app suma el voto en Supabase", s == 200 and r[0]["votos_up"] == 1, (s, r))
    captura("09-detalle-votado", 0.5)


paso("votar", votar)


def escaner():
    abrir("EscanerActivity"); captura("10-escaner", 1)
    escribir("etBuscar", "7412345000660")
    ocultar_teclado()
    tocar("btnBuscar", desplazar=False)
    time.sleep(2)
    ok("el escáner encuentra por código", buscar(texto="Aceite vegetal", desplazar=False) is not None)
    captura("11-escaner-resultado", 0.5)


paso("escaner", escaner)
abrir("PerfilActivity"); captura("12-perfil-con-karma", 4)
abrir("AlertasActivity"); captura("13-alertas", 1)

adb("logcat", "-d", "-f", "/sdcard/log.txt")
log = adb("shell", "cat", "/sdcard/log.txt")
cierres = "FATAL EXCEPTION" in log
ok("sin cierres inesperados", not cierres, log[log.find("FATAL EXCEPTION"):][:3000] if cierres else "")
print("\n%d fallos" % len(fallos))
sys.exit(1 if fallos else 0)
