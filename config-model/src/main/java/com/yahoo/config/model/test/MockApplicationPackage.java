// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.component.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.config.application.api.ApplicationPackage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.jimfs.Jimfs;

/**
 * For testing purposes only
 *
 * @author Tony Vaagenes
 */
public class MockApplicationPackage implements ApplicationPackage {

    public static final String DEPLOYED_BY_USER = "user";
    public static final String APPLICATION_NAME = "application";
    public static final long APPLICATION_GENERATION = 1L;
    public static final String MUSIC_SEARCHDEFINITION = createSearchDefinition("music", "foo");
    public static final String BOOK_SEARCHDEFINITION = createSearchDefinition("book", "bar");

    private final java.nio.file.Path root;
    private final String hostsS;
    private final String servicesS;
    private final List<String> searchDefinitions;
    private final String searchDefinitionDir;
    private final Optional<String> deploymentSpec;
    private final Optional<String> validationOverrides;
    private final boolean failOnValidateXml;
    private final QueryProfileRegistry queryProfileRegistry;
    private final ApplicationMetaData applicationMetaData;

    protected MockApplicationPackage(java.nio.file.Path root, String hosts, String services, List<String> searchDefinitions,
                                     String searchDefinitionDir,
                                     String deploymentSpec, String validationOverrides, boolean failOnValidateXml,
                                     String queryProfile, String queryProfileType) {
        this.root = root;
        this.hostsS = hosts;
        this.servicesS = services;
        this.searchDefinitions = searchDefinitions;
        this.searchDefinitionDir = searchDefinitionDir;
        this.deploymentSpec = Optional.ofNullable(deploymentSpec);
        this.validationOverrides = Optional.ofNullable(validationOverrides);
        this.failOnValidateXml = failOnValidateXml;
        queryProfileRegistry = new QueryProfileXMLReader().read(asNamedReaderList(queryProfileType),
                                                                asNamedReaderList(queryProfile));
        applicationMetaData = new ApplicationMetaData(DEPLOYED_BY_USER, "dir", 0L, false, APPLICATION_NAME, "checksum", APPLICATION_GENERATION, 0L);
    }

    /** Returns the root of this application package relative to the current dir */
    protected java.nio.file.Path root() { return root; }

    @Override
    public String getApplicationName() {
        return "mock application";
    }

    @Override
    public Reader getServices() {
        return new StringReader(servicesS);
    }

    @Override
    public Reader getHosts() {
        if (hostsS==null) return null;
        return new StringReader(hostsS);
    }

    @Override
    public List<NamedReader> getSearchDefinitions() {
        ArrayList<NamedReader> readers = new ArrayList<>();
        SearchBuilder searchBuilder = new SearchBuilder(this,
                                                        new RankProfileRegistry(),
                                                        queryProfileRegistry);
        for (String sd : searchDefinitions) {
            try  {
                String name = searchBuilder.importString(sd);
                readers.add(new NamedReader(name + ApplicationPackage.SD_NAME_SUFFIX, new StringReader(sd)));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
        return readers;
    }

    @Override
    public List<NamedReader> searchDefinitionContents() {
        return new ArrayList<>();
    }

    @Override
    public Map<ConfigDefinitionKey, UnparsedConfigDefinition> getAllExistingConfigDefs() {
        return Collections.emptyMap();
    }

    @Override
    public List<NamedReader> getFiles(Path dir,String fileSuffix,boolean recurse) {
        return new ArrayList<>();
    }

    @Override
    public ApplicationFile getFile(Path file) {
        return new MockApplicationFile(file, root);
    }

    @Override
    public File getFileReference(Path path) {
        return Path.fromString(root.toString()).append(path).toFile();
    }

    @Override
    public String getHostSource() {
        return "mock source";
    }

    @Override
    public String getServicesSource() {
        return "mock source";
    }

    @Override
    public Optional<Reader> getDeployment() {
        return deploymentSpec.map(StringReader::new);
    }

    @Override
    public Optional<Reader> getValidationOverrides() {
        return validationOverrides.map(StringReader::new);
    }

    public List<ComponentInfo> getComponentsInfo(Version vespaVersion) {
        return Collections.emptyList();
    }

    public QueryProfileRegistry getQueryProfiles() { return queryProfileRegistry; }

    public ApplicationMetaData getMetaData() { return applicationMetaData; }

    @Override
    public Reader getRankingExpression(String name) {
        File expressionFile = new File(searchDefinitionDir, name);
        try {
            return IOUtils.createReader(expressionFile, "utf-8");
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read ranking expression file '" +
                                               expressionFile.getAbsolutePath() + "'", e);
        }
    }

    public static ApplicationPackage createEmpty() {
        return new MockApplicationPackage.Builder().withHosts(emptyHosts).withServices(emptyServices).build();
    }

    public static ApplicationPackage fromSearchDefinitionDirectory(String dir) {
        return new MockApplicationPackage.Builder()
                .withEmptyHosts()
                .withEmptyServices()
                .withSearchDefinitionDir(dir).build();
    }

    public static class Builder {

        private FileSystem fileSystem = FileSystems.getDefault();
        private java.nio.file.Path root = fileSystem.getPath("nonexisting");
        private String hosts = null;
        private String services = null;
        private List<String> searchDefinitions = Collections.emptyList();
        private String searchDefinitionDir = null;
        private String deploymentSpec = null;
        private String validationOverrides = null;
        private boolean failOnValidateXml = false;
        private String queryProfile = null;
        private String queryProfileType = null;

        public Builder() {
        }

        public Builder withRoot(File root) {
            this.root = this.fileSystem.getPath(root.getName());
            return this;
        }

        public Builder withInMemoryFileSystem() {
            this.fileSystem = Jimfs.newFileSystem();
            this.root = this.fileSystem.getPath("/");
            return this;
        }

        public Builder withEmptyHosts() {
            return this.withHosts(emptyHosts);
        }

        public Builder withHosts(String hosts) {
            this.hosts = hosts;
            return this;
        }

        public Builder withEmptyServices() {
            return this.withServices(emptyServices);
        }

        public Builder withServices(String services) {
            this.services = services;
            return this;
        }

        public Builder withSearchDefinition(String searchDefinition) {
            this.searchDefinitions = Collections.singletonList(searchDefinition);
            return this;
        }

        public Builder withSearchDefinitions(List<String> searchDefinition) {
            this.searchDefinitions = Collections.unmodifiableList(searchDefinition);
            return this;
        }

        public Builder withSearchDefinitionDir(String searchDefinitionDir) {
            this.searchDefinitionDir = searchDefinitionDir;
            return this;
        }

        public Builder withDeploymentSpec(String deploymentSpec) {
            this.deploymentSpec = deploymentSpec;
            return this;
        }

        public Builder withValidationOverrides(String validationOverrides) {
            this.validationOverrides = validationOverrides;
            return this;
        }

        public Builder failOnValidateXml() {
            this.failOnValidateXml = true;
            return this;
        }

        public Builder queryProfile(String queryProfile) {
            this.queryProfile = queryProfile;
            return this;
        }

        public Builder queryProfileType(String queryProfileType) {
            this.queryProfileType = queryProfileType;
            return this;
        }

        public ApplicationPackage build() {
                return new MockApplicationPackage(root, hosts, services, searchDefinitions, searchDefinitionDir,
                                                  deploymentSpec, validationOverrides, failOnValidateXml,
                                                  queryProfile, queryProfileType);
        }
    }

    public static String createSearchDefinition(String name, String fieldName) {
        return "search " + name + " {" +
                "  document " + name + " {" +
                "    field " + fieldName + " type string {}" +
                "  }" +
                "}";
    }

    private static final String emptyServices = "<services version=\"1.0\">" +
            "  <admin version=\"2.0\">" +
            "    <adminserver hostalias=\"node1\" />" +
            "  </admin>" +
            "</services>";

    private static final String emptyHosts = "<hosts>" +
            "  <host name=\"localhost\">" +
            "    <alias>node1</alias>" +
            "  </host>" +
            "</hosts>";


    @Override
    public void validateXML() {
        if (failOnValidateXml) {
            throw new IllegalArgumentException("Error in application package");
        } else {
            throw new UnsupportedOperationException("This application package cannot validate XML");
        }
    }

    private List<NamedReader> asNamedReaderList(String value) {
        if (value == null) return Collections.emptyList();
        return Collections.singletonList(new NamedReader(extractId(value) + ".xml", new StringReader(value)));
    }

    private String extractId(String xmlStringWithIdAttribute) {
        int idStart = xmlStringWithIdAttribute.indexOf("id=");
        int idEnd = Math.min(xmlStringWithIdAttribute.indexOf(" ", idStart),
                             xmlStringWithIdAttribute.indexOf(">", idStart));
        return xmlStringWithIdAttribute.substring(idStart + 4, idEnd - 1);
    }

    public static class MockApplicationFile extends ApplicationFile {

        /** The path to the application package root */
        private final java.nio.file.Path root;

        /** The File pointing to the actual file represented by this */
        private final java.nio.file.Path file;

        public MockApplicationFile(Path filePath, java.nio.file.Path applicationPackagePath) {
            super(filePath);
            this.root = applicationPackagePath;
            file = applicationPackagePath.resolve(path.toString());
        }

        @Override
        public boolean isDirectory() {
            return Files.isDirectory(file);
        }

        @Override
        public boolean exists() {
            return Files.exists(file);
        }

        @Override
        public Reader createReader() {
            try {
                if ( ! exists()) throw new FileNotFoundException("File '" + file + "' does not exist");
                return Files.newBufferedReader(file);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public InputStream createInputStream() {
            try {
                if ( ! exists()) throw new FileNotFoundException("File '" + file + "' does not exist");
                return Files.newInputStream(file);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public ApplicationFile createDirectory() {
            try {
                Files.createDirectories(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        @Override
        public ApplicationFile writeFile(Reader input) {
            try {
                Files.writeString(file, IOUtils.readAll(input), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                return this;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public ApplicationFile appendFile(String value) {
            try {
                Files.writeString(file, value, StandardOpenOption.APPEND);
                return this;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public List<ApplicationFile> listFiles(PathFilter filter) {
            if ( ! isDirectory()) return Collections.emptyList();
            try {
                return Files.list(file)
                        .map(f -> Path.fromString(f.toString()))
                        .filter(filter::accept)
                        .map(f -> new MockApplicationFile(f, root))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public ApplicationFile delete() {
            try {
                Files.delete(file);
                return this;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public MetaData getMetaData() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(ApplicationFile other) {
            return this.getPath().getName().compareTo((other).getPath().getName());
        }
    }

}
