// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.osgi;

import com.yahoo.log.LogLevel;
import org.osgi.framework.Bundle;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Makes jackson1 jaxrs integration use jersey1 and jackson2 jaxrs integration use jersey2
 * @author tonytv
 */
public class JacksonJaxrsResolverHook implements ResolverHook {
    private static Logger log = Logger.getLogger(JacksonJaxrsResolverHook.class.getName());

    public static class Factory implements ResolverHookFactory {
        @Override
        public ResolverHook begin(Collection<BundleRevision> bundleRevisions) {
            return new JacksonJaxrsResolverHook();
        }
    }

    @Override
    public void filterResolvable(Collection<BundleRevision> bundleRevisions) {}

    @Override
    public void filterSingletonCollisions(BundleCapability bundleCapability, Collection<BundleCapability> bundleCapabilities) {}

    @Override
    public void filterMatches(BundleRequirement bundleRequirement, Collection<BundleCapability> bundleCapabilities) {
        Bundle bundle = bundleRequirement.getRevision().getBundle();
        String symbolicName = bundle.getSymbolicName();

        log.log(LogLevel.DEBUG, "Filtering matches for " + symbolicName);

        if (symbolicName.startsWith("com.fasterxml.jackson.jaxrs"))
            removeBundlesMatching(bundleCapabilities, JacksonJaxrsResolverHook::isJaxRs1Bundle);
        else if (symbolicName.equals("jackson-jaxrs") && bundle.getVersion().getMajor() == 1) {
            removeBundlesMatching(bundleCapabilities, JacksonJaxrsResolverHook::isJaxRs2Bundle);
        }
    }

    private static boolean isJaxRs1Bundle(String bundleSymbolicName) {
        return bundleSymbolicName.startsWith("com.sun.jersey.");
    }

    private static boolean isJaxRs2Bundle(String bundleSymbolicName) {
        return bundleSymbolicName.startsWith("org.glassfish.jersey.") ||
                bundleSymbolicName.equals("javax.ws.rs-api");
    }

    private void removeBundlesMatching(Collection<BundleCapability> bundleCapabilities, Predicate<String> symbolicNamePredicate) {
        for (Iterator<BundleCapability> i = bundleCapabilities.iterator(); i.hasNext(); ) {
            BundleCapability bundleCapability = i.next();
            String symbolicName = bundleCapability.getRevision().getSymbolicName();

            if (symbolicNamePredicate.test(symbolicName)) {
                log.log(LogLevel.DEBUG, "- Removing bundle " + symbolicName);
                i.remove();
            }
        }
    }

    @Override
    public void end() {}
}
