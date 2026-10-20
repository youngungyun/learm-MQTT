package com.example.smartfarm;

import com.example.smartfarm.mqtt.MqttGateway;
import com.example.smartfarm.telemetry.TelemetryRepository;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "mqtt.client-id=smartfarm-test-server",
        "mqtt.clean-start=true",
        "mqtt.session-expiry-seconds=0",
        "smartfarm.simulator.enabled=false"
})
@Testcontainers
public class MqttIntegrationTest {

    @Container
    static final GenericContainer<?> MOSQUITTO =
            new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2"))
                    .withExposedPorts(1883)
                    .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf");

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("mqtt.broker-url", MqttIntegrationTest::brokerUrl);
    }

    static String brokerUrl() {
        return "tcp://%s:%d".formatted(MOSQUITTO.getHost(), MOSQUITTO.getMappedPort(1883));
    }

    @Autowired
    MqttGateway gateway;

    @Autowired
    TelemetryRepository repository;

    @Test
    void 서버가_접속하면_online_상태가_retained로_남는다() throws Exception {
        var client = newTestClient();
        try {
            var received = subscribeInto(client, "farm/server/status");

            // 구독 시점에 이미 저장돼 있는 retained 메시지를 받아야 한다.
            assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("online");
        } finally {
            close(client);
        }
    }

    @Test
    void 센서_메시지를_수신하면_저장된다() throws Exception {
        waitUntilServerOnline();

        gateway.publish("farm/gh-test1/temperature/telemetry", 1, false, """
                {"deviceId":"t1-temp-01","value":24.5,"unit":"C","timestamp":"2026-10-20T04:00:00Z"}
                """);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findRecent("gh-test1", "temperature", 10))
                        .singleElement()
                        .satisfies(r -> {
                            assertThat(r.reading().deviceId()).isEqualTo("t1-temp-01");
                            assertThat(r.reading().value()).isEqualTo(24.5);
                        }));
    }

    @Test
    void 잘못된_JSON은_무시되고_서버는_계속_동작한다() throws Exception {
        waitUntilServerOnline();

        gateway.publish("farm/gh-test2/temperature/telemetry", 1, false, "not-json");
        gateway.publish("farm/gh-test2/temperature/telemetry", 1, false, """
                {"deviceId":"t2-temp-01","value":22.0,"unit":"C","timestamp":"2026-10-20T04:00:00Z"}
                """);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findRecent("gh-test2", "temperature", 10)).hasSize(1));
    }

    @Test
void 임게값을_넘으면_경보가_발행된다() throws Exception {
        waitUntilServerOnline();

        var client = newTestClient();
        try {
            var alerts = subscribeInto(client, "farm/gh-test3/alerts");

            gateway.publish("farm/gh-test3/temperature/telemetry", 1, false, """
                    {"deviceId":"t3-temp-01","value":35.0,"unit":"C","timestamp":"2026-10-20T04:00:00Z"}
                    """);

            var alert = alerts.poll(10, TimeUnit.SECONDS);
            assertThat(alert).isNotNull();
            assertThat(alert).contains("\"greenhouse\":\"gh-test3\"", "\"sensorType\":\"temperature\"");
        } finally {
            close(client);
        }
    }

    // ----------- 헬퍼 -------------

    /**
     * 서버가 구독까지 끝냈다는 신호(online)를 기다린다. 구독 전에 발행하면 메시지가 유실될 수 있다.
     */
    private void waitUntilServerOnline() throws Exception {
        var client = newTestClient();
        try {
            var received = subscribeInto(client, "farm/server/status");
            assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("online");
        } finally {
            close(client);
        }
    }

    /**
     * Paho 1.2.5의 subscribe(String, int, IMqttMessageListener)는 무한 재귀(StackOverflowError)하므로,
     * 콜백을 직접 등록하고 리스너 없는 subscribe(String, int)를 쓴다.
     */
    private BlockingQueue<String> subscribeInto(MqttClient client, String topicFilter) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        client.setCallback(new MqttCallback() {
            @Override
            public void messageArrived(String topic, MqttMessage message) {
                received.add(new String(message.getPayload(), StandardCharsets.UTF_8));
            }

            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
            }

            @Override
            public void mqttErrorOccurred(MqttException exception) {
            }

            @Override
            public void deliveryComplete(IMqttToken token) {
            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
            }
        });
        client.subscribe(topicFilter, 1);
        return received;
    }

    private MqttClient newTestClient() throws Exception {
        var client = new MqttClient(brokerUrl(), "test-" + UUID.randomUUID(), new MemoryPersistence());
        var options = new MqttConnectionOptions();
        options.setCleanStart(true);
        client.connect(options);
        return client;
    }

    private void close(MqttClient client) throws Exception {
        if (client.isConnected()) {
            client.disconnect();
        }
        client.close();
    }
}
