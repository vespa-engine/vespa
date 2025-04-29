// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;


/**
 * Defines properties for sidecar flag.
 *
 * @author glabashnik
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Sidecar {
    private final String name;
    private final SidecarImage image;
    private final SidecarQuota quota;

    @JsonCreator
    public Sidecar(
            @JsonProperty("name") String name,
            @JsonProperty("image") SidecarImage image,
            @JsonProperty("quota") SidecarQuota quota) {
        this.name = name;
        this.image = image;
        this.quota = quota;
    }

    @JsonGetter("name")
    public String getName() {
        return name;
    }
    
    @JsonGetter("image")
    public SidecarImage getImage() {
        return image;
    }

    @JsonGetter("quota")
    public SidecarQuota getQuota() {
        return quota;
    }

    @Override
    public String toString() {
        return "Sidecar{" +
                "image=" + image.toString() + 
                ", quota=" + quota.toString() +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (Sidecar) o;
        return Objects.equals(image, that.image) && Objects.equals(quota, that.quota);
    }

    @Override
    public int hashCode() {
        return Objects.hash(image, quota);
    }
}
