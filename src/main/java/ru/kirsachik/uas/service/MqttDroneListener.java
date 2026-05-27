package ru.kirsachik.uas.service;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class MqttDroneListener {

    private final Map<Long, MqttClient> clients = new ConcurrentHashMap<>();

    public void subscribe(Long droneId, String endpoint, String mqttTopic, Consumer<String> onMessage) {
        unsubscribe(droneId);
        try {
            MqttConnectionOptions options = parseEndpoint(endpoint);
            String topic = (mqttTopic != null && !mqttTopic.isBlank()) ? mqttTopic : "uas/telemetry/" + droneId;

            MqttClient client = new MqttClient(options.brokerUri(), "uas-web-" + droneId, new MemoryPersistence());
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setAutomaticReconnect(true);
            connectOptions.setCleanSession(true);
            if (options.username() != null) {
                connectOptions.setUserName(options.username());
            }
            if (options.password() != null) {
                connectOptions.setPassword(options.password().toCharArray());
            }

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) { }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    onMessage.accept(new String(message.getPayload(), StandardCharsets.UTF_8));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) { }
            });

            client.connect(connectOptions);
            client.subscribe(topic);
            clients.put(droneId, client);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось подключиться к MQTT: " + e.getMessage(), e);
        }
    }

    public void unsubscribe(Long droneId) {
        MqttClient client = clients.remove(droneId);
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private MqttConnectionOptions parseEndpoint(String endpoint) {
        String trimmed = endpoint.trim();
        if (!trimmed.startsWith("mqtt://") && !trimmed.startsWith("tcp://")) {
            throw new IllegalArgumentException("MQTT endpoint: mqtt://host:1883 или mqtt://user:pass@host:1883");
        }
        String uri = trimmed.replace("mqtt://", "tcp://");
        String user = null;
        String pass = null;
        if (uri.contains("@")) {
            int schemeEnd = uri.indexOf("://") + 3;
            int at = uri.indexOf('@');
            String creds = uri.substring(schemeEnd, at);
            uri = uri.substring(0, schemeEnd) + uri.substring(at + 1);
            if (creds.contains(":")) {
                String[] parts = creds.split(":", 2);
                user = parts[0];
                pass = parts[1];
            } else {
                user = creds;
            }
        }
        return new MqttConnectionOptions(uri, user, pass);
    }

    private record MqttConnectionOptions(String brokerUri, String username, String password) {
    }
}
