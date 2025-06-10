// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Defines properties for sidecar flag.
 *
 * @author glabashnik
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Sidecar {
    private final String name;
    private final String image;
    private final SidecarQuota quota;
    private final List<String> volumeMounts;
    private final Map<String, String> envs;
    private final List<String> command;

    @JsonCreator
    public Sidecar(
            @JsonProperty("name") String name,
            @JsonProperty("image") String image,
            @JsonProperty("quota") SidecarQuota quota,
            @JsonProperty("volumeMounts") List<String> volumeMounts,
            @JsonProperty("envs") Map<String, String> envs,
            @JsonProperty("command") List<String> command) {
        this.name = name;
        this.image = image;
        this.quota = quota;
        this.volumeMounts = volumeMounts;
        this.envs = envs;
        this.command = command;
    }

    @JsonGetter("name")
    public String getName() {
        return name;
    }

    @JsonGetter("image")
    public String getImage() {
        return image;
    }

    @JsonGetter("quota")
    public SidecarQuota getQuota() {
        return quota;
    }

    @JsonGetter("volumeMounts")
    public List<String> getVolumeMounts() {
        return volumeMounts;
    }

    @JsonGetter("envs")
    public Map<String, String> getEnvs() {
        return envs;
    }

    @JsonGetter("command")
    public List<String> getCommand() {
        return command;
    }

    @Override
    public String toString() {
        return "Sidecar{name='%s', image='%s', quota=%s, volumeMounts={%s}, envs=[%s], command=[%s]}"
                .formatted(
                        name,
                        image,
                        quota,
                        volumeMounts.stream().map("'%s'"::formatted).collect(Collectors.joining(", ")),
                        envs.entrySet().stream()
                                .map(entry -> "%s='%s'".formatted(entry.getKey(), entry.getValue()))
                                .collect(Collectors.joining(", ")),
                        command.stream().map("'%s'"::formatted).collect(Collectors.joining(", ")));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (Sidecar) o;
        return Objects.equals(image, that.image)
                && Objects.equals(quota, that.quota)
                && Objects.equals(volumeMounts, that.volumeMounts)
                && Objects.equals(envs, that.envs)
                && Objects.equals(command, that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(image, quota);
    }
}
