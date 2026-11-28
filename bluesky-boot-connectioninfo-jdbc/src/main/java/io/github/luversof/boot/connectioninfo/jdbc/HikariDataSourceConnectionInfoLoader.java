package io.github.luversof.boot.connectioninfo.jdbc;

import java.util.List;
import java.util.Properties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.luversof.boot.connectioninfo.ConnectionInfo;
import io.github.luversof.boot.connectioninfo.ConnectionInfoKey;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;
import io.github.luversof.boot.security.crypto.factory.TextEncryptorFactories;

public class HikariDataSourceConnectionInfoLoader
    extends AbstractDataSourceConnectionInfoLoader<HikariDataSource, DataSourceConnectionConfig> {

  protected String loaderKey = "hikaridatasource";

  public HikariDataSourceConnectionInfoLoader(
      ConnectionInfoProperties connectionInfoProperties,
      List<ConnectionInfoReader<DataSourceConnectionConfig>> connectionInfoReaderList) {
    super(connectionInfoProperties, connectionInfoReaderList);
  }

  @Override
  public String getLoaderKey() {
    return loaderKey;
  }

  @Override
  protected ConnectionInfo<HikariDataSource> createConnectionInfo(
      DataSourceConnectionConfig connectionConfig) {
    var config = createHikariConfig(connectionConfig.getConnection());
    var textEncryptor = TextEncryptorFactories.getDelegatingTextEncryptor();
    config.setJdbcUrl(connectionConfig.getUrl());
    config.setUsername(textEncryptor.decrypt(connectionConfig.getUsername()));
    config.setPassword(textEncryptor.decrypt(connectionConfig.getPassword()));
    var hikariDataSource = new HikariDataSource(config);
    return new ConnectionInfo<>(
        new ConnectionInfoKey(getLoaderKey(), connectionConfig.getConnection()), hikariDataSource);
  }

  /**
   * loaders.hikaridatasource.properties.* 를 HikariConfig 속성 이름 그대로 적용한다(예: maximumPoolSize=5). 그 위에
   * connection-properties.{connection}.* 로 connection 하나의 설정을 덮어쓴다. 지정하지 않으면 HikariCP
   * 기본값(connection마다 연결 10개 유지)을 쓴다.
   */
  HikariConfig createHikariConfig(String connection) {
    var loaderProperties =
        (connectionInfoProperties == null || connectionInfoProperties.getLoaders() == null)
            ? null
            : connectionInfoProperties.getLoaders().get(getLoaderKey());
    if (loaderProperties == null) {
      return new HikariConfig();
    }
    var properties = new Properties();
    if (loaderProperties.getProperties() != null) {
      properties.putAll(loaderProperties.getProperties());
    }
    if (loaderProperties.getConnectionProperties() != null) {
      // registry처럼 connection 이름은 대소문자를 구분하지 않는다
      loaderProperties.getConnectionProperties().entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(connection))
          .findFirst()
          .ifPresent(entry -> properties.putAll(entry.getValue()));
    }
    return properties.isEmpty() ? new HikariConfig() : new HikariConfig(properties);
  }
}
