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
import java.util.Collections;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.logstash.common.io.DeadLetterQueueWriter;

public class VespaFeedTest {

    @Test
    public void testValidateOperationAndCreate_ValidOperations() {
        // Test valid "put" operation
        VespaFeed feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("put", false, false), null);
        feed.validateOperationAndCreate(); // Should not throw exception

        // Test valid "update" operation
        feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("update", false, false), null);
        feed.validateOperationAndCreate(); // Should not throw exception

        // Test valid "remove" operation
        feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("remove", false, false), null);
        feed.validateOperationAndCreate(); // Should not throw exception
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOperationAndCreate_InvalidOperation() {
        VespaFeed feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("invalid", false, false), null);
        feed.validateOperationAndCreate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateOperationAndCreate_RemoveWithCreate() {
        VespaFeed feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("remove", true, false), null);
        feed.validateOperationAndCreate();
    }

    @Test
    public void testValidateOperationAndCreate_DynamicOperation() {
        // When operation is dynamic, validation should be skipped
        VespaFeed feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("%{operation}", false, true), null);
        feed.validateOperationAndCreate(); // Should not throw exception
    }

    @Test
    public void testConstructor_DynamicOptions() {
        // Test non-dynamic options
        VespaFeed feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("put", false, false), null);
        assertFalse("Operation should not be dynamic", feed.isDynamicOperation());
        assertEquals("Operation should be 'put'", "put", feed.getOperation());

        // Test dynamic operation
        feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("%{operation}", false, true), null);
        assertTrue("Operation should be dynamic", feed.isDynamicOperation());
        assertEquals("Operation field should be 'operation'", "operation", feed.getOperation());

        // Test dynamic namespace
        Configuration config = VespaFeedTestHelper.createMockConfig("put", false, false);
        when(config.get(VespaFeed.NAMESPACE)).thenReturn("%{my_namespace}");
        feed = new VespaFeed("test-id", config, null);
        assertTrue("Namespace should be dynamic", feed.isDynamicNamespace());
        assertEquals("Namespace field should be 'my_namespace'", "my_namespace", feed.getNamespace());

        // Test dynamic document type
        config = VespaFeedTestHelper.createMockConfig("put", false, false);
        when(config.get(VespaFeed.DOCUMENT_TYPE)).thenReturn("%{doc_type}");
        feed = new VespaFeed("test-id", config, null);
        assertTrue("Document type should be dynamic", feed.isDynamicDocumentType());
        assertEquals("Document type field should be 'doc_type'", "doc_type", feed.getDocumentType());
    }

    @Test
    public void testAddCreateIfApplicable() {
        // Test put operation with create=true
        VespaFeed feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("put", true, false), null);
        OperationParameters params = feed.addCreateIfApplicable("put", "doc1");
        assertTrue("Put operation should have createIfNonExistent=true", params.createIfNonExistent());
        assertEquals("Timeout should be set", Duration.ofSeconds(180), params.timeout().get());

        // Test update operation with create=true
        feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("update", true, false), null);
        params = feed.addCreateIfApplicable("update", "doc1");
        assertTrue("Update operation should have createIfNonExistent=true", params.createIfNonExistent());

        // Test put operation with create=false
        feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("put", false, false), null);
        params = feed.addCreateIfApplicable("put", "doc1");
        assertFalse("Put operation should not have createIfNonExistent when create=false", params.createIfNonExistent());
    }

    @Test
    public void testConstructor_OperationValidation() {
        // Test invalid operation
        Configuration config = VespaFeedTestHelper.createMockConfig("invalid_op", false, false);
        try {
            new VespaFeed("test-id", config, null);
            fail("Should throw IllegalArgumentException for invalid operation");
        } catch (IllegalArgumentException e) {
            assertEquals("Operation must be put, update or remove", e.getMessage());
        }

        // Test remove with create=true
        config = VespaFeedTestHelper.createMockConfig("remove", true, false);
        try {
            new VespaFeed("test-id", config, null);
            fail("Should throw IllegalArgumentException for remove with create=true");
        } catch (IllegalArgumentException e) {
            assertEquals("Operation remove cannot have create=true", e.getMessage());
        }

        // Test that create=true is allowed for put and update
        config = VespaFeedTestHelper.createMockConfig("put", true, false);
        VespaFeed feed = new VespaFeed("test-id", config, null);
        assertTrue("Create should be true for put operation", feed.isCreate());

        config = VespaFeedTestHelper.createMockConfig("update", true, false);
        feed = new VespaFeed("test-id", config, null);
        assertTrue("Create should be true for update operation", feed.isCreate());
    }

    @Test
    public void testAddCertAndKeyToBuilder() throws IOException {
        Configuration config = VespaFeedTestHelper.createMockConfig("put", false, false);
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
        Event event = VespaFeedTestHelper.createMockEvent("test-id", "field_value");
        
        // Test when field exists
        when(event.getField("my_field")).thenReturn("field_value");
        VespaFeed feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("put", false, false), null);
        assertEquals("Should return field value", "field_value", feed.getDynamicField(event, "my_field"));
        
        // Test when field doesn't exist
        when(event.getField("missing_field")).thenReturn(null);
        assertEquals("Should return field name when field doesn't exist", 
                "missing_field", feed.getDynamicField(event, "missing_field"));
    }

    @Test
    public void testToJson() throws Exception {
        VespaFeed feed = new VespaFeed("test-id", VespaFeedTestHelper.createMockConfig("put", false, false), null);
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
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("put", true, false, mockClient);
        Event event = VespaFeedTestHelper.createMockEvent("test-doc-1", "value1");

        CompletableFuture<Result> future = feed.asyncFeed(event);
        assertEquals(Result.Type.success, future.get().type());

        VespaFeedTestHelper.verifyDocument(mockClient, "test-doc-1", "value1");
    }

    @Test
    public void testAsyncFeed_DynamicOperation() throws Exception {
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("%{operation}", false, true, mockClient);

        // Create test event
        Event event = VespaFeedTestHelper.createMockEvent("test-doc-1", "value1");
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
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("put", false, false, mockClient);

        // Create event without ID
        Event event = VespaFeedTestHelper.createMockEvent(null, "value1");

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
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("put", false, false, mockClient);

        // Create test events
        List<Event> events = Arrays.asList(
            VespaFeedTestHelper.createMockEvent("doc1", "value1"),
            VespaFeedTestHelper.createMockEvent("doc2", "value2")
        );

        feed.output(events);
        verify(mockClient, times(2)).put(any(), any(), any());
    }

    @Test
    public void testOutput_JsonSerializationError() throws Exception {
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("put", false, false, mockClient);

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
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        Result errorResult = Mockito.mock(Result.class);
        when(errorResult.type()).thenReturn(Result.Type.conditionNotMet);
        CompletableFuture<Result> errorFuture = CompletableFuture.completedFuture(errorResult);
        when(mockClient.put(any(), any(), any())).thenReturn(errorFuture);

        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("put", false, false, mockClient);
        feed.output(Arrays.asList(VespaFeedTestHelper.createMockEvent("doc1", "value1")));
        // The test passes if no exception is thrown
    }

    @Test
    public void testOutput_StoppedBehavior() throws Exception {
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("put", false, false, mockClient);

        // Stop the feed
        feed.stop();

        // Create test events
        List<Event> events = Arrays.asList(
            VespaFeedTestHelper.createMockEvent("doc1", "value1"),
            VespaFeedTestHelper.createMockEvent("doc2", "value2")
        );

        // Test that output doesn't process events when stopped
        feed.output(events);
        verify(mockClient, times(0)).put(any(), any(), any());
    }

    @Test
    public void testOutput_MultiFeedException() throws Exception {
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        CompletableFuture<Result> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Feed error"));
        when(mockClient.put(any(), any(), any())).thenReturn(future);

        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("put", false, false, mockClient);
        feed.output(Arrays.asList(VespaFeedTestHelper.createMockEvent("doc1", "value1")));
        // Test passes if error is logged but not thrown
    }

    @Test
    public void testAwaitStop() throws Exception {
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("put", false, false, mockClient);
        
        feed.awaitStop();  // Should do nothing
        verify(mockClient, times(0)).close();
        
        feed.stop();
        verify(mockClient, times(1)).close();
    }

    @Test
    public void testConfigSchema() {
        //makes sure that all config options are present in the schema
        
        VespaFeed feed = VespaFeedTestHelper.createVespaFeed("put", false, false, null);
        Collection<PluginConfigSpec<?>> schema = feed.configSchema();
        
        assertTrue(schema.contains(VespaFeed.VESPA_URL));
        assertTrue(schema.contains(VespaFeed.CLIENT_CERT));
        assertTrue(schema.contains(VespaFeed.CLIENT_KEY));
        assertTrue(schema.contains(VespaFeed.OPERATION));
        assertTrue(schema.contains(VespaFeed.CREATE));
        assertTrue(schema.contains(VespaFeed.NAMESPACE));
        assertTrue(schema.contains(VespaFeed.REMOVE_NAMESPACE));
        assertTrue(schema.contains(VespaFeed.DOCUMENT_TYPE));
        assertTrue(schema.contains(VespaFeed.REMOVE_DOCUMENT_TYPE));
        assertTrue(schema.contains(VespaFeed.ID_FIELD));
        assertTrue(schema.contains(VespaFeed.REMOVE_ID));
        assertTrue(schema.contains(VespaFeed.MAX_CONNECTIONS));
        assertTrue(schema.contains(VespaFeed.MAX_STREAMS));
        assertTrue(schema.contains(VespaFeed.MAX_RETRIES));
        assertTrue(schema.contains(VespaFeed.OPERATION_TIMEOUT));
        assertTrue(schema.contains(VespaFeed.GRACE_PERIOD));
        assertTrue(schema.contains(VespaFeed.DOOM_PERIOD));
        // DLQ config options
        assertTrue(schema.contains(VespaFeed.ENABLE_DLQ));
        assertTrue(schema.contains(VespaFeed.DLQ_PATH));
        assertTrue(schema.contains(VespaFeed.MAX_QUEUE_SIZE));
        assertTrue(schema.contains(VespaFeed.MAX_SEGMENT_SIZE));
        assertTrue(schema.contains(VespaFeed.FLUSH_INTERVAL));

    }
    
    @Test
    public void testExplicitIdField() throws Exception {
        Configuration config = VespaFeedTestHelper.createMockConfig("put", false, false);
        when(config.get(VespaFeed.ID_FIELD)).thenReturn("id");
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed output = new VespaFeed("test-id", config, null, mockClient);

        Event event = VespaFeedTestHelper.createMockEvent("doc1", "value1");
        when(event.getField("id")).thenReturn("doc1");

        output.output(Collections.singletonList(event));
        
        // Verify the document ID was still taken from the event's "id" field
        verify(mockClient).put(
            argThat(docId -> docId.userSpecific().equals("doc1")),
            any(),
            any()
        );
    }

    @Test
    public void testIdFieldMissing() throws Exception {
        Configuration config = VespaFeedTestHelper.createMockConfig("put", false, false);
        when(config.get(VespaFeed.ID_FIELD)).thenReturn("missing_field");  // ID field that doesn't exist
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed output = new VespaFeed("test-id", config, null, mockClient);
        Event event = VespaFeedTestHelper.createMockEvent("doc1", "value1");
        output.output(Collections.singletonList(event));
        
        // Verify a UUID was generated (matches UUID pattern)
        verify(mockClient).put(
            argThat(docId -> docId.toString().matches("id:test-namespace:test-doc-type::[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")),
            any(),
            any()
        );
    }

    @Test
    public void testRemoveId() throws Exception {
        Configuration config = VespaFeedTestHelper.createMockConfig("put", false, false);
        when(config.get(VespaFeed.REMOVE_ID)).thenReturn(true);
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed output = new VespaFeed("test-id", config, null, mockClient);

        Event event = VespaFeedTestHelper.createMockEvent("doc1", "value1", "doc_id", "doc1");
        output.output(Collections.singletonList(event));
        verify(event).remove("doc_id");
    }

    @Test
    public void testRemoveNamespace() throws Exception {
        Configuration config = VespaFeedTestHelper.createMockConfig("put", false, false);
        when(config.get(VespaFeed.NAMESPACE)).thenReturn("%{namespace_field}");
        when(config.get(VespaFeed.REMOVE_NAMESPACE)).thenReturn(true);
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed output = new VespaFeed("test-id", config, null, mockClient);

        Event event = VespaFeedTestHelper.createMockEvent("doc1", "value1", "namespace_field", "test-ns");
        output.output(Collections.singletonList(event));
        verify(event).remove("namespace_field");
    }

    @Test
    public void testRemoveDocumentType() throws Exception {
        Configuration config = VespaFeedTestHelper.createMockConfig("put", false, false);
        when(config.get(VespaFeed.DOCUMENT_TYPE)).thenReturn("%{doc_type_field}");
        when(config.get(VespaFeed.REMOVE_DOCUMENT_TYPE)).thenReturn(true);
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed output = new VespaFeed("test-id", config, null, mockClient);

        Event event = VespaFeedTestHelper.createMockEvent("doc1", "value1", "doc_type_field", "test-doc-type");
        output.output(Collections.singletonList(event));
        verify(event).remove("doc_type_field");
    }

    @Test
    public void testRemoveOperation() throws Exception {
        Configuration config = VespaFeedTestHelper.createMockConfig("%{operation_field}", false, true);
        when(config.get(VespaFeed.REMOVE_OPERATION)).thenReturn(true);
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed output = new VespaFeed("test-id", config, null, mockClient);

        Event event = VespaFeedTestHelper.createMockEvent("doc1", "value1", "operation_field", "put");
        output.output(Collections.singletonList(event));
        verify(event).remove("operation_field");
    }

    @Test
    public void testDlqDisabled() throws Exception {
        // Create config with DLQ disabled (default in helper)
        Configuration config = VespaFeedTestHelper.createMockConfig("put", false, false);
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed feed = new VespaFeed("test-id", config, null, mockClient);

        // Create an event that will cause an error
        Event event = VespaFeedTestHelper.createMockEvent("doc1", "value1");
        when(event.getData()).thenThrow(new RuntimeException("Serialization error"));

        // Process the event
        feed.output(Collections.singletonList(event));
        
        // Verify no feed operations were attempted since we had an error
        verify(mockClient, times(0)).put(any(), any(), any());
        verify(mockClient, times(0)).update(any(), any(), any());
        verify(mockClient, times(0)).remove(any(), any());
    }

    @Test
    public void testDlqEnabled() throws Exception {
        DeadLetterQueueWriter mockDlqWriter = mock(DeadLetterQueueWriter.class);
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        VespaFeed feed = VespaFeedTestHelper.createFeedWithDlq(mockDlqWriter, mockClient, "put", false);
        
        // Create a Logstash event that will cause an error
        Event event = VespaFeedTestHelper.createLogstashEvent("doc1", "value1");
        // Make getData() throw an exception that should make asyncFeed() throw an exception as well,
        // which should be caught by output() and the event should be written to the DLQ
        Event spyEvent = spy(event);
        when(spyEvent.getData()).thenThrow(new RuntimeException("Serialization error"));
        
        feed.output(Collections.singletonList(spyEvent));
        
        VespaFeedTestHelper.verifyDlqEntry(mockDlqWriter, "Serialization error");
    }

    @Test
    public void testDlqEnabled_InvalidOperation() throws Exception {
        DeadLetterQueueWriter mockDlqWriter = mock(DeadLetterQueueWriter.class);
        FeedClient mockClient = VespaFeedTestHelper.createMockFeedClient();
        // config has a dynamic operation, so we can test with an invalid operation from an event
        VespaFeed feed = VespaFeedTestHelper.createFeedWithDlq(mockDlqWriter, mockClient, "%{operation_field}", true);
        
        // Create event with invalid operation
        Event event = VespaFeedTestHelper.createLogstashEvent("doc1", "value1");
        Event spyEvent = spy(event);
        when(spyEvent.getField("operation_field")).thenReturn("invalid_operation");
        
        feed.output(Collections.singletonList(spyEvent));
        
        VespaFeedTestHelper.verifyDlqEntry(mockDlqWriter, "Invalid operation (must be put, update or remove)");
        // Verify no feed operations were attempted
        verify(mockClient, times(0)).put(any(), any(), any());
        verify(mockClient, times(0)).update(any(), any(), any());
        verify(mockClient, times(0)).remove(any(), any());
    }

    @Test
    public void testDlqEnabled_FeedOperationFailure() throws Exception {
        DeadLetterQueueWriter mockDlqWriter = mock(DeadLetterQueueWriter.class);
        FeedClient mockClient = mock(FeedClient.class);

        // Make the feed operation fail
        CompletableFuture<Result> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Feed operation failed"));
        when(mockClient.put(any(), any(), any())).thenReturn(failedFuture);
        
        VespaFeed feed = VespaFeedTestHelper.createFeedWithDlq(mockDlqWriter, mockClient, "put", false);
        feed.output(Collections.singletonList(VespaFeedTestHelper.createLogstashEvent("doc1", "value1")));
        
        VespaFeedTestHelper.verifyDlqEntry(mockDlqWriter, "Error while waiting for async operation to complete");
        // Verify feed operation was attempted (but failed)
        verify(mockClient, times(1)).put(any(), any(), any());
    }
} 