package org.logstashplugins;

import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Event;
import co.elastic.logstash.api.PluginConfigSpec;

import org.junit.Test;

import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.OperationParameters;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.Result;
import ai.vespa.feed.client.DocumentId;

import java.net.URI;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.mockito.Mockito;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;

public class VespaFeedTest {

    private static Configuration createMockConfig(String operation, boolean create, boolean dynamicOperation) {
        Configuration config = Mockito.mock(Configuration.class);
        try {
            // Set required config values
            when(config.get(VespaFeed.VESPA_URL)).thenReturn(new URI("http://localhost:8080"));
            when(config.get(VespaFeed.NAMESPACE)).thenReturn(dynamicOperation ? "%{namespace}" : "test-namespace");
            when(config.get(VespaFeed.DOCUMENT_TYPE)).thenReturn("test-doc-type");
            when(config.get(VespaFeed.OPERATION)).thenReturn(operation);
            when(config.get(VespaFeed.CREATE)).thenReturn(create);
            // Set defaults for other required config
            when(config.get(VespaFeed.MAX_CONNECTIONS)).thenReturn(1L);
            when(config.get(VespaFeed.MAX_STREAMS)).thenReturn(128L);
            when(config.get(VespaFeed.MAX_RETRIES)).thenReturn(10L);
            when(config.get(VespaFeed.OPERATION_TIMEOUT)).thenReturn(180L);
            when(config.get(VespaFeed.GRACE_PERIOD)).thenReturn(10L);
            when(config.get(VespaFeed.DOOM_PERIOD)).thenReturn(60L);
            return config;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testValidateOperationAndCreate_ValidOperations() {
        // Test valid "put" operation
        VespaFeed feed = new VespaFeed("test-id", createMockConfig("put", false, false), null);
        feed.validateOperationAndCreate(); // Should not throw exception

        // Test valid "update" operation
        feed = new VespaFeed("test-id", createMockConfig("update", false, false), null);
        feed.validateOperationAndCreate(); // Should not throw exception

        // Test valid "remove" operation
        feed = new VespaFeed("test-id", createMockConfig("remove", false, false), null);
        feed.validateOperationAndCreate(); // Should not throw exception
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOperationAndCreate_InvalidOperation() {
        VespaFeed feed = new VespaFeed("test-id", createMockConfig("invalid", false, false), null);
        feed.validateOperationAndCreate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOperationAndCreate_RemoveWithCreate() {
        VespaFeed feed = new VespaFeed("test-id", createMockConfig("remove", true, false), null);
        feed.validateOperationAndCreate();
    }

    @Test
    public void testValidateOperationAndCreate_DynamicOperation() {
        // When operation is dynamic, validation should be skipped
        VespaFeed feed = new VespaFeed("test-id", createMockConfig("%{operation}", false, true), null);
        feed.validateOperationAndCreate(); // Should not throw exception
    }

    @Test
    public void testConstructor_DynamicOptions() {
        // Test non-dynamic options
        VespaFeed feed = new VespaFeed("test-id", createMockConfig("put", false, false), null);
        assertFalse("Operation should not be dynamic", feed.isDynamicOperation());
        assertEquals("Operation should be 'put'", "put", feed.getOperation());

        // Test dynamic operation
        feed = new VespaFeed("test-id", createMockConfig("%{operation}", false, true), null);
        assertTrue("Operation should be dynamic", feed.isDynamicOperation());
        assertEquals("Operation field should be 'operation'", "operation", feed.getOperation());

        // Test dynamic namespace
        Configuration config = createMockConfig("put", false, false);
        when(config.get(VespaFeed.NAMESPACE)).thenReturn("%{my_namespace}");
        feed = new VespaFeed("test-id", config, null);
        assertTrue("Namespace should be dynamic", feed.isDynamicNamespace());
        assertEquals("Namespace field should be 'my_namespace'", "my_namespace", feed.getNamespace());

        // Test dynamic document type
        config = createMockConfig("put", false, false);
        when(config.get(VespaFeed.DOCUMENT_TYPE)).thenReturn("%{doc_type}");
        feed = new VespaFeed("test-id", config, null);
        assertTrue("Document type should be dynamic", feed.isDynamicDocumentType());
        assertEquals("Document type field should be 'doc_type'", "doc_type", feed.getDocumentType());
    }

    @Test
    public void testAddCreateIfApplicable() {
        // Test put operation with create=true
        VespaFeed feed = new VespaFeed("test-id", createMockConfig("put", true, false), null);
        OperationParameters params = feed.addCreateIfApplicable("put", "doc1");
        assertTrue("Put operation should have createIfNonExistent=true", params.createIfNonExistent());
        assertEquals("Timeout should be set", Duration.ofSeconds(180), params.timeout().get());

        // Test update operation with create=true
        feed = new VespaFeed("test-id", createMockConfig("update", true, false), null);
        params = feed.addCreateIfApplicable("update", "doc1");
        assertTrue("Update operation should have createIfNonExistent=true", params.createIfNonExistent());

        // Test put operation with create=false
        feed = new VespaFeed("test-id", createMockConfig("put", false, false), null);
        params = feed.addCreateIfApplicable("put", "doc1");
        assertFalse("Put operation should not have createIfNonExistent when create=false", params.createIfNonExistent());
    }

    @Test
    public void testConstructor_OperationValidation() {
        // Test invalid operation
        Configuration config = createMockConfig("invalid_op", false, false);
        try {
            new VespaFeed("test-id", config, null);
            fail("Should throw IllegalArgumentException for invalid operation");
        } catch (IllegalArgumentException e) {
            assertEquals("Operation must be put, update or remove", e.getMessage());
        }

        // Test remove with create=true
        config = createMockConfig("remove", true, false);
        try {
            new VespaFeed("test-id", config, null);
            fail("Should throw IllegalArgumentException for remove with create=true");
        } catch (IllegalArgumentException e) {
            assertEquals("Operation remove cannot have create=true", e.getMessage());
        }

        // Test that create=true is allowed for put and update
        config = createMockConfig("put", true, false);
        VespaFeed feed = new VespaFeed("test-id", config, null);
        assertTrue("Create should be true for put operation", feed.isCreate());

        config = createMockConfig("update", true, false);
        feed = new VespaFeed("test-id", config, null);
        assertTrue("Create should be true for update operation", feed.isCreate());
    }

    @Test
    public void testAddCertAndKeyToBuilder() throws IOException {
        Configuration config = createMockConfig("put", false, false);
        FeedClientBuilder builder = FeedClientBuilder.create(URI.create("http://localhost:8080"));

        // Create temporary cert and key files
        Path certPath = Files.createTempFile("test-cert", ".pem");
        Path keyPath = Files.createTempFile("test-key", ".pem");
        try {
            // Write some dummy content
            Files.write(certPath, "test certificate".getBytes());
            Files.write(keyPath, "test key".getBytes());

            // Test with both cert and key. It should not throw an exception
            when(config.get(VespaFeed.CLIENT_CERT)).thenReturn(certPath.toString());
            when(config.get(VespaFeed.CLIENT_KEY)).thenReturn(keyPath.toString());
            VespaFeed.addCertAndKeyToBuilder(config, builder);

            // Test with missing cert/key. Similarly, it should not throw an exception
            when(config.get(VespaFeed.CLIENT_CERT)).thenReturn(null);
            when(config.get(VespaFeed.CLIENT_KEY)).thenReturn(null);
            VespaFeed.addCertAndKeyToBuilder(config, builder);
        } finally {
            // Clean up
            Files.deleteIfExists(certPath);
            Files.deleteIfExists(keyPath);
        }
    }

    @Test
    public void testGetDynamicField() {
        // Create a mock Event
        Event event = createMockEvent("test-id", "field_value");
        
        // Test when field exists
        when(event.getField("my_field")).thenReturn("field_value");
        VespaFeed feed = new VespaFeed("test-id", createMockConfig("put", false, false), null);
        assertEquals("Should return field value", "field_value", feed.getDynamicField(event, "my_field"));
        
        // Test when field doesn't exist
        when(event.getField("missing_field")).thenReturn(null);
        assertEquals("Should return field name when field doesn't exist", 
                "missing_field", feed.getDynamicField(event, "missing_field"));
    }

    @Test
    public void testToJson() throws Exception {
        VespaFeed feed = new VespaFeed("test-id", createMockConfig("put", false, false), null);
        ObjectMapper mapper = new ObjectMapper();
        
        // Test simple map
        Map<String, Object> simpleMap = new HashMap<>();
        simpleMap.put("string", "value");
        simpleMap.put("number", 42);
        simpleMap.put("boolean", true);
        assertEquals(
            mapper.readTree("{\"string\":\"value\",\"number\":42,\"boolean\":true}"),
            mapper.readTree(feed.toJson(simpleMap))
        );
        
        // Test nested map
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nested", simpleMap);
        assertEquals(
            mapper.readTree("{\"nested\":{\"string\":\"value\",\"number\":42,\"boolean\":true}}"),
            mapper.readTree(feed.toJson(nestedMap))
        );
        
        // Test array
        List<String> list = Arrays.asList("one", "two", "three");
        Map<String, Object> mapWithArray = new HashMap<>();
        mapWithArray.put("array", list);
        assertEquals(
            mapper.readTree("{\"array\":[\"one\",\"two\",\"three\"]}"),
            mapper.readTree(feed.toJson(mapWithArray))
        );
    }

    @Test
    public void testAsyncFeed_PutOperation() throws Exception {
        FeedClient mockClient = createMockFeedClient();
        VespaFeed feed = createVespaFeed("put", true, false, mockClient);
        Event event = createMockEvent("test-doc-1", "value1");

        CompletableFuture<Result> future = feed.asyncFeed(event);
        assertEquals(Result.Type.success, future.get().type());

        verifyDocument(mockClient, "test-doc-1", "value1");
    }

    @Test
    public void testAsyncFeed_DynamicOperation() throws Exception {
        FeedClient mockClient = createMockFeedClient();
        VespaFeed feed = createVespaFeed("%{operation}", false, true, mockClient);

        // Create test event
        Event event = createMockEvent("test-doc-1", "value1");
        when(event.getField("operation")).thenReturn("update");

        CompletableFuture<Result> future = feed.asyncFeed(event);
        assertEquals(Result.Type.success, future.get().type());

        verify(mockClient).update(
            eq(DocumentId.of("test-namespace", "test-doc-type", "test-doc-1")),
            contains("\"fields\":{\"field1\":\"value1\",\"doc_id\":\"test-doc-1\"}"),
            any(OperationParameters.class)
        );
    }

    @Test
    public void testAsyncFeed_UUIDGeneration() throws Exception {
        FeedClient mockClient = createMockFeedClient();
        VespaFeed feed = createVespaFeed("put", false, false, mockClient);

        // Create event without ID
        Event event = createMockEvent(null, "value1");

        CompletableFuture<Result> future = feed.asyncFeed(event);
        assertEquals(Result.Type.success, future.get().type());

        verify(mockClient).put(
            argThat(docId -> docId.toString().matches("id:test-namespace:test-doc-type::[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")),
            contains("\"fields\":{\"field1\":\"value1\"}"),
            any(OperationParameters.class)
        );
    }

    @Test
    public void testOutput_SuccessfulBatch() throws Exception {
        FeedClient mockClient = createMockFeedClient();
        VespaFeed feed = createVespaFeed("put", false, false, mockClient);

        // Create test events
        List<Event> events = Arrays.asList(
            createMockEvent("doc1", "value1"),
            createMockEvent("doc2", "value2")
        );

        feed.output(events);
        verify(mockClient, times(2)).put(any(), any(), any());
    }

    @Test
    public void testOutput_JsonSerializationError() throws Exception {
        FeedClient mockClient = createMockFeedClient();
        VespaFeed feed = createVespaFeed("put", false, false, mockClient);

        // Create event that will cause serialization error
        Event badEvent = Mockito.mock(Event.class);
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("field1", new Object() { // Non-serializable object
            @Override
            public String toString() { throw new RuntimeException("Serialization error"); }
        });
        when(badEvent.getData()).thenReturn(eventData);

        // Test that output handles the error gracefully
        feed.output(Arrays.asList(badEvent));
        verify(mockClient, times(0)).put(any(), any(), any());
    }

    @Test
    public void testOutput_FeedClientError() throws Exception {
        FeedClient mockClient = createMockFeedClient();
        Result errorResult = Mockito.mock(Result.class);
        when(errorResult.type()).thenReturn(Result.Type.conditionNotMet);
        CompletableFuture<Result> errorFuture = CompletableFuture.completedFuture(errorResult);
        when(mockClient.put(any(), any(), any())).thenReturn(errorFuture);

        VespaFeed feed = createVespaFeed("put", false, false, mockClient);
        feed.output(Arrays.asList(createMockEvent("doc1", "value1")));
        // The test passes if no exception is thrown
    }

    @Test
    public void testOutput_StoppedBehavior() throws Exception {
        FeedClient mockClient = createMockFeedClient();
        VespaFeed feed = createVespaFeed("put", false, false, mockClient);

        // Stop the feed
        feed.stop();

        // Create test events
        List<Event> events = Arrays.asList(
            createMockEvent("doc1", "value1"),
            createMockEvent("doc2", "value2")
        );

        // Test that output doesn't process events when stopped
        feed.output(events);
        verify(mockClient, times(0)).put(any(), any(), any());
    }

    @Test
    public void testOutput_MultiFeedException() throws Exception {
        FeedClient mockClient = createMockFeedClient();
        CompletableFuture<Result> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Feed error"));
        when(mockClient.put(any(), any(), any())).thenReturn(future);

        VespaFeed feed = createVespaFeed("put", false, false, mockClient);
        feed.output(Arrays.asList(createMockEvent("doc1", "value1")));
        // Test passes if error is logged but not thrown
    }

    @Test
    public void testAwaitStop() throws Exception {
        FeedClient mockClient = createMockFeedClient();
        VespaFeed feed = createVespaFeed("put", false, false, mockClient);
        
        feed.awaitStop();  // Should do nothing
        verify(mockClient, times(0)).close();
        
        feed.stop();
        verify(mockClient, times(1)).close();
    }

    @Test
    public void testConfigSchema() {
        //makes sure that all config options are present in the schema
        
        VespaFeed feed = createVespaFeed("put", false, false, null);
        Collection<PluginConfigSpec<?>> schema = feed.configSchema();
        
        assertTrue(schema.contains(VespaFeed.VESPA_URL));
        assertTrue(schema.contains(VespaFeed.CLIENT_CERT));
        assertTrue(schema.contains(VespaFeed.CLIENT_KEY));
        assertTrue(schema.contains(VespaFeed.OPERATION));
        assertTrue(schema.contains(VespaFeed.CREATE));
        assertTrue(schema.contains(VespaFeed.NAMESPACE));
        assertTrue(schema.contains(VespaFeed.DOCUMENT_TYPE));
        assertTrue(schema.contains(VespaFeed.ID_FIELD));
        assertTrue(schema.contains(VespaFeed.MAX_CONNECTIONS));
        assertTrue(schema.contains(VespaFeed.MAX_STREAMS));
        assertTrue(schema.contains(VespaFeed.MAX_RETRIES));
        assertTrue(schema.contains(VespaFeed.OPERATION_TIMEOUT));
        assertTrue(schema.contains(VespaFeed.GRACE_PERIOD));
        assertTrue(schema.contains(VespaFeed.DOOM_PERIOD));
    }

    private Event createMockEvent(String docId, String value) {
        Event event = Mockito.mock(Event.class);
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("field1", value);
        if (docId != null) {
            eventData.put("doc_id", docId);
        }
        when(event.getData()).thenReturn(eventData);
        when(event.getField("doc_id")).thenReturn(docId);
        return event;
    }

    private FeedClient createMockFeedClient() {
        FeedClient mockClient = Mockito.mock(FeedClient.class);
        Result mockResult = Mockito.mock(Result.class);
        when(mockResult.type()).thenReturn(Result.Type.success);
        CompletableFuture<Result> successFuture = CompletableFuture.completedFuture(mockResult);
        when(mockClient.put(any(), any(), any())).thenReturn(successFuture);
        when(mockClient.update(any(), any(), any())).thenReturn(successFuture);
        when(mockClient.remove(any(), any())).thenReturn(successFuture);
        return mockClient;
    }

    private VespaFeed createVespaFeed(String operation, boolean create, boolean dynamicOperation, FeedClient mockClient) {
        Configuration config = createMockConfig(operation, create, dynamicOperation);
        when(config.get(VespaFeed.ID_FIELD)).thenReturn("doc_id");
        when(config.get(VespaFeed.NAMESPACE)).thenReturn("test-namespace");
        return new VespaFeed("test-id", config, null, mockClient);
    }

    private void verifyDocument(FeedClient mockClient, String docId, String value) {
        verify(mockClient).put(
            eq(DocumentId.of("test-namespace", "test-doc-type", docId)),
            contains("\"fields\":{\"field1\":\"" + value + "\",\"doc_id\":\"" + docId + "\"}"),
            any(OperationParameters.class)
        );
    }
} 