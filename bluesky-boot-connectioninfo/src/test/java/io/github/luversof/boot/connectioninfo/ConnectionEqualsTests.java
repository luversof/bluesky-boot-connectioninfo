package io.github.luversof.boot.connectioninfo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectionEqualsTests {

  private static final Object OTHER_TYPE = "other type";

  @Test
  void connectionInfo() {
    var key = new ConnectionInfoKey("loader", "a");
    var connectionInfo = new ConnectionInfo<>(key, "connection");

    assertEqualContract(connectionInfo, new ConnectionInfo<>(key, "connection"));
    assertNotEqual(
        connectionInfo,
        OTHER_TYPE,
        new ConnectionInfo<>(new ConnectionInfoKey("loader", "b"), "connection"),
        new ConnectionInfo<>(key, "other"),
        new ConnectionInfo<>(key, "connection") {
          @Override
          protected boolean canEqual(Object other) {
            return false;
          }
        });
  }

  @Test
  void dataSourceConnectionConfig() {
    var config = dataSource("c", "u", "n", "p");

    assertEqualContract(config, dataSource("c", "u", "n", "p"));
    assertNotEqual(
        config,
        OTHER_TYPE,
        dataSource("x", "u", "n", "p"),
        dataSource("c", "x", "n", "p"),
        dataSource("c", "u", "x", "p"),
        dataSource("c", "u", "n", "x"),
        new DataSourceConnectionConfig() {
          @Override
          protected boolean canEqual(Object other) {
            return false;
          }
        });
  }

  @Test
  void mongoClientConnectionConfig() {
    var config = mongoClient("c", "s", "d", "n", "p");

    assertEqualContract(config, mongoClient("c", "s", "d", "n", "p"));
    assertNotEqual(
        config,
        OTHER_TYPE,
        mongoClient("x", "s", "d", "n", "p"),
        mongoClient("c", "x", "d", "n", "p"),
        mongoClient("c", "s", "x", "n", "p"),
        mongoClient("c", "s", "d", "x", "p"),
        mongoClient("c", "s", "d", "n", "x"),
        new MongoClientConnectionConfig() {
          @Override
          protected boolean canEqual(Object other) {
            return false;
          }
        });
  }

  private static void assertEqualContract(Object actual, Object equal) {
    Object same = actual;
    assertThat(actual).isEqualTo(same).isEqualTo(equal).hasSameHashCodeAs(equal);
  }

  private static void assertNotEqual(Object actual, Object... others) {
    for (var other : others) {
      assertThat(actual).isNotEqualTo(other);
    }
  }

  private static DataSourceConnectionConfig dataSource(
      String connection, String url, String username, String password) {
    var config = new DataSourceConnectionConfig();
    config.setConnection(connection);
    config.setUrl(url);
    config.setUsername(username);
    config.setPassword(password);
    return config;
  }

  private static MongoClientConnectionConfig mongoClient(
      String connection,
      String connectionString,
      String database,
      String userName,
      String password) {
    var config = new MongoClientConnectionConfig();
    config.setConnection(connection);
    config.setConnectionString(connectionString);
    config.setDatabase(database);
    config.setUserName(userName);
    config.setPassword(password);
    return config;
  }
}
