package org.logstashplugins;

import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.nio.file.Paths;

import org.logstash.LockException;

public class VespaAppPackageWriterTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private QuickStartConfig mockConfig;
    private Map<String, List<String>> typeMappings;
    private Path appPackageDir;
    private Path certDir;
    private Path keyDir;
    private String certPath;
    private String keyPath;

    @Before
    public void setUp() throws IOException {
        setupDirectories();
        setupMockConfig();
        setupTypeMappings();
    }
    
    private void setupDirectories() throws IOException {
        // Create the necessary directories
        appPackageDir = tempFolder.newFolder("app-package").toPath();
        certDir = tempFolder.newFolder("certs").toPath();
        keyDir = tempFolder.newFolder("keys").toPath();
        certPath = certDir.resolve("client.crt").toString();
        keyPath = keyDir.resolve("client.key").toString();
        
        // Ensure the app package directory is completely empty
        clearDirectory(appPackageDir);
        
        // Create an empty app package directory
        Files.createDirectories(appPackageDir);
    }
    
    private void clearDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                 .sorted(Comparator.reverseOrder())
                 .forEach(path -> {
                     try {
                         Files.deleteIfExists(path);
                     } catch (IOException e) {
                         System.err.println("Failed to delete: " + path);
                     }
                 });
        }
    }
    
    private void setupMockConfig() {
        mockConfig = mock(QuickStartConfig.class);
        when(mockConfig.getApplicationPackageDir()).thenReturn(appPackageDir.toString());
        when(mockConfig.isGenerateMtlsCertificates()).thenReturn(false);
        when(mockConfig.getClientCert()).thenReturn(certPath);
        when(mockConfig.getClientKey()).thenReturn(keyPath);
        when(mockConfig.getCertificateCommonName()).thenReturn("test.vespa.example");
        when(mockConfig.getCertificateValidityDays()).thenReturn(365);
        when(mockConfig.getDocumentType()).thenReturn("document_type");
        when(mockConfig.getMaxRetries()).thenReturn(3L);
        when(mockConfig.getGracePeriod()).thenReturn(1L);
    }
    
    private void setupTypeMappings() {
        typeMappings = new HashMap<>();
        typeMappings.put("string", List.of("field {{FIELD_NAME}} type string { indexing: summary | index }"));
        typeMappings.put("double", List.of("field {{FIELD_NAME}} type double { indexing: summary | attribute }"));
        typeMappings.put("int", List.of("field {{FIELD_NAME}} type int { indexing: summary | attribute }"));
    }
    
    private VespaAppPackageWriter createWriter() throws IOException {
        return new VespaAppPackageWriter(mockConfig, typeMappings);
    }
    
    private VespaAppPackageWriter createWriterAndWrite(Map<String, String> fields) throws Exception {
        VespaAppPackageWriter writer = createWriter();
        writer.writeApplicationPackage(fields);
        return writer;
    }
    
    private Map<String, String> createBasicFields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("title", "string");
        return fields;
    }
    
    private void assertFileExists(Path path, String message) {
        assertTrue(message, Files.exists(path));
    }
    
    private String readFileContent(Path path) throws IOException {
        return Files.readString(path);
    }

    @Test
    public void testInitializationWithValidConfig() throws IOException {
        VespaAppPackageWriter writer = createWriter();
        assertNotNull(writer);
    }

    @Test
    public void testDirectoryCreation() throws IOException {
        // Delete the directory and its contents first to test creation
        clearDirectory(appPackageDir);
        
        // Create writer - should create directory
        createWriter();
        
        // Verify directory and schemas subdirectory exist
        assertFileExists(appPackageDir, "App package directory should exist");
        assertFileExists(appPackageDir.resolve("schemas"), "Schemas directory should exist");
    }

    @Test
    public void testVespaIgnoreFileCopying() throws IOException {
        // Create writer - should copy .vespaignore
        createWriter();
        
        // Verify .vespaignore exists
        Path vespaIgnore = appPackageDir.resolve(".vespaignore");
        assertFileExists(vespaIgnore, ".vespaignore should exist");
        
        // Verify content is correct
        String content = readFileContent(vespaIgnore);
        assertTrue(content.contains("write.lock"));
        assertTrue(content.contains("data-plane-private-key.pem"));
    }

    @Test
    public void testVespaIgnoreFileNotOverwritten() throws IOException {
        // Create custom .vespaignore first
        Path vespaIgnore = appPackageDir.resolve(".vespaignore");
        String customContent = "custom content";
        Files.writeString(vespaIgnore, customContent);
        
        // Create writer - should not overwrite existing .vespaignore
        createWriter();
        
        // Verify content is unchanged
        String content = readFileContent(vespaIgnore);
        assertEquals(customContent, content);
    }

    @Test
    public void testCertificateGeneration() throws Exception {
        // Enable mTLS certificate generation
        when(mockConfig.isGenerateMtlsCertificates()).thenReturn(true);
        
        // Create writer - should generate certificates
        createWriter();
        
        // Verify certificate and key files exist
        assertFileExists(Path.of(certPath), "Certificate file should exist");
        assertFileExists(Path.of(keyPath), "Key file should exist");
        
        // Verify certificate content and properties
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(new FileInputStream(certPath));
        
        assertEquals("CN=test.vespa.example", cert.getSubjectX500Principal().getName());
        assertEquals("CN=test.vespa.example", cert.getIssuerX500Principal().getName());
    }

    @Test
    public void testCertificateDirectoryCreation() throws Exception {
        // Delete cert and key directories
        Files.deleteIfExists(certDir);
        Files.deleteIfExists(keyDir);
        
        // Enable mTLS certificate generation
        when(mockConfig.isGenerateMtlsCertificates()).thenReturn(true);
        
        // Create writer - should create directories and certificates
        createWriter();
        
        // Verify directories were created
        assertFileExists(certDir, "Certificate directory should exist");
        assertFileExists(keyDir, "Key directory should exist");
    }

    @Test
    public void testVespaCloudCertificateCopying() throws Exception {
        // Enable mTLS and Vespa Cloud
        when(mockConfig.isGenerateMtlsCertificates()).thenReturn(true);
        when(mockConfig.isVespaCloud()).thenReturn(true);
        when(mockConfig.getVespaCloudTenant()).thenReturn("test-tenant");
        when(mockConfig.getVespaCloudApplication()).thenReturn("test-app");
        when(mockConfig.getVespaCloudInstance()).thenReturn("test");
        
        // Set up Vespa CLI directory path
        String userHome = System.getProperty("user.home");
        Path vespaCliDir = Path.of(userHome, ".vespa", "test-tenant.test-app.test");
        
        try {
            // Create writer - should generate and copy certificates
            createWriter();
            
            // Verify certificates were copied to Vespa CLI directory
            assertFileExists(vespaCliDir, "Vespa CLI directory should exist");
            assertFileExists(vespaCliDir.resolve("data-plane-public-cert.pem"), "Public cert should exist in CLI dir");
            assertFileExists(vespaCliDir.resolve("data-plane-private-key.pem"), "Private key should exist in CLI dir");
        } finally {
            // Clean up: delete the test directory and its contents
            clearDirectory(vespaCliDir);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCertificateGenerationWithNullPaths() throws Exception {
        // Enable mTLS but set null paths
        when(mockConfig.isGenerateMtlsCertificates()).thenReturn(true);
        when(mockConfig.getClientCert()).thenReturn(null);
        when(mockConfig.getClientKey()).thenReturn(null);
        
        // Should throw IllegalArgumentException
        createWriter();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCertificateGenerationWithInvalidPaths() throws Exception {
        // Enable mTLS but set invalid paths
        when(mockConfig.isGenerateMtlsCertificates()).thenReturn(true);
        when(mockConfig.getClientCert()).thenReturn("");
        when(mockConfig.getClientKey()).thenReturn("");
        
        // Should throw IllegalArgumentException
        createWriter();
    }

    @Test
    public void testBasicApplicationPackageWriting() throws Exception {
        // Set up detected fields
        Map<String, String> detectedFields = new HashMap<>();
        detectedFields.put("title", "string");
        detectedFields.put("price", "double");
        detectedFields.put("count", "int");
        
        // Write application package
        createWriterAndWrite(detectedFields);
        
        // Verify services.xml exists and has correct content
        Path servicesXml = appPackageDir.resolve("services.xml");
        assertFileExists(servicesXml, "services.xml should exist");
        String servicesContent = readFileContent(servicesXml);
        assertTrue("services.xml should contain document type", 
            servicesContent.contains("<document type="));
        
        // Verify schema file exists and has correct content
        Path schemaFile = appPackageDir.resolve("schemas").resolve("document_type.sd");
        assertFileExists(schemaFile, "schema file should exist");
        String schemaContent = readFileContent(schemaFile);
        assertTrue("schema should contain field definitions", schemaContent.contains("field title type string"));
        assertTrue("schema should contain field definitions", schemaContent.contains("field price type double"));
        assertTrue("schema should contain field definitions", schemaContent.contains("field count type int"));
    }

    @Test
    public void testLockFileHandling() throws Exception {
        // Set up detected fields
        Map<String, String> detectedFields = createBasicFields();
        
        // Create writer and verify write.lock is created during write
        Path writeLock = appPackageDir.resolve("write.lock");
        assertFalse("write.lock should not exist before write", Files.exists(writeLock));
        
        createWriterAndWrite(detectedFields);
        
        // Verify write.lock is removed after write
        assertFalse("write.lock should be deleted after write", Files.exists(writeLock));
    }

    @Test(expected = LockException.class)
    public void testLockFileTimeout() throws Exception {
        // Create a write.lock file to simulate another process writing
        Path writeLock = appPackageDir.resolve("write.lock");
        Files.createFile(writeLock);
        
        // Configure max retries and grace period to be small for testing
        when(mockConfig.getMaxRetries()).thenReturn(2L);
        when(mockConfig.getGracePeriod()).thenReturn(1L); // 1 second
        
        // This should eventually throw LockException
        createWriterAndWrite(createBasicFields());
    }

    @Test
    public void testFieldReconciliation() throws Exception {
        // Set up initial fields
        Map<String, String> initialFields = new HashMap<>();
        initialFields.put("title", "string");
        initialFields.put("price", "double");
        
        // Write application package with initial fields
        VespaAppPackageWriter writer = createWriter();
        writer.writeApplicationPackage(initialFields);
        
        // Now create a new set of detected fields with some overlap and some new fields
        Map<String, String> newDetectedFields = new HashMap<>();
        newDetectedFields.put("title", "string");  // Same field, same type
        newDetectedFields.put("count", "int");     // New field
        // Note: 'price' field is missing in new detection
        
        // Write again with new fields
        writer.writeApplicationPackage(newDetectedFields);
        
        // Verify schema file and read content
        Path schemaFile = appPackageDir.resolve("schemas").resolve("document_type.sd");
        assertFileExists(schemaFile, "Schema file should exist");
        String schemaContent = readFileContent(schemaFile);
        
        // Verify all fields are present (both old and new)
        assertTrue("Schema should contain 'title' field", schemaContent.contains("field title type string"));
        assertTrue("Schema should contain 'price' field", schemaContent.contains("field price type double"));
        assertTrue("Schema should contain 'count' field", schemaContent.contains("field count type int"));
    }

    // Type conflict resolution tests - grouped together
    @Test
    public void testResolveTypeConflictNonArrays() throws IOException {
        testTypeConflictResolution("long", "float", "float");
        testTypeConflictResolution("float", "long", "float");
        testTypeConflictResolution("position", "object<float>", "object<float>");
        testTypeConflictResolution("object<int8>", "object<long>", "object<long>");
    }
    
    private void testTypeConflictResolution(String type1, String type2, String expectedResult) throws IOException {
        VespaAppPackageWriter writer = createWriter();
        Map<String, Map<String, String>> resolutions = writer.loadTypeConflictResolution();
        String resolvedType = writer.resolveTypeConflict(type1, type2, resolutions);
        assertEquals(type1 + " vs " + type2 + " should resolve to " + expectedResult, 
                     expectedResult, resolvedType);
    }
    
    @Test
    public void testTypeConflictResolution() throws Exception {
        // First, add a long type to the typeMappings
        typeMappings.put("long", List.of("field {{FIELD_NAME}} type long"));
        
        // Set up initial fields with a price as double
        Map<String, String> initialFields = new HashMap<>();
        initialFields.put("price", "double");
        initialFields.put("count", "int");
        
        // Create writer and write application package with initial fields
        VespaAppPackageWriter writer = createWriter();
        writer.writeApplicationPackage(initialFields);
        
        // Now create a new set of detected fields with price as long
        Map<String, String> newDetectedFields = new HashMap<>();
        newDetectedFields.put("price", "long");
        newDetectedFields.put("count", "int"); // same type, no conflict
        
        // Write again with new type for price field
        writer.writeApplicationPackage(newDetectedFields);
        
        // Verify schema file and content
        Path schemaFile = appPackageDir.resolve("schemas").resolve("document_type.sd");
        assertFileExists(schemaFile, "Schema file should exist");
        String schemaContent = readFileContent(schemaFile);
        
        // According to default type conflict resolution, long and double should resolve to double
        assertTrue("Schema should resolve price to double type", schemaContent.contains("field price type double"));
        assertTrue("Schema should keep count as int type", schemaContent.contains("field count type int"));
    }

    @Test
    public void testResolveTypeConflictArraySizes() throws IOException {
        testArraySizeConflictResolution("array<int8>[5]", "array<int8>[10]", "variablearray<int8>");
        testArraySizeConflictResolution("array<float>[3]", "array<float>[7]", "variablearray<float>");
        testArraySizeConflictResolution("array<long>[2]", "array<long>[8]", "variablearray<long>");
    }
    
    private void testArraySizeConflictResolution(String type1, String type2, String expectedResult) throws IOException {
        VespaAppPackageWriter writer = createWriter();
        Map<String, Map<String, String>> resolutions = writer.loadTypeConflictResolution();
        String resolvedType = writer.resolveTypeConflict(type1, type2, resolutions);
        assertEquals(type1 + " vs " + type2 + " should resolve to " + expectedResult, 
                     expectedResult, resolvedType);
    }

    @Test
    public void testResolveTypeConflictArrayDifferentTypes() throws IOException {
        testArrayTypeConflictResolution("array<long>[5]", "array<float>[5]", "variablearray<float>");
        testArrayTypeConflictResolution("array<long>[3]", "array<float>[7]", "variablearray<float>");
    }
    
    private void testArrayTypeConflictResolution(String type1, String type2, String expectedResult) throws IOException {
        VespaAppPackageWriter writer = createWriter();
        Map<String, Map<String, String>> resolutions = writer.loadTypeConflictResolution();
        String resolvedType = writer.resolveTypeConflict(type1, type2, resolutions);
        assertEquals(type1 + " vs " + type2 + " should resolve to " + expectedResult, 
                     expectedResult, resolvedType);
    }

    @Test
    public void testResolveTypeConflictVariableArrayWithArray() throws IOException {
        testVariableArrayConflictResolution("variablearray<int8>", "array<float>[5]", "variablearray<float>");
        testVariableArrayConflictResolution("array<float>[5]", "variablearray<int8>", "variablearray<float>");
    }
    
    private void testVariableArrayConflictResolution(String type1, String type2, String expectedResult) throws IOException {
        VespaAppPackageWriter writer = createWriter();
        Map<String, Map<String, String>> resolutions = writer.loadTypeConflictResolution();
        String resolvedType = writer.resolveTypeConflict(type1, type2, resolutions);
        assertEquals(type1 + " vs " + type2 + " should resolve to " + expectedResult, 
                     expectedResult, resolvedType);
    }

    @Test
    public void testSchemaFileUpdatesWithReconciliation() throws IOException {
        // Create a writer with the default config
        VespaAppPackageWriter writer = createWriter();
        
        // First, write an initial schema with some fields
        Map<String, String> initialFields = new HashMap<>();
        initialFields.put("title", "string");
        initialFields.put("rating", "double");
        writer.writeApplicationPackage(initialFields);
        
        // Add new fields and modify an existing one
        Map<String, String> newFields = new HashMap<>();
        newFields.put("price", "double");           // New field
        newFields.put("count", "int");              // New field
        newFields.put("rating", "int");             // Existing field, different type (type conflict)
        // Note: 'title' field is not mentioned in this detection
        
        // Write application package with new fields - should reconcile with existing
        writer.writeApplicationPackage(newFields);
        
        // Read the updated schema
        Path schemaPath = Paths.get(mockConfig.getApplicationPackageDir(), "schemas", 
                                  mockConfig.getDocumentType() + ".sd");
        String updatedSchema = readFileContent(schemaPath);
        
        // Verify all fields are present and type conflict resolution happened
        assertTrue("Schema should contain the existing 'title' field", 
            updatedSchema.contains("field title type string"));
        assertTrue("Schema should contain the new 'price' field", 
            updatedSchema.contains("field price type double"));
        assertTrue("Schema should contain the new 'count' field", 
            updatedSchema.contains("field count type int"));
        assertTrue("Schema should resolve 'rating' as double due to type conflict resolution", 
            updatedSchema.contains("field rating type double"));
        
        // Check overall schema structure
        assertTrue("Schema should have correct structure", 
            updatedSchema.contains("schema document_type {"));
        assertTrue("Schema should have document section", 
            updatedSchema.contains("document document_type {"));
    }

    // Services.xml tests
    @Test
    public void testServicesXmlMtlsConfig() throws Exception {
        // Set up mock config with mTLS enabled
        when(mockConfig.isGenerateMtlsCertificates()).thenReturn(true);
        
        // Write application package
        createWriterAndWrite(createBasicFields());
        
        // Verify services.xml exists
        Path servicesXmlPath = Paths.get(mockConfig.getApplicationPackageDir(), "services.xml");
        assertFileExists(servicesXmlPath, "services.xml should exist");
        
        // Read and verify services.xml content
        String servicesXml = readFileContent(servicesXmlPath);
        
        // Should contain document type
        assertTrue("services.xml should contain document type", 
            servicesXml.contains("<document type=\"" + mockConfig.getDocumentType() + "\""));
        
        // Should contain uncommented mTLS config (since it's enabled)
        assertTrue("services.xml should contain uncommented client id=mtls section",
            servicesXml.contains("<client id=\"mtls\" permissions=\"read,write\">"));
        assertTrue("services.xml should contain uncommented certificate file section",
            servicesXml.contains("<certificate file=\"security/clients.pem\"/>"));
    }

    @Test
    public void testServicesXmlWithoutMtls() throws Exception {
        // Make sure mTLS is disabled
        when(mockConfig.isGenerateMtlsCertificates()).thenReturn(false);
        
        // Write application package
        createWriterAndWrite(createBasicFields());
        
        // Verify services.xml exists
        Path servicesXmlPath = Paths.get(mockConfig.getApplicationPackageDir(), "services.xml");
        assertFileExists(servicesXmlPath, "services.xml should exist");
        
        // Read and verify services.xml content
        String servicesXml = readFileContent(servicesXmlPath);
        
        // Should contain document type
        assertTrue("services.xml should contain document type", 
            servicesXml.contains("<document type=\"" + mockConfig.getDocumentType() + "\""));
        
        // Verify the exact MTLS comment pattern is present
        String expectedMtlsCommentPattern = 
            "<!-- MTLS\n" +
            "              <client id=\"mtls\" permissions=\"read,write\">\n" +
            "              <certificate file=\"security/clients.pem\"/>\n" +
            "            </client>\n" +
            "            END MTLS -->";
        
        assertTrue("services.xml should contain the exact MTLS comment pattern when disabled",
            servicesXml.contains(expectedMtlsCommentPattern));
    }

    @Test
    public void testSyntheticFieldsGeneration() throws IOException {
        // Set up type mappings with multiple templates (both regular and synthetic fields)
        Map<String, List<String>> mappingsWithSynthetic = new HashMap<>();
        
        // Regular field + synthetic attribute field for string (similar to type_mappings.yml)
        mappingsWithSynthetic.put("string", List.of(
            "field {{FIELD_NAME}} type string { indexing: index }", 
            "field {{FIELD_NAME}}_att type string { \n" +
            "    indexing: input {{FIELD_NAME}} | attribute | summary \n" +
            "}"
        ));
        
        // Set up detected fields
        Map<String, String> detectedFields = new HashMap<>();
        detectedFields.put("title", "string");
        
        // Create writer with the mappings that include synthetic fields
        when(mockConfig.getDocumentType()).thenReturn("test_document");
        VespaAppPackageWriter writer = new VespaAppPackageWriter(mockConfig, mappingsWithSynthetic);
        
        // Write application package
        writer.writeApplicationPackage(detectedFields);
        
        // Read the generated schema file
        Path schemaPath = Paths.get(mockConfig.getApplicationPackageDir(), "schemas", 
                                  mockConfig.getDocumentType() + ".sd");
        String schemaContent = readFileContent(schemaPath);
        
        // Verify regular and synthetic fields are included
        assertTrue("Schema should contain regular 'title' field", 
            schemaContent.contains("field title type string { indexing: index }"));
        assertTrue("Schema should contain synthetic 'title_att' attribute field", 
            schemaContent.contains("field title_att type string"));
        assertTrue("Schema should contain correct indexing for attribute field", 
            schemaContent.contains("indexing: input title | attribute | summary"));
    }
} 