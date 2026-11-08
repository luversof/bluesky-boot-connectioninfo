package io.github.luversof.boot.connectioninfo.mongodb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MongoDB 기본 설정값 Properties
 *
 * <p>connection-info.mongodb.* (connection-map 제외)
 */
@ConfigurationProperties(prefix = "bluesky-boot.connection-info.mongodb")
public class MongoDbDefaultProperties {

  /** 인증 데이터베이스 */
  private String authenticationDatabase = "admin";

  /**
   * ReadPreference: primary, primaryPreferred, secondary, secondaryPreferred, nearest. 비우면 드라이버 기본값
   * primary를 쓴다. secondary 계열은 방금 쓴 데이터를 못 읽을 수 있어 기본값으로 두지 않는다.
   */
  private String readPreference;

  /** ReadConcern level: local, majority, linearizable */
  private String readConcernLevel = "local";

  /** UUID Representation: JAVA_LEGACY, STANDARD, PYTHON_LEGACY, C_SHARP_LEGACY, UNSPECIFIED */
  private String uuidRepresentation = "JAVA_LEGACY";

  /** Connection Pool 설정 */
  private ConnectionPool connectionPool = new ConnectionPool();

  /** Write Concern 설정 */
  private WriteConcern writeConcern = new WriteConcern();

  public String getAuthenticationDatabase() {
    return authenticationDatabase;
  }

  public void setAuthenticationDatabase(String authenticationDatabase) {
    this.authenticationDatabase = authenticationDatabase;
  }

  public String getReadPreference() {
    return readPreference;
  }

  public void setReadPreference(String readPreference) {
    this.readPreference = readPreference;
  }

  public String getReadConcernLevel() {
    return readConcernLevel;
  }

  public void setReadConcernLevel(String readConcernLevel) {
    this.readConcernLevel = readConcernLevel;
  }

  public String getUuidRepresentation() {
    return uuidRepresentation;
  }

  public void setUuidRepresentation(String uuidRepresentation) {
    this.uuidRepresentation = uuidRepresentation;
  }

  public ConnectionPool getConnectionPool() {
    return connectionPool;
  }

  public void setConnectionPool(ConnectionPool connectionPool) {
    this.connectionPool = connectionPool;
  }

  public WriteConcern getWriteConcern() {
    return writeConcern;
  }

  public void setWriteConcern(WriteConcern writeConcern) {
    this.writeConcern = writeConcern;
  }

  /** Connection Pool 설정 */
  public static class ConnectionPool {
    private Integer maxSize = 30;
    // 비우면 드라이버 기본값 0. 미리 열어 두는 연결은 host × connection × 인스턴스 수만큼 곱해진다.
    private Integer minSize;
    private Long maxWaitTimeMs = 120000L;
    private Long maxConnectionLifeTimeMs = 1800000L;
    private Long maxConnectionIdleTimeMs = 120000L;

    public Integer getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
      this.maxSize = maxSize;
    }

    public Integer getMinSize() {
      return minSize;
    }

    public void setMinSize(Integer minSize) {
      this.minSize = minSize;
    }

    public Long getMaxWaitTimeMs() {
      return maxWaitTimeMs;
    }

    public void setMaxWaitTimeMs(Long maxWaitTimeMs) {
      this.maxWaitTimeMs = maxWaitTimeMs;
    }

    public Long getMaxConnectionLifeTimeMs() {
      return maxConnectionLifeTimeMs;
    }

    public void setMaxConnectionLifeTimeMs(Long maxConnectionLifeTimeMs) {
      this.maxConnectionLifeTimeMs = maxConnectionLifeTimeMs;
    }

    public Long getMaxConnectionIdleTimeMs() {
      return maxConnectionIdleTimeMs;
    }

    public void setMaxConnectionIdleTimeMs(Long maxConnectionIdleTimeMs) {
      this.maxConnectionIdleTimeMs = maxConnectionIdleTimeMs;
    }
  }

  /** Write Concern 설정 */
  public static class WriteConcern {
    /** W1, W2, W3, MAJORITY, ACKNOWLEDGED, UNACKNOWLEDGED */
    private String w = "W1";

    private Integer wTimeoutMs = 5000;
    private Boolean journal = false;

    public String getW() {
      return w;
    }

    public void setW(String w) {
      this.w = w;
    }

    public Integer getwTimeoutMs() {
      return wTimeoutMs;
    }

    public void setwTimeoutMs(Integer wTimeoutMs) {
      this.wTimeoutMs = wTimeoutMs;
    }

    public Boolean getJournal() {
      return journal;
    }

    public void setJournal(Boolean journal) {
      this.journal = journal;
    }
  }
}
