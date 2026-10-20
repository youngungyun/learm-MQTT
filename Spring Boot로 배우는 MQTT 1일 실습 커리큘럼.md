# Spring Boot로 배우는 MQTT 1일 실습 커리큘럼

Oct 20, 2026 · @Yun

## 개요

하루(약 8시간) 동안 **스마트팜 센서 모니터링 서버**를 직접 만들면서 MQTT를 익힙니다. 끝나면 센서 데이터 구독·저장, 조회 API, 임계값 경보 발행, 원격 명령 발행, LWT 기반 서버 상태 알림, 통합 테스트까지 갖춘 Spring Boot 프로젝트가 남습니다.

### 완성되는 시스템의 메시지 흐름

1. 센서 시뮬레이터(또는 `mosquitto_pub`)가 `farm/{온실}/{센서}/telemetry` 토픽에 JSON을 발행합니다.
2. Mosquitto 브로커가 구독 중인 Spring Boot 앱으로 메시지를 전달합니다.
3. 앱은 JSON을 파싱해 메모리에 저장하고, REST API(`/api/telemetry`)로 조회할 수 있게 합니다.
4. 온도·습도가 임계값을 벗어나면 앱이 `farm/{온실}/alerts`로 경보를 발행합니다.
5. REST로 받은 제어 명령은 `farm/{온실}/commands`로 발행합니다.
6. 앱 상태는 `farm/server/status`에 retained 메시지(online/offline)로 남고, 비정상 종료 시 브로커가 LWT로 offline을 대신 발행합니다.

### 사용 버전 (2026-10-20 기준 최신 안정판)

| 구성 요소 | 버전 | 비고 |
| --- | --- | --- |
| Java | 25 (LTS) | Spring Boot 4.1은 Java 17 이상 지원 |
| Spring Boot | 4.1.1 | [GitHub Releases](https://github.com/spring-projects/spring-boot/releases) 기준 최신 GA. 4.2는 11월 예정 |
| Spring Integration MQTT | 7.1.x (Boot가 관리) | [7.1.0 릴리스 공지](https://spring.io/blog/2026/06/10/spring-integration-7-1-0-released/) |
| Eclipse Paho MQTT v5 Client | 1.2.5 | Spring Integration에서 optional 의존성이라 직접 추가해야 함 |
| Jackson | 3.x (Boot 4 기본) | 패키지가 `tools.jackson.*`으로 바뀜 |
| Testcontainers | 2.x (Boot가 관리) | 모듈명 `testcontainers-junit-jupiter` |
| Eclipse Mosquitto | 2.1.2 | [Mosquitto 2026 릴리스 목록](https://mosquitto.org/2026/) |
| 빌드 도구 | Gradle (Groovy DSL) | Spring Initializr가 생성하는 Wrapper 사용 |

### 사전 준비물

- JDK 25, IntelliJ IDEA(또는 선호 IDE)
- Docker Desktop(또는 Docker Engine + Compose)
- HTTP 클라이언트: `curl` 또는 IntelliJ HTTP Client
- 선택: [MQTTX](https://mqttx.app/) GUI 클라이언트

### 시간표

| 시간 | 섹션 | 목표 |
| --- | --- | --- |
| 09:00–09:40 | 1. MQTT 핵심 개념 | Pub/Sub, 토픽, QoS, Retained, LWT, 세션 이해 |
| 09:40–10:20 | 2. 브로커 구축 | Docker로 Mosquitto 실행, CLI로 pub/sub |
| 10:20–11:00 | 3. 프로젝트 생성 | Gradle 설정, 설정 클래스, MQTT 연결 |
| 11:00–12:00 | 4. 발행(Publisher) | Gateway + REST로 메시지 발행 |
| 12:00–13:00 | 점심 |  |
| 13:00–13:50 | 5. 구독(Subscriber) | Inbound 어댑터, 수신 헤더 |
| 13:50–14:50 | 6. 미니 프로젝트 | 센서 JSON 처리, 저장, 조회 API |
| 14:50–15:50 | 7. QoS·Retained·LWT·세션 | 신뢰성 기능 실습 |
| 15:50–16:50 | 8. 시뮬레이터와 경보 | 스케줄 발행, 경보·명령 발행 |
| 16:50–17:30 | 9. 통합 테스트 | Testcontainers로 자동 검증 |
| 17:30–18:00 | 10. 최종 점검 | 종합 퀴즈, 다음 학습 |

### 최종 패키지 구조

```
smartfarm-mqtt/
├── compose.yaml
├── mosquitto/config/mosquitto.conf
├── build.gradle
├── settings.gradle
└── src/
    ├── main/
    │   ├── java/com/example/smartfarm/
    │   │   ├── SmartfarmMqttApplication.java
    │   │   ├── config/      MqttProperties, MqttConnectionConfig, MqttOutboundConfig, MqttInboundConfig, SchedulingConfig
    │   │   ├── mqtt/        MqttGateway, MqttInboundHandler, ServerStatusPublisher, MqttEventLogger
    │   │   ├── telemetry/   SensorReading, TelemetryTopic, TelemetryRecord, TelemetryRepository, TelemetryService, TelemetryController
    │   │   ├── alert/       AlertProperties, Alert, AlertService
    │   │   ├── simulator/   DeviceSimulator
    │   │   └── api/         PublishRequest, PublishController, CommandRequest, CommandController
    │   └── resources/application.yml
    └── test/java/com/example/smartfarm/MqttIntegrationTest.java
```

> 참고: 이 문서의 코드는 공식 문서 API와 대조해 작성했지만 작성 환경에서 Maven Central 접근이 막혀 실제 빌드는 돌려보지 못했습니다. 컴파일 오류가 나면 해당 줄을 알려주세요.

## 섹션 1. MQTT 핵심 개념 (09:00–09:40)

MQTT는 **브로커를 사이에 둔 발행/구독(Pub/Sub) 프로토콜**입니다. 발행자와 구독자는 서로를 모르고, 오직 "토픽"이라는 주소로만 연결됩니다. TCP 위에서 동작하며 헤더가 최소 2바이트라 IoT처럼 대역폭과 전력이 제한된 환경에 적합합니다.

### 1-1. 구성 요소

| 용어 | 설명 | 이 프로젝트에서 |
| --- | --- | --- |
| Broker | 메시지를 받아 구독자에게 배달하는 서버 | Mosquitto |
| Client | 브로커에 연결하는 모든 프로그램(발행·구독 겸용 가능) | Spring Boot 앱, 센서 시뮬레이터, mosquitto\_sub |
| Topic | `/`로 구분되는 계층형 주소. 미리 만들 필요 없음 | `farm/gh1/temperature/telemetry` |
| Payload | 메시지 본문(바이트 배열). 형식은 자유 | JSON |
| Client ID | 브로커가 클라이언트를 식별하는 고유 ID. 같은 ID로 접속하면 기존 연결이 끊김 | `smartfarm-server` |

### 1-2. 토픽과 와일드카드

- 토픽은 대소문자를 구분하고, `/`로 레벨을 나눕니다. 맨 앞에 `/`를 붙이지 않는 것이 관례입니다.
- `+` : **한 레벨** 와일드카드. `farm/+/temperature/telemetry`는 `farm/gh1/temperature/telemetry`와 매칭되지만 `farm/gh1/a/temperature/telemetry`와는 매칭되지 않습니다.
- `#` : **여러 레벨** 와일드카드. 반드시 마지막에만 올 수 있습니다. `farm/#`는 `farm` 아래 모든 토픽과 매칭됩니다.
- 와일드카드는 **구독에서만** 쓸 수 있고, 발행 토픽에는 쓸 수 없습니다.
- `$`로 시작하는 토픽(`$SYS/...`)은 브로커 내부용이라 `#` 구독에 포함되지 않습니다.

### 1-3. QoS (Quality of Service)

| QoS | 의미 | 핸드셰이크 | 쓰임새 |
| --- | --- | --- | --- |
| 0 | 최대 1회(At most once). 유실 가능 | PUBLISH | 주기적으로 계속 오는 센서값 |
| 1 | 최소 1회(At least once). 중복 가능 | PUBLISH → PUBACK | 경보, 명령 등 대부분의 업무 메시지 |
| 2 | 정확히 1회(Exactly once) | PUBLISH → PUBREC → PUBREL → PUBCOMP | 과금처럼 중복이 치명적인 경우 |

중요: QoS는 **발행자→브로커**, **브로커→구독자** 구간이 각각 따로 적용됩니다. 실제 배달 QoS는 `min(발행 QoS, 구독 QoS)`입니다.

### 1-4. Retained 메시지

발행 시 retain 플래그를 켜면 브로커가 **토픽당 마지막 메시지 1개**를 저장합니다. 나중에 구독한 클라이언트도 구독 즉시 그 메시지를 받습니다. "현재 상태"(장비 online/offline, 설정값)에 적합합니다. 빈 페이로드를 retain으로 발행하면 저장된 메시지가 삭제됩니다.

### 1-5. LWT (Last Will and Testament)

클라이언트가 **연결할 때** 브로커에 "유언 메시지"를 등록해 둡니다. 클라이언트가 DISCONNECT 패킷 없이 비정상 종료되면(네트워크 단절, 프로세스 kill, Keep Alive 초과) 브로커가 대신 그 메시지를 발행합니다. 정상 종료 시에는 발행되지 않습니다.

### 1-6. 세션 (Clean Start, Session Expiry)

- MQTT 5에서는 `Clean Start`와 `Session Expiry Interval`로 세션을 제어합니다.
- `Clean Start=false` + `Session Expiry > 0`이면 연결이 끊긴 동안에도 브로커가 구독 정보와 **QoS 1·2 메시지**를 보관했다가 재접속 시 전달합니다.
- `Keep Alive`: 이 시간(초) 동안 패킷이 없으면 클라이언트가 PINGREQ를 보냅니다. 브로커는 1.5배 시간 동안 아무것도 못 받으면 연결이 죽었다고 판단합니다.

### 1-7. MQTT 3.1.1 vs 5.0 주요 차이

- 5.0 추가 기능: 사유 코드(Reason Code), User Properties(메시지 헤더), Session/Message Expiry, Shared Subscription(`$share/그룹/토픽`), Request/Response(Response Topic, Correlation Data), Topic Alias.
- 새 프로젝트라면 5.0을 권장합니다. 이 커리큘럼은 **MQTT 5.0**을 사용합니다.

### 1-8. Spring에서 MQTT를 쓰는 방식

Spring Boot에는 MQTT 전용 스타터가 없습니다. 대신 **Spring Integration MQTT** 모듈을 씁니다. 내부적으로 Eclipse Paho 클라이언트를 감싸며, 다음 세 가지를 제공합니다.

- **Outbound 어댑터**(`Mqttv5PahoMessageHandler`): Spring 메시지를 MQTT로 발행
- **Inbound 어댑터**(`Mqttv5PahoMessageDrivenChannelAdapter`): MQTT 메시지를 구독해 Spring 메시지 채널로 전달
- **ClientManager**(`Mqttv5ClientManager`): 위 둘이 하나의 MQTT 연결을 공유하게 해 줌

### 퀴즈 1

1. 구독 토픽 `farm/+/+/telemetry`와 매칭되는 것을 모두 고르세요. (a) `farm/gh1/temperature/telemetry` (b) `farm/gh1/telemetry` (c) `farm/gh2/humidity/telemetry/raw` (d) `farm//humidity/telemetry`
2. 발행자가 QoS 2로 보내고 구독자가 QoS 0으로 구독했습니다. 구독자가 받는 QoS는?
3. 장비의 현재 상태(online/offline)를 새로 접속한 대시보드가 바로 알 수 있게 하려면 어떤 기능 두 가지를 조합해야 할까요?
4. `farm/#/telemetry`가 잘못된 토픽 필터인 이유는?

### 정답 1

1. **(a), (d)**. (b)는 레벨이 3개라 불일치, (c)는 레벨이 5개라 불일치. (d)는 빈 문자열도 하나의 레벨로 취급되므로 `+`에 매칭됩니다(그래서 토픽에 빈 레벨을 만들지 않는 것이 좋습니다).
2. **QoS 0**. 배달 QoS는 `min(2, 0) = 0`입니다.
3. **Retained 메시지 + LWT**. 접속 시 retained로 `online`을 발행하고, LWT를 retained `offline`으로 등록합니다. 섹션 7에서 직접 구현합니다.
4. `#`는 반드시 토픽 필터의 **마지막 레벨**에만 올 수 있기 때문입니다.

## 섹션 2. Mosquitto 브로커 구축 (09:40–10:20)

Docker Compose로 Mosquitto 2.1.2를 띄우고, 컨테이너에 포함된 `mosquitto_pub`/`mosquitto_sub`로 발행·구독을 체험합니다. Mosquitto 2.x는 설정 파일이 없으면 **localhost 접속만 허용**하므로 반드시 리스너를 설정해야 합니다.

### 2-1. 프로젝트 폴더 만들기

```bash
mkdir -p smartfarm-mqtt/mosquitto/config smartfarm-mqtt/mosquitto/data smartfarm-mqtt/mosquitto/log
cd smartfarm-mqtt
```

이후 Spring Boot 프로젝트도 이 폴더(`smartfarm-mqtt`)를 루트로 생성합니다.

### 2-2. `mosquitto/config/mosquitto.conf`

```
# MQTT 기본 포트
listener 1883

# 학습용: 인증 없이 접속 허용 (운영에서는 절대 금지)
allow_anonymous true

# 세션/retained 메시지를 디스크에 저장해 브로커 재시작 후에도 유지
persistence true
persistence_location /mosquitto/data/

# 로그는 표준 출력으로 (docker logs로 확인)
log_dest stdout
log_type all
```

### 2-3. `compose.yaml`

```yaml
services:
  mosquitto:
    image: eclipse-mosquitto:2.1.2
    container_name: mosquitto
    ports:
      - "1883:1883"
    volumes:
      - ./mosquitto/config:/mosquitto/config
      - ./mosquitto/data:/mosquitto/data
      - ./mosquitto/log:/mosquitto/log
    restart: unless-stopped
```

> `2.1.2` 태그를 못 찾는다는 오류가 나면 `eclipse-mosquitto:2`로 바꾸세요(2.x 최신을 가리킴).

### 2-4. 실행과 확인

```bash
docker compose up -d
docker compose logs -f mosquitto
```

로그에 `Opening ipv4 listen socket on port 1883` 이 보이면 성공입니다.

### 2-5. CLI로 발행/구독 해보기

터미널 A (구독):

```bash
docker exec -it mosquitto mosquitto_sub -V mqttv5 -t 'farm/#' -v
```

터미널 B (발행):

```bash
docker exec -it mosquitto mosquitto_pub -V mqttv5 -t 'farm/gh1/temperature/telemetry' \
  -m '{"deviceId":"gh1-temp-01","value":24.5,"unit":"C","timestamp":"2026-10-20T01:00:00Z"}'
```

터미널 A에 토픽과 메시지가 함께 출력되면 성공입니다. 자주 쓰는 옵션은 다음과 같습니다.

| 옵션 | 의미 |
| --- | --- |
| `-t` | 토픽 (sub에서는 와일드카드 가능) |
| `-m` | 보낼 메시지 |
| `-q` | QoS (0/1/2) |
| `-r` | retained로 발행 |
| `-v` | 수신 시 토픽도 함께 출력 |
| `-V mqttv5` | MQTT 5.0으로 접속 |
| `-i` | 클라이언트 ID 지정 |
| `-c` | Clean Start 끄기(세션 유지) |
| `-x` | 세션 만료 시간(초, MQTT 5) |

### 실습 2

1. retained 메시지를 확인하세요. 먼저 구독 없이 아래를 실행한 뒤, 나중에 `farm/gh1/status`를 구독하면 무엇이 보이나요?

```bash
docker exec -it mosquitto mosquitto_pub -V mqttv5 -t 'farm/gh1/status' -m 'online' -r
```

2. 위에서 저장된 retained 메시지를 삭제하는 명령을 작성하세요.
3. 브로커 내부 상태 토픽 중 현재 접속한 클라이언트 수를 구독하는 명령을 작성하세요.

### 정답 2

1. 구독하자마자 `online`이 즉시 출력됩니다. 브로커가 토픽의 마지막 retained 메시지를 저장해 두었다가 새 구독자에게 보내기 때문입니다.
2. 빈 페이로드를 retained로 발행합니다.

```bash
docker exec -it mosquitto mosquitto_pub -V mqttv5 -t 'farm/gh1/status' -r -n
```

(`-n`은 null(빈) 메시지 발행 옵션입니다.)

3. `$SYS` 토픽을 구독합니다. `$`로 시작하므로 `#`로는 받을 수 없고 명시적으로 지정해야 합니다.

```bash
docker exec -it mosquitto mosquitto_sub -t '$SYS/broker/clients/connected' -v
```

## 섹션 3. Spring Boot 프로젝트 생성과 MQTT 연결 (10:20–11:00)

이 섹션이 끝나면 앱이 브로커에 MQTT 5 연결을 맺고, Mosquitto 로그에 접속 기록이 남습니다. 연결은 `Mqttv5ClientManager` 하나로 만들고, 이후 발행·구독 어댑터가 이 연결을 공유합니다.

### 3-1. Spring Initializr 설정

[start.spring.io](https://start.spring.io)에서 다음과 같이 생성하고 압축을 `smartfarm-mqtt` 폴더에 풉니다(섹션 2에서 만든 `compose.yaml`, `mosquitto/`와 같은 위치).

- Project: Gradle - Groovy / Language: Java / Spring Boot: 4.1.1
- Group: `com.example` / Artifact: `smartfarm-mqtt` / Package name: `com.example.smartfarm`
- Java: 25
- Dependencies: Spring Web, Spring Integration, Testcontainers

생성 후 `build.gradle`을 아래 전문으로 교체합니다. Initializr에는 MQTT 모듈과 Paho 클라이언트가 없으므로 직접 추가합니다.

### 3-2. `settings.gradle`

```groovy
rootProject.name = 'smartfarm-mqtt'
```

### 3-3. `build.gradle`

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // REST API (Boot 4부터 spring-boot-starter-web 대신 webmvc 스타터 사용)
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'

    // Spring Integration + MQTT 모듈 (버전은 Boot BOM이 관리)
    implementation 'org.springframework.boot:spring-boot-starter-integration'
    implementation 'org.springframework.integration:spring-integration-mqtt'

    // MQTT v5 클라이언트: spring-integration-mqtt의 optional 의존성이라 직접 추가
    implementation 'org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5'

    // 테스트
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
    testImplementation 'org.awaitility:awaitility'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

### 3-4. `src/main/resources/application.yml`

오늘 사용할 설정을 미리 모두 넣어 둡니다. 아직 쓰지 않는 값은 해당 섹션에서 설명합니다.

```yaml
spring:
  application:
    name: smartfarm-mqtt

server:
  port: 8080

mqtt:
  broker-url: tcp://localhost:1883
  client-id: smartfarm-server       # 브로커 안에서 고유해야 함
  username:                         # 인증을 켜면 입력 (섹션 10 참고)
  password:
  default-qos: 1
  subscribe-topics:
    - farm/+/+/telemetry
  clean-start: true                 # 섹션 7에서 false로 바꿔 실습
  session-expiry-seconds: 0
  keep-alive-seconds: 10            # LWT 실습을 빠르게 보기 위해 짧게 설정
  status-topic: farm/server/status

smartfarm:
  simulator:
    enabled: false                  # 섹션 8에서 true로 변경
    interval-ms: 5000
  alert:
    temperature-max: 30.0
    humidity-min: 40.0

logging:
  level:
    com.example.smartfarm: DEBUG
```

### 3-5. `SmartfarmMqttApplication.java`

`@ConfigurationPropertiesScan`으로 `@ConfigurationProperties` 레코드를 자동 등록합니다.

```java
package com.example.smartfarm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartfarmMqttApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartfarmMqttApplication.class, args);
    }
}
```

### 3-6. `config/MqttProperties.java`

`mqtt.*` 설정을 타입 안전하게 바인딩합니다. YAML의 `broker-url`은 `brokerUrl`로 자동 매핑됩니다(relaxed binding).

```java
package com.example.smartfarm.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt")
public record MqttProperties(
        String brokerUrl,
        String clientId,
        String username,
        String password,
        int defaultQos,
        List<String> subscribeTopics,
        boolean cleanStart,
        long sessionExpirySeconds,
        int keepAliveSeconds,
        String statusTopic) {
}
```

### 3-7. `config/MqttConnectionConfig.java`

`MqttConnectionOptions`는 Paho의 MQTT 5 연결 옵션이고, `Mqttv5ClientManager`는 Spring이 관리하는 단일 연결입니다. 앱이 시작되면(SmartLifecycle) 자동으로 연결하고, 종료되면 정상 DISCONNECT를 보냅니다.

```java
package com.example.smartfarm.config;

import java.nio.charset.StandardCharsets;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
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
        options.setAutomaticReconnect(true);      // 연결이 끊기면 자동 재접속
        options.setConnectionTimeout(10);         // 초
        options.setKeepAliveInterval(props.keepAliveSeconds());
        options.setCleanStart(props.cleanStart());
        options.setSessionExpiryInterval(props.sessionExpirySeconds());

        if (StringUtils.hasText(props.username())) {
            options.setUserName(props.username());
            options.setPassword(props.password().getBytes(StandardCharsets.UTF_8));
        }
        return options;
    }

    @Bean
    public Mqttv5ClientManager mqttClientManager(MqttConnectionOptions options, MqttProperties props) {
        Mqttv5ClientManager clientManager = new Mqttv5ClientManager(options, props.clientId());
        // 기본값은 파일 저장소라 실행 폴더에 임시 디렉터리가 생김. 학습용으로 메모리 사용
        clientManager.setPersistence(new MemoryPersistence());
        return clientManager;
    }
}
```

### 3-8. 실행 확인

```bash
./gradlew bootRun
```

다른 터미널에서 브로커 로그를 봅니다.

```bash
docker compose logs mosquitto | grep smartfarm-server
```

`New client connected from ... as smartfarm-server (p5, c1, k10)` 같은 줄이 보이면 성공입니다. `p5`는 MQTT 5, `c1`은 Clean Start, `k10`은 Keep Alive 10초를 뜻합니다.

### 퀴즈 3

1. 같은 설정으로 앱을 두 개 동시에 실행하면 어떤 일이 생길까요? 해결책은?
2. `spring-integration-mqtt`만 추가하고 Paho v5 클라이언트를 빼면 어떻게 될까요?
3. 로그의 `k10`이 의미하는 Keep Alive 10초 설정에서, 브로커는 클라이언트가 응답이 없을 때 약 몇 초 후 연결 끊김으로 판단하나요?

### 정답 3

1. 같은 Client ID로 접속하면 브로커가 **기존 연결을 끊고** 새 연결을 받습니다. 두 앱 모두 자동 재접속을 하므로 서로를 계속 끊어내는 "접속 핑퐁"이 발생합니다. 인스턴스마다 Client ID를 다르게 하세요. 예: `client-id: smartfarm-server-${random.uuid}` 또는 환경 변수로 주입.
2. `org.eclipse.paho.mqttv5.client` 클래스를 찾지 못해 컴파일(또는 기동) 에러가 납니다. Paho 클라이언트는 optional 의존성이라 직접 추가해야 합니다.
3. 약 **15초**(Keep Alive × 1.5)입니다.

## 섹션 4. 발행(Publisher) 구현 (11:00–12:00)

REST로 받은 요청을 MQTT로 발행합니다. 구조는 `PublishController → MqttGateway(인터페이스) → mqttOutboundChannel → Mqttv5PahoMessageHandler → 브로커` 입니다. 서비스 코드는 MQTT 클래스를 전혀 몰라도 되고, Gateway 메서드만 호출하면 됩니다.

### 4-1. 핵심 개념: 채널과 Gateway

- **MessageChannel**: Spring Integration 컴포넌트 사이의 파이프. 여기서는 호출 스레드에서 바로 처리하는 `DirectChannel`을 씁니다.
- **@MessagingGateway**: 인터페이스만 선언하면 Spring이 구현체를 만들어 줍니다. 메서드 인자는 메시지 페이로드와 헤더로 변환됩니다.
- **MqttHeaders**: `TOPIC`, `QOS`, `RETAINED` 헤더로 메시지마다 발행 옵션을 지정합니다. 헤더가 없으면 핸들러의 기본값이 쓰입니다.

### 4-2. `config/MqttOutboundConfig.java`

```java
package com.example.smartfarm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.messaging.MessageChannel;

@Configuration
public class MqttOutboundConfig {

    public static final String OUTBOUND_CHANNEL = "mqttOutboundChannel";

    @Bean(name = OUTBOUND_CHANNEL)
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow(Mqttv5ClientManager clientManager, MqttProperties props) {
        Mqttv5PahoMessageHandler handler = new Mqttv5PahoMessageHandler(clientManager);
        // 브로커 응답(PUBACK)을 기다리지 않고 바로 반환. 섹션 8에서 이유를 다룸
        handler.setAsync(true);
        handler.setDefaultQos(props.defaultQos());
        handler.setDefaultRetained(false);

        return IntegrationFlow.from(OUTBOUND_CHANNEL)
                .handle(handler)
                .get();
    }
}
```

### 4-3. `mqtt/MqttGateway.java`

Spring Boot가 `@MessagingGateway` 인터페이스를 자동으로 스캔해 빈으로 등록합니다.

```java
package com.example.smartfarm.mqtt;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import com.example.smartfarm.config.MqttOutboundConfig;

@MessagingGateway(defaultRequestChannel = MqttOutboundConfig.OUTBOUND_CHANNEL)
public interface MqttGateway {

    /** 기본 QoS, retained=false 로 발행 */
    void publish(@Header(MqttHeaders.TOPIC) String topic, @Payload String payload);

    /** QoS와 retained를 직접 지정해 발행 */
    void publish(@Header(MqttHeaders.TOPIC) String topic,
                 @Header(MqttHeaders.QOS) int qos,
                 @Header(MqttHeaders.RETAINED) boolean retained,
                 @Payload String payload);
}
```

### 4-4. `api/PublishRequest.java`

```java
package com.example.smartfarm.api;

public record PublishRequest(String topic, String payload, Integer qos, boolean retained) {
}
```

### 4-5. `api/PublishController.java`

발행 토픽에는 와일드카드를 쓸 수 없으므로 API에서 먼저 막습니다.

```java
package com.example.smartfarm.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.smartfarm.mqtt.MqttGateway;

@RestController
@RequestMapping("/api/mqtt")
public class PublishController {

    private final MqttGateway mqttGateway;

    public PublishController(MqttGateway mqttGateway) {
        this.mqttGateway = mqttGateway;
    }

    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publish(@RequestBody PublishRequest request) {
        validateTopic(request.topic());
        int qos = request.qos() == null ? 1 : request.qos();
        if (qos < 0 || qos > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qos는 0, 1, 2 중 하나여야 합니다.");
        }
        String payload = request.payload() == null ? "" : request.payload();

        mqttGateway.publish(request.topic(), qos, request.retained(), payload);

        return ResponseEntity.accepted().body(Map.of(
                "topic", request.topic(),
                "qos", qos,
                "retained", request.retained()));
    }

    private void validateTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topic은 필수입니다.");
        }
        if (topic.contains("+") || topic.contains("#")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발행 토픽에는 와일드카드(+, #)를 쓸 수 없습니다.");
        }
    }
}
```

### 4-6. 실행 확인

터미널 A에서 구독을 걸고:

```bash
docker exec -it mosquitto mosquitto_sub -V mqttv5 -t 'test/#' -v
```

앱을 재시작한 뒤 터미널 B에서 API를 호출합니다.

```bash
curl -i -X POST http://localhost:8080/api/mqtt/publish \
  -H 'Content-Type: application/json' \
  -d '{"topic":"test/hello","payload":"안녕 MQTT","qos":1,"retained":false}'
```

`202 Accepted` 응답과 함께 터미널 A에 `test/hello 안녕 MQTT`가 출력되면 성공입니다. 와일드카드 토픽으로 호출하면 `400`이 나와야 합니다.

```bash
curl -i -X POST http://localhost:8080/api/mqtt/publish \
  -H 'Content-Type: application/json' \
  -d '{"topic":"test/#","payload":"x"}'
```

### 실습 4

1. `retained=true`로 `test/config` 에 `{"interval":10}`을 발행한 뒤, 새 터미널에서 `test/config`를 구독해 즉시 수신되는지 확인하는 명령을 작성하세요.
2. `MqttGateway`에 "토픽만 받고 페이로드 없이 retained 메시지를 지우는" 메서드를 추가한다면 어떻게 선언할까요?
3. `@MessagingGateway`를 쓰지 않고 서비스에서 직접 채널로 보내려면 어떤 코드를 쓰면 될까요?

### 정답 4

1. 발행 후 구독:

```bash
curl -X POST http://localhost:8080/api/mqtt/publish \
  -H 'Content-Type: application/json' \
  -d '{"topic":"test/config","payload":"{\"interval\":10}","qos":1,"retained":true}'

docker exec -it mosquitto mosquitto_sub -V mqttv5 -t 'test/config' -v
```

2. 빈 페이로드 + retained=true를 보내면 됩니다. 페이로드 인자를 빈 문자열로 고정한 default 메서드로 만들 수 있습니다.

```java
default void clearRetained(String topic) {
    publish(topic, 1, true, "");
}
```

3. `MessageBuilder`로 헤더를 직접 넣어 채널에 보냅니다.

```java
@Service
public class DirectPublishService {

    private final MessageChannel mqttOutboundChannel;

    public DirectPublishService(@Qualifier(MqttOutboundConfig.OUTBOUND_CHANNEL) MessageChannel mqttOutboundChannel) {
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    public void send(String topic, String payload) {
        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader(MqttHeaders.TOPIC, topic)
                .setHeader(MqttHeaders.QOS, 1)
                .build();
        mqttOutboundChannel.send(message);
    }
}
```

(import: `org.springframework.beans.factory.annotation.Qualifier`, `org.springframework.messaging.Message`, `org.springframework.messaging.MessageChannel`, `org.springframework.integration.support.MessageBuilder`, `org.springframework.integration.mqtt.support.MqttHeaders`, `org.springframework.stereotype.Service`)
