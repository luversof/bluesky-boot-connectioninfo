package io.github.luversof.boot.connectioninfo.jdbc;

import java.sql.Driver;

import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;

public class MysqlDataSourceConnectionInfoReader
    extends AbstractDataSourceConnectionInfoReader<DataSourceConnectionConfig> {

  protected String readerKey = "mysql-datasource";

  @Override
  public String getReaderKey() {
    return readerKey;
  }

  @Override
  protected Driver getReaderDriver() {
    try {
      return new com.mysql.cj.jdbc.Driver();
    } catch (java.sql.SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public MysqlDataSourceConnectionInfoReader(ConnectionInfoProperties connectionInfoProperties) {
    super(connectionInfoProperties);
  }

  @Override
  protected RowMapper<DataSourceConnectionConfig> getConnectionConfigRowMapper() {
    return new DataClassRowMapper<>(DataSourceConnectionConfig.class);
  }
}
