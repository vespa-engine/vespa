// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application;

import com.yahoo.api.annotations.Beta;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.text.StringUtilities;
import com.yahoo.text.Utf8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.Files.createTempDirectory;

/**
 * Builds an application package on disk and returns a path to the result.
 *
 * @author Tony Vaagenes
 */
@Beta
public class ApplicationBuilder {

    private Path applicationDir = createTempDirectory("application");
    private Networking networking = Networking.disable;

    public ApplicationBuilder() throws IOException {}

    public ApplicationBuilder servicesXml(String servicesXml) throws IOException {
        ensureNotAlreadyBuild();

        String content = servicesXml.startsWith("<?xml") ?
                servicesXml :
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" + '\n' + servicesXml;

        createFile(applicationDir.resolve("services.xml"), content);
        return  this;
    }

    public ApplicationBuilder documentType(String name, String searchDefinition) throws IOException {
        Path path = nestedResource(ApplicationPackage.SCHEMAS_DIR, name, ApplicationPackage.SD_NAME_SUFFIX);
        createFile(path, searchDefinition);
        return this;
    }

    public ApplicationBuilder rankExpression(String name, String rankExpressionContent) throws IOException {
        Path path = nestedResource(ApplicationPackage.SCHEMAS_DIR, name, ApplicationPackage.RANKEXPRESSION_NAME_SUFFIX);
        createFile(path, rankExpressionContent);
        return this;
    }

    public ApplicationBuilder queryProfile(String name, String queryProfile) throws IOException {
        Path path = nestedResource(ApplicationPackage.QUERY_PROFILES_DIR, name, ".xml");
        createFile(path, queryProfile);
        return this;
    }

    public ApplicationBuilder queryProfileType(String name, String queryProfileType) throws IOException {
        Path path = nestedResource(ApplicationPackage.QUERY_PROFILE_TYPES_DIR, name, ".xml");
        createFile(path, queryProfileType);
        return this;
    }

    /**
     * Disabled per default
     */
    public ApplicationBuilder networking(Networking networking) {
        this.networking = networking;
        return this;
    }

    public Application build() {
        Application application =  new Application(applicationDir, networking, true);
        applicationDir = null;
        return application;
    }

    private Path nestedResource(com.yahoo.path.Path nestedPath, String name, String fileType) {
        ensureNotAlreadyBuild();

        String nameWithoutSuffix = StringUtilities.stripSuffix(name, fileType);
        return applicationDir.resolve(nestedPath.getRelative()).resolve(nameWithoutSuffix + fileType);
    }

    private void ensureNotAlreadyBuild() {
        if (applicationDir == null)
            throw new RuntimeException("The ApplicationBuilder must not be used after the build method has been called.");
    }

    private void createFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, Utf8.toBytes(content));
    }

    Path getPath() {
        return applicationDir;
    }

}
