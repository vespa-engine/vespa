// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.collections.Pair;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Creates a local application from vespa-services fragments.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class StandaloneContainer {
    public static String firstContainerId(AbstractConfigProducerRoot root) {
        return root.getConfigProducer("container").get().getConfigId();
    }

    interface ThrowingFunction<T, U> {
        U apply(T input) throws Exception;
    }

    static <T> T withContainerModel(String servicesXml, ThrowingFunction<VespaModel, T> f) throws Exception {
        return withTempDirectory(applicationPath -> {
            writeServicesXml(applicationPath, servicesXml);

            LocalFileDb distributedFiles = new LocalFileDb(applicationPath);
            VespaModel root;
            Pair<VespaModel, com.yahoo.vespa.model.container.Container> rc = StandaloneContainerApplication.createContainerModel(
                    applicationPath, distributedFiles, applicationPath.resolve("preprocesedApp").toFile(), Networking.enable,
                    new ConfigModelRepo());
            root = rc.getFirst();
            return f.apply(root);
        });
    }

    private static <T> T withTempDirectory(ThrowingFunction<Path, T> f) throws Exception {
        Path directory = Files.createTempDirectory("application");
        try {
            return f.apply(directory);
        } finally {
            IOUtils.recursiveDeleteDir(directory.toFile());
        }
    }

    private static void writeServicesXml(Path applicationPath, String servicesXml) throws IOException {
        Path path = applicationPath.resolve("services.xml");
        List<String> output = Arrays.asList("<?xml version=\"1.0\" encoding=\"utf-8\"?>", servicesXml);
        Files.write(path, output, StandardCharsets.UTF_8);
    }
}
