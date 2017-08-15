// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.collections.Tuple2;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.path.Path;
import com.yahoo.io.reader.NamedReader;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Validation of xml files in application package against RELAX NG schemas.
 *
 * @author hmusum
 */
public class ApplicationPackageXmlFilesValidator {

    private final AppSubDirs appDirs;
    
    /** The Vespa version this package should be validated against */
    private final Version vespaVersion;

    private static final FilenameFilter xmlFilter = (dir, name) -> name.endsWith(".xml");


    public ApplicationPackageXmlFilesValidator(AppSubDirs appDirs, Version vespaVersion) {
        this.appDirs = appDirs;
        this.vespaVersion = vespaVersion;
    }

    public static ApplicationPackageXmlFilesValidator createDefaultXMLValidator(File appDir, Version vespaVersion) {
        return new ApplicationPackageXmlFilesValidator(new AppSubDirs(appDir), vespaVersion);
    }

    public static ApplicationPackageXmlFilesValidator createTestXmlValidator(File appDir, Version vespaVersion) {
        return new ApplicationPackageXmlFilesValidator(new AppSubDirs(appDir), vespaVersion);
    }

    @SuppressWarnings("deprecation")
    public void checkApplication() throws IOException {
        validate(SchemaValidator.servicesXmlSchemaName, servicesFileName());
        validateOptional(SchemaValidator.hostsXmlSchemaName, FilesApplicationPackage.HOSTS);
        validateOptional(SchemaValidator.deploymentXmlSchemaName, FilesApplicationPackage.DEPLOYMENT_FILE.getName());
        validateOptional(SchemaValidator.validationOverridesXmlSchemaName, FilesApplicationPackage.VALIDATION_OVERRIDES.getName());

        if (appDirs.searchdefinitions().exists()) {
            if (FilesApplicationPackage.getSearchDefinitionFiles(appDirs.root()).isEmpty()) {
                throw new IllegalArgumentException("Application package in " + appDirs.root() +
                        " must contain at least one search definition (.sd) file when directory searchdefinitions/ exists.");
            }
        }

        validate(appDirs.routingtables, "routing-standalone.rnc");
    }

    // For testing
    public static void checkIncludedDirs(ApplicationPackage app, Version vespaVersion) throws IOException {
        for (String includedDir : app.getUserIncludeDirs()) {
            List<NamedReader> includedFiles = app.getFiles(Path.fromString(includedDir), ".xml", true);
            for (NamedReader file : includedFiles) {
                createSchemaValidator("container-include.rnc", vespaVersion).validate(file);
            }
        }
    }

    private void validateOptional(String schema, String file) throws IOException {
        if ( ! appDirs.file(file).exists()) return;
        validate(schema, file);
    }

    private void validate(String schema, String file) throws IOException {
        createSchemaValidator(schema, vespaVersion).validate(appDirs.file(file));
    }

    @SuppressWarnings("deprecation")
    private String servicesFileName() {
        String servicesFile = FilesApplicationPackage.SERVICES;
        if ( ! appDirs.file(servicesFile).exists()) {
            throw new IllegalArgumentException("Application package in " + appDirs.root() +
                                               " must contain " + FilesApplicationPackage.SERVICES);
        }
        return servicesFile;
    }

    private void validate(Tuple2<File, String> directory, String schemaFile) throws IOException {
        if ( ! directory.first.isDirectory()) return;
        validate(directory, createSchemaValidator(schemaFile, vespaVersion));
    }

    private void validate(Tuple2<File, String> directory, SchemaValidator validator) throws IOException {
        File dir = directory.first;
        if ( ! dir.isDirectory()) return;

        String directoryName = directory.second;
        for (File f : dir.listFiles(xmlFilter)) {
            if (f.isDirectory())
                validate(new Tuple2<>(f, directoryName + File.separator + f.getName()),validator);
            else
                validator.validate(f, directoryName + File.separator + f.getName());
        }
    }

    private static SchemaValidator createSchemaValidator(String schemaFile, Version vespaVersion) {
        return new SchemaValidator(schemaFile, new BaseDeployLogger(), vespaVersion);
    }

}
