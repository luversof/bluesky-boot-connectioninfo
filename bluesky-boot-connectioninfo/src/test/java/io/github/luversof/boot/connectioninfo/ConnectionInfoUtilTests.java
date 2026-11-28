package io.github.luversof.boot.connectioninfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

  @Test
  void returnsNullWithoutRegistryOfRequestedType() {
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      ConnectionInfoUtil.setApplicationContext(context);

      assertThat(ConnectionInfoUtil.getConnection("int-lazy", Long.class)).isNull();
      assertThat(context.getBean(IntLoader.class).lazyLoadCount).hasValue(0);
    }
  }

  @Test
  void concurrentLazyLoadCreatesConnectionOnce() throws Exception {
    try (var context = new AnnotationConfigApplicationContext(Config.class);
        var executor = Executors.newFixedThreadPool(2)) {
      ConnectionInfoUtil.setApplicationContext(context);
      var intLoader = context.getBean(IntLoader.class);
      // 첫 스레드가 load 중일 때 두 번째 스레드가 들어오도록 load를 늦춘다
      intLoader.loadDelayMillis = 200;
      var start = new CountDownLatch(1);
      Callable<Integer> call =
          () -> {
            start.await();
            return ConnectionInfoUtil.getConnection("int-lazy", Integer.class);
          };

      var first = executor.submit(call);
      var second = executor.submit(call);
      start.countDown();

      assertThat(first.get()).isEqualTo(2);
      assertThat(second.get()).isEqualTo(2);
      assertThat(intLoader.lazyLoadCount).hasValue(1);
      assertThat(keys(context, "intRegistry")).containsExactly("int-eager", "int-lazy");
    }
  }

  @Test
  void findsConnectionBySuperTypeOfRegistryType() {
    // registry 빈은 <T extends Integer>로 등록되어 Number로는 빈 검색에 걸리지 않는다(DataSource와 HikariDataSource 관계)
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      ConnectionInfoUtil.setApplicationContext(context);

      assertThat(ConnectionInfoUtil.getConnection("int-eager", Number.class)).isEqualTo(1);
      assertThat(ConnectionInfoUtil.getConnection("int-lazy", Number.class)).isEqualTo(2);

      assertThat(keys(context, "intRegistry")).containsExactly("int-eager", "int-lazy");
      assertThat(keys(context, "strRegistry")).isEmpty();
    }
  }

  @Test
  void objectTypeAddsLazyConnectionToRegistryOfLoaderType() {
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      ConnectionInfoUtil.setApplicationContext(context);

      assertThat(ConnectionInfoUtil.getConnection("str-lazy", Object.class)).isEqualTo("s");

      assertThat(keys(context, "intRegistry")).containsExactly("int-eager");
      assertThat(keys(context, "strRegistry")).containsExactly("str-lazy");
    }
  }

  @Test
  void doesNotReloadRightAwayWhenLoadFails() {
    // DB 장애로 load가 실패한 key를 요청마다 다시 시도하면 연결 timeout만큼 lock을 잡아 요청이 줄줄이 막힌다
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      ConnectionInfoUtil.setApplicationContext(context);
      var intLoader = context.getBean(IntLoader.class);
      intLoader.failLoad = true;

      assertThatThrownBy(() -> ConnectionInfoUtil.getConnection("int-lazy", Integer.class))
          .isInstanceOf(IllegalStateException.class);
      assertThat(ConnectionInfoUtil.getConnection("int-lazy", Integer.class)).isNull();

      assertThat(intLoader.lazyLoadCount).hasValue(1);
    }
  }

  @Test
  void connectionNameListFailsClearlyWithoutProperties() {
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      ConnectionInfoUtil.setApplicationContext(context);

      assertThatThrownBy(() -> ConnectionInfoUtil.getConnectionNameList("any"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("connection-info.loaders is not configured");
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

    volatile long loadDelayMillis;

    volatile boolean failLoad;

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
      if (failLoad) {
        throw new IllegalStateException("meta db is down");
      }
      if (loadDelayMillis > 0) {
        try {
          // 아무도 countDown하지 않는 latch를 기다려 load를 loadDelayMillis만큼 늦춘다
          new CountDownLatch(1).await(loadDelayMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {
          Thread.currentThread().interrupt();
        }
      }
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
