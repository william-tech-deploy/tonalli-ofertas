"""Prueba de seguridad del esquema contra un Supabase local (supabase start).
Hace las mismas llamadas REST que la app (Supabase.java). Uso:
  SB_URL=http://127.0.0.1:54321 SB_KEY=<anon key> python3 prueba_api.py
"""
import json, os, sys, urllib.request, urllib.error

URL = os.environ["SB_URL"].rstrip("/")
KEY = os.environ["SB_KEY"]
fallos = []


def llamar(metodo, ruta, cuerpo=None, token=None, prefer=None):
    h = {"apikey": KEY, "Content-Type": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    if prefer:
        h["Prefer"] = prefer
    data = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(URL + ruta, data=data, headers=h, method=metodo)
    try:
        with urllib.request.urlopen(req) as r:
            t = r.read().decode()
            return r.status, (json.loads(t) if t else None)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        try:
            return e.code, json.loads(t)
        except ValueError:
            return e.code, t


def ok(nombre, cond, extra=""):
    print(("OK    " if cond else "FALLA ") + nombre, extra if not cond else "")
    if not cond:
        fallos.append(nombre)


def cuenta(email, usuario):
    s, r = llamar("POST", "/auth/v1/signup",
                  {"email": email, "password": "una frase larga 2026", "data": {"usuario": usuario}})
    assert s == 200 and r.get("access_token"), (s, r)
    return r["access_token"], r["user"]["id"]


ta, ida = cuenta("ana@example.com", "ana_test")
tb, idb = cuenta("beto@example.com", "ana_test")  # mismo usuario a propósito

s, r = llamar("GET", "/rest/v1/perfiles?select=id,usuario,karma&id=eq." + ida)
ok("registro crea el perfil con el usuario pedido", s == 200 and r[0]["usuario"] == "ana_test", r)
s, r = llamar("GET", "/rest/v1/perfiles?select=usuario&id=eq." + idb)
ok("usuario repetido recibe sufijo, no rompe el registro", s == 200 and r[0]["usuario"].startswith("ana_test_"), r)

nueva = {"titulo": "Arroz blanco 5 lb", "tienda": "Súper Familiar", "categoria": "Supermercado",
         "precio_antes": 3.10, "precio_ahora": 2.45, "ean": "7412345000288", "lat": 13.99, "lng": -89.55}
s, r = llamar("POST", "/rest/v1/ofertas", nueva, prefer="return=representation")
ok("sin sesión no se puede publicar", s in (401, 403), (s, r))

s, r = llamar("POST", "/rest/v1/ofertas", dict(nueva, votos_up=999), ta, "return=representation")
ok("no se pueden falsificar los votos al publicar", s in (401, 403), (s, r))
s, r = llamar("POST", "/rest/v1/ofertas", dict(nueva, autor=idb), ta, "return=representation")
ok("no se puede publicar a nombre de otra cuenta", s in (401, 403), (s, r))

s, r = llamar("POST", "/rest/v1/ofertas", nueva, ta, "return=representation")
ok("con sesión se publica", s == 201 and r[0]["autor"] == ida and r[0]["votos_up"] == 0, (s, r))
oid = r[0]["id"]

s, r = llamar("POST", "/rest/v1/ofertas", dict(nueva, precio_ahora=5), ta)
ok("rechaza precio de oferta mayor que el anterior", s == 400, (s, r))
s, r = llamar("POST", "/rest/v1/ofertas", dict(nueva, ean="123"), ta)
ok("rechaza código de barras inválido", s == 400, (s, r))

s, r = llamar("GET", "/rest/v1/ofertas?select=id,titulo&order=creada.desc&limit=200")
ok("cualquiera puede ver las ofertas", s == 200 and any(o["id"] == oid for o in r), (s, r))
s, r = llamar("GET", "/rest/v1/ofertas?select=id&ean=eq.7412345000288")
ok("búsqueda por código de barras", s == 200 and len(r) == 1, (s, r))

s, r = llamar("POST", "/rest/v1/votos", {"oferta": oid, "valor": 1}, ta)
ok("no se puede votar la propia oferta", s in (401, 403), (s, r))

s, r = llamar("POST", "/rest/v1/votos", {"oferta": oid, "valor": 1}, tb)
ok("otra cuenta vota", s == 201, (s, r))
s, r = llamar("POST", "/rest/v1/votos", {"oferta": oid, "valor": 1}, tb)
ok("no se puede votar dos veces", s == 409, (s, r))
s, r = llamar("GET", "/rest/v1/ofertas?select=votos_up,votos_down&id=eq.%d" % oid)
ok("el servidor cuenta el voto", r[0] == {"votos_up": 1, "votos_down": 0}, r)
s, r = llamar("GET", "/rest/v1/perfiles?select=karma&id=eq." + ida)
ok("karma = 10 por publicar + 1 por voto", r[0]["karma"] == 11, r)

# cambiar el voto: la app borra el anterior y crea el nuevo
s, _ = llamar("DELETE", "/rest/v1/votos?oferta=eq.%d&usuario=eq.%s" % (oid, idb), token=tb)
s2, _ = llamar("POST", "/rest/v1/votos", {"oferta": oid, "valor": -1}, tb)
s, r = llamar("GET", "/rest/v1/ofertas?select=votos_up,votos_down&id=eq.%d" % oid)
ok("cambiar el voto recuenta", s2 == 201 and r[0] == {"votos_up": 0, "votos_down": 1}, r)
s, r = llamar("GET", "/rest/v1/perfiles?select=karma&id=eq." + ida)
ok("karma baja con un voto en contra", r[0]["karma"] == 9, r)

s, r = llamar("GET", "/rest/v1/votos?select=oferta,valor", token=ta)
ok("nadie ve los votos de otros", s == 200 and r == [], r)
s, r = llamar("GET", "/rest/v1/votos?select=oferta,valor", token=tb)
ok("cada quien ve sus votos", s == 200 and r == [{"oferta": oid, "valor": -1}], r)

s, r = llamar("PATCH", "/rest/v1/perfiles?id=eq." + ida, {"karma": 99999}, ta)
ok("no se puede subir el karma a mano", s in (401, 403), (s, r))
s, r = llamar("PATCH", "/rest/v1/perfiles?id=eq." + ida, {"usuario": "ana_nueva"}, tb, "return=representation")
ok("no se puede cambiar el perfil de otro", s == 200 and r == [], (s, r))
s, r = llamar("PATCH", "/rest/v1/perfiles?id=eq." + ida, {"usuario": "ana_nueva"}, ta, "return=representation")
ok("cada quien cambia su nombre", s == 200 and r[0]["usuario"] == "ana_nueva", (s, r))

s, r = llamar("DELETE", "/rest/v1/ofertas?id=eq.%d" % oid, token=tb, prefer="return=representation")
ok("no se puede borrar la oferta de otro", s == 200 and r == [], (s, r))
s, r = llamar("POST", "/rest/v1/rpc/recalcular_karma", {"p": ida}, ta)
ok("las funciones internas no están expuestas", s == 404, (s, r))

for i in range(19):
    llamar("POST", "/rest/v1/ofertas", dict(nueva, titulo="Oferta %d" % i, ean=None), ta)
s, r = llamar("POST", "/rest/v1/ofertas", dict(nueva, titulo="La 21", ean=None), ta)
ok("límite de 20 ofertas por día", s == 400 and "limite_diario" in json.dumps(r), (s, r))

s, r = llamar("DELETE", "/rest/v1/ofertas?id=eq.%d" % oid, token=ta, prefer="return=representation")
ok("el autor borra su oferta", s == 200 and len(r) == 1, (s, r))
s, r = llamar("GET", "/rest/v1/votos?select=oferta", token=tb)
ok("al borrar la oferta se borran sus votos", r == [], r)
s, r = llamar("GET", "/rest/v1/perfiles?select=karma&id=eq." + ida)
ok("karma recalculado tras borrar (19 ofertas)", r[0]["karma"] == 190, r)

s, r = llamar("POST", "/auth/v1/token?grant_type=password", {"email": "ana@example.com", "password": "mala"})
ok("contraseña incorrecta es rechazada", s == 400, (s, r))
s, r = llamar("POST", "/auth/v1/token?grant_type=password", {"email": "ana@example.com", "password": "una frase larga 2026"})
ok("inicio de sesión", s == 200 and r.get("refresh_token"), (s, r))
s, r2 = llamar("POST", "/auth/v1/token?grant_type=refresh_token", {"refresh_token": r["refresh_token"]})
ok("renovar la sesión", s == 200 and r2.get("access_token"), (s, r2))

print("\n%d fallos" % len(fallos))
sys.exit(1 if fallos else 0)
