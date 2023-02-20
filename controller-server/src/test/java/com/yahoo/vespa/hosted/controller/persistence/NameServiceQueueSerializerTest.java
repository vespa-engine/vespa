// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.LatencyAliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.dns.WeightedDirectTarget;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.dns.CreateRecord;
import com.yahoo.vespa.hosted.controller.dns.CreateRecords;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.dns.NameServiceRequest;
import com.yahoo.vespa.hosted.controller.dns.RemoveRecords;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class NameServiceQueueSerializerTest {

    private final NameServiceQueueSerializer serializer = new NameServiceQueueSerializer();

    @Test
    void test_serialization() {
        Optional<TenantAndApplicationId> owner = Optional.of(TenantAndApplicationId.from("t", "a"));
        var record1 = new Record(Record.Type.CNAME, RecordName.from("cname.example.com"), RecordData.from("example.com"));
        var record2 = new Record(Record.Type.TXT, RecordName.from("txt.example.com"), RecordData.from("text"));
        var requests = List.<NameServiceRequest>of(
                new CreateRecord(owner, record1),
                new CreateRecords(owner, List.of(record2)),
                new CreateRecords(owner, List.of(new Record(Record.Type.ALIAS, RecordName.from("alias.example.com"),
                                                            new LatencyAliasTarget(HostName.of("alias1"),
                                                                                   "dns-zone-01",
                                                                                   ZoneId.from("prod", "us-north-1")).pack()),
                                                 new Record(Record.Type.ALIAS, RecordName.from("alias.example.com"),
                                                            new LatencyAliasTarget(HostName.of("alias2"),
                                                                                   "dns-zone-02",
                                                                                   ZoneId.from("prod", "us-north-2")).pack()),
                                                 new Record(Record.Type.ALIAS, RecordName.from("alias.example.com"),
                                                            new LatencyAliasTarget(HostName.of("alias2"),
                                                                                   "ignored",
                                                                                   ZoneId.from("prod", "us-south-1")).pack()))
                ),
                new CreateRecords(Optional.empty(), List.of(new Record(Record.Type.DIRECT, RecordName.from("direct.example.com"),
                                                                       new WeightedDirectTarget(RecordData.from("10.1.2.3"),
                                                                                                ZoneId.from("prod", "us-north-1"),
                                                                                                100).pack()))),
                new RemoveRecords(Optional.empty(), record1.type(), record1.name()),
                new RemoveRecords(owner, record2.type(), record2.name(), Optional.of(record2.data()))
        );

        var queue = new NameServiceQueue(requests);
        var serialized = serializer.fromSlime(serializer.toSlime(queue));
        assertEquals(List.copyOf(queue.requests()), List.copyOf(serialized.requests()));
    }

}
