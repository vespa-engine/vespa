package org.logstashplugins;

public class QuickStartConfig {
    private final boolean deployPackage;
    private final boolean generateMtlsCertificates;
    private final String clientCert;
    private final String clientKey;
    private final String configServer;
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

    public QuickStartConfig(boolean deployPackage, 
                       boolean generateMtlsCertificates,
                       String clientCert,
                       String clientKey,
                       String configServer, 
                       String documentType,
                       long idleBatches,
                       String applicationPackageDir,
                       String typeMappingsFile,
                       String typeConflictResolutionFile,
                       long maxRetries,
                       long gracePeriod,
                       String vespaCloudTenant,
                       String vespaCloudApplication,
                       String vespaCloudInstance) {
        this.deployPackage = deployPackage;
        this.generateMtlsCertificates = generateMtlsCertificates;
        this.clientCert = clientCert;
        this.clientKey = clientKey;
        this.configServer = configServer;
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
    }

    public boolean isDeployPackage() { return deployPackage; }
    public boolean isGenerateMtlsCertificates() { return generateMtlsCertificates; }
    public String getClientCert() { return clientCert; }
    public String getClientKey() { return clientKey; }
    public String getConfigServer() { return configServer; }
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
    
    public boolean isVespaCloud() { 
        return vespaCloudTenant != null && vespaCloudApplication != null; 
    }
} 