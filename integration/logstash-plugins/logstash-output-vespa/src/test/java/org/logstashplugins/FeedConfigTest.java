package org.logstashplugins;

import org.junit.Test;
import java.nio.file.Paths;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FeedConfigTest {

    @Test
    public void testValidateOperationAndCreate_ValidOperations() {
        // Test valid "put" operation
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "put", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate(); // Should not throw exception

        // Test valid "update" operation
        config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "update", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate(); // Should not throw exception

        // Test valid "remove" operation
        config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "remove", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate(); // Should not throw exception
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOperationAndCreate_InvalidOperation() {
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "invalid", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOperationAndCreate_RemoveWithCreate() {
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "remove", true, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate();
    }

    @Test
    public void testValidateOperationAndCreate_DynamicOperation() {
        // When operation is dynamic, validation should be skipped
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "%{operation}", false, "id", false, 180, false, null, null, null
        );
        config.validateOperationAndCreate(); // Should not throw exception
    }
    
    @Test
    public void testDefaultClientCertificatePaths() {
        // Test that default paths are set when application directory is provided
        String appDir = "/path/to/app";
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "put", false, "id", false, 180, false, null, null, appDir
        );
        
        String expectedCertPath = Paths.get(appDir, "security", "clients.pem").toString();
        String expectedKeyPath = Paths.get(appDir, "data-plane-private-key.pem").toString();
        
        assertEquals("Client cert should default to security/clients.pem in app dir", 
                    expectedCertPath, config.getClientCert());
        assertEquals("Client key should default to data-plane-private-key.pem in app dir", 
                    expectedKeyPath, config.getClientKey());
    }

    @Test
    public void testProvidedClientCertificatePaths() {
        // Test that provided paths are preserved
        String appDir = "/path/to/app";
        String providedCertPath = "/custom/path/cert.pem";
        String providedKeyPath = "/custom/path/key.pem";
        
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "put", false, "id", false, 180, false, 
            providedCertPath, providedKeyPath, appDir
        );
        
        assertEquals("Client cert should use provided path", 
                    providedCertPath, config.getClientCert());
        assertEquals("Client key should use provided path", 
                    providedKeyPath, config.getClientKey());
    }

    @Test
    public void testMixedClientCertificatePaths() {
        // Test mixed case where one path is provided and one uses default
        String appDir = "/path/to/app";
        String providedCertPath = "/custom/path/cert.pem";
        
        FeedConfig config = new FeedConfig(
            "test-namespace", false, "test-doc-type", false, 
            "put", false, "id", false, 180, false, 
            providedCertPath, null, appDir
        );
        
        String expectedKeyPath = Paths.get(appDir, "data-plane-private-key.pem").toString();
        
        assertEquals("Client cert should use provided path", 
                    providedCertPath, config.getClientCert());
        assertEquals("Client key should use default path", 
                    expectedKeyPath, config.getClientKey());
    }
    
    @Test
    public void testNamespaceDefaultsToDocumentType() {
        // When namespace is null, it should default to the document type
        String docType = "music";
        FeedConfig config = new FeedConfig(
            null, false, docType, false, 
            "put", false, "id", false, 180, false, null, null, null
        );
        
        assertEquals("Namespace should default to document type when null", 
                    docType, config.getNamespace());
        
        // Test with dynamic document type
        String dynamicDocType = "%{content_type}";
        config = new FeedConfig(
            null, false, dynamicDocType, false, 
            "put", false, "id", false, 180, false, null, null, null
        );
        
        assertEquals("Namespace should default to document type's extracted field name when null", 
                    "content_type", config.getNamespace());
        assertTrue("Dynamic namespace flag should be correctly set when defaulting to dynamic document type", 
                  config.isDynamicNamespace());
    }
} 