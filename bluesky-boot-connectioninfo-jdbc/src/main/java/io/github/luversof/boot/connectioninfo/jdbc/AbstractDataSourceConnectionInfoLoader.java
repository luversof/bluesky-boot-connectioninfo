package io.github.luversof.boot.connectioninfo.jdbc;

import java.util.List;

import javax.sql.DataSource;

import io.github.luversof.boot.connectioninfo.AbstractConnectionInfoLoader;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;

/**
 * Used when obtaining DataSource by loading connectionInfo from DB
 *
 * @author bluesky
 * @param <T> The type of DataSource to be loaded via Loader
 */
public abstract class AbstractDataSourceConnectionInfoLoader<
        T extends DataSource, C extends DataSourceConnectionConfig>
    extends AbstractConnectionInfoLoader<T, C> {

  protected AbstractDataSourceConnectionInfoLoader(
      ConnectionInfoProperties connectionInfoProperties,
      List<ConnectionInfoReader<C>> connectionInfoReaderList) {
    super(connectionInfoProperties, connectionInfoReaderList);
  }
}
