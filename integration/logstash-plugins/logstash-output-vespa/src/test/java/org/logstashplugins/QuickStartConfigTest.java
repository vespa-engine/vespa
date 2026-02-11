package org.logstashplugins;

import org.junit.Test;
import static org.junit.Assert.*;

import java.net.URI;

public class QuickStartConfigTest {

    @Test
    public void testIsVespaCloud() {
        // When both tenant and application are provided, isVespaCloud should return true
        QuickStartConfig config = new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            URI.create("http://localhost:8080"), // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            "test-tenant", // vespaCloudTenant
            "test-app", // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
        
        assertTrue("Should be in Vespa Cloud mode when tenant and application are set", 
                  config.isVespaCloud());
        
        // When neither tenant nor application are provided, isVespaCloud should return false
        config = new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            URI.create("http://localhost:8080"), // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
        
        assertFalse("Should not be in Vespa Cloud mode when tenant and application are not set", 
                   config.isVespaCloud());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testValidateVespaCloudSettings_MissingTenant() {
        // When only application is provided but not tenant, should throw exception
        new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            URI.create("http://localhost:8080"), // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant - Missing tenant
            "test-app", // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testValidateVespaCloudSettings_MissingApplication() {
        // When only tenant is provided but not application, should throw exception
        new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            URI.create("http://localhost:8080"), // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            "test-tenant", // vespaCloudTenant
            null, // vespaCloudApplication - Missing application
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
    }

    @Test
    public void testGetConfigServer_ExplicitConfigServer() {
        URI explicitConfigServer = URI.create("http://localhost:19071");
        QuickStartConfig config = new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            explicitConfigServer, // configServer
            URI.create("http://localhost:8080"), // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
        
        assertEquals("Explicit config server should be used", explicitConfigServer, config.getConfigServer());
    }

    @Test
    public void testGetConfigServer_DerivedFromVespaUrl() {
        URI vespaUrl = URI.create("http://localhost:8080");
        QuickStartConfig config = new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            vespaUrl, // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
        
        URI expectedConfigServer = URI.create("http://localhost:19071");
        assertEquals("Config server should be derived from vespaUrl", expectedConfigServer, config.getConfigServer());
    }

    @Test
    public void testGetConfigServer_NoConfigServerNoVespaUrl() {
        QuickStartConfig config = new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            null, // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
        
        assertNull("Config server should be null when neither configServer nor vespaUrl is provided", 
                  config.getConfigServer());
    }

    @Test
    public void testGetConfigServer_VespaCloud() {
        QuickStartConfig config = new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            null, // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            "test-tenant", // vespaCloudTenant
            "test-app", // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
        
        assertNull("Config server should be null for Vespa Cloud deployments", config.getConfigServer());
    }

    @Test(expected = IllegalArgumentException.class)
    // When both configServer and Vespa Cloud parameters are set, should throw exception
    public void testGetConfigServer_ConflictingConfig() {
        new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            URI.create("http://localhost:19071"), // configServer
            null, // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            "test-tenant", // vespaCloudTenant
            "test-app", // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
    }

    @Test
    public void testGenerateMtlsCertificates_VespaCloud() {
        QuickStartConfig config = new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates - explicitly set to false
            null, // clientCert
            null, // clientKey
            null, // configServer
            URI.create("http://localhost:8080"), // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            "test-tenant", // vespaCloudTenant
            "test-app", // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
        
        assertTrue("generateMtlsCertificates should be true for Vespa Cloud deployments", 
                  config.isGenerateMtlsCertificates());
    }

    @Test
    public void testGenerateMtlsCertificates_NonVespaCloud() {
        QuickStartConfig config = new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            null, // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
        
        assertFalse("generateMtlsCertificates should remain false for non-Vespa Cloud deployments", 
                   config.isGenerateMtlsCertificates());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDynamicDocumentType_NotAllowed() {
        new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            null, // vespaUrl
            "test-doc-type", // documentType
            true, // dynamicDocumentType - set to true, should throw exception
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            30 // certificateValidityDays
        );
    }

    @Test
    public void testCertificateValidityDays_Conversion() {
        QuickStartConfig config = new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates
            null, // clientCert
            null, // clientKey
            null, // configServer
            null, // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            365L // certificateValidityDays - using a long value
        );
        
        assertEquals("certificateValidityDays should be converted to int", 
                   365, config.getCertificateValidityDays());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidCertificateValidityDays() {
        new QuickStartConfig(
            true, // deployPackage
            true, // generateMtlsCertificates - set to true to trigger validation
            null, // clientCert
            null, // clientKey
            null, // configServer
            URI.create("http://localhost:8080"), // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "cloud.vespa.logstash", // certificateCommonName
            -1 // certificateValidityDays - invalid negative value
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidCertificateCommonName() {
        new QuickStartConfig(
            true, // deployPackage
            true, // generateMtlsCertificates - set to true to trigger validation
            null, // clientCert
            null, // clientKey
            null, // configServer
            URI.create("http://localhost:8080"), // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "", // certificateCommonName - invalid empty value
            30 // certificateValidityDays
        );
    }

    @Test
    public void testNoValidationWhenNotGeneratingCertificates() {
        // This should not throw even with invalid certificate parameters
        // because generateMtlsCertificates is false
        new QuickStartConfig(
            true, // deployPackage
            false, // generateMtlsCertificates - set to false to skip validation
            null, // clientCert
            null, // clientKey
            null, // configServer
            URI.create("http://localhost:8080"), // vespaUrl
            "test-doc-type", // documentType
            false, // dynamicDocumentType
            10, // idleBatches
            "/tmp/vespa-app", // applicationPackageDir
            null, // typeMappingsFile
            null, // typeConflictResolutionFile
            10, // maxRetries
            10, // gracePeriod
            null, // vespaCloudTenant
            null, // vespaCloudApplication
            "default", // vespaCloudInstance
            "", // certificateCommonName - invalid empty value
            -1 // certificateValidityDays - invalid negative value
        );
    }
} 