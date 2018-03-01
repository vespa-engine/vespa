// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.vespa.config.SlimeUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Serialization of a list of {@link DeploymentQueue.Triggering} instances to/from Json bytes using Slime.
 *
 * @author jvenstad
 */
public class JobQueueSerializer {

    private static final String atField = "at";
    private static final String retryField = "retry";
    private static final String applicationVersionUpgradeField = "applicationVersionUpgrade";
    private static final String forcedField = "forced";
    private static final String idField = "id";

    public byte[] toJson(Iterable<DeploymentQueue.Triggering> queue) {
        try {
            Slime slime = new Slime();
            Cursor array = slime.setArray();
            queue.forEach((triggering -> {
                Cursor object = array.addObject();
                object.setString(idField, triggering.applicationId().serializedForm());
                object.setLong(atField, triggering.at().toEpochMilli());
                object.setBool(forcedField, triggering.forced());
                object.setBool(applicationVersionUpgradeField, triggering.applicationVersionUpgrade());
                object.setBool(retryField, triggering.retry());
            }));
            return SlimeUtils.toJsonBytes(slime);
        }
        catch (IOException e) {
            throw new RuntimeException("Serialization of a job queue failed", e);
        }
    }

    public List<DeploymentQueue.Triggering> fromJson(byte[] data) {
        Inspector inspector = SlimeUtils.jsonToSlime(data).get();
        List<DeploymentQueue.Triggering> list = new ArrayList<>();
        inspector.traverse((ArrayTraverser) (index, value) -> {
            // TODO: Remove and simplify once queue has been converted to new format, i.e., when this has been deployed and run at least once.
            if (value.type() == Type.STRING)
                list.add(new DeploymentQueue.Triggering(ApplicationId.fromSerializedForm(value.asString()), Instant.now(), false, false, false));

            else
                list.add(new DeploymentQueue.Triggering(ApplicationId.fromSerializedForm(value.field(idField).asString()),
                                                        Instant.ofEpochMilli(value.field(atField).asLong()),
                                                        value.field(forcedField).asBool(),
                                                        value.field(applicationVersionUpgradeField).asBool(),
                                                        value.field(retryField).asBool()));
        });
        return list;
    }

}
