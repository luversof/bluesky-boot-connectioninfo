package io.github.luversof.boot.connectioninfo.jdbc;

import java.sql.Driver;

import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;

public class PostgreSQLDataSourceConnectionInfoReader
    extends AbstractDataSourceConnectionInfoReader<DataSourceConnectionConfig> {

  protected String readerKey = "postgresql-datasource";

  protected Driver readerDriver = new org.postgresql.Driver();

  public PostgreSQLDataSourceConnectionInfoReader(
      ConnectionInfoProperties connectionInfoProperties) {
    super(connectionInfoProperties);
    this.readerQuery =
        """
        SELECT connection, url, username, password
        FROM "DataSourceConnectionConfig"
        WHERE connection IN ({0})
        """;
  }

  @Override
  public String getReaderKey() {
    return readerKey;
  }

  @Override
  public Driver getReaderDriver() {
    return readerDriver;
  }

  @Override
  protected RowMapper<DataSourceConnectionConfig> getConnectionConfigRowMapper() {
    return new DataClassRowMapper<>(DataSourceConnectionConfig.class);
  }
}
