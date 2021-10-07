// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Allows wiremock to be used as a JUnit 5 extension, like
 * <pre>
 *
 *   &#64RegisterExtension
 *   WireMockExtension mockServer1 = new WireMockExtension();
 * </pre>
 */
public class WireMockExtension extends WireMockServer implements BeforeEachCallback, AfterEachCallback {

    public WireMockExtension() {
        this(WireMockConfiguration.options()
                                  .dynamicPort()
                                  .dynamicHttpsPort());
    }

    public WireMockExtension(Options options) {
        super(options);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        stop();
        resetAll();
    }

}
