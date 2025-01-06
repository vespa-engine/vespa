package org.logstashplugins;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.OperationParameters;
import ai.vespa.feed.client.Result;
import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Event;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VespaFeedTestHelper {
    
    public static Configuration createMockConfig(String operation, boolean create, boolean dynamicOperation) {
        Configuration config = mock(Configuration.class);
        try {
            // Set required config values
            when(config.get(VespaFeed.VESPA_URL)).thenReturn(new URI("http://localhost:8080"));
            when(config.get(VespaFeed.NAMESPACE)).thenReturn(dynamicOperation ? "%{namespace}" : "test-namespace");
            when(config.get(VespaFeed.DOCUMENT_TYPE)).thenReturn("test-doc-type");
            when(config.get(VespaFeed.ID_FIELD)).thenReturn("doc_id");
            when(config.get(VespaFeed.REMOVE_ID)).thenReturn(false);
            when(config.get(VespaFeed.REMOVE_NAMESPACE)).thenReturn(false);
            when(config.get(VespaFeed.REMOVE_DOCUMENT_TYPE)).thenReturn(false);
            when(config.get(VespaFeed.OPERATION)).thenReturn(operation);
            when(config.get(VespaFeed.CREATE)).thenReturn(create);
            when(config.get(VespaFeed.REMOVE_OPERATION)).thenReturn(false);
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

    public static Event createMockEvent(String docId, String value, String additionalFieldName, String additionalFieldValue) {
        Event event = mock(Event.class);
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("field1", value);
        if (docId != null) {
            eventData.put("doc_id", docId);
        }
        if (additionalFieldName != null && additionalFieldValue != null) {
            eventData.put(additionalFieldName, additionalFieldValue);
            when(event.getField(additionalFieldName)).thenReturn(additionalFieldValue);
            when(event.remove(additionalFieldName)).thenReturn(additionalFieldName);
        }
        
        when(event.getData()).thenReturn(eventData);
        when(event.getField("doc_id")).thenReturn(docId);
        return event;
    }

    public static Event createMockEvent(String docId, String value) {
        return createMockEvent(docId, value, null, null);
    }

    public static FeedClient createMockFeedClient() {
        FeedClient mockClient = mock(FeedClient.class);
        Result mockResult = mock(Result.class);
        when(mockResult.type()).thenReturn(Result.Type.success);
        CompletableFuture<Result> successFuture = CompletableFuture.completedFuture(mockResult);
        when(mockClient.put(any(), any(), any())).thenReturn(successFuture);
        when(mockClient.update(any(), any(), any())).thenReturn(successFuture);
        when(mockClient.remove(any(), any())).thenReturn(successFuture);
        return mockClient;
    }

    public static VespaFeed createVespaFeed(String operation, boolean create, boolean dynamicOperation, FeedClient mockClient) {
        Configuration config = createMockConfig(operation, create, dynamicOperation);
        when(config.get(VespaFeed.ID_FIELD)).thenReturn("doc_id");
        when(config.get(VespaFeed.NAMESPACE)).thenReturn("test-namespace");
        return new VespaFeed("test-id", config, null, mockClient);
    }

    public static void verifyDocument(FeedClient mockClient, String docId, String value) {
        org.mockito.Mockito.verify(mockClient).put(
            org.mockito.ArgumentMatchers.eq(DocumentId.of("test-namespace", "test-doc-type", docId)),
            org.mockito.ArgumentMatchers.contains("\"fields\":{\"field1\":\"" + value + "\",\"doc_id\":\"" + docId + "\"}"),
            any(OperationParameters.class)
        );
    }
} 