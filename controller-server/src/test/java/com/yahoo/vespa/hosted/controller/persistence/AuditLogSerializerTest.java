// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.vespa.hosted.controller.auditlog.AuditLog;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
public class AuditLogSerializerTest {

    @Test
    void test_serialization() {
        Instant i1 = Instant.now();
        Instant i2 = i1.minus(Duration.ofHours(1));
        Instant i3 = i1.minus(Duration.ofHours(2));
        Instant i4 = i1.minus(Duration.ofHours(3));

        AuditLog log = new AuditLog(List.of(
                new AuditLog.Entry(i1, AuditLog.Entry.Client.other, "bar", AuditLog.Entry.Method.POST,
                                   "/bar/baz/",
                                   "0".repeat(2048).getBytes(StandardCharsets.UTF_8)),
                new AuditLog.Entry(i2, AuditLog.Entry.Client.other, "foo", AuditLog.Entry.Method.POST,
                                   "/foo/bar/",
                                   "{\"foo\":\"bar\"}".getBytes(StandardCharsets.UTF_8)),
                new AuditLog.Entry(i3, AuditLog.Entry.Client.hv, "baz", AuditLog.Entry.Method.POST,
                                   "/foo/baz/",
                                   new byte[0]),
                new AuditLog.Entry(i4, AuditLog.Entry.Client.console, "baz", AuditLog.Entry.Method.POST,
                                   "/foo/baz/",
                                   "000\ufdff\ufeff\uffff000".getBytes(StandardCharsets.UTF_8)), // non-ascii
                new AuditLog.Entry(i4, AuditLog.Entry.Client.cli, "quux", AuditLog.Entry.Method.POST,
                                   "/foo/quux/",
                                   new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF}) // garbage
        ));

        AuditLogSerializer serializer = new AuditLogSerializer();
        AuditLog serialized = serializer.fromSlime(serializer.toSlime(log));
        assertEquals(log.entries().size(), serialized.entries().size());

        for (int i = 0; i < log.entries().size(); i++) {
            AuditLog.Entry entry = log.entries().get(i);
            AuditLog.Entry serializedEntry = serialized.entries().get(i);

            assertEquals(entry.at().truncatedTo(MILLIS), serializedEntry.at());
            assertEquals(entry.client(), serializedEntry.client());
            assertEquals(entry.principal(), serializedEntry.principal());
            assertEquals(entry.method(), serializedEntry.method());
            assertEquals(entry.resource(), serializedEntry.resource());
            assertEquals(entry.data(), serializedEntry.data());
        }

        assertEquals(1024, log.entries().get(0).data().get().length());
        assertTrue(log.entries().get(2).data().isEmpty());
        assertTrue(log.entries().get(3).data().isEmpty());
        assertTrue(log.entries().get(4).data().isEmpty());
    }

}
