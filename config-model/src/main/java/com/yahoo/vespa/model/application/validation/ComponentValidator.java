// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.DeployLogger;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.zip.ZipException;

/**
 * A validator for bundles.  Uses BND library for some of the validation (not active yet)
 *
 * @author hmusum
 * @since 2010-11-11
 */
public class ComponentValidator extends Validator {
    private JarFile jarFile;

    public ComponentValidator() {
    }

    public ComponentValidator(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        ApplicationPackage app = deployState.getApplicationPackage();
        for (ComponentInfo info : app.getComponentsInfo(deployState.getVespaVersion())) {
            try {
                this.jarFile = new JarFile(app.getFileReference(Path.fromString(info.getPathRelativeToAppDir())));
            } catch (ZipException e) {
                throw new IllegalArgumentException("Error opening jar file '" + info.getPathRelativeToAppDir() +
                                                   "'. Please check that this is a valid jar file");
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                validateAll(deployState.getDeployLogger());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void validateAll(DeployLogger deployLogger) throws IOException {
        validateOSGIHeaders(deployLogger);
    }

    public void validateOSGIHeaders(DeployLogger deployLogger) throws IOException {
        Manifest mf = jarFile.getManifest();
        if (mf == null) {
            throw new IllegalArgumentException("Non-existing or invalid manifest in " + jarFile.getName());
        }

        // Check for required OSGI headers
        Attributes attributes = mf.getMainAttributes();
        HashSet<String> mfAttributes = new HashSet<>();
        for (Object attributeSet : attributes.entrySet()) {
            Map.Entry<Object, Object> e = (Map.Entry<Object, Object>) attributeSet;
            mfAttributes.add(e.getKey().toString());
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
