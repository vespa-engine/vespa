// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

/**
 * Validation of xml files in application package against RELAX NG schemas.
 *
 * @author hmusum
 */
public class ApplicationPackageXmlFilesValidator {

    private final AppSubDirs appDirs;
    
    private final SchemaValidators validators;

    private static final FilenameFilter xmlFilter = (dir, name) -> name.endsWith(".xml");


    private ApplicationPackageXmlFilesValidator(AppSubDirs appDirs, Version vespaVersion) {
        this.appDirs = appDirs;
        this.validators = new SchemaValidators(vespaVersion);
    }

    public static ApplicationPackageXmlFilesValidator create(File appDir, Version vespaVersion) {
        return new ApplicationPackageXmlFilesValidator(new AppSubDirs(appDir), vespaVersion);
    }

    public void checkApplication() throws IOException {
        validate(validators.servicesXmlValidator(), servicesFileName());
        validateOptional(validators.hostsXmlValidator(), FilesApplicationPackage.HOSTS);
        validateOptional(validators.deploymentXmlValidator(), FilesApplicationPackage.DEPLOYMENT_FILE.getName());
        validateOptional(validators.validationOverridesXmlValidator(), FilesApplicationPackage.VALIDATION_OVERRIDES.getName());
        validateRouting(appDirs.routingTables());
    }

    // For testing
    public void checkIncludedDirs(ApplicationPackage app) throws IOException {
        for (String includedDir : app.getUserIncludeDirs()) {
            List<NamedReader> includedFiles = app.getFiles(Path.fromString(includedDir), ".xml", true);
            for (NamedReader file : includedFiles) {
                validators.containerIncludeXmlValidator().validate(file);
            }
        }
    }

    private void validateOptional(SchemaValidator validator, String file) throws IOException {
        if ( ! appDirs.file(file).exists()) return;
        validate(validator, file);
    }

    private void validate(SchemaValidator validator, String filename) throws IOException {
        validator.validate(appDirs.file(filename));
    }

    private String servicesFileName() {
        String servicesFile = FilesApplicationPackage.SERVICES;
        if ( ! appDirs.file(servicesFile).exists()) {
            throw new IllegalArgumentException("Application package in " + appDirs.root() +
                                               " must contain " + FilesApplicationPackage.SERVICES);
        }
        return servicesFile;
    }

    private void validateRouting(Tuple2<File, String> directory) throws IOException {
        if ( ! directory.first.isDirectory()) return;
        validateRouting(validators.routingStandaloneXmlValidator(), directory);
    }

    private void validateRouting(SchemaValidator validator, Tuple2<File, String> directory) throws IOException {
        File dir = directory.first;
        if ( ! dir.isDirectory()) return;

        String directoryName = directory.second;
        for (File f : dir.listFiles(xmlFilter)) {
            if (f.isDirectory())
                validateRouting(validator, new Tuple2<>(f, directoryName + File.separator + f.getName()));
            else
                validator.validate(f, directoryName + File.separator + f.getName());
        }
    }

}
