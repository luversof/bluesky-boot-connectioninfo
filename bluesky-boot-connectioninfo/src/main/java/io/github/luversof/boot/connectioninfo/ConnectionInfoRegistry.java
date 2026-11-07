package io.github.luversof.boot.connectioninfo;

import java.util.List;

/**
 * connectionInfo 객체를 담고 있는 Registry
 *
 * @author bluesky
 * @param <T> connection 객체 타입
 */
@FunctionalInterface
public interface ConnectionInfoRegistry<T> {

  /**
   * connectionInfo 객체를 담고 있는 List
   *
   * @return
   */
  List<ConnectionInfo<T>> getConnectionInfoList();

  /**
   * connectionInfo 객체를 List에 추가
   *
   * @param connectionInfo
   */
  default void addConnectionInfo(ConnectionInfo<T> connectionInfo) {
    getConnectionInfoList().add(connectionInfo);
  }

  /**
   * connectionInfo 객체 List를 List에 추가
   *
   * @param connectionInfoList
   */
  default void addConnectionInfoList(List<ConnectionInfo<T>> connectionInfoList) {
    getConnectionInfoList().addAll(connectionInfoList);
  }

  /**
   * connectionInfoKey에 해당하는 connectionInfo 객체를 반환
   *
   * @param connectionInfoKey
   * @return
   */
  default ConnectionInfo<T> getConnectionInfo(ConnectionInfoKey connectionInfoKey) {
    return getConnectionInfoList().stream()
        .filter(connectionInfo -> connectionInfo.getKey().equals(connectionInfoKey))
        .findAny()
        .orElseThrow(() -> new RuntimeException("NOT_EXIST_CONNECTIONINFO"));
  }

  /**
   * connectionKey에 해당하는 connectionInfo 객체 List를 반환
   *
   * @param connectionKey
   * @return
   */
  default List<ConnectionInfo<T>> getConnectionInfo(String connectionKey) {
    return getConnectionInfoList().stream()
        .filter(connectionInfo -> connectionInfo.getKey().connectionKey().equals(connectionKey))
        .toList();
  }
}
