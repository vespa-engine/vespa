// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import com.yahoo.vespa.config.ConfigDefinitionKey;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * For testing purposes only
 *
 * @author Tony Vaagenes
 */
public class MockApplicationPackage implements ApplicationPackage {

    public static final String APPLICATION_NAME = "application";
    public static final long APPLICATION_GENERATION = 1L;
    public static final String MUSIC_SCHEMA = createSchema("music", "foo");
    public static final String BOOK_SCHEMA = createSchema("book", "bar");

    private final File root;
    private final String hostsS;
    private final String servicesS;
    private final List<String> schemas;
    private final Map<Path, MockApplicationFile> files;
    private final String schemaDir;
    private final Optional<String> deploymentSpec;
    private final Optional<String> validationOverrides;
    private final boolean failOnValidateXml;
    private final QueryProfileRegistry queryProfileRegistry;
    private final ApplicationMetaData applicationMetaData;

    protected MockApplicationPackage(File root, String hosts, String services, List<String> schemas,
                                     Map<Path, MockApplicationFile> files,
                                     String schemaDir,
                                     String deploymentSpec, String validationOverrides, boolean failOnValidateXml,
                                     String queryProfile, String queryProfileType) {
        this.root = root;
        this.hostsS = hosts;
        this.servicesS = services;
        this.schemas = schemas;
        this.files = files;
        this.schemaDir = schemaDir;
        this.deploymentSpec = Optional.ofNullable(deploymentSpec);
        this.validationOverrides = Optional.ofNullable(validationOverrides);
        this.failOnValidateXml = failOnValidateXml;
        queryProfileRegistry = new QueryProfileXMLReader().read(asNamedReaderList(queryProfileType),
                                                                asNamedReaderList(queryProfile));
        applicationMetaData = new ApplicationMetaData("dir",
                                                      0L,
                                                      false,
                                                      ApplicationId.from(TenantName.defaultName(),
                                                                         ApplicationName.from(APPLICATION_NAME),
                                                                         InstanceName.defaultName()),
                                                      Tags.empty(),
                                                      "checksum",
                                                      APPLICATION_GENERATION,
                                                      0L);
    }

    /** Returns the root of this application package relative to the current dir */
    protected File root() { return root; }

    @Override
    public ApplicationId getApplicationId() { return ApplicationId.from("default", "mock-application", "default"); }

    @Override
    public Reader getServices() {
        return new StringReader(servicesS);
    }

    @Override
    public Reader getHosts() {
        if (hostsS == null) return null;
        return new StringReader(hostsS);
    }

    @Override
    public List<NamedReader> getSchemas() {
        ArrayList<NamedReader> readers = new ArrayList<>();
        for (String sd : schemas)
            readers.add(new NamedReader(extractSdName(sd) + ApplicationPackage.SD_NAME_SUFFIX, new StringReader(sd)));
        return readers;
    }

    /** To avoid either double parsing or supplying a name explicitly */
    private String extractSdName(String sd) {
        String s = sd.split("\n")[0];
        if (s.startsWith("schema"))
            s = s.substring("schema".length()).trim();
        else if (s.startsWith("search"))
            s = s.substring("search".length()).trim();
        else
            throw new IllegalArgumentException("Expected the first line of a schema but got '" + sd + "'");
        int end = s.indexOf(' ');
        if (end < 0)
            end = s.indexOf('}');
        return s.substring(0, end).trim();
    }

    @Override
    public Map<ConfigDefinitionKey, UnparsedConfigDefinition> getAllExistingConfigDefs() {
        return Collections.emptyMap();
    }

    @Override
    public List<NamedReader> getFiles(Path dir, String fileSuffix, boolean recurse) {
        if (dir.elements().contains(ApplicationPackage.SEARCH_DEFINITIONS_DIR.getName()))
            return List.of(); // No legacy paths
        return getFiles(new File(root, dir.getName()), fileSuffix, recurse);
    }

    private List<NamedReader> getFiles(File dir, String fileSuffix, boolean recurse) {
        try {
            if ( ! dir.exists()) return List.of();
            List<NamedReader> readers = new ArrayList<>();
            for (var i = Files.list(dir.toPath()).iterator(); i.hasNext(); ) {
                var file = i.next();
                if (file.getFileName().toString().endsWith(fileSuffix))
                    readers.add(new NamedReader(file.toString(), IOUtils.createReader(file.toString())));
                else if (recurse)
                    readers.addAll(getFiles(new File(dir, file.getFileName().toString()), fileSuffix, recurse));
            }
            return readers;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ApplicationFile getFile(Path file) {
        if (files.containsKey(file)) return files.get(file);
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
        File expressionFile = new File(schemaDir, name);
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
                       .withSchemaDir(dir).build();
    }

    // TODO: It might work to just merge this and the above
    public static ApplicationPackage fromSearchDefinitionAndRootDirectory(String dir) {
        return new MockApplicationPackage.Builder()
                .withRoot(new File(dir))
                .withEmptyHosts()
                .withEmptyServices()
                .withSchemaDir(dir).build();
    }

    public static class Builder {

        private File root = new File("nonexisting");
        private String hosts = null;
        private String services = null;
        private List<String> schemas = Collections.emptyList();
        private Map<Path, MockApplicationFile> files = new LinkedHashMap<>();
        private String schemaDir = null;
        private String deploymentSpec = null;
        private String validationOverrides = null;
        private boolean failOnValidateXml = false;
        private String queryProfile = null;
        private String queryProfileType = null;

        public Builder() {
        }

        public Builder withRoot(File root) {
            this.root = root;
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
            this.schemas = Collections.singletonList(searchDefinition);
            return this;
        }

        public Builder withSchemas(List<String> searchDefinition) {
            this.schemas = Collections.unmodifiableList(searchDefinition);
            return this;
        }

        /** Additional (mock) files that will exist in this application package, with their content. */
        public Builder withFiles(Map<Path, String> files) {
            Map<Path, MockApplicationFile> mockFiles = new HashMap<>();
            for (var file : files.entrySet())
                mockFiles.put(file.getKey(), new MockApplicationFile(file.getKey(),
                                                                     root, file.getValue()));
            this.files = mockFiles;
            return this;
        }

        public Builder withSchemaDir(String schemaDir) {
            this.schemaDir = schemaDir;
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
            return new MockApplicationPackage(root, hosts, services, schemas, files, schemaDir,
                                              deploymentSpec, validationOverrides, failOnValidateXml,
                                              queryProfile, queryProfileType);
        }
    }

    public static String createSchema(String name, String fieldName) {
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

        /** The application package root */
        private final File root;

        /** The File pointing to the actual file represented by this */
        private final File file;

        /** The content of this file, or null to read it from the file system. */
        private final String content;

        public MockApplicationFile(Path relativeFile, File root) {
            this(relativeFile, root, null);
        }

        private MockApplicationFile(Path relativeFile, File root, String content) {
            super(relativeFile);
            this.root = root;
            this.file = root.toPath().resolve(relativeFile.toString()).toFile();
            this.content = content;
        }

        @Override
        public boolean isDirectory() {
            if (content != null) return false;
            return file.isDirectory();
        }

        @Override
        public boolean exists() {
            if (content != null) return true;
            return file.exists();
        }

        @Override
        public Reader createReader() {
            try {
                if (content != null) return new StringReader(content);
                if ( ! exists()) throw new FileNotFoundException("File '" + file + "' does not exist");
                return IOUtils.createReader(file, "UTF-8");
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public InputStream createInputStream() {
            try {
                if (content != null) throw new UnsupportedOperationException("Not implemented for mock file content");
                if ( ! exists()) throw new FileNotFoundException("File '" + file + "' does not exist");
                return new BufferedInputStream(new FileInputStream(file));
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public ApplicationFile createDirectory() {
            file.mkdirs();
            return this;
        }

        @Override
        public ApplicationFile writeFile(Reader input) {
            try {
                if (content != null) throw new UnsupportedOperationException("Not implemented for mock file content");
                IOUtils.writeFile(file, IOUtils.readAll(input), false);
                return this;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public ApplicationFile appendFile(String value) {
            try {
                if (content != null) throw new UnsupportedOperationException("Not implemented for mock file content");
                IOUtils.writeFile(file, value, true);
                return this;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public List<ApplicationFile> listFiles(PathFilter filter) {
            if ( ! isDirectory()) return List.of();
            return Arrays.stream(file.listFiles()).filter(f -> filter.accept(Path.fromString(f.toString())))
                         .map(f -> new MockApplicationFile(asApplicationRelativePath(f), root))
                         .collect(Collectors.toList());
        }

        @Override
        public ApplicationFile delete() {
            if (content != null) throw new UnsupportedOperationException("Not implemented for mock file content");
            file.delete();
            return this;
        }

        @Override
        public MetaData getMetaData() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(ApplicationFile other) {
            return this.getPath().getName().compareTo((other).getPath().getName());
        }

        /** Strips the application package root path prefix from the path of the given file */
        private Path asApplicationRelativePath(File file) {
            Path path = Path.fromString(file.toString());

            Iterator<String> pathIterator = path.iterator();
            // Skip the path elements this shares with the root
            for (Iterator<String> rootIterator = Path.fromString(root.toString()).iterator(); rootIterator.hasNext(); ) {
                String rootElement = rootIterator.next();
                String pathElement = pathIterator.next();
                if ( ! rootElement.equals(pathElement)) throw new RuntimeException("Assumption broken");
            }
            // Build a path from the remaining
            Path relative = Path.fromString("");
            while (pathIterator.hasNext())
                relative = relative.append(pathIterator.next());
            return relative;
        }

    }

}
