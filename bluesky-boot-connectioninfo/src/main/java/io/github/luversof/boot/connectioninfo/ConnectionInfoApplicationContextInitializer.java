package io.github.luversof.boot.connectioninfo;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class ConnectionInfoApplicationContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    ConnectionInfoUtil.setApplicationContext(applicationContext);
  }
}
