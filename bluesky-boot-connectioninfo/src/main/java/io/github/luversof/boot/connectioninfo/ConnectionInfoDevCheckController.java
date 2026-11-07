package io.github.luversof.boot.connectioninfo;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import io.github.luversof.boot.devcheck.annotation.DevCheckController;
import io.github.luversof.boot.devcheck.annotation.DevCheckDescription;

/**
 * ConnectionInfoDevCheckController
 *
 * @author luversof
 */
@DevCheckController
@RequestMapping(value = "/blueskyBoot/connectionInfo", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConnectionInfoDevCheckController {

  /** connectionInfoRegistryList */
  private final List<ConnectionInfoRegistry<?>> connectionInfoRegistryList;

  public ConnectionInfoDevCheckController(
      List<ConnectionInfoRegistry<?>> connectionInfoRegistryList) {
    this.connectionInfoRegistryList = connectionInfoRegistryList;
  }

  @DevCheckDescription("connectionInfoKeyList")
  @GetMapping("/connectionInfoKeyList")
  List<ConnectionInfoKey> connectionInfoKeyList() {
    return connectionInfoRegistryList.stream()
        .flatMap(x -> x.getConnectionInfoList().stream())
        .map(x -> x.getKey())
        .toList();
  }
}
