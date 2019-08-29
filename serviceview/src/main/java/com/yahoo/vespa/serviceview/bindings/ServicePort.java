// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Splitter;

/**
 * View of {@link com.yahoo.cloud.config.ModelConfig.Hosts.Services.Ports}.
 *
 * @author mortent
 * @author Steinar Knutsen
 */
@JsonIgnoreProperties(value = { "splitOnSpace" }, ignoreUnknown = true)
public class ServicePort {
    public int number;
    public String tags;
    private static final Splitter splitOnSpace = Splitter.on(' ');

    /**
     * Return true if all argument tags are present for this port.
     *
     * @param tag
     *            one or more tag names to check for
     * @return true if all argument tags are present for this port, false
     *         otherwise
     */
    public boolean hasTags(String... tag) {
        if (tags == null) {
            return false;
        }
        List<String> isTaggedWith = splitOnSpace.splitToList(tags);
        for (String t : tag) {
            if (!isTaggedWith.contains(t)) {
                return false;
            }
        }
        return true;
    }
}
