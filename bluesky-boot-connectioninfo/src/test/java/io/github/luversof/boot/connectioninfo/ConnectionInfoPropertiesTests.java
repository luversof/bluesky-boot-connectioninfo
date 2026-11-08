package io.github.luversof.boot.connectioninfo;

import static org.assertj.core.api.Assertions.assertThat;

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
}
