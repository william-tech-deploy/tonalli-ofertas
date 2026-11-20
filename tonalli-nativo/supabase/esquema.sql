-- =====================================================================
-- Tonalli · esquema de base de datos para Supabase (PostgreSQL)
-- Cómo usarlo: Supabase > SQL Editor > New query > pegar todo > Run.
-- Se puede ejecutar más de una vez: primero borra lo que haya creado antes.
--
-- Regla de seguridad principal: la app lleva la clave pública (anon /
-- publishable), así que CUALQUIERA puede llamar a la API. Por eso cada
-- tabla tiene Row Level Security (RLS) y permisos por columna: el
-- servidor decide quién puede leer o escribir cada fila, no la app.
-- Rúbrica: OWASP API Security Top 10 2023 (API1 control por objeto,
-- API3 por propiedad, API4 consumo de recursos) y OWASP MASVS.
-- =====================================================================

-- ---------- limpieza (para poder volver a ejecutar el script) ----------
drop trigger if exists al_registrar on auth.users;
drop table if exists public.votos cascade;
drop table if exists public.ofertas cascade;
drop table if exists public.perfiles cascade;
drop schema if exists privado cascade;

-- Las funciones internas viven en un esquema que la API NO publica,
-- así nadie puede llamarlas desde la app (solo los triggers).
create schema privado;
revoke all on schema privado from public, anon, authenticated;

-- ---------------------------------------------------------------- tablas

-- Perfil público de cada cuenta. El correo y la contraseña los guarda
-- Supabase Auth (auth.users), nunca esta tabla.
create table public.perfiles (
  id      uuid primary key references auth.users (id) on delete cascade,
  usuario text not null unique check (usuario ~ '^[A-Za-z0-9_.]{3,20}$'),
  karma   integer not null default 0,
  creado  timestamptz not null default now()
);

create table public.ofertas (
  id           bigint generated always as identity primary key,
  autor        uuid not null default auth.uid() references public.perfiles (id) on delete cascade,
  titulo       text not null check (char_length(titulo) between 3 and 80),
  tienda       text not null check (char_length(tienda) between 2 and 50),
  categoria    text not null check (categoria in ('Supermercado', 'Tecnología', 'Ropa')),
  precio_antes numeric(10, 2) not null check (precio_antes > 0 and precio_antes <= 100000),
  precio_ahora numeric(10, 2) not null check (precio_ahora > 0),
  ean          text check (ean ~ '^[0-9]{8}$' or ean ~ '^[0-9]{12,14}$'),
  lat          double precision not null default 0 check (lat between -90 and 90),
  lng          double precision not null default 0 check (lng between -180 and 180),
  votos_up     integer not null default 0,
  votos_down   integer not null default 0,
  creada       timestamptz not null default now(),
  check (precio_ahora < precio_antes)
);
create index ofertas_creada_idx on public.ofertas (creada desc);
create index ofertas_ean_idx    on public.ofertas (ean);
create index ofertas_autor_idx  on public.ofertas (autor);

-- Un voto por persona y oferta (la llave primaria lo garantiza).
create table public.votos (
  oferta  bigint not null references public.ofertas (id) on delete cascade,
  usuario uuid not null default auth.uid() references public.perfiles (id) on delete cascade,
  valor   smallint not null check (valor in (-1, 1)),
  creado  timestamptz not null default now(),
  primary key (oferta, usuario)
);
create index votos_usuario_idx on public.votos (usuario);

-- ------------------------------------------------------ funciones internas

-- Al registrarse, Supabase crea la cuenta en auth.users y este trigger
-- crea su perfil con el nombre de usuario que mandó la app.
create function privado.crear_perfil() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  u text := new.raw_user_meta_data ->> 'usuario';
begin
  if u is null or u !~ '^[A-Za-z0-9_.]{3,20}$' then
    u := 'cazador_' || substr(replace(new.id::text, '-', ''), 1, 8);
  end if;
  -- Si el nombre ya existe, se le agrega un sufijo en vez de fallar.
  while exists (select 1 from public.perfiles where usuario = u) loop
    u := left(u, 14) || '_' || substr(md5(random()::text), 1, 5);
  end loop;
  insert into public.perfiles (id, usuario) values (new.id, u);
  return new;
end $$;

create trigger al_registrar
  after insert on auth.users
  for each row execute function privado.crear_perfil();

-- Karma: 10 puntos por oferta publicada + votos a favor - votos en contra.
-- Solo el servidor lo calcula; la app no puede escribirlo.
create function privado.recalcular_karma(p uuid) returns void
language sql security definer set search_path = '' as $$
  update public.perfiles
     set karma = greatest(0, (
           select 10 * count(*) + coalesce(sum(votos_up - votos_down), 0)
             from public.ofertas where autor = p))
   where id = p;
$$;

-- Antes de guardar una oferta: el autor, los votos y la fecha los pone el
-- servidor (la app no puede falsificarlos) y se limita a 20 ofertas por día.
create function privado.antes_de_oferta() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  new.autor      := auth.uid();
  new.votos_up   := 0;
  new.votos_down := 0;
  new.creada     := now();
  if (select count(*) from public.ofertas
       where autor = new.autor and creada > now() - interval '1 day') >= 20 then
    raise exception 'limite_diario' using errcode = 'P0001',
      hint = 'Máximo 20 ofertas por día';
  end if;
  return new;
end $$;

create trigger antes_de_oferta
  before insert on public.ofertas
  for each row execute function privado.antes_de_oferta();

create function privado.tras_oferta() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform privado.recalcular_karma(coalesce(new.autor, old.autor));
  return null;
end $$;

create trigger tras_oferta
  after insert or delete on public.ofertas
  for each row execute function privado.tras_oferta();

-- Cada vez que alguien vota o quita su voto, se recuentan los votos de esa
-- oferta y el karma de su autor.
create function privado.tras_voto() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  o bigint := coalesce(new.oferta, old.oferta);
  a uuid;
begin
  update public.ofertas
     set votos_up   = (select count(*) from public.votos where oferta = o and valor = 1),
         votos_down = (select count(*) from public.votos where oferta = o and valor = -1)
   where id = o
   returning autor into a;
  if a is not null then
    perform privado.recalcular_karma(a);
  end if;
  return null;
end $$;

create trigger tras_voto
  after insert or delete on public.votos
  for each row execute function privado.tras_voto();

revoke all on all functions in schema privado from public, anon, authenticated;

-- ------------------------------------------- Row Level Security (RLS)

alter table public.perfiles enable row level security;
alter table public.ofertas  enable row level security;
alter table public.votos    enable row level security;

-- perfiles: todos ven nombre y karma; solo el dueño cambia su nombre.
create policy perfiles_ver on public.perfiles
  for select to anon, authenticated using (true);
create policy perfiles_editar on public.perfiles
  for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- ofertas: todos las ven; solo una cuenta publica (como ella misma) y
-- solo el autor borra las suyas. Nadie las edita después de publicar.
create policy ofertas_ver on public.ofertas
  for select to anon, authenticated using (true);
create policy ofertas_crear on public.ofertas
  for insert to authenticated with check (autor = (select auth.uid()));
create policy ofertas_borrar on public.ofertas
  for delete to authenticated using (autor = (select auth.uid()));

-- votos: cada quien ve y borra solo los suyos, y no puede votar su oferta.
create policy votos_ver on public.votos
  for select to authenticated using (usuario = (select auth.uid()));
create policy votos_crear on public.votos
  for insert to authenticated with check (
    usuario = (select auth.uid())
    and exists (select 1 from public.ofertas o
                 where o.id = oferta and o.autor <> (select auth.uid())));
create policy votos_borrar on public.votos
  for delete to authenticated using (usuario = (select auth.uid()));

-- ------------------------------------- permisos por columna (API3)
-- RLS dice QUÉ FILAS; estos permisos dicen QUÉ COLUMNAS se pueden escribir.
revoke all on public.perfiles, public.ofertas, public.votos from anon, authenticated;
grant usage on schema public to anon, authenticated;

grant select on public.perfiles to anon, authenticated;
grant update (usuario) on public.perfiles to authenticated;      -- el karma no

grant select on public.ofertas to anon, authenticated;
grant insert (titulo, tienda, categoria, precio_antes, precio_ahora, ean, lat, lng)
  on public.ofertas to authenticated;                              -- votos, autor y fecha no
grant delete on public.ofertas to authenticated;

grant select, delete on public.votos to authenticated;
grant insert (oferta, valor) on public.votos to authenticated;
