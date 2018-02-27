// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;


import com.yahoo.component.Version;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes overrides of version confidence.
 *
 * @author mpolden
 */
public class ConfidenceOverrideSerializer {

    private final static String overridesField = "overrides";

    public Slime toSlime(Map<Version, Confidence> overrides) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor object = root.setObject(overridesField);
        overrides.forEach((version, confidence) -> object.setString(version.toString(), confidence.name()));
        return slime;
    }

    public Map<Version, Confidence> fromSlime(Slime slime) {
        Cursor root = slime.get();
        Cursor overridesObject = root.field(overridesField);
        Map<Version, Confidence> overrides = new LinkedHashMap<>();
        overridesObject.traverse((ObjectTraverser) (name, value) -> {
            overrides.put(Version.fromString(name), Confidence.valueOf(value.asString()));
        });
        return Collections.unmodifiableMap(overrides);
    }

}
