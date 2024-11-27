package com.yahoo.vespa.athenz.api;

import com.yahoo.config.provision.SystemName;

import java.net.URI;
import java.nio.file.Path;

/**
 * Represents an Athenz instance.
 *
 * @param name                    The human-readable name of the Athenz instance.
 * @param ztsUri                  The base URL to the ZTS API, typically ending in /zts/v1.
 * @param zmsUri                  The base URL to the ZMS API, typically ending in /zms/v1.
 * @param userCredentialDirectory The absolute path of the directory containing the user certificate key and cert.
 */
public record Athenz(String name, URI ztsUri, URI zmsUri, Path userCredentialDirectory) {
    public static Athenz VESPA = new Athenz("Vespa Athenz", "athenz.vespa-cloud.com", ".vespaai/athenz");
    public static Athenz VESPA_CD = new Athenz("Vespa Athenz CD", "athenz.cd.vespa-cloud.com", ".vespaai/athenz-cd");
    public static Athenz YAHOO = new Athenz("Yahoo Athenz", "athenz.ouroath.com", ".athenz");
    public static Athenz YAHOO_ONPREM = new Athenz("Yahoo on-prem Athens", "athens.yahoo.com", ".athenz");

    public static Athenz in(SystemName system) {
        return switch (system) {
            case Public -> VESPA;
            case PublicCd -> VESPA_CD;
            case cd, main -> YAHOO;
            case dev -> throw new IllegalArgumentException("No Athenz instance associated with '" + system + "'");
        };
    }

    private static URI makeURI(boolean isZts, String domain) {
        String tag = isZts ? "zts" : "zms";
        return URI.create("https://%s.%s:4443/%s/v1".formatted(tag, domain, tag));
    }

    private Athenz(String name, String domain, String userCredentialDirectory) {
        this(name, makeURI(true, domain), makeURI(false, domain), Path.of(System.getProperty("user.home"), userCredentialDirectory));
    }

    public Path userCertPath() { return userCredentialDirectory.resolve("cert"); }
    public Path userKeyPath() { return userCredentialDirectory.resolve("key"); }

    @Override
    public String toString() {
        return name;
    }
}
