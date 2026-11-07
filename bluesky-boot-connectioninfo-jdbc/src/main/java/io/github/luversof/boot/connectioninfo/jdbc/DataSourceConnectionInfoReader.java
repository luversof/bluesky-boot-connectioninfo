package io.github.luversof.boot.connectioninfo.jdbc;

import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;

public interface DataSourceConnectionInfoReader<C extends DataSourceConnectionConfig>
    extends ConnectionInfoReader<C> {}
