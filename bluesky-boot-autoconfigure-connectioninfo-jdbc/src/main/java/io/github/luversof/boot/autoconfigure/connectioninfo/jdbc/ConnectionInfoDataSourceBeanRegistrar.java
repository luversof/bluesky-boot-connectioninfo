package io.github.luversof.boot.autoconfigure.connectioninfo.jdbc;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

import io.github.luversof.boot.connectioninfo.ConnectionInfo;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoRegistry;

/**
 * {@code loaders.hikaridatasource.connections.<alias>}마다 {@code <alias>DataSource} 이름으로
 * HikariDataSource 빈을 등록한다.
 *
 * <p>빈이 되어야 Spring Boot가 hikaricp 지표와 DB health를 붙이고, {@code @Qualifier("stockDataSource")}처럼 주입할 수
 * 있다. 이름에 타입 접미사를 붙여 도메인 빈 이름(예: {@code stock})과 겹치지 않게 한다. 같은 이름의 빈이 이미 있으면 등록하지 않는다.
 */
class ConnectionInfoDataSourceBeanRegistrar
    implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

  static final String LOADER_KEY = "hikaridatasource";

  static final String BEAN_NAME_SUFFIX = "DataSource";

  private static final Logger log =
      LoggerFactory.getLogger(ConnectionInfoDataSourceBeanRegistrar.class);

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    // ConnectionInfoProperties 빈은 아직 없으므로 같은 prefix를 직접 바인딩한다
    var loaderProperties =
        Binder.get(environment)
            .bind("bluesky-boot.connection-info", ConnectionInfoProperties.class)
            .map(properties ->
                properties.getLoaders() == null ? null : properties.getLoaders().get(LOADER_KEY))
            .orElse(null);
    if (loaderProperties == null || loaderProperties.getConnections() == null) {
      return;
    }

    var beanFactory = (BeanFactory) registry;
    loaderProperties
        .getConnections()
        .forEach((alias, connectionList) -> {
          var beanName = alias + BEAN_NAME_SUFFIX;
          if (connectionList == null || connectionList.isEmpty()) {
            return;
          }
          if (registry.containsBeanDefinition(beanName)) {
            log.info("skip connection-info bean ({}): a bean with the same name exists", beanName);
            return;
          }
          var connection = connectionList.getFirst();
          var beanDefinition =
              new RootBeanDefinition(
                  HikariDataSource.class, () -> findDataSource(beanFactory, beanName, connection));
          // 풀은 만든 Loader가 앱 종료 시 닫는다. AutoCloseable 추론으로 Spring이 한 번 더 닫지 않게 한다.
          beanDefinition.setDestroyMethodName("");
          registry.registerBeanDefinition(beanName, beanDefinition);
        });
  }

  /** registry에 미리 올라간 connection을 꺼낸다. 메타 DB에 없어 못 만들었으면 null(빈 없음)로 두어 기동은 막지 않는다. */
  private static HikariDataSource findDataSource(
      BeanFactory beanFactory, String beanName, String connection) {
    ObjectProvider<ConnectionInfoRegistry<HikariDataSource>> provider =
        beanFactory.getBeanProvider(
            ResolvableType.forClassWithGenerics(
                ConnectionInfoRegistry.class, HikariDataSource.class));
    var connectionInfoRegistry = provider.getIfAvailable();
    List<ConnectionInfo<HikariDataSource>> connectionInfoList =
        connectionInfoRegistry == null
            ? List.of()
            : connectionInfoRegistry.getConnectionInfo(connection);
    if (connectionInfoList.isEmpty()) {
      log.warn(
          "connection-info connection ({}) is not loaded, {} is not created", connection, beanName);
      return null;
    }
    return connectionInfoList.getFirst().getConnection();
  }
}
