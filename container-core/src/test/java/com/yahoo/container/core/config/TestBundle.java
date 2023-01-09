// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.container.bundle.MockBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

import java.util.List;

/**
 * @author gjoranv
 */
public class TestBundle extends MockBundle {

    private static final BundleRevision revision = new TestBundleRevision();

    private final String symbolicName;

    boolean started = false;

    public TestBundle(String symbolicName) {
        this.symbolicName = symbolicName;
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public String getSymbolicName() {
        return symbolicName;
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T> T adapt(Class<T> type) {
        if (type.equals(BundleRevision.class)) {
            return (T) revision;
        } else {
            throw new UnsupportedOperationException();
        }
    }


    static class TestBundleRevision implements BundleRevision {

        // Ensure this is not seen as a fragment bundle.
        @Override
        public int getTypes() {
            return 0;
        }

        @Override
        public String getSymbolicName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Version getVersion() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BundleCapability> getDeclaredCapabilities(String namespace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BundleRequirement> getDeclaredRequirements(String namespace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BundleWiring getWiring() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Capability> getCapabilities(String namespace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Requirement> getRequirements(String namespace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle getBundle() {
            throw new UnsupportedOperationException();
        }
    }

}
