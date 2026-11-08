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
    var config = createHikariConfig();
    var textEncryptor = TextEncryptorFactories.getDelegatingTextEncryptor();
    config.setJdbcUrl(connectionConfig.getUrl());
    config.setUsername(textEncryptor.decrypt(connectionConfig.getUsername()));
    config.setPassword(textEncryptor.decrypt(connectionConfig.getPassword()));
    var hikariDataSource = new HikariDataSource(config);

    createdDataSources.add(hikariDataSource);

    return new ConnectionInfo<>(
        new ConnectionInfoKey(getLoaderKey(), connectionConfig.getConnection()), hikariDataSource);
  }

  /**
   * loaders.hikaridatasource.properties.* 를 HikariConfig 속성 이름 그대로 적용한다(예: maximumPoolSize=5). 지정하지
   * 않으면 HikariCP 기본값(connection마다 연결 10개 유지)을 쓴다.
   */
  HikariConfig createHikariConfig() {
    var loaderProperties =
        (connectionInfoProperties == null || connectionInfoProperties.getLoaders() == null)
            ? null
            : connectionInfoProperties.getLoaders().get(getLoaderKey());
    if (loaderProperties == null || loaderProperties.getProperties() == null) {
      return new HikariConfig();
    }
    var properties = new Properties();
    properties.putAll(loaderProperties.getProperties());
    return new HikariConfig(properties);
  }
}
