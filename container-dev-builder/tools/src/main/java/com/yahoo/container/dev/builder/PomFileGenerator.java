// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.dev.builder;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeSet;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class PomFileGenerator {

    public static void main(String[] args) throws IOException {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("com.yahoo.vespa");
        model.setArtifactId("container-dev");
        model.setVersion(args[0]);
        model.getProperties().setProperty("project.build.sourceEncoding", StandardCharsets.UTF_8.name());
        for (String str : new TreeSet<>(Arrays.asList(args).subList(1, args.length))) {
            Dependency dependency = newDependency(str);
            if (dependency == null) {
                continue;
            }
            if (dependency.getGroupId().equals(model.getGroupId()) &&
                dependency.getArtifactId().equals(model.getArtifactId())) {
                continue;
            }
            model.addDependency(dependency);
        }
        new MavenXpp3Writer().write(System.out, model);
    }

    private static Dependency newDependency(String str) {
        String[] arr = str.split(":");
        if (arr.length != 5) {
            return null;
        }
        Dependency out = new Dependency();
        out.setGroupId(arr[0]);
        out.setArtifactId(arr[1]);
        out.setVersion(arr[3]);
        return out;
    }
}
