package io.github.luversof.boot.connectioninfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ConnectionInfoProperties */
@ConfigurationProperties(prefix = "bluesky-boot.connection-info")
public class ConnectionInfoProperties {

  /** Manage reader information for each reader. */
  private Map<String, ConnectionInfoReaderProperties> readers = new HashMap<>();

  /**
   * Manage loader information for each loader and a list of connection targets to be called through
   * the loader.
   */
  private Map<String, ConnectionInfoLoaderProperties> loaders = new HashMap<>();

  public Map<String, ConnectionInfoReaderProperties> getReaders() {
    return readers;
  }

  public void setReaders(Map<String, ConnectionInfoReaderProperties> readers) {
    this.readers = readers;
  }

  public Map<String, ConnectionInfoLoaderProperties> getLoaders() {
    return loaders;
  }

  public void setLoaders(Map<String, ConnectionInfoLoaderProperties> loaders) {
    this.loaders = loaders;
  }

  /** Whether to use the devcheck controller or not */
  public static class ConnectionInfoReaderProperties {

    /** Whether to use this Loader or not */
    private boolean enabled;

    /** Manage loader call information Currently used in an informal form. */
    private Map<String, String> properties;

    public ConnectionInfoReaderProperties() {}

    public ConnectionInfoReaderProperties(boolean enabled, Map<String, String> properties) {
      this.enabled = enabled;
      this.properties = properties;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Map<String, String> getProperties() {
      return properties;
    }

    public void setProperties(Map<String, String> properties) {
      this.properties = properties;
    }
  }

  public static class ConnectionInfoLoaderProperties {

    /** Whether to use this Loader or not */
    private boolean enabled;

    /** Manage loader call information Currently used in an informal form. */
    private Map<String, List<String>> connections;

    /** Settings applied to every connection the loader creates (e.g. HikariCP maximumPoolSize) */
    private Map<String, String> properties;

    public ConnectionInfoLoaderProperties() {}

    public ConnectionInfoLoaderProperties(boolean enabled, Map<String, List<String>> connections) {
      this.enabled = enabled;
      this.connections = connections;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Map<String, List<String>> getConnections() {
      return connections;
    }

    public void setConnections(Map<String, List<String>> connections) {
      this.connections = connections;
    }

    public Map<String, String> getProperties() {
      return properties;
    }

    public void setProperties(Map<String, String> properties) {
      this.properties = properties;
    }
  }
}
