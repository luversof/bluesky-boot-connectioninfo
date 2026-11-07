package io.github.luversof.boot.connectioninfo.jdbc;

import java.sql.Driver;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;
import io.github.luversof.boot.security.crypto.factory.TextEncryptorFactories;

public abstract class AbstractDataSourceConnectionInfoReader<C extends DataSourceConnectionConfig>
    implements DataSourceConnectionInfoReader<C> {

  protected final ConnectionInfoProperties connectionInfoProperties;

  protected String readerQuery =
      """
      SELECT connection, url, username, password
      FROM DataSourceConnectionConfig
      WHERE connection IN ({0})
      """;

  protected AbstractDataSourceConnectionInfoReader(
      ConnectionInfoProperties connectionInfoProperties) {
    this.connectionInfoProperties = connectionInfoProperties;
  }

  public String getReaderQuery() {
    return readerQuery;
  }

  /**
   * Call the target Driver object to be used by the loader
   *
   * @return Target Driver object
   */
  protected abstract @NonNull Driver getReaderDriver();

  @Override
  public List<C> readConnectionConfigList(@NonNull List<String> connectionList) {
    String sql =
        MessageFormat.format(
            getReaderQuery(), String.join(",", Collections.nCopies(connectionList.size(), "?")));
    return getJdbcTemplate()
        .query(
            sql,
            new ArgumentPreparedStatementSetter(connectionList.toArray()),
            getConnectionConfigRowMapper());
  }

  protected abstract @NonNull RowMapper<C> getConnectionConfigRowMapper();

  private JdbcTemplate getJdbcTemplate() {
    var encryptor = TextEncryptorFactories.getDelegatingTextEncryptor();

    var readerProperties =
        connectionInfoProperties.getReaders().get(getReaderKey()).getProperties();
    String url = readerProperties.get("url");
    String username = encryptor.decrypt(readerProperties.get("username"));
    String password = encryptor.decrypt(readerProperties.get("password"));

    return new JdbcTemplate(new SimpleDriverDataSource(getReaderDriver(), url, username, password));
  }
}
