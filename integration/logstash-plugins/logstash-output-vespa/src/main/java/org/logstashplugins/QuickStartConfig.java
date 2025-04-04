// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package org.logstashplugins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;

public class QuickStartConfig {
    private static final Logger logger = LogManager.getLogger(QuickStartConfig.class);
    
    private final boolean deployPackage;
    private boolean generateMtlsCertificates;
    private final String clientCert;
    private final String clientKey;
    private final URI configServer;
    private final String documentType;
    private final String applicationPackageDir;
    private final long idleBatches;
    private final long maxRetries;
    private final long gracePeriod;
    private final String typeMappingsFile;
    private final String typeConflictResolutionFile;
    private final String vespaCloudTenant;
    private final String vespaCloudApplication;
    private final String vespaCloudInstance;
    private final String certificateCommonName;
    private final int certificateValidityDays;

    public QuickStartConfig(boolean deployPackage, 
                       boolean generateMtlsCertificates,
                       String clientCert,
                       String clientKey,
                       URI configServer,
                       URI vespaUrl,
                       String documentType,
                       boolean dynamicDocumentType,
                       long idleBatches,
                       String applicationPackageDir,
                       String typeMappingsFile,
                       String typeConflictResolutionFile,
                       long maxRetries,
                       long gracePeriod,
                       String vespaCloudTenant,
                       String vespaCloudApplication,
                       String vespaCloudInstance,
                       String certificateCommonName,
                       long certificateValidityDays) {
        
        logger.warn("Quick start mode enabled! We will not send documents to Vespa, but will generate an application package.");

        if (dynamicDocumentType) {
            throw new IllegalArgumentException("Dynamic document type is not supported in quick start mode." + 
                "Use a static document type or just start with one and edit the generated application package.");
        }

        this.deployPackage = deployPackage;
        this.generateMtlsCertificates = generateMtlsCertificates;
        this.clientCert = clientCert;
        this.clientKey = clientKey;
        this.documentType = documentType;
        this.typeConflictResolutionFile = typeConflictResolutionFile;
        this.applicationPackageDir = applicationPackageDir;
        this.idleBatches = idleBatches;
        this.maxRetries = maxRetries;
        this.gracePeriod = gracePeriod;
        this.typeMappingsFile = typeMappingsFile;
        this.vespaCloudTenant = vespaCloudTenant;
        this.vespaCloudApplication = vespaCloudApplication;
        this.vespaCloudInstance = vespaCloudInstance;
        this.certificateCommonName = certificateCommonName;
        
        // we need this to be int when we use it in the SelfSignedCertGenerator
        this.certificateValidityDays = (int) certificateValidityDays;
        
        // valid for Vespa Cloud
        validateVespaCloudSettings();

        // valid for self-hosted Vespa
        this.configServer = getConfigServer(configServer, vespaUrl);
    }

    public URI getConfigServer(URI configServer, URI vespaUrl) {
        // throw an error if configServer AND one of the Vespa Cloud parameters are set as well
        if (configServer != null && (vespaCloudTenant != null || vespaCloudApplication != null || vespaCloudInstance != null)) {
            throw new IllegalArgumentException("Use config_server for local deployments" + 
                " and vespa_cloud_tenant+vespa_cloud_application+vespa_cloud_instance for Vespa Cloud. You can't have both set.");
        }

        // If we're not in Cloud mode, set config_server to vespa_url with port 19071 if not explicitly set
        if (!isVespaCloud() && configServer == null && vespaUrl != null) {
            String scheme = vespaUrl.getScheme();
            String host = vespaUrl.getHost();
            String configServerUrl = scheme + "://" + host + ":19071";
            logger.info("No config_server specified, using default derived from vespa_url: {}", configServerUrl);
            return URI.create(configServerUrl);
        } else {
            return configServer;
        }
    }

    /**
     * Validates that if one of the Vespa Cloud parameters is set, all required
     * parameters are also set correctly.
     * 
     * @throws IllegalArgumentException if validation fails
     */
    private void validateVespaCloudSettings() {
        // If only one of the required Cloud params is set, throw an error
        if ((vespaCloudTenant != null && vespaCloudApplication == null) ||
            (vespaCloudTenant == null && vespaCloudApplication != null)) {
            throw new IllegalArgumentException("Both vespa_cloud_tenant and vespa_cloud_application must be specified for Vespa Cloud deployment");
        }
        
        // Validate that instance is provided if we're in Vespa Cloud mode
        if (isVespaCloud()) {
            // we default to "default", so this shouldn't happen unless the user set it to an empty string
            if (vespaCloudInstance == null || vespaCloudInstance.trim().isEmpty()) {
                throw new IllegalArgumentException("vespa_cloud_instance must be specified for Vespa Cloud deployment");
            }
            
            logger.info("Vespa Cloud mode enabled with tenant: {}, application: {}, instance: {}", 
                       vespaCloudTenant, vespaCloudApplication, vespaCloudInstance);

            if (!generateMtlsCertificates) {
                logger.info("Generating mTLS certificates for Vespa Cloud: you're very likely going to need them :)");
                generateMtlsCertificates = true;
            }
        }
    }
    
    public boolean isVespaCloud() { 
        return vespaCloudTenant != null && vespaCloudApplication != null; 
    }

    public boolean isDeployPackage() { return deployPackage; }
    public boolean isGenerateMtlsCertificates() { return generateMtlsCertificates; }
    public String getClientCert() { return clientCert; }
    public String getClientKey() { return clientKey; }
    public URI getConfigServer() { return configServer; }
    public String getDocumentType() { return documentType; }
    public String getApplicationPackageDir() { return applicationPackageDir; }
    public String getTypeConflictResolutionFile() { return typeConflictResolutionFile; }
    public long getIdleBatches() { return idleBatches; }
    public long getMaxRetries() { return maxRetries; }
    public long getGracePeriod() { return gracePeriod; }
    public String getTypeMappingsFile() { return typeMappingsFile; }
    public String getVespaCloudTenant() { return vespaCloudTenant; }
    public String getVespaCloudApplication() { return vespaCloudApplication; }
    public String getVespaCloudInstance() { return vespaCloudInstance; }
    public String getCertificateCommonName() { return certificateCommonName; }
    public int getCertificateValidityDays() { return certificateValidityDays; }
} 