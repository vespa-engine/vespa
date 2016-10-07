// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Statistics;

import java.io.IOException;
import java.net.URL;


/**
 * Class that makes HTTP request to get docker container stats as docker-java's
 * {@link com.github.dockerjava.api.DockerClient#statsCmd(String)} fails because of jersey version conflict.
 *
 * @author valerijf
 */
public class DockerStatsCmd {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Statistics getContainerStatistics(ContainerName containerName) throws IOException {
        URL url = new URL("http://localhost:2376/containers/" + containerName.asString() + "/stats");

        return objectMapper.readValue(url, Statistics.class);
    }
}
