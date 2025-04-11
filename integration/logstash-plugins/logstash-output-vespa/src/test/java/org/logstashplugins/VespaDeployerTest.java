package org.logstashplugins;

import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VespaDeployerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    
    private QuickStartConfig mockConfig;
    private VespaDeployer deployer;
    private File appDir;
    private String documentType = "test_document";
    
    @Before
    public void setup() throws Exception {
        // Create a temporary application package directory
        appDir = temporaryFolder.newFolder("vespa_app");
        
        // Create mock config
        mockConfig = mock(QuickStartConfig.class);
        when(mockConfig.getApplicationPackageDir()).thenReturn(appDir.getAbsolutePath());
        when(mockConfig.getDocumentType()).thenReturn(documentType);
        when(mockConfig.isVespaCloud()).thenReturn(false);
        when(mockConfig.getConfigServer()).thenReturn(URI.create("http://localhost:19071"));
        
        // Create the deployer with the mock config
        deployer = new VespaDeployer(mockConfig);
    }
    
    @Test
    public void testWriteLockExists() throws IOException {
        // Create a write.lock file to simulate another thread already deploying
        Path lockFile = Paths.get(appDir.getAbsolutePath(), "write.lock");
        Files.createFile(lockFile);
        
        // Call deployApplicationPackage
        deployer.deployApplicationPackage();
        
        // Check that lockFile still exists (wasn't deleted)
        assertTrue("Lock file should still exist", Files.exists(lockFile));
        
        // Verify that no other methods were called on the mockConfig after getApplicationPackageDir()
        verify(mockConfig).getApplicationPackageDir();
        verifyNoMoreInteractions(mockConfig);
    }
    
    @Test
    public void testVespaCloudDeployment() throws Exception {
        // Configure for Vespa Cloud
        when(mockConfig.isVespaCloud()).thenReturn(true);
        when(mockConfig.getVespaCloudTenant()).thenReturn("my-tenant");
        when(mockConfig.getVespaCloudApplication()).thenReturn("my-app");
        when(mockConfig.getVespaCloudInstance()).thenReturn("default");
        
        // Reset the static AtomicBoolean field to ensure instructions are shown
        resetDeploymentInstructionsShownFlag();
        
        // Lock file will be created and then deleted by the deployment process
        Path lockFile = Paths.get(appDir.getAbsolutePath(), "write.lock");
        
        // Call deployApplicationPackage
        deployer.deployApplicationPackage();
        
        // Verify expected interactions
        verify(mockConfig, atLeastOnce()).getApplicationPackageDir();
        verify(mockConfig).isVespaCloud();
        verify(mockConfig).getVespaCloudTenant();
        verify(mockConfig).getVespaCloudApplication();
        verify(mockConfig).getVespaCloudInstance();
        
        // Verify that the lock file was deleted
        assertFalse("Lock file should be deleted after Cloud deployment", Files.exists(lockFile));
    }
    
    @Test
    public void testCreateApplicationZip() throws Exception {
        // Setup schema and services.xml files
        File schemasDir = new File(appDir, "schemas");
        schemasDir.mkdir();
        createServicesXml();
        createSchemaFile();
        
        // Create the zip file
        byte[] zipContent = deployer.createApplicationZip();
        
        // Verify the zip content
        assertNotNull("Zip content should not be null", zipContent);
        assertTrue("Zip content should not be empty", zipContent.length > 0);
        
        // Analyze the zip structure
        ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent));
        
        int entryCount = 0;
        boolean hasServicesXml = false;
        boolean hasSchemaFile = false;
        String schemaFileName = "schemas/" + documentType + ".sd";
        ZipEntry entry;
        
        while ((entry = zipInputStream.getNextEntry()) != null) {
            entryCount++;
            
            if ("services.xml".equals(entry.getName())) {
                hasServicesXml = true;
                
                // Verify the content of services.xml
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zipInputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, len);
                }
                
                String content = outputStream.toString("UTF-8");
                assertTrue("services.xml should contain document type", 
                         content.contains("document type=\"" + documentType + "\""));
            }
            
            if (schemaFileName.equals(entry.getName())) {
                hasSchemaFile = true;
                
                // Verify the content of the schema file
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zipInputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, len);
                }
                
                String content = outputStream.toString("UTF-8");
                assertTrue("Schema file should contain schema definition", 
                         content.contains("schema " + documentType));
            }
            
            zipInputStream.closeEntry();
        }
        
        assertEquals("Zip should contain exactly 2 entries", 2, entryCount);
        assertTrue("Zip should contain services.xml", hasServicesXml);
        assertTrue("Zip should contain schema file", hasSchemaFile);
    }
    
    // Helper methods to create test files
    private void createServicesXml() throws IOException {
        String servicesXml = "<services version=\"1.0\">\n" +
                "  <container id=\"default\" version=\"1.0\">\n" +
                "    <document-api/>\n" +
                "    <search/>\n" +
                "  </container>\n" +
                "  <content id=\"content\" version=\"1.0\">\n" +
                "    <redundancy>1</redundancy>\n" +
                "    <documents>\n" +
                "      <document type=\"" + documentType + "\" mode=\"index\"/>\n" +
                "    </documents>\n" +
                "    <nodes count=\"1\"/>\n" +
                "  </content>\n" +
                "</services>";
        
        Path servicesPath = Paths.get(appDir.getAbsolutePath(), "services.xml");
        Files.write(servicesPath, servicesXml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    
    private void createSchemaFile() throws IOException {
        String schemaContent = "schema " + documentType + " {\n" +
                "  document " + documentType + " {\n" +
                "    field id type string {\n" +
                "      indexing: summary | attribute\n" +
                "    }\n" +
                "    field text type string {\n" +
                "      indexing: index | summary\n" +
                "    }\n" +
                "  }\n" +
                "}\n";
        
        Path schemaPath = Paths.get(appDir.getAbsolutePath(), "schemas", documentType + ".sd");
        Files.write(schemaPath, schemaContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    
    /**
     * Helper method to reset the static deploymentInstructionsShown flag
     * using reflection to allow testing the instructions output
     */
    private void resetDeploymentInstructionsShownFlag() throws ReflectiveOperationException {
        // Get the field
        Field field = VespaDeployer.class.getDeclaredField("deploymentInstructionsShown");
        field.setAccessible(true);
        
        // Get the current value
        AtomicBoolean atomicBoolean = (AtomicBoolean) field.get(null);
        
        // Reset it
        atomicBoolean.set(false);
    }
} 