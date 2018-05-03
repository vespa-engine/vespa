// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/**
 * @author hakon
 */
@Immutable
public class Mark {
    private final Position position;
    private final Version version;
    private final Object token;

    Mark(Position position, Version version, Object token) {
        this.position = position;
        this.version = version;
        this.token = token;
    }

    public Position position() {
        return position;
    }

    public Version version() {
        return version;
    }

    public Object secret() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mark mark = (Mark) o;
        return Objects.equals(position, mark.position) &&
                Objects.equals(version, mark.version) &&
                token == mark.token;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, version, token);
    }

    @Override
    public String toString() {
        return position.coordinateString() + "@" + version;
    }
}
