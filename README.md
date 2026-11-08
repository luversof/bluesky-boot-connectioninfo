# Bluesky Boot AutoConfigure ConnectionInfo

Spring Boot 환경에서 다양한 외부 시스템(데이터베이스, MongoDB 등)과의 연결 정보를 동적으로 로드하고 관리하는 자동 구성 모듈입니다.

## 개요

이 모듈은 연결 정보를 메타데이터로 저장하고, 런타임에 동적으로 로드하여 실제 연결 객체를 생성하는 패턴을 제공합니다. 이를 통해 하드코딩된 연결 정보 없이 유연하고 확장 가능한 연결 관리를 할 수 있습니다.

## 핵심 아키텍처

### 주요 구성 요소와 관계

```
┌─────────────────────────┐    reads from    ┌──────────────────────┐
│   ConnectionConfig      │ ◄────────────── │  ConnectionInfoReader │
│   (연결 설정 정보)        │                  │  (설정 정보 리더)       │
└─────────────────────────┘                  └──────────────────────┘
           │                                              │
           │ used by                                      │ injected into
           ▼                                              ▼
┌─────────────────────────┐    creates      ┌──────────────────────┐
│   ConnectionInfo<T>     │ ◄────────────── │  ConnectionInfoLoader │
│   (실제 연결 객체)        │                  │  (연결 객체 로더)       │
└─────────────────────────┘                  └──────────────────────┘
           │                                              │
           │ managed by                                   │ loads
           ▼                                              │
┌─────────────────────────┐                              │
│ ConnectionInfoRegistry  │ ◄────────────────────────────┘
│ (연결 정보 레지스트리)     │
└─────────────────────────┘
```

### 1. ConnectionConfig
```java
public interface ConnectionConfig {
    String getConnection();
}
```
- **역할**: 연결에 필요한 기본 설정 정보를 정의하는 인터페이스
- **특징**: 각 연결 타입별로 구체적인 구현체가 존재 (DataSourceConnectionConfig, MongoClientConnectionConfig 등)
- **책임**: 연결 이름과 연결에 필요한 메타데이터 제공

### 2. ConnectionInfoReader<C extends ConnectionConfig>
```java
public interface ConnectionInfoReader<C extends ConnectionConfig> {
    String getReaderKey();
    List<C> readConnectionConfigList(List<String> connectionList);
}
```
- **역할**: 외부 저장소(DB, 파일 등)에서 연결 설정 정보를 읽어오는 인터페이스
- **특징**: 다양한 저장소 타입을 지원할 수 있도록 확장 가능
- **책임**: 연결 이름 목록을 받아 해당하는 ConnectionConfig 객체들을 반환

### 3. ConnectionInfo<T>
```java
@Data
@AllArgsConstructor
public class ConnectionInfo<T> {
    private final ConnectionInfoKey key;
    private final T connection;
}
```
- **역할**: 실제 연결 객체와 식별 키를 담는 컨테이너
- **특징**: 제네릭 타입으로 다양한 연결 객체 타입 지원 (DataSource, MongoClient 등)
- **책임**: 생성된 연결 객체를 키와 함께 관리

### 4. ConnectionInfoKey
```java
public record ConnectionInfoKey(String loaderKey, String connectionKey) {}
```
- **역할**: 연결 정보를 고유하게 식별하는 키
- **구성**: loaderKey (로더 타입) + connectionKey (연결 이름)
- **특징**: Java Record로 구현된 불변 객체

### 5. ConnectionInfoLoader<T, C extends ConnectionConfig>
```java
public interface ConnectionInfoLoader<T, C extends ConnectionConfig> {
    String getLoaderKey();
    List<ConnectionInfoReader<C>> getConnectionInfoReaderList();
    List<ConnectionInfo<T>> load();
    List<ConnectionInfo<T>> load(List<String> connectionList);
}
```
- **역할**: ConnectionInfoReader들을 활용하여 실제 연결 객체를 생성하고 관리
- **특징**: 여러 Reader를 주입받아 다양한 소스에서 설정 정보 수집 가능
- **책임**: 설정 정보를 바탕으로 실제 사용 가능한 연결 객체 생성

### 6. ConnectionInfoRegistry<T>
```java
@FunctionalInterface
public interface ConnectionInfoRegistry<T> {
    List<ConnectionInfo<T>> getConnectionInfoList();
    // ... default methods
}
```
- **역할**: 생성된 연결 정보들을 중앙에서 관리하는 레지스트리
- **특징**: 함수형 인터페이스로 구현이 간단함
- **책임**: 연결 정보 조회, 추가, 검색 기능 제공

## 동작 흐름

1. **설정 단계**: Properties에서 reader와 loader 설정 정보 로드
2. **Reader 단계**: ConnectionInfoReader가 외부 저장소에서 ConnectionConfig 정보 읽기
3. **Loader 단계**: ConnectionInfoLoader가 Reader들을 활용해 ConnectionInfo 객체 생성
4. **Registry 단계**: 생성된 ConnectionInfo들을 Registry에 등록
5. **사용 단계**: 애플리케이션에서 Registry를 통해 연결 객체 조회 및 사용

## 설정 구조

### application.yml 설정 예시

```yaml
bluesky-boot:
  connection-info:
    readers:
      mariadb-datasource:
        enabled: true
        properties:
          url: "jdbc:mariadb://localhost:3306/connection_meta"
          username: "{cipher}encrypted_username"
          password: "{cipher}encrypted_password"
      mongodb-mongoclient:
        enabled: true
        properties:
          connectionString: "mongodb://localhost:27017"
          database: "connection_meta"
    
    loaders:
      hikaridatasource:
        enabled: true
        connections:
          app1: ["db-primary", "db-secondary"]
          app2: ["db-reporting", "db-analytics"]
      mongoclient:
        enabled: true
        connections:
          service1: ["mongo-primary", "mongo-cache"]
```

### 설정 구조 설명

- **readers**: 연결 설정 정보를 읽어올 소스 정의
  - `enabled`: 해당 reader 활성화 여부
  - `properties`: reader별 연결 정보 (메타데이터 저장소 접근 정보)

- **loaders**: 실제 연결 객체를 생성할 loader 정의
  - `enabled`: 해당 loader 활성화 여부
  - `connections`: 애플리케이션별 사용할 연결 이름 목록

## 모듈 구성

### 1. 핵심 모듈
- **bluesky-boot-connectioninfo**: 핵심 인터페이스와 모델 정의
- **bluesky-boot-autoconfigure-connectioninfo**: Spring Boot 자동 구성

### 2. 구현 모듈
- **bluesky-boot-connectioninfo-jdbc**: JDBC DataSource 지원
- **bluesky-boot-autoconfigure-connectioninfo-jdbc**: JDBC 자동 구성
- **bluesky-boot-connectioninfo-mongodb**: MongoDB 지원
- **bluesky-boot-autoconfigure-connectioninfo-mongodb**: MongoDB 자동 구성

## 사용 예시

### 1. 기본 사용법

```java
@Service
public class DatabaseService {
    
    private final ConnectionInfoRegistry<HikariDataSource> dataSourceRegistry;
    
    public DatabaseService(ConnectionInfoRegistry<HikariDataSource> dataSourceRegistry) {
        this.dataSourceRegistry = dataSourceRegistry;
    }
    
    public void executeQuery(String connectionName) {
        ConnectionInfoKey key = new ConnectionInfoKey("hikaridatasource", connectionName);
        ConnectionInfo<HikariDataSource> connectionInfo = dataSourceRegistry.getConnectionInfo(key);
        
        try (Connection conn = connectionInfo.getConnection().getConnection()) {
            // 데이터베이스 작업 수행
        }
    }
}
```

### 2. 특정 연결 정보로 JdbcTemplate 생성

```java
@Configuration
public class DatabaseConfiguration {
    
    @Bean
    @Primary
    public JdbcTemplate primaryJdbcTemplate(ConnectionInfoRegistry<HikariDataSource> registry) {
        ConnectionInfoKey key = new ConnectionInfoKey("hikaridatasource", "db-primary");
        HikariDataSource dataSource = registry.getConnectionInfo(key).getConnection();
        return new JdbcTemplate(dataSource);
    }
}
```

### 3. 동적 연결 선택

```java
@Service
public class MultiTenantService {
    
    private final ConnectionInfoRegistry<HikariDataSource> registry;
    
    public void processForTenant(String tenantId) {
        String connectionName = "tenant-" + tenantId;
        ConnectionInfoKey key = new ConnectionInfoKey("hikaridatasource", connectionName);
        
        try {
            ConnectionInfo<HikariDataSource> connectionInfo = registry.getConnectionInfo(key);
            // 테넌트별 데이터베이스 작업
        } catch (RuntimeException e) {
            // 연결 정보가 없는 경우 처리
            log.warn("Connection not found for tenant: {}", tenantId);
        }
    }
```

## ConnectionInfoUtil (HikariDataSource / MongoClient 사용 사례)

`ConnectionInfoUtil.getConnection(String connectionKey, Class<T> connectionType)`은 `connectionType`의 `ConnectionInfoRegistry`와
`ConnectionInfoLoader`에서만 `connectionKey`를 찾습니다. `connectionType`에는 Registry에 등록된 타입을 그대로 지정합니다
(JDBC는 `HikariDataSource.class`, MongoDB는 `MongoClient.class`). 상위 타입(예: `DataSource.class`)으로는 Registry를 찾지 못해 `null`이 반환됩니다.

동작 요약:
- 해당 타입의 레지스트리에서 `connectionKey`를 찾으면 첫 번째 연결 객체 반환. `connectionKey`는 대소문자를 구분하지 않습니다.
- 못 찾으면 해당 타입을 만드는 `ConnectionInfoLoader`들에 대해 `load(List.of(connectionKey))`를 시도(지연 로드). 같은 키의 동시 요청은 한 번만 로드합니다.
- 로드 성공 시 결과를 해당 타입의 레지스트리에 추가하고 연결 객체 반환. 실패 시 `null` 반환.

구체적 예시:

```java
// HikariDataSource 반환 예시
HikariDataSource ds = ConnectionInfoUtil.getConnection("db-primary", HikariDataSource.class);
if (ds != null) {
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    // 데이터베이스 작업
}

// MongoClient 반환 예시
MongoClient client = ConnectionInfoUtil.getConnection("mongo-primary", MongoClient.class);
if (client != null) {
    MongoDatabase db = client.getDatabase("mydb");
    // Mongo 작업
}
```

주의사항:
- 타입 없는 `getConnection(String connectionKey)`는 모든 레지스트리와 로더를 검색합니다. 반환 타입을 검사하지 않아 `ClassCastException`이 날 수 있고,
  지연 로드 결과가 다른 타입의 레지스트리에 추가될 수 있습니다. JDBC와 MongoDB를 함께 쓰면 타입을 지정하는 메서드를 사용하세요.
- 초기화 시점: 호출 시점에 해당 loader/registry의 자동구성이 완료되어 있어야 합니다. 초기화가 끝나기 전 호출하면 `null`이 반환될 수 있습니다.

## 확장 방법

### 1. 새로운 연결 타입 지원

```java
// 1. ConnectionConfig 구현
@Data
public class RedisConnectionConfig implements ConnectionConfig {
    private String connection;
    private String host;
    private int port;
    private String password;
}

// 2. ConnectionInfoReader 구현
@Component
public class RedisConnectionInfoReader implements ConnectionInfoReader<RedisConnectionConfig> {
    
    @Override
    public String getReaderKey() {
        return "redis";
    }
    
    @Override
    public List<RedisConnectionConfig> readConnectionConfigList(List<String> connectionList) {
        // Redis 연결 정보 읽기 구현
        return Collections.emptyList();
    }
}

// 3. ConnectionInfoLoader 구현
@Component
public class RedisConnectionInfoLoader implements ConnectionInfoLoader<JedisPool, RedisConnectionConfig> {
    
    @Override
    public String getLoaderKey() {
        return "redis";
    }
    
    @Override
    public List<ConnectionInfo<JedisPool>> load() {
        // JedisPool 생성 로직 구현
        return Collections.emptyList();
    }
    
    // ... 기타 메서드 구현
}
```

### 2. 커스텀 Reader 구현

```java
@Component
public class FileBasedConnectionInfoReader implements ConnectionInfoReader<DataSourceConnectionConfig> {
    
    @Override
    public String getReaderKey() {
        return "file-datasource";
    }
    
    @Override
    public List<DataSourceConnectionConfig> readConnectionConfigList(List<String> connectionList) {
        // 파일에서 연결 정보 읽기
        return readFromFile(connectionList);
    }
    
    private List<DataSourceConnectionConfig> readFromFile(List<String> connectionList) {
        // JSON, YAML, Properties 파일 등에서 읽기 구현
        return Collections.emptyList();
    }
}
```

## 보안 고려사항

### 1. 암호화된 자격증명 — `bluesky-boot-crypto` 사용

이 프로젝트는 `bluesky-boot-crypto`를 사용해 properties 값(예: DB 사용자명/비밀번호)을 암호화하여 안전하게 관리할 수 있습니다. 주요 요점은 다음과 같습니다:

- 암호화된 값은 `{encryptorId}암호문` 형식으로 저장됩니다(예: `{text}...`).
- 암호화된 값을 포함한 properties 파일을 그대로 커밋/배포해도 되며, 애플리케이션 시작 시 복호화되어 런타임 프로퍼티로 사용됩니다.
- `bluesky-boot-crypto`를 의존성으로 추가하면 제공되는 `DecryptEnvironmentPostProcessor`가 자동으로 실행되어 환경 변수를 복호화합니다.

간단 사용 절차

1. `pom.xml`에 의존성 추가:

```xml
<dependency>
    <groupId>io.github.luversof</groupId>
    <artifactId>bluesky-boot-crypto</artifactId>
    <version>${currentVersion}</version>
</dependency>
```

2. (선택) 커스텀 `TextEncryptor`를 등록하려면 애플리케이션 시작 전에 `TextEncryptorFactories.createDelegatingTextEncryptor(...)`로 등록하세요.

```java
public static void main(String[] args) {
        // 예: 기본 provided encryptor 대신 키를 지정한 encryptor 등록
        TextEncryptorFactories.createDelegatingTextEncryptor("myid", myEncryptor);
        SpringApplication.run(Application.class, args);
}
```

3. 값을 암호화하여 properties에 넣습니다(암호문은 `{id}...` 형식). 예: `application.yml`에서 `connection-info` 설정:

```yaml
bluesky-boot:
    connection-info:
        readers:
            mariadb-datasource:
                enabled: true
                properties:
                    url: "jdbc:mariadb://localhost:3306/connection_meta"
                    username: "{text}07d9e2cf0928..."
                    password: "{text}a4c1ae078761..."
```

주의 및 고급 사용법

- `@PropertySource`로 로드되는 파일에서 암호화를 사용하려면 `DecryptPropertySourceFactory`를 `factory`로 지정해야 합니다:

```java
@Configuration
@PropertySource(value = "classpath:some-${profile}.properties", factory = DecryptPropertySourceFactory.class)
public class SomeConfiguration { }
```

- `/actuator/env`(actuator enabled)에서 복호화된 값이 어떻게 처리되는지 확인할 수 있습니다.
- `DecryptEnvironmentPostProcessor`는 Spring Boot의 `prepareEnvironment` 단계에서 동작하므로, 일부 `@PropertySource` 기반 로딩은 그 이후에 발생할 수 있습니다. 이 경우 `DecryptPropertySourceFactory` 사용을 권장합니다.

예시: ConnectionInfo 리더에 암호화된 자격증명 사용

```yaml
bluesky-boot:
    connection-info:
        readers:
            mariadb-datasource:
                enabled: true
                properties:
                    url: "jdbc:mariadb://localhost:3306/connection_meta"
                    username: "{text}..."
                    password: "{text}..."
```

이렇게 설정하면 리더 구현은 기존과 동일하게 `environment.getProperty(...)`로 값을 읽으면 되고, 런타임에서는 복호화된 평문 값을 받게 됩니다.


### 2. 환경별 분리
- 개발/스테이징/운영 환경별로 다른 메타데이터 저장소 사용
- Spring Profiles를 활용한 환경별 설정 관리

## 모니터링 및 디버깅

### 1. 연결 정보 상태 확인
```java
@RestController
public class ConnectionInfoController {
    
    private final ConnectionInfoRegistry<HikariDataSource> registry;
    
    @GetMapping("/connection-info/status")
    public Map<String, Object> getConnectionStatus() {
        List<ConnectionInfo<HikariDataSource>> connections = registry.getConnectionInfoList();
        
        return connections.stream()
            .collect(Collectors.toMap(
                info -> info.getKey().toString(),
                info -> Map.of(
                    "active", info.getConnection().getHikariPoolMXBean().getActiveConnections(),
                    "idle", info.getConnection().getHikariPoolMXBean().getIdleConnections()
                )
            ));
    }
}
```

### 2. 로깅 설정
```properties
# 연결 정보 로딩 과정 로깅
logging.level.io.github.luversof.boot.connectioninfo=DEBUG

# 특정 구현체별 상세 로깅
logging.level.io.github.luversof.boot.connectioninfo.jdbc=TRACE
logging.level.io.github.luversof.boot.connectioninfo.mongodb=TRACE
```

## 주요 변경사항 (리팩토링 후)

1. **명명 개선**: `ConnectionConfigReader` → `ConnectionInfoReader`
2. **구조 최적화**: `ConnectionInfoKey`가 record 타입으로 구현
3. **설정 분리**: Properties가 `readers`와 `loaders`로 명확히 분리
4. **타입 안전성**: 제네릭 활용으로 컴파일 타임 타입 체크 강화

## 장점

1. **유연성**: 런타임에 연결 정보 변경 가능
2. **확장성**: 새로운 연결 타입을 쉽게 추가 가능
3. **보안성**: 암호화된 자격증명 지원
4. **중앙화**: 모든 연결 정보를 중앙에서 관리
5. **느슨한 결합**: Reader와 Loader의 독립적 구현 가능
6. **타입 안전성**: 제네릭을 활용한 컴파일 타임 타입 체크

## 주의사항

1. **메타데이터 저장소 의존성**: Reader용 데이터베이스가 항상 사용 가능해야 함
2. **초기화 순서**: ConnectionInfo 로드가 완료된 후 애플리케이션 로직 실행
3. **성능**: 대량의 연결 정보가 있을 경우 초기화 시간 고려 필요
4. **장애 처리**: Reader나 메타데이터 저장소 장애 시 대응 방안 필요

## 라이센스

Apache License 2.0