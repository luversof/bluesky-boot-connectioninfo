package io.github.luversof.boot.autoconfigure.connectioninfo.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ResolvableType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

import io.github.luversof.boot.autoconfigure.connectioninfo.ConnectionInfoAutoConfiguration;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoRegistry;
import io.github.luversof.boot.connectioninfo.jdbc.HikariDataSourceConnectionInfoLoader;
import io.github.luversof.boot.security.crypto.env.DecryptEnvironmentPostProcessor;

class ConnectionInfoTests {

  private static final Logger log = LoggerFactory.getLogger(ConnectionInfoTests.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
          .withInitializer(applicationContext ->
              new DecryptEnvironmentPostProcessor()
                  .postProcessEnvironment(applicationContext.getEnvironment(), null))
          .withPropertyValues("spring.profiles.active=localdev")
          .withPropertyValues(
              "bluesky-boot.connection-info.readers.mariadb-datasource.enabled=true",
              "bluesky-boot.connection-info.readers.mariadb-datasource.properties.url=jdbc:mariadb://mariadb.bluesky.local:3306/connection_info_localdev",
              "bluesky-boot.connection-info.readers.mariadb-datasource.properties.username={text}df9b549831440b381434dee7d1e7169eb80e2453abb28e184358f552cf561b33",
              "bluesky-boot.connection-info.readers.mariadb-datasource.properties.password={text}df9b549831440b381434dee7d1e7169eb80e2453abb28e184358f552cf561b33",
              "bluesky-boot.connection-info.readers.sqlserver-datasource.enabled=true",
              "bluesky-boot.connection-info.readers.sqlserver-datasource.properties.url=jdbc:sqlserver://mssql.bluesky.local;encrypt=false;databaseName=connection_info",
              "bluesky-boot.connection-info.readers.sqlserver-datasource.properties.username={text}402dc2850a816b6594e8b2d0293cb87251209160720a66b1f2a25c9760df58ac",
              "bluesky-boot.connection-info.readers.sqlserver-datasource.properties.password={text}0704d3da6333d293af85804a0ea58884734b4283026956835e8de40ce0d3480e",
              "bluesky-boot.connection-info.loaders.hikaridatasource.enabled=true",
              "bluesky-boot.connection-info.loaders.hikaridatasource.connections.mapexample=test1,test3")
          .withUserConfiguration(ConnectionInfoAutoConfiguration.class)
          .withUserConfiguration(ConnectionInfoJdbcAutoConfiguration.class);

  @Test
  @Disabled("mariadb.bluesky.local, mssql.bluesky.local 접속이 필요한 수동 확인용 테스트")
  void connectionInfoProperties() {
    this.contextRunner.run(context -> {
      var connectionInfoProperties = context.getBean(ConnectionInfoProperties.class);
      assertThat(connectionInfoProperties).isNotNull();
    });
  }

  @Test
  @Disabled("mariadb.bluesky.local, mssql.bluesky.local 접속이 필요한 수동 확인용 테스트")
  void mariaDbDataSourceConnectionInfoTest() {
    this.contextRunner.run(context -> {
      var connectionInfoLoader = context.getBean(HikariDataSourceConnectionInfoLoader.class);
      var connectionInfoList = connectionInfoLoader.load();

      log.debug("connectionInfoList : {}", connectionInfoList);

      var jdbcTemplate =
          new JdbcTemplate(
              connectionInfoList.stream()
                  .filter(x -> x.getKey().connectionKey().equals("test1"))
                  .findFirst()
                  .get()
                  .getConnection());
      var connectionInfo = jdbcTemplate.queryForList("SELECT * FROM DataSourceConnectionConfig");
      log.debug("connectionInfo : {}", connectionInfo);
    });
  }

  @Test
  @Disabled("mariadb.bluesky.local, mssql.bluesky.local 접속이 필요한 수동 확인용 테스트")
  void mariaDbDataSourceConnectionInfoReaderTest() {
    this.contextRunner.run(context -> {
      var connectionInfoProperties = context.getBean(ConnectionInfoProperties.class);

      log.debug("connectionInfoProperties : {}", connectionInfoProperties);
      log.debug(
          "connectionInfoProperties username : {}",
          connectionInfoProperties
              .getReaders()
              .get("mariadb-datasource")
              .getProperties()
              .get("username"));

      var connectionInfoRegistry = context.getBean(ConnectionInfoRegistry.class);
      log.debug("connectionInfoRegistry : {}", connectionInfoRegistry.getConnectionInfoList());
    });
  }

  @Test
  @Disabled("mariadb.bluesky.local, mssql.bluesky.local 접속이 필요한 수동 확인용 테스트")
  void sqlServerDataSourceConnectionInfoReaderTest() {
    this.contextRunner.run(context -> {
      var connectionInfoProperties = context.getBean(ConnectionInfoProperties.class);

      log.debug("connectionInfoProperties : {}", connectionInfoProperties);
      log.debug(
          "connectionInfoProperties username : {}",
          connectionInfoProperties
              .getReaders()
              .get("sqlserver-datasource")
              .getProperties()
              .get("username"));

      var connectionInfoRegistry = context.getBean(ConnectionInfoRegistry.class);
      log.debug("connectionInfoRegistry : {}", connectionInfoRegistry.getConnectionInfoList());
    });
  }

  @SuppressWarnings("unchecked")
  @Test
  @Disabled("mariadb.bluesky.local, mssql.bluesky.local 접속이 필요한 수동 확인용 테스트")
  void connectionInfoRegistryTest() {
    this.contextRunner.run(context -> {
      var type =
          ResolvableType.forClassWithGenerics(ConnectionInfoRegistry.class, HikariDataSource.class);
      var beanProvider = context.getBeanProvider(type);
      log.debug(
          "connectionInfoRegistry connectionList : {}",
          ((ConnectionInfoRegistry<HikariDataSource>) beanProvider.orderedStream().toList().get(0))
              .getConnectionInfoList());
    });
  }
}
