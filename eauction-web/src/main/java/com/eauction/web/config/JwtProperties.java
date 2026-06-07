package com.eauction.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /** Base64-encoded 256-bit secret. Must be at least 32 bytes when decoded. */
    private String secret;

    /** Access token lifetime in seconds (default 15 min). */
    private long accessTokenExpirySeconds = 900;

    /** Refresh token lifetime in seconds (default 7 days). */
    private long refreshTokenExpirySeconds = 604_800;

    /** JWT issuer claim. */
    private String issuer = "eauction-platform";
}
