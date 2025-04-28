package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class Sidecars {
    private final List<Sidecar> sidecars;
    
    public static Sidecars createDisabled() {
        return new Sidecars(null);
    }
    
    @JsonCreator
    public Sidecars(@JsonProperty("sidecars") List<Sidecar> sidecars) {
        this.sidecars = sidecars;
    }
    
    @JsonGetter("sidecars")
    public List<Sidecar> getSidecars() {
        return sidecars;
    }
    
    @Override
    public String toString() {
        return "Sidecars{" +
                "sidecars=" + sidecars +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (Sidecars) o;
        return Objects.equals(sidecars, that.sidecars);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sidecars);
    }
}
