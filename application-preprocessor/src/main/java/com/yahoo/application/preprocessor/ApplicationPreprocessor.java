// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.preprocessor;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.DeployData;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.yolean.Exceptions;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Main entry for preprocessing an application package.
 *
 * @author Ulf Lilleengen
 */
public class ApplicationPreprocessor {

    private final File applicationDir;
    private final Optional<File> outputDir;
    private final Optional<InstanceName> instance;
    private final Optional<Environment> environment;
    private final Optional<RegionName> region;
    private final Tags tags;

    public ApplicationPreprocessor(File applicationDir,
                                   Optional<File> outputDir,
                                   Optional<InstanceName> instance,
                                   Optional<Environment> environment,
                                   Optional<RegionName> region,
                                   Tags tags) {
        this.applicationDir = applicationDir;
        this.outputDir = outputDir;
        this.instance = instance;
        this.environment = environment;
        this.region = region;
        this.tags = tags;
    }

    public void run() throws IOException {
        FilesApplicationPackage.Builder applicationPackageBuilder = new FilesApplicationPackage.Builder(applicationDir);
        outputDir.ifPresent(applicationPackageBuilder::preprocessedDir);
        applicationPackageBuilder.deployData(new DeployData(applicationDir.getAbsolutePath(),
                                                            ApplicationId.from(TenantName.defaultName(),
                                                                               ApplicationName.defaultName(),
                                                                               instance.orElse(InstanceName.defaultName())),
                                                            tags,
                                                            System.currentTimeMillis(),
                                                            false,
                                                            0L,
                                                            -1));

        ApplicationPackage preprocessed = applicationPackageBuilder.build().preprocess(new Zone(environment.orElse(Environment.defaultEnvironment()),
                                                                                                region.orElse(RegionName.defaultName())),
                                                                                       new BaseDeployLogger());
        preprocessed.validateXML();
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: vespa-application-preprocessor <application package path> [instance] [environment] [region] [tag] [output path]");
            System.exit(1);
        }
        File applicationDir = new File(args[0]);
        Optional<InstanceName> instance;
        Optional<Environment> environment;
        Optional<RegionName> region;
        Tags tags;
        Optional<File> outputDir;
        if (args.length <= 4) { // Legacy: No instance and tags
            instance = Optional.empty();
            environment = args.length > 1 ? Optional.of(Environment.valueOf(args[1])) : Optional.empty();
            region = args.length > 2 ? Optional.of(RegionName.from(args[2])) : Optional.empty();
            tags = Tags.empty();
            outputDir = args.length > 3 ? Optional.of(new File(args[3])) : Optional.empty();
        }
        else {
            instance = Optional.of(InstanceName.from(args[1]));
            environment = Optional.of(Environment.valueOf(args[2]));
            region = Optional.of(RegionName.from(args[3]));
            tags = Tags.fromString(args[4]);
            outputDir = args.length > 5 ? Optional.of(new File(args[5])) : Optional.empty();
        }
        ApplicationPreprocessor preprocessor = new ApplicationPreprocessor(applicationDir,
                                                                           outputDir,
                                                                           instance,
                                                                           environment,
                                                                           region,
                                                                           tags);
        try {
            preprocessor.run();
            System.out.println("Application preprocessed successfully and written to " +
                               outputDir.orElse(new File(applicationDir, FilesApplicationPackage.preprocessed)).getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error validating application package: " + Exceptions.toMessageString(e));
            System.exit(1);
        }
    }

}
