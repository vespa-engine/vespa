// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.HashMap;
import java.util.Map;

public class Annotation {

    private final Map<String, Object> annotations;

    Annotation() {
        this(new HashMap<>());
    }

    Annotation(Map<String, Object> annotations) {
        this.annotations = annotations;
    }

    public Annotation append(Annotation a) {
        this.annotations.putAll(a.annotations);
        return this;
    }

    public boolean contains(String key) {
        return annotations.containsKey(key);
    }

    public Object get(String key) {
        return annotations.get(key);
    }

    @Override
    public String toString() {
        return annotations == null || annotations.isEmpty() ? "" : Q.toJson(annotations);
    }

}
