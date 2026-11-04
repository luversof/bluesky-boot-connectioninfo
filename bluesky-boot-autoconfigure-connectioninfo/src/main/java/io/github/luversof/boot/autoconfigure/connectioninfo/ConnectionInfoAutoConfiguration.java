package io.github.luversof.boot.autoconfigure.connectioninfo;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.github.luversof.boot.connectioninfo.ConnectionInfoDevCheckController;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoRegistry;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for ConnectionInfo support.
 * @author bluesky
 *
 */
@AutoConfiguration("blueskyBootConnectionInfoAutoConfiguration")
@EnableConfigurationProperties(ConnectionInfoProperties.class)
@ConditionalOnProperty(prefix = "bluesky-boot.connection-info", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConnectionInfoAutoConfiguration {
	
	/** dev-check 를 켰을 때만. {@code @DevCheckController} 는 {@code @RestController} 라 꺼도 원래 경로로 노출된다. */
	@Bean
	@ConditionalOnProperty(prefix = "bluesky-boot.dev-check", name = "enabled", havingValue = "true")
	ConnectionInfoDevCheckController connectionInfoDevCheckController(List<ConnectionInfoRegistry<?>> connectionInfoRegistryList) {
		return new ConnectionInfoDevCheckController(connectionInfoRegistryList);
	}
	
}
