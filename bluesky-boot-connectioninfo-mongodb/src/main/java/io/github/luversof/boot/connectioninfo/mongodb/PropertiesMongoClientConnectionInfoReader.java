package io.github.luversof.boot.connectioninfo.mongodb;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.MongoClientConnectionConfig;
import io.github.luversof.boot.security.crypto.factory.TextEncryptorFactories;

/**
 * Properties 파일에서 MongoDB 연결 정보를 읽어오는 Reader
 *
 * <p>기본 설정은 connection-info.mongodb.* 에서 읽으며, 프로젝트의 application.properties에서 override 가능합니다.
 *
 * <p>기본 설정 예시:
 *
 * <pre>
 * # 기본 연결 설정
 * bluesky-boot.connection-info.mongodb.host=localhost
 * bluesky-boot.connection-info.mongodb.port=27017
 * bluesky-boot.connection-info.mongodb.authentication-database=admin
 *
 * # Connection Pool 설정
 * bluesky-boot.connection-info.mongodb.connection-pool.max-size=30
 * bluesky-boot.connection-info.mongodb.connection-pool.min-size=10
 * bluesky-boot.connection-info.mongodb.connection-pool.max-wait-time-ms=120000
 *
 * # Read/Write 설정
 * bluesky-boot.connection-info.mongodb.read-concern-level=local
 * bluesky-boot.connection-info.mongodb.read-preference=secondaryPreferred
 * bluesky-boot.connection-info.mongodb.write-concern.w=W1
 * bluesky-boot.connection-info.mongodb.write-concern.w-timeout-ms=5000
 * bluesky-boot.connection-info.mongodb.write-concern.journal=false
 *
 * # UUID Representation
 * bluesky-boot.connection-info.mongodb.uuid-representation=JAVA_LEGACY
 * </pre>
 *
 * <p>개별 연결 정보:
 *
 * <pre>
 * bluesky-boot.connection-info.mongodb.connection-map.{connectionName}.hosts=host1:port1,host2:port2
 * bluesky-boot.connection-info.mongodb.connection-map.{connectionName}.database=dbname
 * bluesky-boot.connection-info.mongodb.connection-map.{connectionName}.username={text}encryptedUser
 *
 * bluesky-boot.connection-info.mongodb.connection-map.{connectionName}.password={text}encryptedPassword
 * </pre>
 */
public class PropertiesMongoClientConnectionInfoReader
    implements ConnectionInfoReader<MongoClientConnectionConfig> {

  private static final Logger log =
      LoggerFactory.getLogger(PropertiesMongoClientConnectionInfoReader.class);

  protected String readerKey = "properties-mongoclient";

  protected final ConnectionInfoProperties connectionInfoProperties;
  protected final MongoDbDefaultProperties defaultProperties;
  protected final MongoDbConnectionMapProperties connectionMapProperties;

  public PropertiesMongoClientConnectionInfoReader(
      ConnectionInfoProperties connectionInfoProperties,
      MongoDbDefaultProperties defaultProperties,
      MongoDbConnectionMapProperties connectionMapProperties) {
    this.connectionInfoProperties = connectionInfoProperties;
    this.defaultProperties = defaultProperties;
    this.connectionMapProperties = connectionMapProperties;
  }

  @Override
  public String getReaderKey() {
    return readerKey;
  }

  @Override
  public List<MongoClientConnectionConfig> readConnectionConfigList(List<String> connectionList) {
    List<MongoClientConnectionConfig> configList = new ArrayList<>();
    for (String connectionName : connectionList) {
      var config = readConnectionConfig(connectionName);
      if (config != null) {
        configList.add(config);
      }
    }
    return configList;
  }

  /** connection-map 설정 하나를 읽는다. 필수 정보가 없거나 계정 정보가 반쪽이면 null. */
  private MongoClientConnectionConfig readConnectionConfig(String connectionName) {
    // MongoDbConnectionMapProperties에서 설정 가져오기
    MongoDbConnectionMapProperties.MongoConnectionConfig connConfig =
        connectionMapProperties.getConnectionMap().get(connectionName);

    if (connConfig == null || connConfig.getHosts() == null || connConfig.getDatabase() == null) {
      // 필수 정보가 없으면 skip
      return null;
    }

    // username/password는 암호화되어 있을 수 있으므로 복호화
    var encryptor = TextEncryptorFactories.getDelegatingTextEncryptor();
    String username =
        connConfig.getUsername() == null ? null : encryptor.decrypt(connConfig.getUsername());
    String password =
        connConfig.getPassword() == null ? null : encryptor.decrypt(connConfig.getPassword());
    if ((username == null) != (password == null)) {
      // 한쪽만 있으면 인증 없이 연결되어 첫 쿼리에서야 인증 오류가 나므로 설정 누락을 바로 알린다
      log.warn(
          "skip mongodb connection ({}): username and password must be set together",
          connectionName);
      return null;
    }

    MongoClientConnectionConfig config = new MongoClientConnectionConfig();
    config.setConnection(connectionName);
    config.setDatabase(connConfig.getDatabase());
    config.setUserName(username);
    config.setPassword(password);
    config.setConnectionString(buildConnectionString(connConfig, username, password));
    return config;
  }

  /** 드라이버는 '+' 를 공백으로 풀지 않으므로 URLEncoder 의 '+' 를 %20 으로 바꾼다. */
  private static String encodeUserInfo(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * MongoDB connectionString을 생성합니다.
   *
   * @param connConfig MongoDB 연결 설정
   * @param username 복호화한 사용자명
   * @param password 복호화한 비밀번호
   * @return MongoDB connectionString
   */
  String buildConnectionString(
      MongoDbConnectionMapProperties.MongoConnectionConfig connConfig,
      String username,
      String password) {
    StringBuilder connectionString = new StringBuilder("mongodb://");

    // username/password가 있으면 추가. '@' ':' '/' '%' 가 든 비밀번호도 되도록 URL 인코딩한다(드라이버가 디코딩한다)
    if (username != null && password != null) {
      connectionString
          .append(encodeUserInfo(username))
          .append(":")
          .append(encodeUserInfo(password))
          .append("@");
    }

    // hosts 추가 (이미 포트를 포함한 형식: "host:port" 또는 "host1:port1,host2:port2")
    connectionString.append(connConfig.getHosts());

    // database 추가
    connectionString.append("/").append(connConfig.getDatabase());

    // authSource 추가
    if (username != null) {
      connectionString.append("?authSource=").append(defaultProperties.getAuthenticationDatabase());
    }

    return connectionString.toString();
  }
}
