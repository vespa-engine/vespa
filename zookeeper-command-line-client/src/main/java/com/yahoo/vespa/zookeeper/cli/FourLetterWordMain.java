// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper.cli;

import com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder;
import org.apache.zookeeper.common.X509Exception;
import org.slf4j.impl.SimpleLogger;

import java.io.IOException;
import java.util.Map;

/**
 * Wraps {@link org.apache.zookeeper.client.FourLetterWordMain} with SSL configuration from Vespa.
 *
 * @author bjorncs
 */
public class FourLetterWordMain {

    static {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "WARN");
    }

    public static void main(String[] args) throws X509Exception.SSLContextException, IOException {
        Map<String, String> zkClientConfig = new ZkClientConfigBuilder().toConfigProperties();
        zkClientConfig.forEach(System::setProperty);
        String[] rewrittenArgs = overrideSecureClientArgument(args, zkClientConfig);
        org.apache.zookeeper.client.FourLetterWordMain.main(rewrittenArgs);
    }

    // Override secure cli argument based on Vespa TLS configuration
    // Secure flag is the 4th parameter (optional) to FourLetterWordMain
    private static String[] overrideSecureClientArgument(String[] args, Map<String, String> zkClientConfig) {
        String secureClientArgument = zkClientConfig.getOrDefault(ZkClientConfigBuilder.CLIENT_SECURE_PROPERTY, Boolean.FALSE.toString());
        return args.length == 3 || args.length == 4
                ? new String[] {args[0], args[1], args[2], secureClientArgument}
                : args;
    }

}
