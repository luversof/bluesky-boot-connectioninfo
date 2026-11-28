package io.github.luversof.boot.connectioninfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ConnectionInfoPropertiesTests {

  @Test
  void bindsLoaderPropertiesKeepingCamelCaseKeys() {
    // HikariConfig 속성 이름(camelCase)이 바인딩 후에도 그대로 남아야 HikariConfig에 적용된다
    var source =
        new MapConfigurationPropertySource(
            Map.of(
                "bluesky-boot.connection-info.loaders.hikaridatasource.properties.maximumPoolSize",
                "5"));

    var properties =
        new Binder(source)
            .bind("bluesky-boot.connection-info", ConnectionInfoProperties.class)
            .get();

    assertThat(properties.getLoaders().get("hikaridatasource").getProperties())
        .containsEntry("maximumPoolSize", "5");
  }

  @Test
  void bindsAliasAndConnectionNameKeysWithUnderscore() {
    // alias와 connection 이름에 '_'가 흔하다(google_api, stock_postgresql). 바인딩 후에도 그대로 남아야 조회된다
    var prefix = "bluesky-boot.connection-info.loaders.hikaridatasource.";
    var source =
        new MapConfigurationPropertySource(
            Map.of(
                prefix + "connections.google_api", "google_api_postgresql",
                prefix + "connection-properties.stock_postgresql.maximumPoolSize", "20"));

    var loaderProperties =
        new Binder(source)
            .bind("bluesky-boot.connection-info", ConnectionInfoProperties.class)
            .get()
            .getLoaders()
            .get("hikaridatasource");

    assertThat(loaderProperties.getConnections())
        .containsEntry("google_api", List.of("google_api_postgresql"));
    assertThat(loaderProperties.getConnectionProperties())
        .containsEntry("stock_postgresql", Map.of("maximumPoolSize", "20"));
  }
}
