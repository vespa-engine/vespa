package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.vespa.hosted.controller.versions.CertifiedOsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
class CertifiedOsVersionSerializerTest {

    @Test
    public void serialization() {
        Set<CertifiedOsVersion> certifiedVersion = Set.of(new CertifiedOsVersion(new OsVersion(Version.fromString("1.2.3"),
                                                                                               CloudName.from("cloud1")),
                                                                                 Version.fromString("4.5.6")),
                                                          new CertifiedOsVersion(new OsVersion(Version.fromString("3.2.1"),
                                                                                               CloudName.from("cloud2")),
                                                                                 Version.fromString("6.5.4")));
        CertifiedOsVersionSerializer serializer = new CertifiedOsVersionSerializer();
        assertEquals(certifiedVersion, serializer.fromSlime(serializer.toSlime(certifiedVersion)));
    }

}
