// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import org.w3c.dom.Document;

import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;

/**
 * @author gjoranv
 */
public class PublicApiBundleValidator extends AbstractBundleValidator {

    @Override
    protected void validateManifest(DeployState state, JarFile jar, Manifest mf) {
        String nonPublicApiAttribute = mf.getMainAttributes().getValue("X-JDisc-Non-PublicApi-Import-Package");
        if (nonPublicApiAttribute == null) return;

        var nonPublicApisUsed = Arrays.asList(nonPublicApiAttribute.split(","));
        if (! nonPublicApisUsed.isEmpty()) {
            log(state, Level.WARNING, "Jar file '%s' uses non-public Vespa APIs: %s", filename(jar), nonPublicApisUsed);
        }
    }

    @Override
    protected void validatePomXml(DeployState state, JarFile jar, Document pom) { }

}
