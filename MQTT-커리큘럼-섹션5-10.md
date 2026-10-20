# Spring Boot로 배우는 MQTT 1일 실습 커리큘럼 (이어서: 섹션 5~10)

> 앞부분(개요, 버전표, 시간표, 섹션 1~4)에 이어지는 문서입니다. 패키지 구조, `application.yml`, 클래스 이름은 앞부분과 동일합니다.
>
> 참고: 코드는 Spring Integration MQTT, Paho v5, Jackson 3, Testcontainers 공식 API 기준으로 작성했지만 실제 빌드는 돌려보지 못했습니다. 컴파일 오류나 동작 차이가 나면 해당 줄을 알려주세요. 특히 섹션 7의 `MqttSubscribedEvent` 기반 online 발행은 실행 로그로 반드시 확인하세요.

## 섹션 5. 구독(Subscriber) 구현 (13:00–13:50)

브로커가 보낸 메시지를 받아 Spring 메시지 채널로 흘려보냅니다. 구조는 `브로커 → Mqttv5PahoMessageDrivenChannelAdapter → mqttInboundChannel → @ServiceActivator 핸들러` 입니다. 연결은 섹션 3에서 만든 `Mqttv5ClientManager`를 그대로 공유하므로 발행과 구독이 **하나의 MQTT 연결**을 씁니다.

### 5-1. 핵심 개념

- **Inbound 어댑터**: 구독 토픽을 등록하고, 메시지가 오면 Spring `Message`로 만들어 출력 채널로 보냅니다. 연결이 끊겼다가 복구되면 자동으로 다시 구독합니다.
- **payloadType**: 지정하지 않으면 페이로드가 `byte[]`로 옵니다. `String.class`로 지정하면 UTF-8 문자열로 변환됩니다.
- **수신 헤더**: 수신 메시지에는 `MqttHeaders.RECEIVED_TOPIC`, `RECEIVED_QOS`, `RECEIVED_RETAINED`, `DUPLICATE` 헤더가 붙습니다. (발행용 `TOPIC`, `QOS`, `RETAINED`와 이름이 다르니 주의하세요.)
- **구독 QoS**: 어댑터에 지정한 QoS가 구독 QoS입니다. 실제 배달 QoS는 `min(발행 QoS, 구독 QoS)`입니다.

### 5-2. `config/MqttInboundConfig.java`

```java
package com.example.smartfarm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;

@Configuration
public class MqttInboundConfig {

    public static final String INBOUND_CHANNEL = "mqttInboundChannel";

    @Bean(name = INBOUND_CHANNEL)
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public Mqttv5PahoMessageDrivenChannelAdapter mqttInboundAdapter(
            Mqttv5ClientManager clientManager, MqttProperties props) {

        String[] topics = props.subscribeTopics().toArray(String[]::new);
        Mqttv5PahoMessageDrivenChannelAdapter adapter =
                new Mqttv5PahoMessageDrivenChannelAdapter(clientManager, topics);

        adapter.setQos(props.defaultQos());      // 구독 QoS
        adapter.setPayloadType(String.class);    // byte[] 대신 문자열로 수신
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }
}
```

### 5-3. `mqtt/MqttInboundHandler.java` (1차 버전: 로그만 출력)

```java
package com.example.smartfarm.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.example.smartfarm.config.MqttInboundConfig;

@Component
public class MqttInboundHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttInboundHandler.class);

    @ServiceActivator(inputChannel = MqttInboundConfig.INBOUND_CHANNEL)
    public void handle(@Payload String payload,
                       @Header(MqttHeaders.RECEIVED_TOPIC) String topic,
                       @Header(MqttHeaders.RECEIVED_QOS) int qos,
                       @Header(value = MqttHeaders.RECEIVED_RETAINED, required = false) Boolean retained) {
        log.debug("수신 topic={} qos={} retained={} payload={}", topic, qos, retained, payload);
    }
}
```

### 5-4. 실행 확인

앱을 재시작한 뒤 CLI로 센서 메시지를 발행합니다.

```bash
docker exec -it mosquitto mosquitto_pub -V mqttv5 -q 1 -t 'farm/gh1/temperature/telemetry' \
  -m '{"deviceId":"gh1-temp-01","value":24.5,"unit":"C","timestamp":"2026-10-20T04:00:00Z"}'
```

앱 로그에 `수신 topic=farm/gh1/temperature/telemetry qos=1 retained=false payload={...}` 가 찍히면 성공입니다. 이어서 구독 패턴 밖의 토픽도 발행해 보세요. 로그가 찍히지 않아야 정상입니다.

```bash
docker exec -it mosquitto mosquitto_pub -V mqttv5 -t 'farm/gh1/telemetry' -m 'x'
```

### 실습 5

1. QoS 0으로 발행(`-q 0`)하면 로그의 `qos` 값은 얼마일까요? 이유는?
2. `mqtt.subscribe-topics`에 `farm/+/alerts`를 추가하고 `mosquitto_pub`으로 `farm/gh1/alerts`를 발행해 로그에 찍히는지 확인하세요.
3. 같은 Client ID를 쓰지 않는 앱 두 개를 띄우고 같은 토픽을 구독하면 메시지는 몇 번 전달될까요? 한 메시지를 둘 중 한 인스턴스만 처리하게 하려면?

### 정답 5

1. **0**입니다. 배달 QoS는 `min(발행 0, 구독 1) = 0`이기 때문입니다.
2. `application.yml`의 `subscribe-topics`에 항목을 추가하고 앱을 재시작하면 됩니다. 이후 `farm/gh1/alerts` 메시지가 핸들러까지 들어옵니다. (다만 섹션 8에서 앱이 직접 `alerts`를 발행하므로, 실습이 끝나면 이 항목은 다시 지우세요.)
3. **각 인스턴스가 한 번씩, 총 2번** 전달됩니다(브로드캐스트). 한 인스턴스만 처리하게 하려면 MQTT 5의 **Shared Subscription**을 씁니다. 구독 토픽을 `$share/workers/farm/+/+/telemetry` 형태로 지정하면 같은 그룹(`workers`) 안에서 메시지가 분산됩니다.

### 퀴즈 5

1. `payloadType`을 지정하지 않으면 핸들러의 `@Payload`는 어떤 타입으로 들어오나요?
2. 수신 메시지가 retained로 저장돼 있던 것인지 새로 발행된 것인지 구분하는 헤더는?
3. 앱이 `farm/+/+/telemetry`를 구독하고 `farm/gh1/alerts`로 발행합니다. 메시지가 앱으로 다시 돌아와 무한 루프가 생길까요?

### 정답 5-퀴즈

1. `byte[]`입니다. 문자열로 받으려면 `setPayloadType(String.class)`를 지정하거나 핸들러에서 직접 변환해야 합니다.
2. `MqttHeaders.RECEIVED_RETAINED`입니다. 구독 직후 브로커가 보내 준 저장 메시지는 `true`로 옵니다.
3. 루프가 생기지 않습니다. `farm/gh1/alerts`는 레벨이 3개라 `farm/+/+/telemetry`(레벨 4개)와 매칭되지 않습니다. 구독 필터와 발행 토픽 설계가 겹치지 않게 하는 것이 중요합니다.

## 섹션 6. 미니 프로젝트: 센서 데이터 처리·저장·조회 (13:50–14:50)

수신한 JSON을 파싱해 메모리에 저장하고 REST로 조회합니다. 토픽에서 온실(`gh1`)과 센서 종류(`temperature`)를 뽑아 데이터와 함께 기록합니다.

### 6-1. 메시지 규약

- 토픽: `farm/{온실}/{센서종류}/telemetry` (예: `farm/gh1/temperature/telemetry`)
- 페이로드 JSON:

```json
{"deviceId":"gh1-temp-01","value":24.5,"unit":"C","timestamp":"2026-10-20T04:00:00Z"}
```

> Spring Boot 4는 Jackson 3을 씁니다. 패키지가 `tools.jackson.*`으로 바뀌었고, `JacksonException`은 **언체크 예외**입니다. 어노테이션 패키지(`com.fasterxml.jackson.annotation`)만 예전 그대로입니다. Boot가 `JsonMapper` 빈을 자동 등록하므로 주입받아 쓰면 됩니다.

### 6-2. `telemetry/SensorReading.java`

```java
package com.example.smartfarm.telemetry;

import java.time.Instant;

public record SensorReading(String deviceId, double value, String unit, Instant timestamp) {
}
```

### 6-3. `telemetry/TelemetryTopic.java`

토픽 문자열을 안전하게 분해합니다. 형식이 맞지 않으면 빈 `Optional`을 돌려줍니다.

```java
package com.example.smartfarm.telemetry;

import java.util.Optional;

public record TelemetryTopic(String greenhouse, String sensorType) {

    public static Optional<TelemetryTopic> parse(String topic) {
        if (topic == null) {
            return Optional.empty();
        }
        String[] levels = topic.split("/", -1);
        boolean valid = levels.length == 4
                && "farm".equals(levels[0])
                && "telemetry".equals(levels[3])
                && !levels[1].isBlank()
                && !levels[2].isBlank();
        return valid ? Optional.of(new TelemetryTopic(levels[1], levels[2])) : Optional.empty();
    }
}
```

### 6-4. `telemetry/TelemetryRecord.java`

```java
package com.example.smartfarm.telemetry;

import java.time.Instant;

public record TelemetryRecord(
        String greenhouse,
        String sensorType,
        SensorReading reading,
        Instant receivedAt,
        int qos) {

    public String key() {
        return greenhouse + "/" + sensorType;
    }
}
```

### 6-5. `telemetry/TelemetryRepository.java`

DB 없이 메모리에 보관합니다. 온실·센서 조합마다 최근 1000건만 유지합니다.

```java
package com.example.smartfarm.telemetry;

import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Repository;

@Repository
public class TelemetryRepository {

    private static final int MAX_PER_KEY = 1000;

    private final Map<String, Deque<TelemetryRecord>> store = new ConcurrentHashMap<>();

    public void save(TelemetryRecord record) {
        Deque<TelemetryRecord> queue =
                store.computeIfAbsent(record.key(), key -> new ConcurrentLinkedDeque<>());
        queue.addFirst(record);                 // 최신이 맨 앞
        while (queue.size() > MAX_PER_KEY) {
            queue.pollLast();
        }
    }

    /** greenhouse, sensorType이 null이면 해당 조건은 무시. 최신순. */
    public List<TelemetryRecord> findRecent(String greenhouse, String sensorType, int limit) {
        return store.values().stream()
                .flatMap(Deque::stream)
                .filter(r -> greenhouse == null || r.greenhouse().equals(greenhouse))
                .filter(r -> sensorType == null || r.sensorType().equals(sensorType))
                .sorted(Comparator.comparing(TelemetryRecord::receivedAt).reversed())
                .limit(limit)
                .toList();
    }

    /** 온실/센서 조합별 가장 최근 값 */
    public Map<String, TelemetryRecord> latestByKey() {
        Map<String, TelemetryRecord> result = new TreeMap<>();
        store.forEach((key, queue) -> {
            TelemetryRecord first = queue.peekFirst();
            if (first != null) {
                result.put(key, first);
            }
        });
        return result;
    }
}
```

### 6-6. `telemetry/TelemetryService.java`

잘못된 메시지 하나가 구독 흐름을 멈추게 하면 안 됩니다. 파싱 실패는 로그만 남기고 버립니다.

```java
package com.example.smartfarm.telemetry;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final JsonMapper jsonMapper;
    private final TelemetryRepository repository;

    public TelemetryService(JsonMapper jsonMapper, TelemetryRepository repository) {
        this.jsonMapper = jsonMapper;
        this.repository = repository;
    }

    public void handle(String topic, String payload, int qos) {
        Optional<TelemetryTopic> parsedTopic = TelemetryTopic.parse(topic);
        if (parsedTopic.isEmpty()) {
            log.warn("텔레메트리 토픽 형식이 아님: {}", topic);
            return;
        }

        SensorReading reading;
        try {
            reading = jsonMapper.readValue(payload, SensorReading.class);
        } catch (JacksonException e) {
            log.warn("JSON 파싱 실패 topic={} payload={} : {}", topic, payload, e.getMessage());
            return;
        }
        if (reading.deviceId() == null || reading.deviceId().isBlank()) {
            log.warn("deviceId 누락 topic={} payload={}", topic, payload);
            return;
        }

        TelemetryRecord record = new TelemetryRecord(
                parsedTopic.get().greenhouse(),
                parsedTopic.get().sensorType(),
                reading,
                Instant.now(),
                qos);
        repository.save(record);
        log.debug("저장 {} = {}{}", record.key(), reading.value(), reading.unit());
    }
}
```

### 6-7. `MqttInboundHandler` 수정 (2차 버전: 서비스에 위임)

핸들러는 MQTT 헤더를 꺼내 서비스로 넘기기만 합니다. 비즈니스 로직은 MQTT를 몰라도 되게 분리하는 것이 목적입니다.

```java
package com.example.smartfarm.mqtt;

import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.example.smartfarm.config.MqttInboundConfig;
import com.example.smartfarm.telemetry.TelemetryService;

@Component
public class MqttInboundHandler {

    private final TelemetryService telemetryService;

    public MqttInboundHandler(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @ServiceActivator(inputChannel = MqttInboundConfig.INBOUND_CHANNEL)
    public void handle(@Payload String payload,
                       @Header(MqttHeaders.RECEIVED_TOPIC) String topic,
                       @Header(MqttHeaders.RECEIVED_QOS) int qos) {
        telemetryService.handle(topic, payload, qos);
    }
}
```

### 6-8. `telemetry/TelemetryController.java`

```java
package com.example.smartfarm.telemetry;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryRepository repository;

    public TelemetryController(TelemetryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<TelemetryRecord> list(
            @RequestParam(name = "greenhouse", required = false) String greenhouse,
            @RequestParam(name = "sensorType", required = false) String sensorType,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        if (limit < 1 || limit > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit은 1~500이어야 합니다.");
        }
        return repository.findRecent(greenhouse, sensorType, limit);
    }

    @GetMapping("/latest")
    public Map<String, TelemetryRecord> latest() {
        return repository.latestByKey();
    }
}
```

### 6-9. 실행 확인

```bash
# 센서 데이터 3건 발행
for v in 24.1 25.3 26.8; do
  docker exec mosquitto mosquitto_pub -V mqttv5 -q 1 -t 'farm/gh1/temperature/telemetry' \
    -m "{\"deviceId\":\"gh1-temp-01\",\"value\":$v,\"unit\":\"C\",\"timestamp\":\"2026-10-20T04:00:00Z\"}"
done
docker exec mosquitto mosquitto_pub -V mqttv5 -q 1 -t 'farm/gh2/humidity/telemetry' \
  -m '{"deviceId":"gh2-hum-01","value":55.0,"unit":"%","timestamp":"2026-10-20T04:00:00Z"}'

# 조회
curl 'http://localhost:8080/api/telemetry?greenhouse=gh1&sensorType=temperature&limit=2'
curl http://localhost:8080/api/telemetry/latest

# 잘못된 JSON (경고 로그만 남고 앱은 계속 동작해야 함)
docker exec mosquitto mosquitto_pub -V mqttv5 -t 'farm/gh1/temperature/telemetry' -m 'not-json'
```

### 실습 6

1. `SensorReading`에 `battery`(정수, 배터리 잔량 %) 필드를 추가하세요. 기존 센서가 이 필드 없이 보내도 파싱이 되려면?
2. `/api/telemetry` 응답에서 `receivedAt`이 가장 최근인 항목이 맨 앞에 오는지 확인하세요.
3. `TelemetryRepository.MAX_PER_KEY`를 3으로 낮춰 같은 센서로 5건을 보내고, 몇 건이 남는지 확인하세요.

### 정답 6

1. 필드를 `Integer battery`(래퍼 타입)로 선언하면 누락 시 `null`이 들어갑니다. `int`로 선언하면 누락 시 기본값 0이 들어가 "배터리 0%"로 오해할 수 있으니 래퍼 타입이 안전합니다. (Boot의 기본 JSON 설정은 모르는 필드가 있어도 실패하지 않습니다.)
2. `findRecent`가 `receivedAt` 내림차순 정렬을 하므로 맨 앞이 가장 최근입니다. 응답의 첫 항목 `reading.value`가 마지막으로 보낸 값이면 정상입니다.
3. **3건**이 남습니다. `save`가 큐 크기가 3을 넘으면 가장 오래된 항목을 제거합니다.

### 퀴즈 6

1. `farm/gh1/temperature/telemetry`를 `TelemetryTopic.parse`에 넣으면 결과는? `farm/gh1/telemetry`는?
2. 파싱 실패 시 예외를 던지지 않고 로그만 남기는 이유는?
3. 이 저장소 방식의 한계를 두 가지 말해 보세요.

### 정답 6-퀴즈

1. 앞의 것은 `TelemetryTopic("gh1", "temperature")`, 뒤의 것은 레벨이 3개라 `Optional.empty()`입니다.
2. 센서나 외부 시스템이 보내는 데이터는 언제든 잘못될 수 있습니다. 예외가 어댑터까지 전파되면 로그가 지저분해지거나 처리 흐름이 영향을 받을 수 있으므로 서비스 경계에서 검증하고 잘못된 메시지는 버립니다. (운영에서는 버린 메시지를 `farm/dead-letter` 같은 토픽이나 DB에 따로 보관하기도 합니다.)
3. (1) 앱을 재시작하면 데이터가 사라집니다. (2) 인스턴스가 여러 개면 각자 일부만 갖고 있고, 메모리가 계속 쓰입니다. 운영에서는 DB나 시계열 저장소(InfluxDB, TimescaleDB 등)를 씁니다.

## 섹션 7. QoS·Retained·LWT·세션 실습 (14:50–15:50)

신뢰성 기능 네 가지를 앱에 적용하고 직접 실험합니다.

### 7-1. 서버 상태 알림 설계

| 상황 | 토픽 `farm/server/status` 의 값 | 누가 발행? |
| --- | --- | --- |
| 앱이 (재)접속하고 구독까지 끝남 | `online` (retained, QoS 1) | 앱 |
| 앱이 정상 종료(Ctrl+C) | `offline` (retained, QoS 1) | 앱 (종료 직전) |
| 앱이 비정상 종료(kill -9, 네트워크 단절) | `offline` (retained, QoS 1) | **브로커 (LWT)** |

정상 종료에서는 LWT가 발행되지 않으므로 앱이 직접 `offline`을 발행해야 합니다.

### 7-2. `MqttConnectionConfig` 수정: LWT 등록

LWT는 **연결 시점**에 등록합니다. `mqttConnectionOptions` 빈을 아래처럼 바꿉니다. 나머지(`mqttClientManager`)는 그대로입니다.

```java
package com.example.smartfarm.config;

import java.nio.charset.StandardCharsets;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.util.StringUtils;

@Configuration
public class MqttConnectionConfig {

    @Bean
    public MqttConnectionOptions mqttConnectionOptions(MqttProperties props) {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setServerURIs(new String[] { props.brokerUrl() });
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(props.keepAliveSeconds());
        options.setCleanStart(props.cleanStart());
        options.setSessionExpiryInterval(props.sessionExpirySeconds());

        // LWT: 비정상 종료 시 브로커가 대신 발행할 메시지
        MqttMessage will = new MqttMessage("offline".getBytes(StandardCharsets.UTF_8));
        will.setQos(1);
        will.setRetained(true);
        options.setWill(props.statusTopic(), will);

        if (StringUtils.hasText(props.username())) {
            options.setUserName(props.username());
            options.setPassword(props.password().getBytes(StandardCharsets.UTF_8));
        }
        return options;
    }

    @Bean
    public Mqttv5ClientManager mqttClientManager(MqttConnectionOptions options, MqttProperties props) {
        Mqttv5ClientManager clientManager = new Mqttv5ClientManager(options, props.clientId());
        clientManager.setPersistence(new MemoryPersistence());
        return clientManager;
    }
}
```

### 7-3. `mqtt/ServerStatusPublisher.java`

- 구독이 완료되면(`MqttSubscribedEvent`) `online`을 retained로 발행합니다. 자동 재접속 후에도 이 이벤트가 다시 발생하므로, 브로커가 LWT로 `offline`을 남겼더라도 복구 시 `online`으로 되돌립니다.
- 앱이 종료되기 시작하면(`ContextClosedEvent`) 연결을 끊기 전에 `offline`을 발행합니다.

```java
package com.example.smartfarm.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.stereotype.Component;

import com.example.smartfarm.config.MqttProperties;

@Component
public class ServerStatusPublisher {

    private static final Logger log = LoggerFactory.getLogger(ServerStatusPublisher.class);

    private final MqttGateway gateway;
    private final MqttProperties props;

    public ServerStatusPublisher(MqttGateway gateway, MqttProperties props) {
        this.gateway = gateway;
        this.props = props;
    }

    @EventListener
    public void onSubscribed(MqttSubscribedEvent event) {
        log.info("구독 완료 → online 발행 ({})", event.getMessage());
        gateway.publish(props.statusTopic(), 1, true, "online");
    }

    @EventListener
    public void onShutdown(ContextClosedEvent event) {
        log.info("정상 종료 → offline 발행");
        gateway.publish(props.statusTopic(), 1, true, "offline");
    }
}
```

> 종료 시점 발행은 비동기(`setAsync(true)`)라 연결이 끊기기 직전에 아주 드물게 전달되지 못할 수 있습니다. 그래도 그 경우에는 브로커가 아니라 앱이 정상 종료한 것이므로 LWT가 오지 않아 `online`이 남을 수 있습니다. 운영에서는 종료 훅에서 전송 완료를 기다리거나, 대시보드가 상태 메시지의 시각도 함께 보도록 설계합니다.

### 7-4. `mqtt/MqttEventLogger.java`

어댑터가 발행하는 MQTT 연동 이벤트(연결 실패, 구독 완료 등)를 한곳에서 로그로 남깁니다. 연결 문제를 추적할 때 유용합니다.

```java
package com.example.smartfarm.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttIntegrationEvent;
import org.springframework.stereotype.Component;

@Component
public class MqttEventLogger {

    private static final Logger log = LoggerFactory.getLogger(MqttEventLogger.class);

    @EventListener
    public void onMqttEvent(MqttIntegrationEvent event) {
        if (event.getCause() != null) {
            log.warn("MQTT 이벤트 {} : {}", event.getClass().getSimpleName(), event.getCause().toString());
        } else {
            log.info("MQTT 이벤트 {}", event.getClass().getSimpleName());
        }
    }
}
```

### 7-5. 실험 A: Retained + online

1. 대시보드 역할로 터미널 A에서 먼저 구독합니다.

```bash
docker exec -it mosquitto mosquitto_sub -V mqttv5 -t 'farm/server/status' -v
```

2. 앱을 (재)시작합니다. 터미널 A에 `farm/server/status online`이 출력됩니다.
3. 터미널 A를 끄고 새로 구독해 보세요. **앱을 재시작하지 않았는데도** 구독 즉시 `online`이 나옵니다. 브로커가 retained 메시지를 갖고 있기 때문입니다.

### 7-6. 실험 B: 정상 종료 vs 비정상 종료 (LWT)

터미널 A는 계속 구독 상태로 둡니다.

1. 앱을 **Ctrl+C**로 종료합니다. 즉시 `offline`이 출력됩니다(앱이 직접 발행).
2. 앱을 다시 띄워 `online`이 오는 것을 확인합니다.
3. 이번에는 프로세스를 강제 종료합니다.

```bash
pkill -9 -f SmartfarmMqttApplication
```

약 **15초**(Keep Alive 10초 × 1.5) 뒤에 `offline`이 출력됩니다. 앱은 아무것도 발행하지 못했고, 브로커가 LWT로 대신 발행한 것입니다. 브로커 로그에서도 `Client smartfarm-server closed its connection` 또는 keep alive 만료 메시지를 확인해 보세요.

```bash
docker compose logs --tail 20 mosquitto
```

### 7-7. 실험 C: QoS와 실제 배달 QoS

앱 로그가 `DEBUG`이므로 섹션 5의 로그 대신 아래처럼 수신 QoS를 확인합니다. `MqttInboundHandler`에 임시로 `log.debug("qos={}", qos)`를 한 줄 넣어도 됩니다. 구독 QoS가 `default-qos: 1`이므로 다음과 같이 나와야 합니다.

| 발행 QoS | 구독 QoS | 앱이 받는 QoS |
| --- | --- | --- |
| 0 | 1 | 0 |
| 1 | 1 | 1 |
| 2 | 1 | 1 |

### 7-8. 실험 D: 세션 유지 (오프라인 동안의 메시지 보관)

1. `application.yml`을 수정합니다.

```yaml
mqtt:
  clean-start: false
  session-expiry-seconds: 3600   # 세션을 1시간 보관
```

2. 앱을 재시작합니다. 브로커 로그의 접속 줄이 `(p5, c0, k10)`로 바뀝니다. `c0`이 Clean Start 꺼짐입니다. (바꾼 뒤 **처음 한 번**은 구독이 아직 등록되기 전이므로, 앱을 한 번 더 재시작해서 구독이 세션에 저장된 상태로 시작하세요.)
3. 앱을 Ctrl+C로 종료합니다.
4. 앱이 꺼진 동안 QoS 1과 QoS 0으로 각각 발행합니다.

```bash
docker exec mosquitto mosquitto_pub -V mqttv5 -q 1 -t 'farm/gh1/temperature/telemetry' \
  -m '{"deviceId":"gh1-temp-01","value":21.0,"unit":"C","timestamp":"2026-10-20T05:00:00Z"}'
docker exec mosquitto mosquitto_pub -V mqttv5 -q 0 -t 'farm/gh1/temperature/telemetry' \
  -m '{"deviceId":"gh1-temp-01","value":22.0,"unit":"C","timestamp":"2026-10-20T05:00:01Z"}'
```

5. 앱을 다시 시작하고 조회합니다.

```bash
curl 'http://localhost:8080/api/telemetry?greenhouse=gh1&sensorType=temperature&limit=5'
```

QoS 1로 보낸 `21.0`은 재접속 직후 도착해 있고, QoS 0으로 보낸 `22.0`은 없습니다. QoS 0은 세션에 보관되지 않습니다. 조건은 세 가지입니다. **같은 Client ID**, **Clean Start = false (세션 만료 > 0)**, **구독 QoS가 1 이상**입니다.

> 실험이 끝나면 `clean-start: true`, `session-expiry-seconds: 0`으로 되돌려도 됩니다. 단, 브로커가 가진 이전 세션은 Clean Start=true 접속 시 삭제됩니다. 브로커에 쌓이는 최대 메시지 수는 `max_queued_messages`(기본 1000)로 제한됩니다.

### 실습 7

1. `farm/gh1/status`에 대한 LWT를 센서 장비 입장에서 설계해 보세요. 필요한 값(토픽, 페이로드, QoS, retained)을 적어 보세요.
2. 위 실험 B의 3번에서 `offline`이 나오기까지 시간을 단축하려면 어떤 설정을 바꿔야 할까요? 너무 짧게 하면 어떤 문제가 생길까요?
3. 같은 Client ID로 `mosquitto_sub -i smartfarm-server`를 실행하면 앱에는 어떤 일이 생기고, LWT는 발행될까요?

### 정답 7

1. 토픽 `farm/gh1/status`, 페이로드 `offline`(정상 접속 시 `online`을 같은 토픽에 retained로 발행), QoS 1, retained `true`입니다. 장비마다 별도 토픽(`farm/gh1/{장비ID}/status`)을 쓰면 장비별 상태를 구분할 수 있습니다.
2. `mqtt.keep-alive-seconds`를 줄입니다(예: 5초 → 약 7.5초). 너무 짧으면 네트워크가 잠깐만 불안정해도 연결이 끊긴 것으로 판단돼 `offline`이 자주 오고, PINGREQ 트래픽이 늘어납니다. 운영에서는 보통 30~60초를 씁니다.
3. 브로커가 기존 연결을 끊고 새 연결을 받습니다. 끊긴 앱은 자동 재접속을 시도하고 다시 서로를 끊는 핑퐁이 생깁니다. LWT는 비정상 종료로 간주되어 **발행될 수 있습니다**(MQTT 5에서 세션 탈취(takeover)로 인한 연결 종료도 유언 발행 대상입니다). 실습 후에는 꼭 중지하세요.

### 퀴즈 7

1. 앱이 Ctrl+C로 종료될 때 LWT가 발행되지 않는 이유는?
2. LWT를 retained로 등록하는 이유는?
3. 실험 D에서 QoS 0 메시지가 유실되는 이유는?
4. `Clean Start=true`로 접속하면 이전 세션의 구독과 보관 메시지는 어떻게 되나요?

### 정답 7-퀴즈

1. 정상 종료는 클라이언트가 DISCONNECT 패킷을 보내기 때문에 브로커가 "의도된 종료"로 보고 유언을 발행하지 않습니다.
2. 나중에 접속한 대시보드나 다른 클라이언트도 마지막 상태(`offline`)를 바로 볼 수 있도록 하기 위해서입니다. retained가 아니면 발행 시점에 구독 중이던 클라이언트만 알 수 있습니다.
3. QoS 0은 브로커가 배달 보장 없이 "보내고 잊는" 방식이라 오프라인 클라이언트용으로 저장하지 않습니다. 보관 대상은 QoS 1·2 메시지입니다.
4. 이전 세션이 삭제됩니다. 구독 정보와 보관돼 있던 메시지가 모두 사라집니다.

## 섹션 8. 시뮬레이터와 경보·명령 발행 (15:50–16:50)

센서 장비가 없어도 시스템을 계속 돌려 볼 수 있게 주기적으로 데이터를 발행하는 시뮬레이터를 만들고, 임계값을 벗어나면 경보를 발행하며, REST로 원격 명령을 발행합니다.

### 8-1. 토픽 설계 정리

| 용도 | 토픽 | QoS | retained | 방향 |
| --- | --- | --- | --- | --- |
| 센서값 | `farm/{온실}/{센서}/telemetry` | 0 | false | 센서 → 서버 |
| 경보 | `farm/{온실}/alerts` | 1 | false | 서버 → 구독자 |
| 명령 | `farm/{온실}/commands` | 1 | false | 서버 → 장비 |
| 서버 상태 | `farm/server/status` | 1 | true | 서버/브로커 |

주기적으로 오는 센서값은 일부 유실돼도 다음 값이 곧 오므로 QoS 0, 업무적으로 중요한 경보와 명령은 QoS 1을 씁니다.

### 8-2. `config/SchedulingConfig.java`

```java
package com.example.smartfarm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

### 8-3. `simulator/DeviceSimulator.java`

`smartfarm.simulator.enabled=true`일 때만 빈이 만들어집니다. 온실 2개 × 센서 2종을 주기적으로 발행하고, 가끔 임계값을 넘는 값을 섞어 경보를 유도합니다.

```java
package com.example.smartfarm.simulator;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.smartfarm.mqtt.MqttGateway;
import com.example.smartfarm.telemetry.SensorReading;

import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "smartfarm.simulator", name = "enabled", havingValue = "true")
public class DeviceSimulator {

    private static final Logger log = LoggerFactory.getLogger(DeviceSimulator.class);
    private static final List<String> GREENHOUSES = List.of("gh1", "gh2");

    private final MqttGateway gateway;
    private final JsonMapper jsonMapper;

    public DeviceSimulator(MqttGateway gateway, JsonMapper jsonMapper) {
        this.gateway = gateway;
        this.jsonMapper = jsonMapper;
    }

    @Scheduled(fixedDelayString = "${smartfarm.simulator.interval-ms:5000}")
    public void publishReadings() {
        for (String gh : GREENHOUSES) {
            publish(gh, "temperature", "C", randomTemperature());
            publish(gh, "humidity", "%", randomHumidity());
        }
    }

    private void publish(String greenhouse, String sensorType, String unit, double value) {
        double rounded = Math.round(value * 10) / 10.0;
        String deviceId = "%s-%s-01".formatted(greenhouse, sensorType.substring(0, 4));
        SensorReading reading = new SensorReading(deviceId, rounded, unit, Instant.now());
        String topic = "farm/%s/%s/telemetry".formatted(greenhouse, sensorType);

        gateway.publish(topic, 0, false, jsonMapper.writeValueAsString(reading));
        log.debug("시뮬레이터 발행 {} = {}{}", topic, rounded, unit);
    }

    // 평소 20~28℃, 10% 확률로 31~36℃ (임계값 30℃ 초과)
    private double randomTemperature() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return r.nextInt(10) == 0 ? r.nextDouble(31, 36) : r.nextDouble(20, 28);
    }

    // 평소 50~70%, 10% 확률로 30~39% (임계값 40% 미만)
    private double randomHumidity() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return r.nextInt(10) == 0 ? r.nextDouble(30, 39) : r.nextDouble(50, 70);
    }
}
```

> 섹션 4에서 `handler.setAsync(true)`로 설정한 이유가 여기서 보입니다. 동기 모드에서는 발행 스레드가 브로커 응답(QoS 1의 PUBACK)을 받을 때까지 블록됩니다. 비동기 모드는 바로 반환하므로 스케줄러나 요청 스레드가 막히지 않습니다. 대신 전송 실패가 호출자에게 예외로 전달되지 않으므로, 실패를 알아야 한다면 `handler.setAsyncEvents(true)`로 전송·전달 완료 이벤트를 받아 처리합니다.

### 8-4. `alert/AlertProperties.java`

```java
package com.example.smartfarm.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.alert")
public record AlertProperties(double temperatureMax, double humidityMin) {
}
```

### 8-5. `alert/Alert.java`

```java
package com.example.smartfarm.alert;

import java.time.Instant;

public record Alert(
        String greenhouse,
        String sensorType,
        String deviceId,
        double value,
        double threshold,
        String message,
        Instant occurredAt) {
}
```

### 8-6. `alert/AlertService.java`

```java
package com.example.smartfarm.alert;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.smartfarm.mqtt.MqttGateway;
import com.example.smartfarm.telemetry.TelemetryRecord;

import tools.jackson.databind.json.JsonMapper;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertProperties props;
    private final MqttGateway gateway;
    private final JsonMapper jsonMapper;

    public AlertService(AlertProperties props, MqttGateway gateway, JsonMapper jsonMapper) {
        this.props = props;
        this.gateway = gateway;
        this.jsonMapper = jsonMapper;
    }

    public void evaluate(TelemetryRecord record) {
        double value = record.reading().value();
        switch (record.sensorType()) {
            case "temperature" -> {
                if (value > props.temperatureMax()) {
                    raise(record, props.temperatureMax(),
                            "온도 %.1f℃ 가 상한 %.1f℃ 를 초과했습니다.".formatted(value, props.temperatureMax()));
                }
            }
            case "humidity" -> {
                if (value < props.humidityMin()) {
                    raise(record, props.humidityMin(),
                            "습도 %.1f%% 가 하한 %.1f%% 미만입니다.".formatted(value, props.humidityMin()));
                }
            }
            default -> { /* 다른 센서는 경보 대상 아님 */ }
        }
    }

    private void raise(TelemetryRecord record, double threshold, String message) {
        Alert alert = new Alert(
                record.greenhouse(),
                record.sensorType(),
                record.reading().deviceId(),
                record.reading().value(),
                threshold,
                message,
                Instant.now());
        String topic = "farm/%s/alerts".formatted(record.greenhouse());
        gateway.publish(topic, 1, false, jsonMapper.writeValueAsString(alert));
        log.warn("경보 발행 {} : {}", topic, message);
    }
}
```

### 8-7. `TelemetryService` 수정: 저장 후 경보 평가

생성자에 `AlertService`를 추가하고, `repository.save(record)` 다음 줄에서 평가를 호출합니다.

```java
private final AlertService alertService;

public TelemetryService(JsonMapper jsonMapper, TelemetryRepository repository, AlertService alertService) {
    this.jsonMapper = jsonMapper;
    this.repository = repository;
    this.alertService = alertService;
}

// handle(...) 안에서
repository.save(record);
alertService.evaluate(record);   // 추가
log.debug("저장 {} = {}{}", record.key(), reading.value(), reading.unit());
```

(import: `com.example.smartfarm.alert.AlertService`)

### 8-8. `api/CommandRequest.java`

```java
package com.example.smartfarm.api;

import java.util.Map;

public record CommandRequest(String device, String action, Map<String, Object> params) {
}
```

### 8-9. `api/CommandController.java`

온실 이름이 토픽의 한 레벨로 들어가므로, `/`나 `+`, `#`이 섞여 엉뚱한 토픽으로 발행되는 일(토픽 인젝션)을 정규식으로 막습니다.

```java
package com.example.smartfarm.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.smartfarm.mqtt.MqttGateway;

import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/greenhouses")
public class CommandController {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final MqttGateway gateway;
    private final JsonMapper jsonMapper;

    public CommandController(MqttGateway gateway, JsonMapper jsonMapper) {
        this.gateway = gateway;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/{greenhouse}/commands")
    public ResponseEntity<Map<String, Object>> send(
            @PathVariable("greenhouse") String greenhouse,
            @RequestBody CommandRequest request) {

        if (!NAME.matcher(greenhouse).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "온실 이름은 영문, 숫자, _, - 만 가능합니다.");
        }
        if (request.device() == null || request.action() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "device와 action은 필수입니다.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("device", request.device());
        body.put("action", request.action());
        body.put("params", request.params() == null ? Map.of() : request.params());
        body.put("issuedAt", Instant.now().toString());

        String topic = "farm/%s/commands".formatted(greenhouse);
        gateway.publish(topic, 1, false, jsonMapper.writeValueAsString(body));

        return ResponseEntity.accepted().body(Map.of("topic", topic, "command", body));
    }
}
```

### 8-10. 실행 확인

1. `application.yml`에서 시뮬레이터를 켭니다.

```yaml
smartfarm:
  simulator:
    enabled: true
    interval-ms: 5000
```

2. 터미널 A에서 경보와 명령 토픽을 구독합니다.

```bash
docker exec -it mosquitto mosquitto_sub -V mqttv5 -t 'farm/+/alerts' -t 'farm/+/commands' -v
```

3. 앱을 재시작하고 몇십 초 기다립니다. 간헐적으로 `farm/gh1/alerts {...}` 가 출력되고, `/api/telemetry/latest`에 4개 조합이 쌓입니다.
4. 명령을 발행합니다.

```bash
curl -i -X POST http://localhost:8080/api/greenhouses/gh1/commands \
  -H 'Content-Type: application/json' \
  -d '{"device":"fan-01","action":"ON","params":{"speed":2}}'
```

터미널 A에 `farm/gh1/commands {...}` 가 출력되면 성공입니다. 잘못된 온실 이름(`gh1%2F..%2Fx` 등)은 `400`이 나와야 합니다.

### 실습 8

1. 경보가 5초마다 계속 쏟아지지 않도록 같은 온실·센서 조합은 **30초에 한 번만** 발행하도록 `AlertService`를 개선해 보세요.
2. 경보 토픽을 `farm/{온실}/alerts/{센서종류}`처럼 한 레벨 더 쪼갠다면 구독 쪽 와일드카드는 어떻게 쓸까요?
3. 명령 토픽 `farm/gh1/commands`를 장비(`fan-01`)가 구독해야 한다면, 장비가 자기에게 온 명령만 받게 하려면 토픽을 어떻게 설계하면 좋을까요?

### 정답 8

1. 키(`greenhouse/sensorType`)별 마지막 발행 시각을 `ConcurrentHashMap<String, Instant>`에 저장해 두고, `Instant.now()`와의 차이가 30초 미만이면 `raise`를 건너뜁니다.

```java
private final Map<String, Instant> lastRaised = new ConcurrentHashMap<>();

private boolean inCooldown(TelemetryRecord record) {
    Instant now = Instant.now();
    Instant last = lastRaised.get(record.key());
    if (last != null && Duration.between(last, now).getSeconds() < 30) {
        return true;
    }
    lastRaised.put(record.key(), now);
    return false;
}
// raise() 맨 앞에서: if (inCooldown(record)) return;
```

2. 온실 전체의 경보는 `farm/+/alerts/#`, 특정 온실의 온도 경보는 `farm/gh1/alerts/temperature`, 모든 온실의 온도 경보는 `farm/+/alerts/temperature`로 구독합니다.
3. 장비 ID를 토픽에 넣습니다. 예: `farm/gh1/commands/fan-01`. 장비는 자기 토픽만 구독하고, 온실 전체에 방송하고 싶을 때는 `farm/gh1/commands/all`을 함께 구독하게 합니다. 이 경우 `CommandController`는 `request.device()`를 토픽 마지막 레벨로 사용하되 같은 정규식 검증을 거쳐야 합니다.

### 퀴즈 8

1. 센서값은 QoS 0, 경보와 명령은 QoS 1로 둔 이유를 한 문장씩 설명해 보세요.
2. 앱이 같은 토픽 계열(`farm/+/+/telemetry`)을 구독하면서 경보를 `farm/{온실}/alerts`로 발행해도 루프가 생기지 않는 이유는?
3. QoS 1 명령을 발행했는데 장비가 같은 명령을 두 번 받았습니다. 정상일까요? 어떻게 대응해야 하나요?

### 정답 8-퀴즈

1. 센서값은 주기적으로 반복되므로 일부 유실돼도 영향이 작고 오버헤드가 적은 QoS 0이 적합합니다. 경보와 명령은 유실되면 사고로 이어질 수 있으므로 최소 한 번은 도착하도록 QoS 1을 씁니다.
2. 경보 토픽은 레벨이 3개라 4레벨 구독 필터와 매칭되지 않습니다.
3. 정상입니다. QoS 1은 "최소 1회"라서 PUBACK이 유실되면 재전송되어 중복이 생길 수 있습니다. 명령에 고유 ID(`commandId`)를 넣고 장비가 이미 처리한 ID는 무시하는 **멱등 처리**로 대응합니다. 중복 자체를 막아야 하면 QoS 2를 쓰지만 비용이 큽니다.

## 섹션 9. Testcontainers로 통합 테스트 (16:50–17:30)

진짜 Mosquitto 컨테이너를 테스트마다 띄워 "발행 → 수신 → 저장 → 경보 발행" 흐름을 자동으로 검증합니다. Docker가 실행 중이어야 합니다.

### 9-1. 테스트 설계

| 테스트 | 검증 내용 |
| --- | --- |
| `서버가_접속하면_online_상태가_retained로_남는다` | 구독 완료 후 online 발행, 새 구독자가 retained로 수신 |
| `센서_메시지를_수신하면_저장된다` | 발행 → 구독 → 저장소 반영 |
| `잘못된_JSON은_무시되고_서버는_계속_동작한다` | 오류 메시지 뒤에도 정상 메시지 처리 |
| `임계값을_넘으면_경보가_발행된다` | 별도 구독 클라이언트가 `alerts` 토픽에서 수신 |

### 9-2. `src/test/java/com/example/smartfarm/MqttIntegrationTest.java`

- 브로커는 `eclipse-mosquitto:2.1.2` 이미지에 포함된 `/mosquitto-no-auth.conf`(1883 리스너, 익명 허용)로 실행합니다. 설정 파일을 따로 마운트할 필요가 없습니다.
- Mosquitto에는 `@ServiceConnection`이 없으므로 `@DynamicPropertySource`로 접속 주소를 주입합니다. 컨테이너 포트는 매번 무작위로 매핑됩니다.
- 비동기 처리를 기다릴 때는 `Thread.sleep` 대신 Awaitility를 씁니다.

```java
package com.example.smartfarm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.smartfarm.mqtt.MqttGateway;
import com.example.smartfarm.telemetry.TelemetryRepository;

@SpringBootTest(properties = {
        "mqtt.client-id=smartfarm-test-server",
        "mqtt.clean-start=true",
        "mqtt.session-expiry-seconds=0",
        "smartfarm.simulator.enabled=false"
})
@Testcontainers
class MqttIntegrationTest {

    @Container
    static final GenericContainer<?> MOSQUITTO =
            new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.1.2"))
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
        MqttClient client = newTestClient();
        try {
            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            client.subscribe("farm/server/status", 1,
                    (topic, message) -> received.add(new String(message.getPayload(), UTF_8)));

            // 구독 시점에 이미 저장돼 있는 retained 메시지를 받아야 한다
            assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("online");
        } finally {
            close(client);
        }
    }

    @Test
    void 센서_메시지를_수신하면_저장된다() throws Exception {
        waitUntilServerOnline();

        gateway.publish("farm/gh-test1/temperature/telemetry", 1, false, """
                {"deviceId":"t1-temp-01","value":24.5,"unit":"C","timestamp":"2026-10-20T04:00:00Z"}""");

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
                {"deviceId":"t2-temp-01","value":22.0,"unit":"C","timestamp":"2026-10-20T04:00:00Z"}""");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findRecent("gh-test2", "temperature", 10)).hasSize(1));
    }

    @Test
    void 임계값을_넘으면_경보가_발행된다() throws Exception {
        waitUntilServerOnline();

        MqttClient client = newTestClient();
        try {
            BlockingQueue<String> alerts = new LinkedBlockingQueue<>();
            client.subscribe("farm/gh-test3/alerts", 1,
                    (topic, message) -> alerts.add(new String(message.getPayload(), UTF_8)));

            gateway.publish("farm/gh-test3/temperature/telemetry", 1, false, """
                    {"deviceId":"t3-temp-01","value":35.0,"unit":"C","timestamp":"2026-10-20T04:00:00Z"}""");

            String alert = alerts.poll(10, TimeUnit.SECONDS);
            assertThat(alert).isNotNull();
            assertThat(alert).contains("\"greenhouse\":\"gh-test3\"", "\"sensorType\":\"temperature\"");
        } finally {
            close(client);
        }
    }

    // ---------- 헬퍼 ----------

    /** 서버가 구독까지 끝냈다는 신호(online)를 기다린다. 구독 전에 발행하면 메시지가 유실될 수 있다. */
    private void waitUntilServerOnline() throws Exception {
        MqttClient client = newTestClient();
        try {
            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            client.subscribe("farm/server/status", 1,
                    (topic, message) -> received.add(new String(message.getPayload(), UTF_8)));
            assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("online");
        } finally {
            close(client);
        }
    }

    private MqttClient newTestClient() throws Exception {
        MqttClient client = new MqttClient(brokerUrl(), "test-" + UUID.randomUUID(), new MemoryPersistence());
        MqttConnectionOptions options = new MqttConnectionOptions();
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
```

### 9-3. 실행

```bash
./gradlew test
```

Docker가 켜져 있고 이미지를 처음 받는다면 첫 실행은 조금 걸립니다. 결과 리포트는 `build/reports/tests/test/index.html`에서 볼 수 있습니다.

### 실습 9

1. 테스트 4개 중 하나를 일부러 깨 보세요(예: 기대 값을 `35.0` → 임계값 이하인 `25.0`으로 바꾸기). 어떤 실패 메시지가 나오는지, 왜 Awaitility/큐 타임아웃으로 실패하는지 확인하세요.
2. 습도 하한 미만(예: 35%)일 때 경보가 발행되는 테스트를 추가하세요.
3. 세션 유지(섹션 7-8)를 자동 테스트로 만든다면 어떤 단계가 필요할까요?

### 정답 9

1. 경보가 오지 않으면 `alerts.poll(10, SECONDS)`이 `null`을 돌려주고 `assertThat(alert).isNotNull()`이 실패합니다. 메시지 전달은 비동기이므로 "안 왔다"를 확인하려면 일정 시간 기다려야 하고, 그래서 타임아웃 실패로 드러납니다.
2. 경보 테스트를 복사해 토픽을 `farm/gh-test4/humidity/telemetry`, 값을 `35.0`, 단위를 `%`로 바꾸고 구독 토픽도 `farm/gh-test4/alerts`로 바꿉니다.
3. (1) 고정 Client ID로 `clean-start=false`, 세션 만료 > 0인 클라이언트로 접속해 구독한 뒤 연결을 끊고, (2) 끊긴 동안 QoS 1 메시지를 발행하고, (3) 같은 Client ID와 `clean-start=false`로 다시 접속해 메시지가 도착하는지 확인합니다. Spring 앱 대신 Paho 클라이언트만으로 브로커 동작을 검증하는 테스트가 간단합니다.

### 퀴즈 9

1. 테스트가 시작하자마자 발행하면 가끔 실패하는 이유와 이 문서에서 쓴 해결책은?
2. 테스트에서 `mqtt.client-id`를 따로 지정한 이유는?
3. `Thread.sleep(5000)` 대신 Awaitility를 쓰는 장점은?

### 정답 9-퀴즈

1. 앱이 아직 구독을 마치기 전에 발행하면 해당 메시지는 구독자가 없어서 버려집니다(retained나 보관 세션이 없는 경우). 서버가 구독 후 발행하는 `online` retained 메시지를 먼저 기다린 뒤 발행하는 방식으로 해결했습니다.
2. 로컬에서 실제 앱(`smartfarm-server`)이 다른 브로커에 떠 있을 때 같은 ID가 충돌하는 것을 피하기 위해서입니다. (테스트 브로커는 별개지만 습관적으로 구분해 두면 안전합니다.)
3. 조건이 만족되는 즉시 통과해 테스트가 빨라지고, 느린 환경에서도 최대 대기 시간까지는 안정적으로 기다립니다. 고정 sleep은 느리거나 불안정합니다.

## 섹션 10. 최종 점검 (17:30–18:00)

### 10-1. 전체 시나리오 시연 (5분)

1. `docker compose up -d`로 브로커를 띄웁니다.
2. 터미널 A에서 `farm/#`, `farm/server/status`를 구독해 둡니다.

```bash
docker exec -it mosquitto mosquitto_sub -V mqttv5 -t 'farm/#' -v
```

3. `./gradlew bootRun`으로 앱을 시작합니다. `online`이 보여야 합니다.
4. 시뮬레이터를 켠 상태에서 `GET /api/telemetry/latest`로 값이 쌓이는지 확인합니다.
5. 경보 토픽에 임계값 초과 알림이 오는지 확인합니다.
6. `POST /api/greenhouses/gh1/commands`로 명령을 보냅니다.
7. Ctrl+C로 종료해 `offline`이 즉시 오는지, `kill -9`로 종료하면 약 15초 뒤 LWT `offline`이 오는지 확인합니다.
8. `./gradlew test`가 전부 통과하는지 확인합니다.

### 10-2. 자주 겪는 문제

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| 앱 기동 시 `ClassNotFoundException: org.eclipse.paho.mqttv5...` | Paho v5 클라이언트 의존성 누락 | `build.gradle`에 `org.eclipse.paho.mqttv5.client:1.2.5` 추가 |
| 브로커에 연결이 거부됨 | Mosquitto에 리스너 설정 없음(localhost만 허용) | `mosquitto.conf`에 `listener 1883`, `allow_anonymous true` |
| 앱을 두 개 띄웠더니 계속 재접속 | Client ID 중복 | 인스턴스별 Client ID 분리 |
| 메시지가 앱에 안 옴 | 구독 필터와 토픽 레벨 수 불일치 | `+`/`#` 규칙 재확인, `mosquitto_sub -t '#' -v`로 실제 토픽 확인 |
| 한글이 깨짐 | 터미널/curl 인코딩 | UTF-8 사용, `Content-Type: application/json` 확인 |
| LWT가 안 옴 | 정상 종료(DISCONNECT)였거나 Keep Alive 대기 중 | `kill -9` 후 약 15초 대기, `k10` 값 확인 |
| 재시작 후 메시지가 안 쌓임 | `clean-start=true`이거나 QoS 0 | `clean-start=false`, 세션 만료 > 0, QoS 1 이상 |
| 테스트가 Docker 오류로 실패 | Docker 미실행/권한 | Docker Desktop 실행 확인 |

### 종합 퀴즈

1. 구독 필터 `farm/+/+/telemetry`와 매칭되는 토픽을 모두 고르세요. (a) `farm/gh2/humidity/telemetry` (b) `farm/server/status` (c) `farm/gh1/alerts` (d) `farm/gh1/temperature/telemetry/raw`
2. 발행자가 QoS 1, 구독자가 QoS 2로 구독했습니다. 배달 QoS는? QoS 2로 받으려면 무엇을 바꿔야 하나요?
3. LWT가 동작하는 경우를 세 가지, 동작하지 않는 경우를 한 가지 말해 보세요.
4. retained 메시지를 지우는 방법은?
5. 앱이 오프라인인 동안 발행된 메시지를 재접속 후에도 받으려면 필요한 조건 세 가지는?
6. `$SYS/#`가 `#` 구독에 포함되지 않는 이유는?
7. 앱이 두 인스턴스로 확장될 때, 같은 센서 메시지를 한 번만 처리하게 하는 MQTT 5 기능은?
8. 이 프로젝트에서 `MqttGateway`와 `Mqttv5ClientManager`는 각각 어떤 역할을 하나요?

### 종합 퀴즈 정답

1. **(a)만** 해당합니다. (b)는 레벨이 3개, (c)도 레벨이 3개, (d)는 레벨이 5개라 모두 불일치입니다. 마지막 레벨이 `telemetry`이고 전체 4레벨인 토픽만 매칭됩니다.
2. 배달 QoS는 **1**입니다(`min(1, 2)`). 2로 받으려면 **발행 QoS도 2**로 올려야 합니다.
3. 동작: 네트워크 단절, 프로세스 강제 종료(kill -9), Keep Alive 초과(응답 없음), (MQTT 5에서) 같은 Client ID 세션 탈취 등. 동작하지 않음: 클라이언트가 DISCONNECT를 보내는 정상 종료.
4. 같은 토픽에 **빈 페이로드를 retained로 발행**합니다(`mosquitto_pub -t 토픽 -r -n`).
5. **같은 Client ID**, **Clean Start=false + Session Expiry > 0**, **구독 QoS 1 이상(그리고 발행 QoS 1 이상)**.
6. `$`로 시작하는 토픽은 브로커 내부용으로 예약돼 있어 와일드카드로 구독되지 않고 명시적으로 구독해야 합니다.
7. **Shared Subscription** (`$share/그룹명/토픽필터`). 같은 그룹 안에서 메시지가 분산됩니다.
8. `MqttGateway`는 서비스 코드가 MQTT를 모르고 메서드 호출만으로 발행하게 해 주는 Spring Integration 인터페이스입니다. `Mqttv5ClientManager`는 브로커 연결 하나를 만들고 관리하며, 발행(Outbound)과 구독(Inbound) 어댑터가 이 연결을 공유하게 합니다.

### 10-3. 오늘 만든 것 체크리스트

- [ ] Docker로 Mosquitto 2.1.2 구동, CLI로 pub/sub 확인
- [ ] Spring Boot 4.1.1 프로젝트가 MQTT 5로 브로커에 연결
- [ ] REST → MQTT 발행 API (`/api/mqtt/publish`), 와일드카드 방지 검증
- [ ] 구독 어댑터로 센서 JSON 수신, 저장, 조회 API (`/api/telemetry`)
- [ ] QoS, Retained, LWT, 세션 유지 동작 실험
- [ ] 시뮬레이터, 임계값 경보 발행, 원격 명령 API
- [ ] Testcontainers 통합 테스트 4종 통과

### 10-4. 다음 학습 주제

- **보안**: `password_file`, `acl_file`로 인증·권한 설정, TLS(8883)와 인증서, 클라이언트별 토픽 권한 분리
- **확장성**: Shared Subscription으로 소비자 분산, 브로커 클러스터링(EMQX, HiveMQ), Mosquitto Bridge
- **MQTT 5 기능 심화**: Request/Response(Response Topic, Correlation Data), User Properties, Message Expiry, Topic Alias
- **영속화**: 메모리 저장소를 PostgreSQL/TimescaleDB/InfluxDB로 교체, 대시보드(Grafana) 연동
- **운영**: Micrometer로 수신/발행 메트릭 수집, 헬스체크에 브로커 연결 상태 반영, 데드레터 토픽
- **IoT 표준**: Sparkplug B, 토픽 네이밍 컨벤션, 장비 프로비저닝
- **프로토콜 비교**: HTTP/WebSocket/AMQP/Kafka와 MQTT의 쓰임새 차이
