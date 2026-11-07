package io.github.luversof.boot.autoconfigure.connectioninfo.jdbc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.microsoft.sqlserver.jdbc.SQLServerDriver;
import com.zaxxer.hikari.HikariDataSource;

import io.github.luversof.boot.autoconfigure.connectioninfo.ConnectionInfoAutoConfiguration;
import io.github.luversof.boot.connectioninfo.ConnectionInfo;
import io.github.luversof.boot.connectioninfo.ConnectionInfoLoader;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.ConnectionInfoRegistry;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;
import io.github.luversof.boot.connectioninfo.jdbc.HikariDataSourceConnectionInfoLoader;
import io.github.luversof.boot.connectioninfo.jdbc.MariaDbDataSourceConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.jdbc.MysqlDataSourceConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.jdbc.PostgreSQLDataSourceConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.jdbc.SQLServerDataSourceConnectionInfoReader;

@AutoConfiguration(
    value = "blueskyBootConnectionInfoJdbcAutoConfiguration",
    before = {DataSourceAutoConfiguration.class, ConnectionInfoAutoConfiguration.class})
@ConditionalOnClass({DataSource.class, EmbeddedDatabaseType.class, HikariDataSource.class})
public class ConnectionInfoJdbcAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({
    DataSource.class,
    JdbcTemplate.class,
    HikariDataSource.class,
    org.mariadb.jdbc.Driver.class
  })
  static class MariaDbDataSourceConnectionInfoConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "bluesky-boot.connection-info.readers",
        name = "mariadb-datasource.enabled",
        havingValue = "true")
    MariaDbDataSourceConnectionInfoReader mariaDbDataSourceConnectionInfoReader(
        ConnectionInfoProperties connectionInfoProperties) {
      return new MariaDbDataSourceConnectionInfoReader(connectionInfoProperties);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({
    DataSource.class,
    JdbcTemplate.class,
    HikariDataSource.class,
    SQLServerDriver.class
  })
  static class SQLServerDataSourceConnectionInfoConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "bluesky-boot.connection-info.readers",
        name = "sqlserver-datasource.enabled",
        havingValue = "true")
    SQLServerDataSourceConnectionInfoReader sqlServerDataSourceConnectionInfoReader(
        ConnectionInfoProperties connectionInfoProperties) {
      return new SQLServerDataSourceConnectionInfoReader(connectionInfoProperties);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({
    DataSource.class,
    JdbcTemplate.class,
    HikariDataSource.class,
    com.mysql.jdbc.Driver.class
  })
  static class MysqlDbDataSourceConnectionInfoConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "bluesky-boot.connection-info.readers",
        name = "mysql-datasource.enabled",
        havingValue = "true")
    MysqlDataSourceConnectionInfoReader mysqlDataSourceConnectionInfoReader(
        ConnectionInfoProperties connectionInfoProperties) {
      return new MysqlDataSourceConnectionInfoReader(connectionInfoProperties);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({
    DataSource.class,
    JdbcTemplate.class,
    HikariDataSource.class,
    org.postgresql.Driver.class
  })
  static class PostgreSQLDataSourceConnectionInfoConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "bluesky-boot.connection-info.readers",
        name = "postgresql-datasource.enabled",
        havingValue = "true")
    PostgreSQLDataSourceConnectionInfoReader postgreSQLDataSourceConnectionInfoReader(
        ConnectionInfoProperties connectionInfoProperties) {
      return new PostgreSQLDataSourceConnectionInfoReader(connectionInfoProperties);
    }
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "bluesky-boot.connection-info.loaders",
      name = "hikaridatasource.enabled",
      havingValue = "true")
  HikariDataSourceConnectionInfoLoader hikariDataSourceConnectionInfoLoader(
      ConnectionInfoProperties connectionInfoProperties,
      List<ConnectionInfoReader<DataSourceConnectionConfig>> connectionInfoReaderList) {
    return new HikariDataSourceConnectionInfoLoader(
        connectionInfoProperties, connectionInfoReaderList);
  }

  @Bean
  <T extends HikariDataSource, C extends DataSourceConnectionConfig>
      ConnectionInfoRegistry<T> dataSourceConnectionInfoRegistry(
          List<ConnectionInfoLoader<T, C>> connectionInfoLoaderList) {
    // LazyLoad(ConnectionInfoUtil.getConnection)로 런타임에 추가될 수 있어 조회/추가가 동시에 일어난다.
    var connectionInfoList = new CopyOnWriteArrayList<ConnectionInfo<T>>();
    connectionInfoLoaderList.forEach(
        connectionInfoLoader -> connectionInfoList.addAll(connectionInfoLoader.load()));
    return () -> connectionInfoList;
  }
}
