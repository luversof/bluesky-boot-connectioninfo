package io.github.luversof.boot.connectioninfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JDBC/MongoDB 자동구성처럼 connection 타입이 다른 Registry 두 개가 함께 있는 상황 */
class ConnectionInfoUtilTests {

  @Test
  void lazyLoadAddsToRegistryOfRequestedType() {
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      ConnectionInfoUtil.setApplicationContext(context);

      Integer connection = ConnectionInfoUtil.getConnection("int-lazy", Integer.class);

      assertThat(connection).isEqualTo(2);
      assertThat(keys(context, "intRegistry")).containsExactly("int-eager", "int-lazy");
      assertThat(keys(context, "strRegistry")).isEmpty();
    }
  }

  @Test
  void findsEagerConnectionOfRequestedType() {
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      ConnectionInfoUtil.setApplicationContext(context);

      assertThat(ConnectionInfoUtil.getConnection("int-eager", Integer.class)).isEqualTo(1);
    }
  }

  @Test
  void ignoresConnectionOfOtherType() {
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      ConnectionInfoUtil.setApplicationContext(context);

      assertThat(ConnectionInfoUtil.getConnection("str-lazy", Integer.class)).isNull();
      assertThat(keys(context, "intRegistry")).containsExactly("int-eager");
      assertThat(keys(context, "strRegistry")).isEmpty();
    }
  }

  @Test
  void doesNotReloadMissingKeyRightAway() {
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      ConnectionInfoUtil.setApplicationContext(context);

      assertThat(ConnectionInfoUtil.getConnection("missing", Integer.class)).isNull();
      assertThat(ConnectionInfoUtil.getConnection("MISSING", Integer.class)).isNull();

      assertThat(context.getBean(IntLoader.class).lazyLoadCount).hasValue(1);
    }
  }

  private static List<String> keys(
      AnnotationConfigApplicationContext context, String registryName) {
    return context
        .getBean(registryName, ConnectionInfoRegistry.class)
        .getConnectionInfoList()
        .stream()
        .map(connectionInfo -> ((ConnectionInfo<?>) connectionInfo).getKey().connectionKey())
        .toList();
  }

  record TestConfig(String getConnection) implements ConnectionConfig {}

  abstract static class TestLoader<T> implements ConnectionInfoLoader<T, TestConfig> {

    private final String lazyKey;

    private final T lazyConnection;

    final AtomicInteger lazyLoadCount = new AtomicInteger();

    TestLoader(String lazyKey, T lazyConnection) {
      this.lazyKey = lazyKey;
      this.lazyConnection = lazyConnection;
    }

    @Override
    public List<ConnectionInfoReader<TestConfig>> getConnectionInfoReaderList() {
      return List.of();
    }

    @Override
    public List<ConnectionInfo<T>> load(List<String> connectionList) {
      lazyLoadCount.incrementAndGet();
      if (!connectionList.contains(lazyKey)) {
        return List.of();
      }
      return List.of(
          new ConnectionInfo<>(new ConnectionInfoKey(getLoaderKey(), lazyKey), lazyConnection));
    }
  }

  static class IntLoader extends TestLoader<Integer> {

    IntLoader() {
      super("int-lazy", 2);
    }

    @Override
    public String getLoaderKey() {
      return "int";
    }

    @Override
    public List<ConnectionInfo<Integer>> load() {
      return List.of(new ConnectionInfo<>(new ConnectionInfoKey("int", "int-eager"), 1));
    }
  }

  static class StrLoader extends TestLoader<String> {

    StrLoader() {
      super("str-lazy", "s");
    }

    @Override
    public String getLoaderKey() {
      return "str";
    }

    @Override
    public List<ConnectionInfo<String>> load() {
      return List.of();
    }
  }

  /** 자동구성의 registry 빈과 같은 제네릭 메서드 형태 */
  @Configuration(proxyBeanMethods = false)
  static class Config {

    @Bean
    IntLoader intLoader() {
      return new IntLoader();
    }

    @Bean
    StrLoader strLoader() {
      return new StrLoader();
    }

    @Bean
    <T extends Integer, C extends TestConfig> ConnectionInfoRegistry<T> intRegistry(
        List<ConnectionInfoLoader<T, C>> loaders) {
      return registry(loaders);
    }

    @Bean
    <T extends String, C extends TestConfig> ConnectionInfoRegistry<T> strRegistry(
        List<ConnectionInfoLoader<T, C>> loaders) {
      return registry(loaders);
    }

    private static <T, C extends TestConfig> ConnectionInfoRegistry<T> registry(
        List<ConnectionInfoLoader<T, C>> loaders) {
      var connectionInfoList = new CopyOnWriteArrayList<ConnectionInfo<T>>();
      loaders.forEach(loader -> connectionInfoList.addAll(loader.load()));
      return () -> connectionInfoList;
    }
  }
}
