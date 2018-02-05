// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.provision.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import com.yahoo.searchdefinition.*;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.config.application.api.ApplicationPackage;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;

/**
 * For testing purposes only
 *
 * @author Tony Vaagenes
 */
public class MockApplicationPackage implements ApplicationPackage {

    public static final String MUSIC_SEARCHDEFINITION = createSearchDefinition("music", "foo");
    public static final String BOOK_SEARCHDEFINITION = createSearchDefinition("book", "bar");

    private final String hostsS;
    private final String servicesS;
    private final List<String> searchDefinitions;
    private final String searchDefinitionDir;
    private final Optional<String> deploymentSpec;
    private final Optional<String> validationOverrides;
    private final boolean failOnValidateXml;
    private final QueryProfileRegistry queryProfileRegistry;

    protected MockApplicationPackage(String hosts, String services, List<String> searchDefinitions, String searchDefinitionDir,
                                     String deploymentSpec, String validationOverrides, boolean failOnValidateXml,
                                     String queryProfile, String queryProfileType) {
        this.hostsS = hosts;
        this.servicesS = services;
        this.searchDefinitions = searchDefinitions;
        this.searchDefinitionDir = searchDefinitionDir;
        this.deploymentSpec = Optional.ofNullable(deploymentSpec);
        this.validationOverrides = Optional.ofNullable(validationOverrides);
        this.failOnValidateXml = failOnValidateXml;
        queryProfileRegistry = new QueryProfileXMLReader().read(asNamedReaderList(queryProfileType),
                                                                asNamedReaderList(queryProfile));
    }

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
        throw new UnsupportedOperationException();
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
                return new MockApplicationPackage(hosts, services, searchDefinitions, searchDefinitionDir,
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
    public void validateXML() throws IOException {
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

}
