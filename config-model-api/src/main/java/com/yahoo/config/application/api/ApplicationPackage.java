// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinitionKey;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;

/**
 * Represents an application package; a complete specification of an application, that is used to
 * build a config model.
 *
 * @author Vegard Havdal
 */
public interface ApplicationPackage {

    // Caution!! If you add something here it must probably also be added to ZooKeeperDeployer.writeSomeOf(applicationPackage)

    String HOSTS = "hosts.xml";
    String SERVICES = "services.xml";

    Path SCHEMAS_DIR = Path.fromString("schemas");
    Path SEARCH_DEFINITIONS_DIR = Path.fromString("searchdefinitions"); // Legacy addition to schemas
    String COMPONENT_DIR = "components";
    String SEARCHCHAINS_DIR = "search/chains";
    String DOCPROCCHAINS_DIR = "docproc/chains";
    String PROCESSORCHAINS_DIR = "processor/chains";
    String ROUTINGTABLES_DIR = "routing/tables";

    /** Machine-learned models - only present in user-uploaded package instances */
    Path MODELS_DIR = Path.fromString("models");
    /** Files generated from machine-learned models */
    Path MODELS_GENERATED_DIR = Path.fromString("models.generated");
    /** Files generated from machine-learned models which should be replicated in ZooKeeper */
    Path MODELS_GENERATED_REPLICATED_DIR = MODELS_GENERATED_DIR.append("replicated");
    /** Constant tensors */
    Path CONSTANTS_DIR = Path.fromString("constants");

    // NOTE: this directory is created in serverdb during deploy, and should not exist in the original user application
    /** Do not use */
    String CONFIG_DEFINITIONS_DIR = "configdefinitions";

    Path QUERY_PROFILES_DIR= Path.fromString("search/query-profiles");
    Path QUERY_PROFILE_TYPES_DIR= Path.fromString("search/query-profiles/types");
    Path PAGE_TEMPLATES_DIR= Path.fromString("page-templates");
    Path RULES_DIR = Path.fromString("rules");

    Path APPLICATION_DEFINITION_FILE = Path.fromString("application.xml");
    Path DEPLOYMENT_FILE = Path.fromString("deployment.xml");
    Path VALIDATION_OVERRIDES = Path.fromString("validation-overrides.xml");

    Path SECURITY_DIR = Path.fromString("security");

    String SD_NAME_SUFFIX = ".sd";
    String RANKEXPRESSION_NAME_SUFFIX = ".expression";
    String RANKPROFILE_NAME_SUFFIX = ".profile";
    String RULES_NAME_SUFFIX = ".sr";
    String EXT_DIR = "ext";

    ApplicationId getApplicationId();

    /**
     * Contents of services.xml. Caller must close reader after use.
     *
     * @return a Reader, or null if no services.xml present
     */
    Reader getServices();

    /**
     * Contents of hosts.xml. Caller must close reader after use.
     *
     * @return a Reader, or null if no hosts.xml present
     */
    Reader getHosts();

    /**
     * Returns the include dirs given by the user in the services.xml file.
     */
    default List<String> getUserIncludeDirs() {
        throw new UnsupportedOperationException("This application package does not have special handling for user include dirs.");
    }

    default void validateIncludeDir(String dirName) {
        throw new UnsupportedOperationException("This application package does not support validation of include dirs.");
    }

    /**
     * Returns all the config definitions available in this package as unparsed data.
     */
    Map<ConfigDefinitionKey, UnparsedConfigDefinition> getAllExistingConfigDefs();

    /**
     * Returns the files in a directory as readers. The readers <b>must</b>
     * be closed by the caller.
     *
     * @param  pathFromRoot the relative path string from the root of the application package
     * @param  suffix the suffix of files to return, or null to return all
     * @param  recurse return files in all subdirectories (recursively) as well
     * @return a list of the files at this location, or an empty list (never null)
     *         if the directory does not exist or is empty. The list gets owned by the caller
     *         and can be modified freely.
     */
    List<NamedReader> getFiles(Path pathFromRoot, String suffix, boolean recurse);

    /** Same as getFiles(pathFromRoot, suffix, false) */
    default List<NamedReader> getFiles(Path pathFromRoot, String suffix) {
        return getFiles(pathFromRoot, suffix, false);
    }

    /** Returns the major version this application is valid for, or empty if it is valid for all versions */
    default Optional<Integer> getMajorVersion() {
        return getDeploymentSpec().majorVersion();
    }

    /**
     * Returns information about a file
     *
     * @param relativePath the relative path of the file within this application package.
     * @return information abut the file, returned whether or not the file exists
     */
    ApplicationFile getFile(Path relativePath);

    /** Does {@link #getFiles} on the query profile directory and gets all xml files */
    default List<NamedReader> getQueryProfileFiles() { return getFiles(QUERY_PROFILES_DIR,".xml"); }

    /** Does {@link #getFiles} on the query profile directory and gets all xml files */
    default List<NamedReader> getQueryProfileTypeFiles() { return getFiles(QUERY_PROFILE_TYPES_DIR,".xml"); }

    /** Does {@link #getFiles} on the page template directory and gets all xml files */
    default List<NamedReader> getPageTemplateFiles() { return getFiles(PAGE_TEMPLATES_DIR,".xml"); }

    /** Returns handle for the file containing client certificate authorities */
    default ApplicationFile getClientSecurityFile() { return getFile(SECURITY_DIR.append("clients.pem")); }

    Optional<Reader> getApplicationDefinition();

    Optional<Reader> getDeployment();

    /**
     * Returns the parsed deployment spec of this,
     * without validating it, and without reparsing on each request.
     */
    DeploymentSpec getDeploymentSpec();

    default DeploymentSpec parseDeploymentSpec(boolean validate) {
         return getDeployment()
                   .map(new DeploymentSpecXmlReader(validate)::read)
                   .orElse(DeploymentSpec.empty);
    }

    Optional<Reader> getValidationOverrides();

    List<ComponentInfo> getComponentsInfo(Version vespaVersion);

    /**
     * Reads a ranking expression from file to a string and returns it.
     *
     * @param name the name of the file to return, relative to the search definition directory in the application package
     * @return the content of a ranking expression file
     * @throws IllegalArgumentException if the file was not found or could not be read
     */
    Reader getRankingExpression(String name);

    /**
     * The name of an SD in a JarEntry
     */
    static String getFileName(JarEntry je) {
        String name = je.getName();
        name = name.replaceAll(".*/", "");
        return name;
    }

    /**
     * Gets the ApplicationMetaData instance for this application package.
     *
     * @return an ApplicationMetaData instance
     */
    ApplicationMetaData getMetaData();

    File getFileReference(Path pathRelativeToAppDir);

    default void validateXML() throws IOException {
        throw new UnsupportedOperationException("This application package cannot validate XML");
    }

    default void validateXMLFor(Optional<Version> vespaVersion) throws IOException {
        throw new UnsupportedOperationException("This application package cannot validate XML");
    }

    default void writeMetaData() throws IOException {
        throw new UnsupportedOperationException("This application package cannot write its metadata");
    }

    /** Returns the host allocation info of this, or empty if no allocation is available */
    default Optional<AllocatedHosts> getAllocatedHosts() {
        return Optional.empty();
    }

    default Map<Version, FileRegistry> getFileRegistries() {
        return Map.of();
    }

    default Map<String, String> legacyOverrides() {
        return Map.of();
    }

    /**
     * Readers for all the schema files.
     *
     * @return a collection of readers for schemas
     */
    Collection<NamedReader> getSchemas();

    /**
     * Preprocess an application for a given zone and return a new application package pointing to the preprocessed
     * application package. This is the entry point for the multi environment application package support. This method
     * will not mutate the existing application package.
     *
     * @param zone a valid {@link Zone} instance, used to decide which parts of services to keep and remove
     * @param logger a {@link DeployLogger} to add output that will be returned to the user
     * @return a new application package instance pointing to a new location
     */
    default ApplicationPackage preprocess(Zone zone, DeployLogger logger) throws IOException {
        throw new UnsupportedOperationException("This application package does not support preprocessing");
    }

}
