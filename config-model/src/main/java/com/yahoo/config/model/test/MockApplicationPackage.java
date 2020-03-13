// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
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

    public static final String DEPLOYED_BY_USER = "user";
    public static final String APPLICATION_NAME = "application";
    public static final long APPLICATION_GENERATION = 1L;
    public static final String MUSIC_SEARCHDEFINITION = createSearchDefinition("music", "foo");
    public static final String BOOK_SEARCHDEFINITION = createSearchDefinition("book", "bar");

    private final File root;
    private final String hostsS;
    private final String servicesS;
    private final List<String> schemas;
    private final String schemaDir;
    private final Optional<String> deploymentSpec;
    private final Optional<String> validationOverrides;
    private final boolean failOnValidateXml;
    private final QueryProfileRegistry queryProfileRegistry;
    private final ApplicationMetaData applicationMetaData;

    protected MockApplicationPackage(File root, String hosts, String services, List<String> schemas,
                                     String schemaDir,
                                     String deploymentSpec, String validationOverrides, boolean failOnValidateXml,
                                     String queryProfile, String queryProfileType) {
        this.root = root;
        this.hostsS = hosts;
        this.servicesS = services;
        this.schemas = schemas;
        this.schemaDir = schemaDir;
        this.deploymentSpec = Optional.ofNullable(deploymentSpec);
        this.validationOverrides = Optional.ofNullable(validationOverrides);
        this.failOnValidateXml = failOnValidateXml;
        queryProfileRegistry = new QueryProfileXMLReader().read(asNamedReaderList(queryProfileType),
                                                                asNamedReaderList(queryProfile));
        applicationMetaData = new ApplicationMetaData(DEPLOYED_BY_USER,
                                                      "dir",
                                                      0L,
                                                      false,
                                                      ApplicationId.from(TenantName.defaultName(),
                                                                         ApplicationName.from(APPLICATION_NAME),
                                                                         InstanceName.defaultName()),
                                                      "checksum",
                                                      APPLICATION_GENERATION,
                                                      0L);
    }

    /** Returns the root of this application package relative to the current dir */
    protected File root() { return root; }

    @Override
    @SuppressWarnings("deprecation")
    public String getApplicationName() {
        return "mock application";
    }

    @Override
    public ApplicationId getApplicationId() { return ApplicationId.from("default", getApplicationName(), "default"); }

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
    public List<NamedReader> getSearchDefinitions() {
        ArrayList<NamedReader> readers = new ArrayList<>();
        SearchBuilder searchBuilder = new SearchBuilder(this,
                                                        new RankProfileRegistry(),
                                                        queryProfileRegistry);
        for (String sd : schemas) {
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
        return new MockApplicationFile(file, Path.fromString(root.toString()));
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

    public static class Builder {

        private File root = new File("nonexisting");
        private String hosts = null;
        private String services = null;
        private List<String> schemas = Collections.emptyList();
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
                return new MockApplicationPackage(root, hosts, services, schemas, schemaDir,
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
        private final Path root;

        /** The File pointing to the actual file represented by this */
        private final File file;

        public MockApplicationFile(Path filePath, Path applicationPackagePath) {
            super(filePath);
            this.root = applicationPackagePath;
            file = applicationPackagePath.append(filePath).toFile();
        }

        @Override
        public boolean isDirectory() {
            return file.isDirectory();
        }

        @Override
        public boolean exists() {
            return file.exists();
        }

        @Override
        public Reader createReader() {
            try {
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
                IOUtils.writeFile(file, value, true);
                return this;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public List<ApplicationFile> listFiles(PathFilter filter) {
            if ( ! isDirectory()) return Collections.emptyList();
            return Arrays.stream(file.listFiles()).filter(f -> filter.accept(Path.fromString(f.toString())))
                         .map(f -> new MockApplicationFile(asApplicationRelativePath(f), root))
                         .collect(Collectors.toList());
        }

        @Override
        public ApplicationFile delete() {
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
            for (Iterator<String> rootIterator = root.iterator(); rootIterator.hasNext(); ) {
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
