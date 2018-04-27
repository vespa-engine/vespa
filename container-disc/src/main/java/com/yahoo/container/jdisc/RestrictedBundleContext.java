// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import org.osgi.framework.*;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.22.0
 */
public class RestrictedBundleContext implements BundleContext {
    private final BundleContext wrapped;

    public RestrictedBundleContext(BundleContext wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public ServiceRegistration<?> registerService(String[] strings, Object o, Dictionary<String, ?> stringDictionary) {
        if (wrapped == null) {
            return null;
        }
        return wrapped.registerService(strings, o, stringDictionary);
    }

    @Override
    public ServiceRegistration<?> registerService(String localHostname, Object o, Dictionary<String, ?> stringDictionary) {
        if (wrapped == null) {
            return null;
        }
        return wrapped.registerService(localHostname, o, stringDictionary);
    }

    @Override
    public <S> ServiceRegistration<S> registerService(Class<S> sClass, S s, Dictionary<String, ?> stringDictionary) {
        if (wrapped == null) {
            return null;
        }
        return wrapped.registerService(sClass, s, stringDictionary);
    }

    @Override
    public <S> ServiceRegistration<S> registerService(Class<S> aClass, ServiceFactory<S> serviceFactory, Dictionary<String, ?> dictionary) {
        return null;
    }

    @Override
    public ServiceReference<?>[] getServiceReferences(String localHostname, String localHostname2) throws InvalidSyntaxException {
        if (wrapped == null) {
            return new ServiceReference<?>[0];
        }
        return wrapped.getServiceReferences(localHostname, localHostname2);
    }

    @Override
    public ServiceReference<?>[] getAllServiceReferences(String localHostname, String localHostname2) throws InvalidSyntaxException {
        if (wrapped == null) {
            return new ServiceReference<?>[0];
        }
        return wrapped.getAllServiceReferences(localHostname, localHostname2);
    }

    @Override
    public ServiceReference<?> getServiceReference(String localHostname) {
        if (wrapped == null) {
            return null;
        }
        return wrapped.getServiceReference(localHostname);
    }

    @Override
    public <S> ServiceReference<S> getServiceReference(Class<S> sClass) {
        if (wrapped == null) {
            return null;
        }
        return wrapped.getServiceReference(sClass);
    }

    @Override
    public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> sClass, String localHostname) throws InvalidSyntaxException {
        if (wrapped == null) {
            return Collections.<ServiceReference<S>>emptyList();
        }
        return wrapped.getServiceReferences(sClass, localHostname);
    }

    @Override
    public <S> S getService(ServiceReference<S> sServiceReference) {
        if (wrapped == null) {
            return null;
        }
        return wrapped.getService(sServiceReference);
    }

    @Override
    public boolean ungetService(ServiceReference<?> serviceReference) {
        if (wrapped == null) {
            return false;
        }
        return wrapped.ungetService(serviceReference);
    }

    @Override
    public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> serviceReference) {
        return null;
    }


    //---------------------


    @Override
    public String getProperty(String localHostname) {
        throw newException();
    }

    @Override
    public Bundle getBundle() {
        throw newException();
    }

    @Override
    public Bundle installBundle(String localHostname, InputStream inputStream) throws BundleException {
        throw newException();
    }

    @Override
    public Bundle installBundle(String localHostname) throws BundleException {
        throw newException();
    }

    @Override
    public Bundle getBundle(long l) {
        throw newException();
    }

    @Override
    public Bundle[] getBundles() {
        throw newException();
    }

    @Override
    public void addServiceListener(ServiceListener serviceListener, String localHostname) throws InvalidSyntaxException {
        throw newException();
    }

    @Override
    public void addServiceListener(ServiceListener serviceListener) {
        throw newException();
    }

    @Override
    public void removeServiceListener(ServiceListener serviceListener) {
        throw newException();
    }

    @Override
    public void addBundleListener(BundleListener bundleListener) {
        throw newException();
    }

    @Override
    public void removeBundleListener(BundleListener bundleListener) {
        throw newException();
    }

    @Override
    public void addFrameworkListener(FrameworkListener frameworkListener) {
        throw newException();
    }

    @Override
    public void removeFrameworkListener(FrameworkListener frameworkListener) {
        throw newException();
    }

    @Override
    public File getDataFile(String localHostname) {
        throw newException();
    }

    @Override
    public Filter createFilter(String localHostname) throws InvalidSyntaxException {
        throw newException();
    }

    @Override
    public Bundle getBundle(String localHostname) {
        throw newException();
    }

    private RuntimeException newException() {
        return new UnsupportedOperationException("This BundleContext operation is not available to components.");
    }
}
