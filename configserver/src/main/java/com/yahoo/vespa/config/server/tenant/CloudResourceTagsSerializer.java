// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.CloudResourceTags;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serialization of {@link CloudResourceTags} to/from Slime.
 *
 * @author gjoranv
 */
public class CloudResourceTagsSerializer {

    private CloudResourceTagsSerializer() {}

    public static CloudResourceTags fromSlime(Inspector object) {
        Map<String, String> tags = new LinkedHashMap<>();
        object.traverse((String key, Inspector value) -> tags.put(key, value.asString()));
        return tags.isEmpty() ? CloudResourceTags.empty() : CloudResourceTags.from(tags);
    }

    public static void toSlime(CloudResourceTags tags, Cursor object) {
        tags.asMap().forEach(object::setString);
    }

}
