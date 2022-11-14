// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.os.OsVersionChange;
import com.yahoo.vespa.hosted.provision.os.OsVersionTarget;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class OsVersionChangeSerializerTest {

    @Test
    public void serialization() {
        var change = new OsVersionChange(Map.of(
                NodeType.host, new OsVersionTarget(NodeType.host, Version.fromString("1.2.3")),
                NodeType.proxyhost, new OsVersionTarget(NodeType.proxyhost, Version.fromString("4.5.6")),
                NodeType.confighost, new OsVersionTarget(NodeType.confighost, Version.fromString("7.8.9"))
        ));
        var serialized = OsVersionChangeSerializer.fromJson(OsVersionChangeSerializer.toJson(change));
        assertEquals(serialized, change);
    }

}
