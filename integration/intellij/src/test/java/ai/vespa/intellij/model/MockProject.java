// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.model;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.picocontainer.PicoContainer;

import java.io.File;
import java.util.Map;

public class MockProject implements Project {

    private final File schemasDir;

    public MockProject(File schemasDir) {
        this.schemasDir = schemasDir;
    }

    @Override
    public String getName() { return "mock project"; }

    @Override
    public VirtualFile getBaseDir() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBasePath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public VirtualFile getProjectFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getProjectFilePath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public VirtualFile getWorkspaceFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLocationHash() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void save() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() { return true; }

    @Override
    public boolean isInitialized() { return true; }

    @Override
    public <T> T getComponent(Class<T> interfaceClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] getComponents(Class<T> baseClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PicoContainer getPicoContainer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInjectionForExtensionSupported() { return false; }

    @Override
    public MessageBus getMessageBus() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDisposed() { return false; }

    @Override
    public Condition<?> getDisposed() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T instantiateClassWithConstructorInjection(Class<T> aClass, Object key, PluginId pluginId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuntimeException createError(Throwable error, PluginId pluginId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuntimeException createError(String message, PluginId pluginId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuntimeException createError(String message, Throwable error, PluginId pluginId, Map<String, String> attachments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Class<T> loadClass(String className, PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActivityCategory getActivityCategory(boolean isExtension) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dispose() {
    }

    @Override
    public <T> T getUserData(Key<T> key) { return null; }

    @Override
    public <T> void putUserData(Key<T> key, T value) {
        throw new UnsupportedOperationException();
    }

}
