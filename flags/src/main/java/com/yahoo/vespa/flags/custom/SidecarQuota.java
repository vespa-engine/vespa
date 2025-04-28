package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class SidecarQuota {
    private final Double cpus;
    private final String memory;
    private final String gpu;

    @JsonCreator
    public SidecarQuota(
            @JsonProperty("cpus") Double cpus, 
            @JsonProperty("memory") String memory, 
            @JsonProperty("gpu") String gpu) {
        this.cpus = cpus;
        this.memory = memory;
        this.gpu = gpu;
    }
    
    @JsonProperty("cpus")
    public Double getCpus() {
        return cpus;
    }

    @JsonProperty("memory")
    public String getMemory() {
        return memory;
    }

    @JsonProperty("gpu")
    public String getGpu() {
        return gpu;
    }

    @Override
    public String toString() {
        return "SidecarQuota{" +
                "cpus=" + cpus +
                ", memory='" + memory + '\'' +
                ", gpu='" + gpu + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (SidecarQuota) o;
        return Objects.equals(cpus, that.cpus) &&
                Objects.equals(memory, that.memory) &&
                Objects.equals(gpu, that.gpu);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpus, memory, gpu);
    }
    
    
}
