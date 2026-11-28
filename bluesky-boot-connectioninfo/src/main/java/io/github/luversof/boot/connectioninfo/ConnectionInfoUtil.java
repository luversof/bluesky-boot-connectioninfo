package io.github.luversof.boot.connectioninfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

public final class ConnectionInfoUtil {

  private static volatile ApplicationContext applicationContext;

  /**
   * LazyLoad를 connectionKey 단위로 직렬화하기 위한 lock. 개수가 고정이라 요청 key가 늘어도 메모리가 늘지 않고, registry처럼 대소문자를
   * 구분하지 않는다. 다른 key가 같은 lock을 나눠 쓸 수 있지만 LazyLoad는 드물어 문제되지 않는다.
   */
  private static final Object[] lazyLoadLocks = new Object[64];

  static {
    Arrays.setAll(lazyLoadLocks, _ -> new Object());
  }

  /** 못 찾은 connection(타입:key)과 다시 조회할 수 있는 시각. 없는 key를 부를 때마다 메타 DB를 다시 조회하지 않기 위함이다. */
  private static final Map<String, Long> missUntilMap = new ConcurrentHashMap<>();

  private static final long MISS_CACHE_MILLIS = 60_000L;

  private static final int MISS_CACHE_MAX_SIZE = 10_000;

  private static final Map<Class<?>, Lookup> lookupMap = new ConcurrentHashMap<>();

  private ConnectionInfoUtil() {}

  public static void setApplicationContext(ApplicationContext applicationContext) {
    ConnectionInfoUtil.applicationContext = applicationContext;
    missUntilMap.clear();
    lookupMap.clear();
  }

  private static Object lazyLoadLock(String connectionKey) {
    return lazyLoadLocks[
        Math.floorMod(connectionKey.toLowerCase(Locale.ROOT).hashCode(), lazyLoadLocks.length)];
  }

  private static String missKey(Class<?> connectionType, String connectionKey) {
    return connectionType.getName() + ":" + connectionKey.toLowerCase(Locale.ROOT);
  }

  private static boolean recentlyMissed(String missKey) {
    var missUntil = missUntilMap.get(missKey);
    return missUntil != null && missUntil > System.currentTimeMillis();
  }

  private static void recordMiss(String missKey) {
    // shortcut: 꽉 차면 통째로 비운다. 서로 다른 없는 key가 1분에 만 개를 넘게 들어오면 LRU로 바꾼다.
    if (missUntilMap.size() >= MISS_CACHE_MAX_SIZE) {
      missUntilMap.clear();
    }
    missUntilMap.put(missKey, System.currentTimeMillis() + MISS_CACHE_MILLIS);
  }

  /**
   * connectionKey에 해당하는 connection을 connectionType의 Registry/Loader에서만 찾는다.
   *
   * <p>connectionType은 Registry에 등록된 타입(예: {@code HikariDataSource.class}, {@code
   * MongoClient.class})이나 그 상위 타입(예: {@code DataSource.class})을 지정한다. LazyLoad 결과는 그 connection을 만든
   * Loader 타입의 Registry에 추가되므로 다른 타입의 Registry가 섞이지 않는다.
   *
   * @param connectionKey connection key
   * @param connectionType 찾을 connection 타입
   * @return connection, 없으면 null
   */
  public static <T> T getConnection(String connectionKey, Class<T> connectionType) {
    var lookup = lookup(connectionType);
    if (lookup.registries().isEmpty()) {
      return null;
    }

    var connection = findConnection(lookup, connectionKey, connectionType);
    if (connection != null) {
      return connection;
    }

    var missKey = missKey(connectionType, connectionKey);
    if (recentlyMissed(missKey)) {
      return null;
    }

    synchronized (lazyLoadLock(connectionKey)) {
      // lock 대기 중 다른 스레드가 이미 LazyLoad를 끝냈을 수 있으므로 다시 확인한다.
      connection = findConnection(lookup, connectionKey, connectionType);
      if (connection != null || recentlyMissed(missKey)) {
        return connection;
      }

      try {
        connection = lazyLoad(lookup, connectionKey, connectionType);
      } catch (RuntimeException exception) {
        // DB 장애로 load가 실패해도 miss로 기억한다. 아니면 요청마다 연결 timeout만큼 lock을 잡고 다시 시도해 요청이 줄줄이 막힌다.
        recordMiss(missKey);
        throw exception;
      }
      if (connection == null) {
        recordMiss(missKey);
      }
      return connection;
    }
  }

  private static <T> T lazyLoad(Lookup lookup, String connectionKey, Class<T> connectionType) {
    for (var loaderTarget : lookup.loaderTargets()) {
      var connectionInfoList = loaderTarget.loader().load(List.of(connectionKey));
      if (connectionInfoList != null && !connectionInfoList.isEmpty()) {
        loaderTarget.registry().addConnectionInfoList(connectionInfoList);
        return connectionType.cast(connectionInfoList.getFirst().getConnection());
      }
    }
    return null;
  }

  private static <T> T findConnection(
      Lookup lookup, String connectionKey, Class<T> connectionType) {
    for (var registry : lookup.registries()) {
      var connectionInfoList = registry.getConnectionInfo(connectionKey);
      if (connectionInfoList != null && !connectionInfoList.isEmpty()) {
        return connectionType.cast(connectionInfoList.getFirst().getConnection());
      }
    }
    return null;
  }

  /** connectionType으로 찾을 Registry와 LazyLoad할 Loader. 제네릭 빈 검색은 Spring이 캐시하지 않아 타입별로 한 번만 구한다. */
  private record Lookup(
      List<ConnectionInfoRegistry<Object>> registries, List<LoaderTarget> loaderTargets) {}

  /** Loader와 그 Loader가 만든 connection을 담을 Registry */
  private record LoaderTarget(
      ConnectionInfoLoader<Object, ?> loader, ConnectionInfoRegistry<Object> registry) {}

  private static Lookup lookup(Class<?> connectionType) {
    // computeIfAbsent는 빈 생성 중 다른 타입 조회가 다시 들어오면 재귀 갱신 예외가 나므로 쓰지 않는다.
    var lookup = lookupMap.get(connectionType);
    if (lookup == null) {
      lookup = createLookup(connectionType);
      var previous = lookupMap.putIfAbsent(connectionType, lookup);
      if (previous != null) {
        lookup = previous;
      }
    }
    return lookup;
  }

  /**
   * Registry 빈은 {@code ConnectionInfoRegistry<T extends HikariDataSource>}처럼 등록되어 상위
   * 타입(DataSource)으로는 빈 검색에 걸리지 않는다. 그래서 같은 타입의 Registry에 더해, 상위 타입을 만드는 Loader의 실제 타입으로 Registry를
   * 찾는다.
   */
  @SuppressWarnings("unchecked")
  private static Lookup createLookup(Class<?> connectionType) {
    var registries = new LinkedHashSet<>(registries(connectionType));
    var loaderTargets = new ArrayList<LoaderTarget>();
    // Loader는 두 번째 제네릭(ConnectionConfig)이 제각각이라 타입으로 찾을 수 없어 클래스의 제네릭으로 거른다.
    for (var loader : applicationContext.getBeanProvider(ConnectionInfoLoader.class)) {
      var loadedType = loadedType(loader);
      if (loadedType != null && connectionType.isAssignableFrom(loadedType)) {
        registries(loadedType).stream()
            .findFirst()
            .ifPresent(registry -> {
              registries.add(registry);
              loaderTargets.add(new LoaderTarget(loader, registry));
            });
      }
    }
    return new Lookup(List.copyOf(registries), List.copyOf(loaderTargets));
  }

  private static List<ConnectionInfoRegistry<Object>> registries(Class<?> connectionType) {
    ObjectProvider<ConnectionInfoRegistry<Object>> provider =
        applicationContext.getBeanProvider(
            ResolvableType.forClassWithGenerics(ConnectionInfoRegistry.class, connectionType));
    return provider.orderedStream().toList();
  }

  private static Class<?> loadedType(Object loader) {
    return ResolvableType.forClass(ClassUtils.getUserClass(loader))
        .as(ConnectionInfoLoader.class)
        .resolveGeneric(0);
  }

  /**
   * connectionKey에 해당하는 connectionName을 반환하는 유틸리티 메서드 connectionKey -> connectionName ->
   * ConnectionInfo -> Connection
   */
  public static String getConnectionName(String connectionKey) {
    return getConnectionNameList(connectionKey).stream()
        .findFirst()
        .orElseThrow(() ->
            new IllegalStateException(
                "connection-info connection is not configured: " + connectionKey));
  }

  /**
   * connectionKey에 해당하는 connectionName List를 반환하는 유틸리티 메서드
   *
   * @param connectionKey
   * @return
   */
  public static List<String> getConnectionNameList(String connectionKey) {
    // getBean은 null 대신 예외를 던지므로, 스위치를 꺼서 빈이 없을 때도 아래 메시지가 나오게 provider로 꺼낸다
    var connectionInfoProperties =
        applicationContext.getBeanProvider(ConnectionInfoProperties.class).getIfAvailable();

    if (connectionInfoProperties == null || connectionInfoProperties.getLoaders() == null) {
      throw new IllegalStateException("connection-info.loaders is not configured");
    }

    return connectionInfoProperties.getLoaders().values().stream()
        .map(ConnectionInfoProperties.ConnectionInfoLoaderProperties::getConnections)
        .filter(Objects::nonNull)
        .map(connections -> connections.get(connectionKey))
        .filter(connectionList -> connectionList != null && !connectionList.isEmpty())
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }
}
