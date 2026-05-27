package ru.kirsachik.uas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.kirsachik.uas.model.ConnectionProtocol;

public record ConnectDroneRequest(
        @NotNull ConnectionProtocol protocol,
        @NotBlank String endpoint,
        String apiKey,
        String externalDeviceId,
        String mqttTopic
) {
}
