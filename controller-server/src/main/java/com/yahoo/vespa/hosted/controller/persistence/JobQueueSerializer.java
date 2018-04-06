// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Serialization of a queue of ApplicationIds to/from Json bytes using Slime.
 *
 * The queue is serialized as an array of strings.
 *
 * @author bratseth
 */
public class JobQueueSerializer {

    public byte[] toJson(Iterable<ApplicationId> queue) {
        try {
            Slime slime = new Slime();
            Cursor array = slime.setArray();
            queue.forEach((id ->  array.addString(id.serializedForm())));
            return SlimeUtils.toJsonBytes(slime);
        }
        catch (IOException e) {
            throw new RuntimeException("Serialization of a job queue failed", e);
        }
    }

    public Deque<ApplicationId> fromSlime(Slime slime) {
        Inspector inspector = slime.get();
        Deque<ApplicationId> queue = new ArrayDeque<>();
        inspector.traverse((ArrayTraverser) (index, value) -> queue.addLast(ApplicationId.fromSerializedForm(value.asString())));
        return queue;
    }

}
