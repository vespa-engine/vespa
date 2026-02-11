package org.logstashplugins;

import co.elastic.logstash.api.Event;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

public class VespaQuickStarterTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    
    private QuickStartConfig mockConfig;
    private VespaDeployer mockDeployer;
    private VespaAppPackageWriter mockAppWriter;
    private VespaQuickStarter quickStarter;
    private File appDir;
    
    @Before
    public void setup() throws Exception {
        // Create a temporary application package directory
        appDir = temporaryFolder.newFolder("vespa_app");
        
        // Create mock config
        mockConfig = mock(QuickStartConfig.class);
        when(mockConfig.getApplicationPackageDir()).thenReturn(appDir.getAbsolutePath());
        when(mockConfig.getDocumentType()).thenReturn("test_document");
        when(mockConfig.isVespaCloud()).thenReturn(false);
        when(mockConfig.getConfigServer()).thenReturn(URI.create("http://localhost:19071"));
        when(mockConfig.isDeployPackage()).thenReturn(true);
        when(mockConfig.getIdleBatches()).thenReturn(3L); // Trigger deployment after 3 empty batches
        
        // Use null for the type mappings file to use the built-in one
        when(mockConfig.getTypeMappingsFile()).thenReturn(null);
        
        // Create the quick starter with the real config
        // We'll inject mocks for the deployer and app writer
        quickStarter = new VespaQuickStarter(mockConfig);
        
        // Create mocks for injection
        mockDeployer = mock(VespaDeployer.class);
        mockAppWriter = mock(VespaAppPackageWriter.class);
        
        // Inject the mocks using reflection
        injectMockDeployer(quickStarter, mockDeployer);
        injectMockAppWriter(quickStarter, mockAppWriter);
    }
    
    private void injectMockDeployer(VespaQuickStarter quickStarter, VespaDeployer mockDeployer) throws Exception {
        Field field = VespaQuickStarter.class.getDeclaredField("deployer");
        field.setAccessible(true);
        field.set(quickStarter, mockDeployer);
    }
    
    private void injectMockAppWriter(VespaQuickStarter quickStarter, VespaAppPackageWriter mockAppWriter) throws Exception {
        Field field = VespaQuickStarter.class.getDeclaredField("appPackageWriter");
        field.setAccessible(true);
        field.set(quickStarter, mockAppWriter);
    }
    
    @Test
    public void testEmptyBatchCountingAndDeployment() throws IOException {
        // Initially both counters should be zero/false
        assertEquals("Initial empty batches count should be 0", 0, quickStarter.getEmptyBatchesCount());
        assertFalse("Initial gotEvents should be false", quickStarter.hasGotEvents());
        
        // Create an empty batch
        Collection<Event> emptyBatch = new ArrayList<>();
        
        // Run with empty batch - first time
        quickStarter.run(emptyBatch);
        
        // Verify count incremented but gotEvents still false
        assertEquals("Empty batches count should be 1", 1, quickStarter.getEmptyBatchesCount());
        assertFalse("gotEvents should still be false", quickStarter.hasGotEvents());
        verify(mockDeployer, never()).deployApplicationPackage();
        
        // Run with empty batch - second time
        quickStarter.run(emptyBatch);
        
        // Verify count incremented again
        assertEquals("Empty batches count should be 2", 2, quickStarter.getEmptyBatchesCount());
        assertFalse("gotEvents should still be false", quickStarter.hasGotEvents());
        verify(mockDeployer, never()).deployApplicationPackage();
        
        // Run with empty batch - third time
        quickStarter.run(emptyBatch);
        
        // Verify count incremented again but still no deployment (gotEvents is false)
        assertEquals("Empty batches count should be 3", 3, quickStarter.getEmptyBatchesCount());
        assertFalse("gotEvents should still be false", quickStarter.hasGotEvents());
        verify(mockDeployer, never()).deployApplicationPackage();
        
        // Now send a non-empty batch to set gotEvents to true
        Collection<Event> nonEmptyBatch = new ArrayList<>();
        Event mockEvent = mock(Event.class);
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message", "test message");
        when(mockEvent.getData()).thenReturn(eventData);
        nonEmptyBatch.add(mockEvent);
        
        quickStarter.run(nonEmptyBatch);
        
        // Verify counter reset and gotEvents set to true
        assertEquals("Empty batches count should be reset to 0", 0, quickStarter.getEmptyBatchesCount());
        assertTrue("gotEvents should now be true", quickStarter.hasGotEvents());
        verify(mockAppWriter, times(1)).writeApplicationPackage(anyMap());
        
        // Now send 2 empty batches (not enough to trigger deployment)
        quickStarter.run(emptyBatch);
        assertEquals("Empty batches count should be 1", 1, quickStarter.getEmptyBatchesCount());
        
        quickStarter.run(emptyBatch);
        assertEquals("Empty batches count should be 2", 2, quickStarter.getEmptyBatchesCount());
        
        // Send another non-empty batch to test counter reset by events
        quickStarter.run(nonEmptyBatch);
        assertEquals("Empty batches count should be reset to 0 by non-empty batch", 0, quickStarter.getEmptyBatchesCount());
        
        // Now run with 3 empty batches to trigger deployment
        quickStarter.run(emptyBatch);
        assertEquals("Empty batches count should be 1", 1, quickStarter.getEmptyBatchesCount());
        
        quickStarter.run(emptyBatch);
        assertEquals("Empty batches count should be 2", 2, quickStarter.getEmptyBatchesCount());
        
        quickStarter.run(emptyBatch);
        
        // Verify count is reset after deployment
        assertEquals("Empty batches count should be reset to 0 after deployment", 0, quickStarter.getEmptyBatchesCount());
        
        // Now the deployment should have been triggered
        verify(mockDeployer, times(1)).deployApplicationPackage();
    }
    
    @Test
    public void testDeploymentDisabled() throws Exception {
        // Configure to disable deployment
        when(mockConfig.isDeployPackage()).thenReturn(false);
        
        // Reset the quickStarter with new config
        VespaQuickStarter newQuickStarter = new VespaQuickStarter(mockConfig);
        
        // Inject the mocks
        injectMockDeployer(newQuickStarter, mockDeployer);
        injectMockAppWriter(newQuickStarter, mockAppWriter);
        
        // First send some events to set gotEvents=true
        Collection<Event> nonEmptyBatch = new ArrayList<>();
        Event mockEvent = mock(Event.class);
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message", "test message");
        when(mockEvent.getData()).thenReturn(eventData);
        nonEmptyBatch.add(mockEvent);
        
        newQuickStarter.run(nonEmptyBatch);
        assertTrue("gotEvents should be true", newQuickStarter.hasGotEvents());
        
        // Now send enough empty batches to trigger deployment (if it were enabled)
        Collection<Event> emptyBatch = new ArrayList<>();
        newQuickStarter.run(emptyBatch);
        newQuickStarter.run(emptyBatch);
        newQuickStarter.run(emptyBatch);
        
        // The counter should still be at 3 since no deployment happened to reset it
        assertEquals("Empty batches count should be 3", 3, newQuickStarter.getEmptyBatchesCount());
        
        // Verify deployment never called because it's disabled
        verify(mockDeployer, never()).deployApplicationPackage();
    }
    
    @Test
    public void testDetectTypeString() {
        String value = "test string";
        assertEquals("string", quickStarter.detectType(value));
    }
    
    @Test
    public void testDetectTypeInt8() {
        Integer smallInt = 127; // Within byte range
        assertEquals("int8", quickStarter.detectType(smallInt));
    }
    
    @Test
    public void testDetectTypeLong() {
        // Integer outside byte range
        Integer largeInt = 1000;
        assertEquals("long", quickStarter.detectType(largeInt));
        
        // Actual Long value
        Long longValue = 1000000000000L;
        assertEquals("long", quickStarter.detectType(longValue));
    }
    
    @Test
    public void testDetectTypeFloat() {
        Double doubleValue = 3.14159;
        assertEquals("float", quickStarter.detectType(doubleValue));
    }
    
    @Test
    public void testDetectTypeBool() {
        Boolean boolValue = true;
        assertEquals("bool", quickStarter.detectType(boolValue));
    }
    
    @Test
    public void testDetectTypePosition() {
        Map<String, Object> positionMap = new HashMap<>();
        positionMap.put("lat", 59.9);
        positionMap.put("lng", 10.7);
        
        assertEquals("position", quickStarter.detectType(positionMap));
    }
    
    @Test
    public void testDetectTypeObjectWithStringValues() {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("key1", "value1");
        objectMap.put("key2", "value2");
        
        assertEquals("object<string>", quickStarter.detectType(objectMap));
    }
    
    @Test
    public void testDetectTypeObjectWithNumberValues() {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("key1", 42);
        objectMap.put("key2", 100);
        
        assertEquals("object<int8>", quickStarter.detectType(objectMap));
    }
    
    @Test
    public void testDetectTypeArray() {
        List<String> stringList = Arrays.asList("one", "two", "three");
        assertEquals("array<string>[3]", quickStarter.detectType(stringList));
        
        List<Integer> intList = Arrays.asList(1, 2, 3, 4, 5);
        assertEquals("array<int8>[5]", quickStarter.detectType(intList));
        
        List<Double> doubleList = Arrays.asList(1.1, 2.2, 3.3);
        assertEquals("array<float>[3]", quickStarter.detectType(doubleList));
    }
    
    @Test
    public void testDetectTypeEmptyArray() {
        List<Object> emptyList = new ArrayList<>();
        assertNull("Empty list should return null", quickStarter.detectType(emptyList));
    }
    
    @Test
    public void testDetectTypeNestedStructures() {
        // Array of objects
        List<Map<String, Object>> arrayOfObjects = new ArrayList<>();
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("first_name", "John");
        obj1.put("last_name", "Smith");
        arrayOfObjects.add(obj1);
        
        assertEquals("array<object<string>>[1]", quickStarter.detectType(arrayOfObjects));
        
        // Object with array values
        Map<String, Object> objectWithArrays = new HashMap<>();
        objectWithArrays.put("tags", Arrays.asList("tag1", "tag2"));
        
        assertEquals("object<array<string>[2]>", quickStarter.detectType(objectWithArrays));
    }
    
    @Test
    public void testDetectTypeUnsupportedType() {
        // Create an unsupported type (e.g., a custom class)
        class CustomClass {}
        CustomClass customObj = new CustomClass();
        
        assertNull("Unsupported type should return null", quickStarter.detectType(customObj));
    }
    
    @Test
    public void testTypeConflictResolution() throws IOException {
        // Setup for conflict resolution
        when(mockAppWriter.resolveTypeConflict("string", "int8")).thenReturn("string");
        
        // Create multiple events with the same field but different types
        Collection<Event> initialBatch = new ArrayList<>();
        Event firstEvent = mock(Event.class);
        Map<String, Object> firstEventData = new HashMap<>();
        firstEventData.put("field1", "string value");
        when(firstEvent.getData()).thenReturn(firstEventData);
        initialBatch.add(firstEvent);
        
        // Process the first event
        quickStarter.run(initialBatch);
        
        // Verify app writer was called with field1 as string type
        ArgumentCaptor<Map<String, String>> fieldTypesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockAppWriter).writeApplicationPackage(fieldTypesCaptor.capture());
        Map<String, String> capturedTypes = fieldTypesCaptor.getValue();
        assertEquals("string", capturedTypes.get("field1"));
        
        // Create second event with different type for the same field
        Collection<Event> secondBatch = new ArrayList<>();
        Event secondEvent = mock(Event.class);
        Map<String, Object> secondEventData = new HashMap<>();
        secondEventData.put("field1", 42); // Now it's an integer
        when(secondEvent.getData()).thenReturn(secondEventData);
        secondBatch.add(secondEvent);
        
        // Reset the mock to capture the next call
        reset(mockAppWriter);
        when(mockAppWriter.resolveTypeConflict("string", "int8")).thenReturn("string");
        
        // Process the second event
        quickStarter.run(secondBatch);
        
        // Verify type conflict was resolved
        verify(mockAppWriter).resolveTypeConflict("string", "int8");
        
        // Verify app writer was called with the resolved type
        verify(mockAppWriter).writeApplicationPackage(fieldTypesCaptor.capture());
        capturedTypes = fieldTypesCaptor.getValue();
        assertEquals("string", capturedTypes.get("field1"));
    }
    
    @Test
    public void testDetectTypeEmptyMap() {
        Map<String, Object> emptyMap = Collections.emptyMap();
        
        assertNull("Empty map should return null", quickStarter.detectType(emptyMap));
    }
    
    @Test
    public void testEventProcessingWithNullValues() throws IOException {
        // Create an event with a null value field
        Collection<Event> batch = new ArrayList<>();
        Event event = mock(Event.class);
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("field1", "value1");
        eventData.put("field2", null);  // Null value field
        when(event.getData()).thenReturn(eventData);
        batch.add(event);
        
        // Process the event
        quickStarter.run(batch);
        
        // Verify app writer was called with only the non-null field
        ArgumentCaptor<Map<String, String>> fieldTypesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockAppWriter).writeApplicationPackage(fieldTypesCaptor.capture());
        Map<String, String> capturedTypes = fieldTypesCaptor.getValue();
        
        assertTrue("Only non-null field should be detected", capturedTypes.containsKey("field1"));
        assertFalse("Null field should be skipped", capturedTypes.containsKey("field2"));
        assertEquals("string", capturedTypes.get("field1"));
    }
} 