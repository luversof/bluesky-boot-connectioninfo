package io.github.luversof.boot.connectioninfo.jdbc;

import java.util.List;

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
    var config = new HikariConfig();
    var textEncryptor = TextEncryptorFactories.getDelegatingTextEncryptor();
    config.setJdbcUrl(connectionConfig.getUrl());
    config.setUsername(textEncryptor.decrypt(connectionConfig.getUsername()));
    config.setPassword(textEncryptor.decrypt(connectionConfig.getPassword()));
    var hikariDataSource = new HikariDataSource(config);

    createdDataSources.add(hikariDataSource);

    return new ConnectionInfo<>(
        new ConnectionInfoKey(getLoaderKey(), connectionConfig.getConnection()), hikariDataSource);
  }
}
