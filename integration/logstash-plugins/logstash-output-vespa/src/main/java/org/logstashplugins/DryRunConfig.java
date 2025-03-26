package org.logstashplugins;

public class DryRunConfig {
    private final boolean deployPackage;
    private final String configServer;
    private final String documentType;
    private final String applicationPackageDir;
    private final long idleBatches;
    private final long maxRetries;
    private final long gracePeriod;
    private final String typeMappingsFile;
    private final String typeConflictResolutionFile;

    public DryRunConfig(boolean deployPackage, 
                       String configServer, 
                       String documentType,
                       long idleBatches,
                       String applicationPackageDir,
                       String typeMappingsFile,
                       String typeConflictResolutionFile,
                       long maxRetries,
                       long gracePeriod) {
        this.deployPackage = deployPackage;
        this.configServer = configServer;
        this.documentType = documentType;
        this.typeConflictResolutionFile = typeConflictResolutionFile;
        this.applicationPackageDir = applicationPackageDir;
        this.idleBatches = idleBatches;
        this.maxRetries = maxRetries;
        this.gracePeriod = gracePeriod;
        this.typeMappingsFile = typeMappingsFile;
    }

    public boolean isDeployPackage() { return deployPackage; }
    public String getConfigServer() { return configServer; }
    public String getDocumentType() { return documentType; }
    public String getApplicationPackageDir() { return applicationPackageDir; }
    public String getTypeConflictResolutionFile() { return typeConflictResolutionFile; }
    public long getIdleBatches() { return idleBatches; }
    public long getMaxRetries() { return maxRetries; }
    public long getGracePeriod() { return gracePeriod; }
    public String getTypeMappingsFile() { return typeMappingsFile; }
} 