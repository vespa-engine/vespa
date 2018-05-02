// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.buildergen;

import com.yahoo.config.ConfigInstance;

import javax.tools.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Locale;

/**
 * Represents a compiler that waits performing the compilation until the requested builder is requested from the
 * {@link CompiledBuilder}.
 *
 * @author lulf
 * @since 5.2
 */
public class LazyConfigCompiler implements ConfigCompiler {
    private final File outputDirectory;
    private final ClassLoader classLoader;
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    public LazyConfigCompiler(File outputDirectory) {
        this.outputDirectory = outputDirectory;
        try {
            this.classLoader = new URLClassLoader(new URL[]{outputDirectory.toURI().toURL()});
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Unable to create class loader for directory '" + outputDirectory.getAbsolutePath() + "'", e);
        }
    }

    @Override
    public CompiledBuilder compile(ConfigDefinitionClass defClass) {
        Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(new StringSourceObject(defClass.getName(), defClass.getDefinition()));
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ENGLISH, null);
        Iterable<String> options = Arrays.asList("-d", outputDirectory.getAbsolutePath());
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
        return new LazyCompiledBuilder(classLoader, defClass.getName(), new CompilationTask(task, diagnostics));
    }

    /**
     * Lazy implementation of compiled builder that defers compilation until class is requested.
     */
    private static class LazyCompiledBuilder implements CompiledBuilder {
        private final ClassLoader classLoader;
        private final String classUrl;
        private final CompilationTask compilationTask;
        private LazyCompiledBuilder(ClassLoader classLoader, String classUrl, CompilationTask compilationTask) {
            this.classLoader = classLoader;
            this.classUrl = classUrl;
            this.compilationTask = compilationTask;
        }

        @Override
        public <BUILDER extends ConfigInstance.Builder> BUILDER newInstance() {
            compileBuilder();
            String builderClassUrl = classUrl + "$Builder";
            return loadBuilder(builderClassUrl);

        }

        private void compileBuilder() {
            try {
                compilationTask.call();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Error compiling '" + classUrl + "'", e);
            }
        }

        @SuppressWarnings("unchecked")
        private <BUILDER extends ConfigInstance.Builder> BUILDER loadBuilder(String builderClassUrl) {
            try {
                Class<BUILDER> clazz = (Class<BUILDER>) classLoader.<BUILDER>loadClass(builderClassUrl);
                return clazz.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException
                    | NoSuchMethodException | InvocationTargetException e) {
                throw new RuntimeException("Error creating new instance of '" + builderClassUrl + "'", e);
            }
        }
    }
}
