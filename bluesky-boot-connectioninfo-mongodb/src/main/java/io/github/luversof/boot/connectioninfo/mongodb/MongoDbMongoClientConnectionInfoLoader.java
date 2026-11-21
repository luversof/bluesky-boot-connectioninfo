package io.github.luversof.boot.connectioninfo.mongodb;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.UuidRepresentation;
import org.springframework.util.StringUtils;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadConcern;
import com.mongodb.ReadConcernLevel;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.connection.ConnectionPoolSettings;

import io.github.luversof.boot.connectioninfo.AbstractConnectionInfoLoader;
import io.github.luversof.boot.connectioninfo.ConnectionInfo;
import io.github.luversof.boot.connectioninfo.ConnectionInfoKey;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.MongoClientConnectionConfig;

/** MongoClient를 만드는 Loader. 만든 MongoClient는 앱 종료 시 닫혀 서버측 logical session을 반납(endSessions)한다. */
public class MongoDbMongoClientConnectionInfoLoader
    extends AbstractConnectionInfoLoader<MongoClient, MongoClientConnectionConfig> {

  protected String loaderKey = "mongoclient";

  // connection-info.mongodb.* 기본 설정(pool/read/write 등). MongoClient 생성 시 적용한다.
  protected final MongoDbDefaultProperties defaultProperties;

  public MongoDbMongoClientConnectionInfoLoader(
      ConnectionInfoProperties connectionInfoProperties,
      List<ConnectionInfoReader<MongoClientConnectionConfig>> connectionInfoReaderList) {
    this(connectionInfoProperties, connectionInfoReaderList, new MongoDbDefaultProperties());
  }

  public MongoDbMongoClientConnectionInfoLoader(
      ConnectionInfoProperties connectionInfoProperties,
      List<ConnectionInfoReader<MongoClientConnectionConfig>> connectionInfoReaderList,
      MongoDbDefaultProperties defaultProperties) {
    super(connectionInfoProperties, connectionInfoReaderList);
    this.defaultProperties =
        (defaultProperties != null) ? defaultProperties : new MongoDbDefaultProperties();
  }

  @Override
  public String getLoaderKey() {
    return loaderKey;
  }

  @Override
  protected ConnectionInfo<MongoClient> createConnectionInfo(
      MongoClientConnectionConfig connectionConfig) {
    var mongoClient = MongoClients.create(buildMongoClientSettings(connectionConfig));
    return new ConnectionInfo<>(
        new ConnectionInfoKey(getLoaderKey(), connectionConfig.getConnection()), mongoClient);
  }

  /**
   * connectionString을 기반으로 MongoClientSettings를 구성한다.
   *
   * <p>connection-info.mongodb.* 의 pool/read/write 설정은 connectionString에 해당 옵션이 명시되지 않은 경우에만
   * 적용한다(connectionString의 옵션이 우선). pool 설정이 적용되지 않으면 드라이버 기본값 (maxPoolSize=100,
   * maxConnectionIdleTime=무제한)이 사용되어 커넥션/세션이 과도하게 유지된다.
   */
  protected MongoClientSettings buildMongoClientSettings(
      MongoClientConnectionConfig connectionConfig) {
    var connectionString = new ConnectionString(connectionConfig.getConnectionString());
    var settingsBuilder = MongoClientSettings.builder().applyConnectionString(connectionString);

    var connectionPoolProperties = this.defaultProperties.getConnectionPool();
    if (connectionPoolProperties != null) {
      settingsBuilder.applyToConnectionPoolSettings(poolBuilder ->
          applyConnectionPool(poolBuilder, connectionString, connectionPoolProperties));
    }

    if (connectionString.getReadPreference() == null) {
      applyQuietly(
          connectionConfig,
          "read-preference",
          this.defaultProperties.getReadPreference(),
          value -> settingsBuilder.readPreference(ReadPreference.valueOf(value)));
    }

    if (connectionString.getReadConcern() == null) {
      applyQuietly(
          connectionConfig,
          "read-concern-level",
          this.defaultProperties.getReadConcernLevel(),
          value ->
              settingsBuilder.readConcern(new ReadConcern(ReadConcernLevel.fromString(value))));
    }

    if (connectionString.getWriteConcern() == null) {
      applyWriteConcern(connectionConfig, settingsBuilder);
    }

    if (connectionString.getUuidRepresentation() == null) {
      applyQuietly(
          connectionConfig,
          "uuid-representation",
          this.defaultProperties.getUuidRepresentation(),
          value -> settingsBuilder.uuidRepresentation(UuidRepresentation.valueOf(value)));
    }

    // 서버측 currentOp/세션 추적 시 어떤 connection이 만든 클라이언트인지 식별하기 위해 지정한다.
    if (connectionString.getApplicationName() == null) {
      settingsBuilder.applicationName(connectionConfig.getConnection());
    }

    return settingsBuilder.build();
  }

  private void applyConnectionPool(
      ConnectionPoolSettings.Builder poolBuilder,
      ConnectionString connectionString,
      MongoDbDefaultProperties.ConnectionPool connectionPoolProperties) {
    if (connectionString.getMaxConnectionPoolSize() == null
        && connectionPoolProperties.getMaxSize() != null) {
      poolBuilder.maxSize(connectionPoolProperties.getMaxSize());
    }
    if (connectionString.getMinConnectionPoolSize() == null
        && connectionPoolProperties.getMinSize() != null) {
      poolBuilder.minSize(connectionPoolProperties.getMinSize());
    }
    if (connectionString.getMaxWaitTime() == null
        && connectionPoolProperties.getMaxWaitTimeMs() != null) {
      poolBuilder.maxWaitTime(connectionPoolProperties.getMaxWaitTimeMs(), TimeUnit.MILLISECONDS);
    }
    if (connectionString.getMaxConnectionLifeTime() == null
        && connectionPoolProperties.getMaxConnectionLifeTimeMs() != null) {
      poolBuilder.maxConnectionLifeTime(
          connectionPoolProperties.getMaxConnectionLifeTimeMs(), TimeUnit.MILLISECONDS);
    }
    if (connectionString.getMaxConnectionIdleTime() == null
        && connectionPoolProperties.getMaxConnectionIdleTimeMs() != null) {
      poolBuilder.maxConnectionIdleTime(
          connectionPoolProperties.getMaxConnectionIdleTimeMs(), TimeUnit.MILLISECONDS);
    }
  }

  private void applyWriteConcern(
      MongoClientConnectionConfig connectionConfig, MongoClientSettings.Builder settingsBuilder) {
    var writeConcernProperties = this.defaultProperties.getWriteConcern();
    if (writeConcernProperties == null || !StringUtils.hasText(writeConcernProperties.getW())) {
      return;
    }

    var writeConcern = WriteConcern.valueOf(writeConcernProperties.getW());
    if (writeConcern == null) {
      log.warn(
          "ignore invalid connection-info.mongodb.write-concern.w ({}) for connection ({})",
          writeConcernProperties.getW(),
          connectionConfig.getConnection());
      return;
    }

    if (writeConcernProperties.getwTimeoutMs() != null) {
      writeConcern =
          writeConcern.withWTimeout(writeConcernProperties.getwTimeoutMs(), TimeUnit.MILLISECONDS);
    }
    if (writeConcernProperties.getJournal() != null) {
      writeConcern = writeConcern.withJournal(writeConcernProperties.getJournal());
    }

    settingsBuilder.writeConcern(writeConcern);
  }

  /** 설정값이 비어있으면 건너뛰고, 값이 잘못된 경우 경고만 남긴 뒤 드라이버 기본값을 사용한다. 설정 오타 하나로 애플리케이션 기동이 실패하지 않도록 하기 위함이다. */
  private void applyQuietly(
      MongoClientConnectionConfig connectionConfig,
      String propertyName,
      String value,
      Consumer<String> applier) {
    if (!StringUtils.hasText(value)) {
      return;
    }

    try {
      applier.accept(value);
    } catch (IllegalArgumentException _) {
      log.warn(
          "ignore invalid connection-info.mongodb.{} ({}) for connection ({})",
          propertyName,
          value,
          connectionConfig.getConnection());
    }
  }
}
