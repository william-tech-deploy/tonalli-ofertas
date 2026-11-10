package com.tonalli.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Formulario de registro. No tiene campo "rol": nadie puede elegirse un rol al registrarse. */
public class RegistroForm {

    @NotBlank(message = "Escribe tu nombre.")
    @Size(max = 60, message = "El nombre admite hasta 60 caracteres.")
    private String nombre;

    @NotBlank(message = "Escribe tu correo.")
    @Email(message = "El correo no es válido.")
    @Size(max = 120, message = "El correo admite hasta 120 caracteres.")
    private String correo;

    @NotBlank(message = "Escribe una contraseña.")
    @Size(min = 10, max = 64, message = "La contraseña debe tener entre 10 y 64 caracteres.")
    private String password;

    @NotBlank(message = "Repite la contraseña.")
    private String confirmar;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmar() { return confirmar; }
    public void setConfirmar(String confirmar) { this.confirmar = confirmar; }
}
