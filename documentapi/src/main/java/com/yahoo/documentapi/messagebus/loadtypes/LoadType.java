// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.loadtypes;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

/**
 * @author thomasg
 */
public class LoadType {
    private int id;
    private String name;
    private DocumentProtocol.Priority priority;

    public static LoadType DEFAULT = new LoadType(0, "default", DocumentProtocol.Priority.NORMAL_3);

    public LoadType(int id, String name, DocumentProtocol.Priority priority) {
        this.id = id;
        this.name = name;
        this.priority = priority;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LoadType)) {
            return false;
        }

        LoadType o = (LoadType)other;

        return name.equals(o.getName()) && id == o.getId() && priority == o.getPriority();
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, id, priority);
    }

    public String getName() { return name; }

    public String toString() { return name + " (id " + id + ")"; }

    public DocumentProtocol.Priority getPriority() { return priority; }

    public int getId() { return id; }
}
