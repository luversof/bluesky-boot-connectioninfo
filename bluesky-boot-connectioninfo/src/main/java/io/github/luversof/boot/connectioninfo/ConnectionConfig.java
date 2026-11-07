package io.github.luversof.boot.connectioninfo;

/** ConnnectionInfo를 생성하기 위해 reader를 통해 호출되는 connnection 정보를 제공하는 인터페이스 */
public interface ConnectionConfig {

  /**
   * connection 정보를 구분하기 위한 key
   *
   * @return connection key
   */
  String getConnection();
}
