'use strict';
/**
 * Envío de correo transaccional (verificación, restablecimiento, avisos de seguridad).
 *  - Solo texto plano: sin HTML, sin imágenes remotas, sin nada que inyectar.
 *  - Solo se interpolan valores que genera el servidor (enlaces y un usuario ya validado como ASCII).
 *  - Los enlaces llevan el token en el FRAGMENTO (#): el navegador no lo envía a ningún servidor,
 *    no queda en registros de acceso ni en cabeceras Referer.
 *  - Drivers: console (solo desarrollo), memory (pruebas) y resend (producción, API HTTPS).
 */
const TEXTS = {
  es: {
    verify: ['Confirma tu correo en Tonalli', 'Hola {handle},\n\nConfirma tu correo abriendo este enlace (vence en 24 horas):\n{link}\n\nSi no creaste una cuenta, ignora este mensaje.'],
    reset: ['Restablece tu contraseña de Tonalli', 'Recibimos una solicitud para restablecer tu contraseña. Abre este enlace (vence en 30 minutos):\n{link}\n\nSi no fuiste tú, ignora este mensaje: tu contraseña no cambia hasta que uses el enlace.'],
    exists: ['Intento de registro en Tonalli', 'Alguien intentó crear una cuenta con este correo, que ya está registrado. Si fuiste tú, inicia sesión o restablece tu contraseña. Si no, puedes ignorar este mensaje.'],
    changed: ['Tu contraseña de Tonalli cambió', 'La contraseña de tu cuenta se cambió. Se cerraron todas tus sesiones. Si no fuiste tú, restablece tu contraseña de inmediato desde la app.'],
  },
  en: {
    verify: ['Confirm your email on Tonalli', 'Hi {handle},\n\nConfirm your email by opening this link (expires in 24 hours):\n{link}\n\nIf you did not create an account, ignore this message.'],
    reset: ['Reset your Tonalli password', 'We received a request to reset your password. Open this link (expires in 30 minutes):\n{link}\n\nIf it was not you, ignore this message: your password does not change until the link is used.'],
    exists: ['Sign-up attempt on Tonalli', 'Someone tried to create an account with this email, which is already registered. If it was you, sign in or reset your password. If not, you can ignore this message.'],
    changed: ['Your Tonalli password changed', 'Your account password was changed and all your sessions were closed. If it was not you, reset your password immediately from the app.'],
  },
  pt: {
    verify: ['Confirme seu e-mail no Tonalli', 'Olá {handle},\n\nConfirme seu e-mail abrindo este link (expira em 24 horas):\n{link}\n\nSe você não criou uma conta, ignore esta mensagem.'],
    reset: ['Redefina sua senha do Tonalli', 'Recebemos um pedido para redefinir sua senha. Abra este link (expira em 30 minutos):\n{link}\n\nSe não foi você, ignore esta mensagem: sua senha não muda até o link ser usado.'],
    exists: ['Tentativa de cadastro no Tonalli', 'Alguém tentou criar uma conta com este e-mail, que já está cadastrado. Se foi você, entre ou redefina sua senha. Se não, ignore esta mensagem.'],
    changed: ['Sua senha do Tonalli mudou', 'A senha da sua conta foi alterada e todas as sessões foram encerradas. Se não foi você, redefina sua senha imediatamente pelo app.'],
  },
};

function render(template, lang, params) {
  const set = TEXTS[lang] || TEXTS.es;
  const [subject, body] = set[template];
  const fill = (s) => s.replace(/\{(\w+)\}/g, (_, k) => String(params[k] ?? ''));
  return { subject, text: fill(body) };
}

function createMailer({ driver, from, resendApiKey, baseUrl, logger, fetchImpl = fetch }) {
  const sent = []; // solo el driver "memory" la usa (pruebas)

  async function deliver(to, message) {
    if (driver === 'memory') { sent.push({ to, ...message }); return; }
    if (driver === 'console') {
      logger.info('mail_console', { to_domain: String(to).split('@')[1], subject: message.subject, text: message.text });
      return;
    }
    if (driver === 'resend') {
      const res = await fetchImpl('https://api.resend.com/emails', {
        method: 'POST',
        headers: { Authorization: `Bearer ${resendApiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ from, to: [to], subject: message.subject, text: message.text }),
        signal: AbortSignal.timeout(8000),
      });
      if (!res.ok) throw new Error(`mail_provider_status_${res.status}`);
      return;
    }
    throw new Error('MAIL_DRIVER no configurado');
  }

  /** No lanza: un fallo de correo no debe romper la solicitud ni revelar si la cuenta existe. */
  async function send({ to, template, lang = 'es', handle = '', token = '' }) {
    try {
      const link = token ? `${baseUrl}/#/${template === 'verify' ? 'verify' : 'reset'}/${token}` : '';
      await deliver(to, render(template, lang, { handle, link }));
    } catch (err) {
      logger.error('mail_failed', { template, err });
    }
  }

  return { send, sent };
}

module.exports = { createMailer, render };
