// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa;

import com.yahoo.config.codegen.MakeConfig;
import com.yahoo.config.codegen.MakeConfigProperties;
import com.yahoo.config.codegen.PropertyException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.yahoo.config.codegen.DefParser.DEFAULT_PACKAGE_PREFIX;


/**
 * Goal which generates config classes from def-files.
 */
@Mojo(name = "config-gen", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class ConfigGenMojo extends AbstractMojo {
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    /**
     * Generate source to here.
     */
    @Parameter(property = "plugin.configuration.outputDirectory",
            defaultValue = "${project.build.directory}/generated-sources/vespa-configgen-plugin")
    private File outputDirectory;

	/**
	 * Location of def files to generate source from, comma separated list of directories.
     */
    @Parameter(property = "plugin.configuration.defFilesDirectories",
            defaultValue = "src/main/resources/configdefinitions")
    private String defFilesDirectories;

    /**
     * Set to 'false' to create pure data config classes without any vespa framework code
     */
    @Parameter(property = "plugin.configuration.useFramework", defaultValue = "true")
    private Boolean useFramework;

    /**
     * Set to 'false' to allow generation of config classes that have the default namespace 'config'.
     */
    @Parameter(property = "plugin.configuration.requireNamespace", defaultValue = "true")
    private Boolean requireNamespace;

    /**
     * Package prefix of generated configs. The resulting package name will be packagePrefix.namespace if specified.
     */
    @Parameter(property = "plugin.configuration.packagePrefix", defaultValue = DEFAULT_PACKAGE_PREFIX)
    private String packagePrefix;

    /**
     * If true, the config sources are only intended for use during testing.
     *
     */
    @Parameter(property = "plugin.configuration.testConfig", defaultValue = "false")
    private boolean testConfig;

    /**
     * Returns List of all def-files in all defFilesDirectories, including path.
     * @return The list of def-files.
     */
    private List<String> getDefFileNames() {
        List<String> defFileNames = new ArrayList<>();

        String[] dirNames = defFilesDirectories.split(",");
        List<File> dirs = new ArrayList<>();
        for (String dirName : dirNames) {
            File dir = new File(project.getBasedir(), dirName.trim());
            if (dir.isDirectory() && dir.canRead()) {
                dirs.add(dir);
            }
        }
        for (File dir : dirs) {
            String[] dirFiles = dir.list(new FilenameFilter () {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".def");
                }
            });
            for (String filename : dirFiles) {
                defFileNames.add(dir.toString() + File.separator + filename);
            }
        }
        return defFileNames;
    }

    public void execute()
        throws MojoExecutionException
    {
        List<String> defFileNames = getDefFileNames();

        // Silent failure when there are no def-files to process...
        if (defFileNames.size() == 0) {
            return;
        }

        String configSpec = String.join(",", defFileNames);

        boolean generateSources;
        // optionally create the output directory
        File f = outputDirectory;
        if (!f.exists() ) {
            f.mkdirs();
            generateSources = true;
            getLog().debug("Output dir does not exist");
        } else {
            getLog().debug("Output dir exists");
            generateSources = isSomeGeneratedFileStale(outputDirectory, defFileNames);
        }

        if (generateSources) {
            getLog().debug("Will generate config class files");
            try {
                MakeConfigProperties config = new MakeConfigProperties(outputDirectory.toString(),
                                                                       configSpec,
                                                                       null,
                                                                       null,
                                                                       null,
                                                                       useFramework.toString(),
                                                                       packagePrefix);
                if (!MakeConfig.makeConfig(config)) {
                    throw new MojoExecutionException("Failed to generate config for: " + configSpec);
                }
            } catch (IOException | PropertyException e) {
                throw new MojoExecutionException("Failed to generate config for: " + configSpec, e);
            }
        } else {
            getLog().debug("No changes, will not generate config class files");
        }

        // We have created files, so add the output directory to the compile source root
        addSourceRoot(outputDirectory.toString());
    }

    private boolean isSomeGeneratedFileStale(File outputDirectory, List<String> defFileNames) {
        long oldestGeneratedModifiedTime = walk(outputDirectory.toPath())
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .peek(f -> getLog().debug("Checking generated file " + f))
                    .mapToLong(File::lastModified)
                    .min()
                    .orElse(Long.MAX_VALUE);

        long lastModifiedSource = defFileNames.stream()
                .peek(sourceFile -> getLog().debug("Checking source file " + sourceFile))
                .map(File::new)
                .mapToLong(File::lastModified)
                .max()
                .orElse(0L);

        getLog().debug("lastModifiedSource: " + lastModifiedSource + ", oldestTGeneratedModified: " + oldestGeneratedModifiedTime);
        return lastModifiedSource > oldestGeneratedModifiedTime;
    }

    private static Stream<Path> walk(Path path) {
        try {
            return Files.walk(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addSourceRoot(String outputDirectory) {
        if (testConfig) {
            project.addTestCompileSourceRoot(outputDirectory);
        } else {
            project.addCompileSourceRoot(outputDirectory);
        }
    }
}
