// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;

/**
 * A validator for bundles.  Uses BND library for some of the validation (not active yet)
 *
 * @author hmusum
 * @author bjorncs
 */
public class BundleValidator extends Validator {

    public BundleValidator() {}

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        ApplicationPackage app = deployState.getApplicationPackage();
        for (ComponentInfo info : app.getComponentsInfo(deployState.getVespaVersion())) {
            try {
                Path path = Path.fromString(info.getPathRelativeToAppDir());
                DeployLogger deployLogger = deployState.getDeployLogger();
                deployLogger.log(Level.FINE, String.format("Validating bundle at '%s'", path));
                JarFile jarFile = new JarFile(app.getFileReference(path));
                validateJarFile(deployLogger, jarFile);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Failed to validate JAR file '" + info.getPathRelativeToAppDir() + "'", e);
            }
        }
    }

    void validateJarFile(DeployLogger deployLogger, JarFile jarFile) throws IOException {
        validateOSGIHeaders(deployLogger, jarFile);
    }

    public void validateOSGIHeaders(DeployLogger deployLogger, JarFile jarFile) throws IOException {
        Manifest mf = jarFile.getManifest();
        if (mf == null) {
            throw new IllegalArgumentException("Non-existing or invalid manifest in " + jarFile.getName());
        }

        // Check for required OSGI headers
        Attributes attributes = mf.getMainAttributes();
        HashSet<String> mfAttributes = new HashSet<>();
        for (Map.Entry<Object,Object> entry : attributes.entrySet()) {
            mfAttributes.add(entry.getKey().toString());
        }
        List<String> requiredOSGIHeaders = Arrays.asList(
                "Bundle-ManifestVersion", "Bundle-Name", "Bundle-SymbolicName", "Bundle-Version");
        for (String header : requiredOSGIHeaders) {
            if (!mfAttributes.contains(header)) {
                throw new IllegalArgumentException("Required OSGI header '" + header +
                        "' was not found in manifest in '" + jarFile.getName() + "'");
            }
        }

        if (attributes.getValue("Bundle-Version").endsWith(".SNAPSHOT")) {
            deployLogger.logApplicationPackage(Level.WARNING, "Deploying snapshot bundle " + jarFile.getName() +
                    ".\nTo use this bundle, you must include the qualifier 'SNAPSHOT' in  the version specification in services.xml.");
        }
    }
}
