# Bluesky Boot AutoConfigure ConnectionInfo JDBC

Spring Boot 자동 구성 모듈로, 데이터베이스에서 JDBC DataSource 연결 정보를 동적으로 로드하고 관리하는 기능을 제공합니다.

## 개요

이 모듈은 다음과 같은 기능을 제공합니다:

- **동적 DataSource 연결 정보 로드**: 데이터베이스에서 연결 정보를 읽어와 런타임에 DataSource 생성
- **다중 데이터베이스 지원**: MariaDB, PostgreSQL, SQL Server 지원
- **HikariCP 통합**: HikariDataSource를 통한 고성능 커넥션 풀링
- **암호화된 자격증명**: 비밀번호 등 민감한 정보의 암호화 저장 및 복호화
- **Spring Boot 자동 구성**: 조건부 Bean 등록으로 간편한 설정

## 아키텍처

### 핵심 컴포넌트

1. **ConnectionConfigReader**: 데이터베이스에서 연결 정보를 읽는 인터페이스
2. **ConnectionInfoLoader**: 연결 정보를 기반으로 실제 DataSource를 생성하는 인터페이스
3. **ConnectionInfoRegistry**: 생성된 연결 정보들을 관리하는 레지스트리

### 클래스 구조

```
AbstractDataSourceConnectionConfigReader
├── MariaDbDataSourceConnectionConfigReader
├── PostgreSQLDataSourceConnectionConfigReader
└── SQLServerDataSourceConnectionConfigReader

AbstractDataSourceConnectionInfoLoader
└── HikariDataSourceConnectionInfoLoader
```

## 설정

### application.properties 설정 예시

```properties
# MariaDB DataSource Reader 활성화
bluesky-boot.connection-config.readers.mariadb-datasource.enabled=true
bluesky-boot.connection-config.readers.mariadb-datasource.properties.url=jdbc:mariadb://localhost:3306/connection_info
bluesky-boot.connection-config.readers.mariadb-datasource.properties.username={cipher}encrypted_username
bluesky-boot.connection-config.readers.mariadb-datasource.properties.password={cipher}encrypted_password

# HikariDataSource Loader 활성화
bluesky-boot.connection-info.loaders.hikaridatasource.enabled=true
bluesky-boot.connection-info.loaders.hikaridatasource.connections.app1=connection1,connection2
bluesky-boot.connection-info.loaders.hikaridatasource.connections.app2=connection3,connection4
# (선택) 모든 HikariDataSource에 적용할 HikariConfig 속성. 이름은 HikariConfig 속성 그대로(camelCase) 쓴다.
# 지정하지 않으면 HikariCP 기본값(connection마다 연결 10개 유지)을 쓴다.
bluesky-boot.connection-info.loaders.hikaridatasource.properties.maximumPoolSize=5
bluesky-boot.connection-info.loaders.hikaridatasource.properties.minimumIdle=1
```

### 데이터베이스 스키마

연결 정보를 저장할 테이블이 필요합니다:

```sql
CREATE TABLE DataSourceConnectionConfig (
    connection VARCHAR(255) PRIMARY KEY,
    url VARCHAR(512) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    extradata TEXT
);
```

### 데이터 예시

```sql
INSERT INTO DataSourceConnectionConfig (connection, url, username, password) VALUES
('app-db', 'jdbc:mariadb://localhost:3306/app_db', '{cipher}encrypted_user', '{cipher}encrypted_pass'),
('report-db', 'jdbc:postgresql://localhost:5432/report_db', '{cipher}encrypted_user', '{cipher}encrypted_pass');
```

## 사용법

### 1. 의존성 추가

```xml
<dependency>
    <groupId>io.github.luversof</groupId>
    <artifactId>bluesky-boot-autoconfigure-connectioninfo-jdbc</artifactId>
    <version>${currentVersion}</version>
</dependency>
```

### 2. DataSource 주입 및 사용

```java
@Service
public class DatabaseService {
    
    private final ConnectionInfoRegistry<HikariDataSource> connectionRegistry;
    
    public DatabaseService(ConnectionInfoRegistry<HikariDataSource> connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }
    
    public void useDatabase(String connectionName) {
        Optional<HikariDataSource> dataSource = connectionRegistry.getConnectionInfoList()
            .stream()
            .filter(info -> info.getConnectionInfoKey().getConnection().equals(connectionName))
            .map(ConnectionInfo::getConnection)
            .findFirst();
            
        dataSource.ifPresent(ds -> {
            // DataSource 사용
            try (Connection conn = ds.getConnection()) {
                // 데이터베이스 작업 수행
            }
        });
    }
}
```

### 3. JdbcTemplate과 함께 사용

```java
@Configuration
public class DatabaseConfiguration {
    
    @Bean
    @Primary
    public JdbcTemplate primaryJdbcTemplate(ConnectionInfoRegistry<HikariDataSource> registry) {
        DataSource primaryDataSource = registry.getConnectionInfoList()
            .stream()
            .filter(info -> "primary-db".equals(info.getConnectionInfoKey().getConnection()))
            .map(ConnectionInfo::getConnection)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Primary database not found"));
            
        return new JdbcTemplate(primaryDataSource);
    }
}
```

## 지원 데이터베이스

### MariaDB
- **Reader Key**: `mariadb-datasource`
- **Driver**: `org.mariadb.jdbc.Driver`
- **URL 형식**: `jdbc:mariadb://host:port/database`

### PostgreSQL
- **Reader Key**: `postgresql-datasource`
- **Driver**: `org.postgresql.Driver`
- **URL 형식**: `jdbc:postgresql://host:port/database`

### SQL Server
- **Reader Key**: `sqlserver-datasource`
- **Driver**: `com.microsoft.sqlserver.jdbc.SQLServerDriver`
- **URL 형식**: `jdbc:sqlserver://host:port;databaseName=database`

## 보안

### 암호화된 자격증명

이 모듈은 `bluesky-boot-crypto`와 통합되어 데이터베이스 자격증명을 암호화하여 저장할 수 있습니다:

```properties
# 암호화된 값 사용
bluesky-boot.connection-config.readers.mariadb-datasource.properties.username={cipher}AQB...
bluesky-boot.connection-config.readers.mariadb-datasource.properties.password={cipher}AQC...
```

### 설정 파일 보안

- 민감한 정보는 반드시 암호화하여 저장
- 환경변수나 외부 설정 서버 사용 권장
- 프로덕션 환경에서는 별도의 Key Management Service 사용

## 확장

### 커스텀 데이터베이스 지원

새로운 데이터베이스를 지원하려면 `AbstractDataSourceConnectionConfigReader`를 확장합니다:

```java
@Component
public class CustomDatabaseConnectionConfigReader 
    extends AbstractDataSourceConnectionConfigReader<DataSourceConnectionConfig> {
    
    @Getter
    protected String readerKey = "custom-database";
    
    @Getter 
    protected Driver readerDriver = new com.custom.jdbc.Driver();
    
    public CustomDatabaseConnectionConfigReader(ConnectionInfoProperties properties) {
        super(properties);
    }
    
    @Override
    protected RowMapper<DataSourceConnectionConfig> getConnectionConfigRowMapper() {
        return new DataClassRowMapper<>(DataSourceConnectionConfig.class);
    }
}
```

### 커스텀 DataSource 구현

다른 DataSource 구현체를 사용하려면 `AbstractDataSourceConnectionInfoLoader`를 확장합니다:

```java
@Component
public class CustomDataSourceConnectionInfoLoader 
    extends AbstractDataSourceConnectionInfoLoader<CustomDataSource, DataSourceConnectionConfig> {
    
    @Getter
    protected String loaderKey = "customdatasource";
    
    @Override
    protected ConnectionInfo<CustomDataSource> createConnectionInfo(
            DataSourceConnectionConfig connectionConfig) {
        // 커스텀 DataSource 생성 로직
        CustomDataSource dataSource = new CustomDataSource();
        // 설정...
        
        return new ConnectionInfo<>(
            new ConnectionInfoKey(getLoaderKey(), connectionConfig.getConnection()),
            dataSource
        );
    }
}
```

## 모니터링 및 관리

### DevCheck 통합

개발 환경에서 연결 정보 상태를 확인할 수 있습니다:

```
GET /devcheck/connection-info
```

### 로깅

연결 정보 로드 과정은 DEBUG 레벨로 로깅됩니다:

```properties
logging.level.io.github.luversof.boot.connectioninfo.jdbc=DEBUG
```

## 예외 처리

### 일반적인 문제와 해결방법

1. **연결 정보를 찾을 수 없음**
   ```
   cannot find database connection (connection-name)
   ```
   - 데이터베이스에 해당 connection 데이터가 있는지 확인
   - 설정된 연결 이름이 정확한지 확인

2. **데이터베이스 연결 실패**
   - Reader용 데이터베이스 연결 정보 확인
   - 네트워크 연결 상태 확인
   - 자격증명 복호화 확인

3. **Driver 클래스를 찾을 수 없음**
   - 필요한 JDBC Driver 의존성이 추가되어 있는지 확인
   - 클래스패스에 올바른 Driver가 포함되어 있는지 확인

## 요구사항

- Java 17+
- Spring Boot 3.x
- 지원 데이터베이스의 JDBC Driver
- bluesky-boot-connectioninfo 모듈
- bluesky-boot-crypto 모듈 (암호화 기능 사용시)

## 라이센스

이 프로젝트는 Apache License 2.0 하에 배포됩니다.