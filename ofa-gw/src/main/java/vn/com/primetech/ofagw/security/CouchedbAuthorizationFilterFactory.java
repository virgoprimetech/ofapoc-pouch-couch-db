package vn.com.primetech.ofagw.security;


import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AbstractNameValueGatewayFilterFactory;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import vn.com.primetech.ofagw.config.properties.CouchdbProperties;
import vn.com.primetech.ofagw.utils.HmacUtils;

import java.util.HexFormat;

@Component
public class CouchedbAuthorizationFilterFactory extends AbstractGatewayFilterFactory<CouchedbAuthorizationFilterFactory.Config> {

  private final CouchdbProperties couchdbProperties;

  public CouchedbAuthorizationFilterFactory(CouchdbProperties couchdbProperties) {
    super(Config.class);
    this.couchdbProperties = couchdbProperties;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> exchange.getPrincipal()
        .filter(p -> p instanceof JwtAuthenticationToken)
        .cast(JwtAuthenticationToken.class)
        .flatMap(jwt -> {
          final var tenant = jwt.getTokenAttributes().get("tenant_code");
          final var username = jwt.getTokenAttributes().get("username");
          final var user = "admin"; // CouchDB DB names must be lowercase
          final var roles = "_admin"; // Or extract from JWT as shown before
          final var tokenByte = HmacUtils.hmac(
              user,
              couchdbProperties.getProxyAuth().getSecret(),
              couchdbProperties.getProxyAuth().getAlgorithm(),
              couchdbProperties.getProxyAuth().getEncoding());
          final var token = HexFormat.of().formatHex(tokenByte);

          // 1. Calculate the New Path: /db/anything -> /user-{user}/anything
          // Normalize double slashes

//          final var path = exchange.getRequest().getURI().getRawPath();
//          if (path.startsWith("/_")) {
//            return chain.filter(exchange);
//          }

//          final var db = exchange.getRequest().getURI().getRawPath().replace("/", "");
//          final var newPath = String.format("/%s$%s$%s/", tenant, username, db);

          // 2. Mutate request with both Headers and the New URI
          final var mutatedRequest = exchange
              .getRequest()
              .mutate()
              .header("X-Auth-CouchDB-UserName", user)
              .header("X-Auth-CouchDB-Roles", roles)
              .header("X-Auth-CouchDB-Token", token)
//              .path(newPath)
              .build();

          return chain.filter(exchange.mutate().request(mutatedRequest).build());
        })
        .switchIfEmpty(chain.filter(exchange)); // If no JWT, just pass through (e.g. for login)
  }

  public static class Config {
    // Leave empty for now
  }
}
