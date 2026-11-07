package io.github.luversof.boot.connectioninfo;

import java.util.List;

import org.jspecify.annotations.NonNull;

/**
 * Connection 정보를 읽어들이는 Reader
 *
 * @param <C> connection config type
 */
public interface ConnectionInfoReader<C extends ConnectionConfig> {

  /**
   * reader key
   *
   * @return
   */
  String getReaderKey();

  /**
   * connection 정보를 읽어들일 query
   *
   * @return
   */
  List<C> readConnectionConfigList(@NonNull List<String> connectionList);
}
