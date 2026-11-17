package com.tonalli.web.dto;

/** Cifras del panel de inicio del administrador. Solo conteos: ningún dato personal. */
public class ResumenAdmin {

    private final long usuarios;
    private final long moderadores;
    private final long administradores;
    private final long ofertasActivas;
    private final long ofertasOcultas;
    private final long reportesAbiertos;
    private final long propuestasPendientes;

    public ResumenAdmin(long usuarios, long moderadores, long administradores, long ofertasActivas,
                        long ofertasOcultas, long reportesAbiertos, long propuestasPendientes) {
        this.usuarios = usuarios;
        this.moderadores = moderadores;
        this.administradores = administradores;
        this.ofertasActivas = ofertasActivas;
        this.ofertasOcultas = ofertasOcultas;
        this.reportesAbiertos = reportesAbiertos;
        this.propuestasPendientes = propuestasPendientes;
    }

    public long getUsuarios() { return usuarios; }
    public long getModeradores() { return moderadores; }
    public long getAdministradores() { return administradores; }
    public long getOfertasActivas() { return ofertasActivas; }
    public long getOfertasOcultas() { return ofertasOcultas; }
    public long getReportesAbiertos() { return reportesAbiertos; }
    public long getPropuestasPendientes() { return propuestasPendientes; }
}
