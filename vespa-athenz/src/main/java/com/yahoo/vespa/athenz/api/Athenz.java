package com.yahoo.vespa.athenz.api;

import java.net.URI;

/**
 * Represents an Athenz instance.
 *
 * @param name   The human-readable name of the Athenz instance.
 * @param ztsUri The base URL to the ZTS API, typically ending in /zts/v1.
 * @param zmsUri The base URL to the ZMS API, typically ending in /zms/v1.
 */
public record Athenz(String name, URI ztsUri, URI zmsUri) {
    public static Athenz VESPA = new Athenz("Vespa Athenz", "athenz.vespa-cloud.com");
    public static Athenz VESPA_CD = new Athenz("Vespa Athenz CD", "athenz.cd.vespa-cloud.com");
    public static Athenz YAHOO = new Athenz("Yahoo Athenz", "athenz.ouroath.com");
    public static Athenz YAHOO_ONPREM = new Athenz("Yahoo on-prem Athens", "athens.yahoo.com");

    private static URI makeURI(boolean isZts, String domain) {
        String tag = isZts ? "zts" : "zms";
        return URI.create("https://%s.%s:4443/%s/v1".formatted(tag, domain, tag));
    }

    private Athenz(String name, String domain) {
        this(name, makeURI(true, domain), makeURI(false, domain));
    }

    @Override
    public String toString() {
        return name;
    }
}
