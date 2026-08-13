package ai.vespa.schemals.intellij;

import com.intellij.openapi.application.PathManager;

import java.nio.file.Path;

final class PluginPaths {

    private PluginPaths() {}

    static Path libDirectoryOf(Class<?> clazz) {
        Path jar = PathManager.getJarForClass(clazz);
        if (jar == null) {
            throw new IllegalStateException("Could not locate the jar containing " + clazz.getName() +
                    ". Cannot start the Vespa Schema Language Support plugin.");
        }
        Path libDirectory = jar.getParent();
        if (libDirectory == null) {
            throw new IllegalStateException("Unexpected plugin layout: " + jar +
                    " is not inside a plugin lib directory. Cannot start the Vespa Schema Language Support plugin.");
        }
        return libDirectory.toAbsolutePath();
    }

    static Path pluginDirectoryOf(Class<?> clazz) {
        Path libDirectory = libDirectoryOf(clazz);
        Path pluginDirectory = libDirectory.getParent();
        if (pluginDirectory == null) {
            throw new IllegalStateException("Unexpected plugin layout: " + libDirectory +
                    " has no parent directory. Cannot start the Vespa Schema Language Support plugin.");
        }
        return pluginDirectory.toAbsolutePath();
    }
}