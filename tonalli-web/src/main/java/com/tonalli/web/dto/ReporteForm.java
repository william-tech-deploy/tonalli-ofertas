package com.tonalli.web.dto;

import com.tonalli.web.model.MotivoReporte;
import jakarta.validation.constraints.NotNull;

public class ReporteForm {

    @NotNull(message = "Elige un motivo.")
    private MotivoReporte motivo;

    public MotivoReporte getMotivo() { return motivo; }
    public void setMotivo(MotivoReporte motivo) { this.motivo = motivo; }
}
