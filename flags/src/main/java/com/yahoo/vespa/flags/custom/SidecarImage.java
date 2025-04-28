package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SidecarImage {
    private final String reference;
    
    @JsonCreator
    public SidecarImage(@JsonProperty("reference") String reference) {
        this.reference = reference;
    }
    
    @JsonProperty("reference")
    public String getReference() {
        return reference;
    }

    @Override
    public String toString() {
        return "SidecarImage{" +
                "reference='" + reference + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (SidecarImage) o;
        return Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference);
    }
}
