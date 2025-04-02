// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package org.logstashplugins;

import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FeedConfig {
    private static final Logger logger = LogManager.getLogger(FeedConfig.class);
    
    private final String documentType;
    private final boolean dynamicDocumentType;
    private final boolean removeDocumentType;
    private final String namespace;
    private final boolean dynamicNamespace;
    private final boolean removeNamespace;
    private final String operation;
    private final boolean dynamicOperation;
    private final boolean create;
    private final String idField;
    private final boolean removeId; 
    private final long operationTimeout;
    private final boolean removeOperation;
    private String clientCert;
    private String clientKey;

    public FeedConfig(
        String namespace,
        boolean removeNamespace,
        String documentType,
        boolean removeDocumentType,
        String operation,
        boolean create,
        String idField,
        boolean removeId,
        long operationTimeout,
        boolean removeOperation,
        String clientCert,
        String clientKey,
        String applicationPackageDir
    ) {
        this.namespace = namespace;
        this.removeNamespace = removeNamespace;
        this.removeDocumentType = removeDocumentType;
        this.create = create;
        this.idField = idField;
        this.removeId = removeId;
        this.operationTimeout = operationTimeout;
        this.removeOperation = removeOperation;

        // if the namespace matches %{field_name} or %{[field_name]}, it's dynamic
        DynamicOption configOption = new DynamicOption(
            // if namespace is not set, use the document type name as namespace
            this.namespace != null ? this.namespace : documentType
        );
        dynamicNamespace = configOption.isDynamic();
        namespace = configOption.getParsedConfigValue();
        // same with document type
        configOption = new DynamicOption(documentType);
        dynamicDocumentType = configOption.isDynamic();
        this.documentType = configOption.getParsedConfigValue();
        // and operation
        configOption = new DynamicOption(operation);
        dynamicOperation = configOption.isDynamic();
        this.operation = configOption.getParsedConfigValue();

        validateOperationAndCreate();

        setClientCertAndKey(clientCert, clientKey, applicationPackageDir);
    }

    private void setClientCertAndKey(String clientCert, String clientKey, String applicationPackageDir) {
        // Set default paths if not specified and application package dir is available
        if (applicationPackageDir != null) {
            if (clientCert == null) {
                this.clientCert = Paths.get(applicationPackageDir, "security", "clients.pem").toString();
                logger.info("No client_cert specified, using default path: {}", this.clientCert);
            }
            
            if (clientKey == null) {
                this.clientKey = Paths.get(applicationPackageDir, "data-plane-private-key.pem").toString();
                logger.info("No client_key specified, using default path: {}", this.clientKey);
            }
        }
    }

    public String getClientCert() {
        return clientCert;
    }

    public String getClientKey() {
        return clientKey;
    }
    public String getDocumentType() {
        return documentType;
    }

    public boolean isDynamicDocumentType() {
        return dynamicDocumentType;
    }

    public boolean isRemoveDocumentType() {
        return removeDocumentType;
    }

    public String getNamespace() {
        return namespace;
    }

    public boolean isDynamicNamespace() {
        return dynamicNamespace;
    }

    public boolean isRemoveNamespace() {
        return removeNamespace;
    }

    public String getOperation() {
        return operation;
    }

    public boolean isDynamicOperation() {
        return dynamicOperation;
    }

    public boolean isCreate() {
        return create;
    }

    public String getIdField() {
        return idField;
    }

    public boolean isRemoveId() {
        return removeId;
    }

    public long getOperationTimeout() {
        return operationTimeout;
    }

    public boolean isRemoveOperation() {
        return removeOperation;
    }

    public void validateOperationAndCreate() {
        if (!dynamicOperation) {
            if (!operation.equals("put") && !operation.equals("update") && !operation.equals("remove")) {
                throw new IllegalArgumentException("Operation must be put, update or remove");
            }
            if (operation.equals("remove") && create) {
                throw new IllegalArgumentException("Operation remove cannot have create=true");
            }
        }
    }
}
