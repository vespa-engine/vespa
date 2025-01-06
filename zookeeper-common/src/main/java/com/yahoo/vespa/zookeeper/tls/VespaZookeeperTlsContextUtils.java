package com.yahoo.vespa.zookeeper.tls;

import com.yahoo.security.tls.ConfigFileBasedTlsContext;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.vespa.defaults.Defaults;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * @author jonmv
 */
public class VespaZookeeperTlsContextUtils {

    public static final Path ZOOKEEPER_TLS_CONFIG_FILE = Path.of(Defaults.getDefaults().underVespaHome("var/zookeeper/conf/tls.conf.json"));
    private static final TlsContext tlsContext = Files.exists(ZOOKEEPER_TLS_CONFIG_FILE)
                                                 ? new ConfigFileBasedTlsContext(ZOOKEEPER_TLS_CONFIG_FILE, TransportSecurityUtils.getInsecureAuthorizationMode())
                                                 : TransportSecurityUtils.getSystemTlsContext().orElse(null);

    public static Optional<TlsContext> tlsContext() {
        return Optional.ofNullable(tlsContext);
    }

}
