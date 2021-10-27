// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.vespa.defaults.Defaults;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * @author olaa
 */
public class CoredumpGatherer {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final Path COREDUMP_PATH = Path.of(Defaults.getDefaults().underVespaHome("var/crash/processing"));

    public static JsonNode gatherCoredumpMetrics(FileWrapper fileWrapper) {
        int coredumps = getNumberOfCoredumps(fileWrapper);
        ObjectNode packet = jsonMapper.createObjectNode();
        packet.put("status_code", coredumps == 0 ? 0 : 1);
        packet.put("status_msg", coredumps == 0 ? "OK" : String.format("Found %d coredump(s)", coredumps));
        packet.put("timestamp", Instant.now().getEpochSecond());
        packet.put("application", "system-coredumps-processing");
        return packet;
    }

    private static int getNumberOfCoredumps(FileWrapper fileWrapper) {
        try (Stream<Path> stream = fileWrapper.walkTree(COREDUMP_PATH)){
            return (int) stream
                    .filter(fileWrapper::isRegularFile)
                    .count();
        } catch (NoSuchFileException e) {
            return 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
