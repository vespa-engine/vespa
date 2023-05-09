// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.yahoo.container.logging.TraceRenderer;
import com.yahoo.data.JsonProducer;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.data.access.simple.JsonRender;
import com.yahoo.data.access.simple.Value;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.lang.MutableBoolean;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution.Trace;
import com.yahoo.processing.rendering.AsynchronousSectionedRenderer;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.result.AbstractList;
import com.yahoo.search.grouping.result.BucketGroupId;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupId;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.grouping.result.ValueGroupId;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.DefaultErrorHit;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.NanNumber;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.JsonFormat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

import static com.fasterxml.jackson.databind.SerializationFeature.FLUSH_AFTER_WRITE_VALUE;

/**
 * JSON renderer for search results.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
// NOTE: The JSON format is a public API. If new elements are added be sure to update the reference doc.
public class JsonRenderer extends AsynchronousSectionedRenderer<Result> {

    private static final CompoundName WRAP_DEEP_MAPS = CompoundName.from("renderer.json.jsonMaps");
    private static final CompoundName WRAP_WSETS = CompoundName.from("renderer.json.jsonWsets");
    private static final CompoundName DEBUG_RENDERING_KEY = CompoundName.from("renderer.json.debug");
    private static final CompoundName JSON_CALLBACK = CompoundName.from("jsoncallback");

    // if this must be optimized, simply use com.fasterxml.jackson.core.SerializableString
    private static final String BUCKET_LIMITS = "limits";
    private static final String BUCKET_TO = "to";
    private static final String BUCKET_FROM = "from";
    private static final String CHILDREN = "children";
    private static final String CONTINUATION = "continuation";
    private static final String COVERAGE = "coverage";
    private static final String COVERAGE_COVERAGE = "coverage";
    private static final String COVERAGE_DOCUMENTS = "documents";
    private static final String COVERAGE_DEGRADE = "degraded";
    private static final String COVERAGE_DEGRADE_MATCHPHASE = "match-phase";
    private static final String COVERAGE_DEGRADE_TIMEOUT = "timeout";
    private static final String COVERAGE_DEGRADE_ADAPTIVE_TIMEOUT = "adaptive-timeout";
    private static final String COVERAGE_DEGRADED_NON_IDEAL_STATE = "non-ideal-state";
    private static final String COVERAGE_FULL = "full";
    private static final String COVERAGE_NODES = "nodes";
    private static final String COVERAGE_RESULTS = "results";
    private static final String COVERAGE_RESULTS_FULL = "resultsFull";
    private static final String ERRORS = "errors";
    private static final String ERROR_CODE = "code";
    private static final String ERROR_MESSAGE = "message";
    private static final String ERROR_SOURCE = "source";
    private static final String ERROR_STACK_TRACE = "stackTrace";
    private static final String ERROR_SUMMARY = "summary";
    private static final String FIELDS = "fields";
    private static final String ID = "id";
    private static final String LABEL = "label";
    private static final String RELEVANCE = "relevance";
    private static final String ROOT = "root";
    private static final String SOURCE = "source";
    private static final String TOTAL_COUNT = "totalCount";
    private static final String TIMING = "timing";
    private static final String QUERY_TIME = "querytime";
    private static final String SUMMARY_FETCH_TIME = "summaryfetchtime";
    private static final String SEARCH_TIME = "searchtime";
    private static final String TYPES = "types";
    private static final String GROUPING_VALUE = "value";
    private static final String VESPA_HIDDEN_FIELD_PREFIX = "$";

    private static final JsonFactory generatorFactory = createGeneratorFactory();

    private volatile JsonGenerator generator;
    private volatile FieldConsumer fieldConsumer;
    private volatile Deque<Integer> renderedChildren;

    static class FieldConsumerSettings {
        volatile boolean debugRendering = false;
        volatile boolean jsonDeepMaps = true;
        volatile boolean jsonWsets = true;
        volatile boolean jsonMapsAll = true;
        volatile boolean jsonWsetsAll = false;
        volatile boolean tensorShortForm = true;
        volatile boolean tensorDirectValues = false;
        boolean convertDeep() { return (jsonDeepMaps || jsonWsets); }
        void init() {
            this.debugRendering = false;
            this.jsonDeepMaps = true;
            this.jsonWsets = true;
            this.jsonMapsAll = true;
            this.jsonWsetsAll = true;
            this.tensorShortForm = true;
            this.tensorDirectValues = false;
        }
        void getSettings(Query q) {
            if (q == null) {
                init();
                return;
            }
            var props = q.properties();
            this.debugRendering = props.getBoolean(DEBUG_RENDERING_KEY, false);
            this.jsonDeepMaps = props.getBoolean(WRAP_DEEP_MAPS, true);
            this.jsonWsets = props.getBoolean(WRAP_WSETS, true);
            // we may need more fine tuning, but for now use the same query parameters here:
            this.jsonMapsAll = props.getBoolean(WRAP_DEEP_MAPS, true);
            this.jsonWsetsAll = props.getBoolean(WRAP_WSETS, true);
            this.tensorShortForm = q.getPresentation().getTensorShortForm();
            this.tensorDirectValues = q.getPresentation().getTensorDirectValues();
            }
    }

    private volatile FieldConsumerSettings fieldConsumerSettings;
    private volatile LongSupplier timeSource;
    private volatile OutputStream stream;

    public JsonRenderer() {
        this(null);
    }

    /**
     * Creates a json renderer using a custom executor.
     * Using a custom executor is useful for tests to avoid creating new threads for each renderer registry.
     */
    public JsonRenderer(Executor executor) {
        super(executor);
    }

    private static JsonFactory createGeneratorFactory() {
        var factory = new JsonFactoryBuilder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
                .build();
        factory.setCodec(new ObjectMapper(factory).disable(FLUSH_AFTER_WRITE_VALUE));
        return factory;
    }

    @Override
    public void init() {
        super.init();
        fieldConsumerSettings = new FieldConsumerSettings();
        fieldConsumerSettings.init();
        setGenerator(null, fieldConsumerSettings);
        renderedChildren = null;
        timeSource = System::currentTimeMillis;
        stream = null;
    }

    @Override
    public void beginResponse(OutputStream stream) throws IOException {
        beginJsonCallback(stream);
        fieldConsumerSettings.getSettings(getResult().getQuery());
        setGenerator(generatorFactory.createGenerator(stream, JsonEncoding.UTF8), fieldConsumerSettings);
        renderedChildren = new ArrayDeque<>();
        generator.writeStartObject();
        renderTrace(getExecution().trace());
        renderTiming();
        generator.writeFieldName(ROOT);
    }

    private void renderTiming() throws IOException {
        if (!getResult().getQuery().getPresentation().getTiming()) return;

        double milli = .001d;
        long now = timeSource.getAsLong();
        long searchTime = now - getResult().getElapsedTime().first();
        double searchSeconds = searchTime * milli;

        generator.writeObjectFieldStart(TIMING);
        if (getResult().getElapsedTime().firstFill() != 0L) {
            long queryTime = getResult().getElapsedTime().weightedSearchTime();
            long summaryFetchTime = getResult().getElapsedTime().weightedFillTime();
            double querySeconds = queryTime * milli;
            double summarySeconds = summaryFetchTime * milli;
            generator.writeNumberField(QUERY_TIME, querySeconds);
            generator.writeNumberField(SUMMARY_FETCH_TIME, summarySeconds);
        }

        generator.writeNumberField(SEARCH_TIME, searchSeconds);
        generator.writeEndObject();
    }

    protected void renderTrace(Trace trace) throws IOException {
        if (!trace.traceNode().children().iterator().hasNext()) return;
        if (getResult().getQuery().getTrace().getLevel() == 0) return;

        try {
            long basetime = trace.traceNode().timestamp();
            if (basetime == 0L)
                basetime = getResult().getElapsedTime().first();
            trace.accept(new TraceRenderer(generator, fieldConsumer, basetime));
        } catch (TraceRenderer.TraceRenderWrapper e) {
            throw new IOException(e);
        }
    }

    @Override
    public void beginList(DataList<?> list) throws IOException {
        Preconditions.checkArgument(list instanceof HitGroup,
                                    "Expected subclass of com.yahoo.search.result.HitGroup, got %s.",
                                    list.getClass());
        moreChildren();
        renderHitGroupHead((HitGroup) list);
    }

    protected void moreChildren() throws IOException {
        if (!renderedChildren.isEmpty())
            childrenArray();

        renderedChildren.push(0);
    }

    private void childrenArray() throws IOException {
        if (renderedChildren.peek() == 0)
            generator.writeArrayFieldStart(CHILDREN);
        renderedChildren.push(renderedChildren.pop() + 1);
    }

    private void lessChildren() throws IOException {
        int lastRenderedChildren = renderedChildren.pop();
        if (lastRenderedChildren > 0) {
            generator.writeEndArray();
        }
    }

    protected void renderHitGroupHead(HitGroup hitGroup) throws IOException {
        generator.writeStartObject();

        renderHitContents(hitGroup);
        if (getRecursionLevel() == 1)
            renderCoverage();

        ErrorHit errorHit = hitGroup.getErrorHit();
        if (errorHit != null)
            renderErrors(errorHit.errors());

        // the framework will invoke begin methods as needed from here
    }

    protected void renderErrors(Set<ErrorMessage> errors) throws IOException {
        if (errors.isEmpty()) return;

        generator.writeArrayFieldStart(ERRORS);
        for (ErrorMessage e : errors) {
            String summary = e.getMessage();
            String source = e.getSource();
            Throwable cause = e.getCause();
            String message = e.getDetailedMessage();
            generator.writeStartObject();
            generator.writeNumberField(ERROR_CODE, e.getCode());
            generator.writeStringField(ERROR_SUMMARY, summary);
            if (source != null) {
                generator.writeStringField(ERROR_SOURCE, source);
            }
            if (message != null) {
                generator.writeStringField(ERROR_MESSAGE, message);
            }
            if (cause != null && cause.getStackTrace().length > 0) {
                StringWriter s = new StringWriter();
                PrintWriter p = new PrintWriter(s);
                cause.printStackTrace(p);
                p.close();
                generator.writeStringField(ERROR_STACK_TRACE, s.toString());
            }
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    protected void renderCoverage() throws IOException {
        Coverage c = getResult().getCoverage(false);
        if (c == null) return;

        generator.writeObjectFieldStart(COVERAGE);
        generator.writeNumberField(COVERAGE_COVERAGE, c.getResultPercentage());
        generator.writeNumberField(COVERAGE_DOCUMENTS, c.getDocs());
        if (c.isDegraded()) {
            generator.writeObjectFieldStart(COVERAGE_DEGRADE);
            generator.writeBooleanField(COVERAGE_DEGRADE_MATCHPHASE, c.isDegradedByMatchPhase());
            generator.writeBooleanField(COVERAGE_DEGRADE_TIMEOUT, c.isDegradedByTimeout());
            generator.writeBooleanField(COVERAGE_DEGRADE_ADAPTIVE_TIMEOUT, c.isDegradedByAdapativeTimeout());
            generator.writeBooleanField(COVERAGE_DEGRADED_NON_IDEAL_STATE, c.isDegradedByNonIdealState());
            generator.writeEndObject();
        }
        generator.writeBooleanField(COVERAGE_FULL, c.getFull());
        generator.writeNumberField(COVERAGE_NODES, c.getNodes());
        generator.writeNumberField(COVERAGE_RESULTS, c.getResultSets());
        generator.writeNumberField(COVERAGE_RESULTS_FULL, c.getFullResultSets());
        generator.writeEndObject();
    }

    protected void renderHit(Hit hit) throws IOException {
        if (!shouldRender(hit)) return;

        childrenArray();
        generator.writeStartObject();
        renderHitContents(hit);
        generator.writeEndObject();
    }

    protected boolean shouldRender(Hit hit) {
        return ! (hit instanceof DefaultErrorHit);
    }

    protected void renderHitContents(Hit hit) throws IOException {
        String id = hit.getDisplayId();
        if (id != null)
            generator.writeStringField(ID, id);

        generator.writeNumberField(RELEVANCE, hit.getRelevance().getScore());

        if (hit.types().size() > 0) {
            generator.writeArrayFieldStart(TYPES);
            for (String t : hit.types()) {
                generator.writeString(t);
            }
            generator.writeEndArray();
        }

        String source = hit.getSource();
        if (source != null)
            generator.writeStringField(SOURCE, hit.getSource());

        renderSpecialCasesForGrouping(hit);

        renderAllFields(hit);
    }

    protected void renderAllFields(Hit hit) throws IOException {
        fieldConsumer.startHitFields();
        renderTotalHitCount(hit);
        renderStandardFields(hit);
        fieldConsumer.endHitFields();
    }

    private void renderStandardFields(Hit hit) {
        hit.forEachFieldAsRaw(fieldConsumer);
    }

    private void renderSpecialCasesForGrouping(Hit hit) throws IOException {
        if (hit instanceof AbstractList) {
            renderGroupingListSyntheticFields((AbstractList) hit);
        } else if (hit instanceof Group) {
            renderGroupingGroupSyntheticFields(hit);
        }
    }

    private void renderGroupingGroupSyntheticFields(Hit hit) throws IOException {
        renderGroupMetadata(((Group) hit).getGroupId());
        if (hit instanceof RootGroup) {
            renderContinuations(Map.of(Continuation.THIS_PAGE, ((RootGroup) hit).continuation()));
        }
    }

    private void renderGroupingListSyntheticFields(AbstractList a) throws IOException {
        writeGroupingLabel(a);
        renderContinuations(a.continuations());
    }

    private void writeGroupingLabel(AbstractList a) throws IOException {
        generator.writeStringField(LABEL, a.getLabel());
    }

    protected void renderContinuations(Map<String, Continuation> continuations) throws IOException {
        if (continuations.isEmpty()) return;

        generator.writeObjectFieldStart(CONTINUATION);
        for (Map.Entry<String, Continuation> e : continuations.entrySet()) {
            generator.writeStringField(e.getKey(), e.getValue().toString());
        }
        generator.writeEndObject();
    }

    protected void renderGroupMetadata(GroupId id) throws IOException {
        if (!(id instanceof ValueGroupId<?> || id instanceof BucketGroupId)) return;

        if (id instanceof ValueGroupId<?> valueId) {
            generator.writeStringField(GROUPING_VALUE, valueId.getValue().toString());
        } else {
            BucketGroupId<?> bucketId = (BucketGroupId<?>) id;
            generator.writeObjectFieldStart(BUCKET_LIMITS);
            generator.writeStringField(BUCKET_FROM, bucketId.getFrom().toString());
            generator.writeStringField(BUCKET_TO, bucketId.getTo().toString());
            generator.writeEndObject();
        }
    }

    protected void renderTotalHitCount(Hit hit) throws IOException {
        if ( ! (getRecursionLevel() == 1 && hit instanceof HitGroup)) return;

        fieldConsumer.ensureFieldsField();
        generator.writeNumberField(TOTAL_COUNT, getResult().getTotalHitCount());
        // alternative for the above two lines:
        // fieldConsumer.accept(TOTAL_COUNT, getResult().getTotalHitCount());
    }

    @Override
    public void data(Data data) throws IOException {
        Preconditions.checkArgument(data instanceof Hit,
                                    "Expected subclass of com.yahoo.search.result.Hit, got %s.",
                                    data.getClass());
        renderHit((Hit) data);
    }

    @Override
    public void endList(DataList<?> list) throws IOException {
        lessChildren();
        generator.writeEndObject();
    }

    @Override
    public void endResponse() throws IOException {
        generator.close();
        endJsonCallback();
    }

    @Override
    public String getEncoding() {
        return "utf-8";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    private Result getResult() {
        Response r = getResponse();
        Preconditions.checkArgument(r instanceof Result,
                                    "JsonRenderer can only render instances of com.yahoo.search.Result, got instance of %s.",
                                    r.getClass());
        return (Result) r;
    }

    /**
     * Adds JSONP (Json with padding) support.
     *
     * Basically, if the JSON renderer receives a query parameter "jsoncallback=...",
     * the JSON response will be wrapped in a function call with the name specified
     * by the client. This side-steps the same-origin policy, thus supports calling
     * Vespa from javascript loaded from a different domain then the Vespa instance.
     */
    private void beginJsonCallback(OutputStream stream) throws IOException {
        if (shouldRenderJsonCallback()) {
            String jsonCallback = getJsonCallback() + "(";
            stream.write(jsonCallback.getBytes(StandardCharsets.UTF_8));
            this.stream = stream;
        }
    }

    private void endJsonCallback() throws IOException {
        if (shouldRenderJsonCallback() && stream != null) {
            stream.write(");".getBytes(StandardCharsets.UTF_8));
        }
    }

    private boolean shouldRenderJsonCallback() {
        String jsonCallback = getJsonCallback();
        return jsonCallback != null && !"".equals(jsonCallback);
    }

    private String getJsonCallback() {
        Result result = getResult();
        Query query = result.getQuery();
        if (query != null) {
            return query.properties().getString(JSON_CALLBACK, null);
        }
        return null;
    }

    private void setGenerator(JsonGenerator generator, FieldConsumerSettings settings) {
        this.generator = generator;
        this.fieldConsumer = generator == null ? null : createFieldConsumer(generator, settings);
    }

    /** Override this method to use a custom {@link FieldConsumer} sub-class to render fields */
    protected FieldConsumer createFieldConsumer(boolean debugRendering) {
        fieldConsumerSettings.debugRendering = debugRendering;
        return createFieldConsumer(generator, fieldConsumerSettings);
    }

    private FieldConsumer createFieldConsumer(JsonGenerator generator, FieldConsumerSettings settings) {
        return new FieldConsumer(generator, settings);
    }

    /**
     * Only for testing. Never to be used in any other context.
     */
    void setTimeSource(LongSupplier timeSource) {
        this.timeSource = timeSource;
    }

    /**
     * Received callbacks when fields of hits are encountered.
     * This instance is reused for all hits of a Result since we are in a single-threaded context
     * and want to limit object creation.
     */
    public static class FieldConsumer implements Hit.RawUtf8Consumer, TraceRenderer.FieldConsumer {

        private final JsonGenerator generator;
        private final FieldConsumerSettings settings;
        private MutableBoolean hasFieldsField;

        /** Invoke this from your constructor when sub-classing {@link FieldConsumer} */
        protected FieldConsumer(boolean debugRendering, boolean tensorShortForm, boolean jsonMaps) {
            this(null, debugRendering, tensorShortForm, jsonMaps);
        }

        private FieldConsumer(JsonGenerator generator, boolean debugRendering, boolean tensorShortForm, boolean jsonMaps) {
            this.generator = generator;
            this.settings = new FieldConsumerSettings();
            this.settings.debugRendering = debugRendering;
            this.settings.tensorShortForm = tensorShortForm;
            this.settings.jsonDeepMaps = jsonMaps;
        }

        FieldConsumer(JsonGenerator generator, FieldConsumerSettings settings) {
            this.generator = generator;
            this.settings = settings;
        }

        /**
         * Call before using this for a hit to track whether we
         * have created the "fields" field of the JSON object
         */
        void startHitFields() {
            this.hasFieldsField = new MutableBoolean(false);
        }

        /** Call before rendering a field to the generator */
        void ensureFieldsField() throws IOException {
            if (hasFieldsField.get()) return;
            generator().writeObjectFieldStart(FIELDS);
            hasFieldsField.set(true);
        }

        /** Call after all fields in a hit to close the "fields" field of the JSON object */
        void endHitFields() throws IOException {
            if ( ! hasFieldsField.get()) return;
            generator().writeEndObject();
            this.hasFieldsField = null;
        }

        @Override
        public void accept(String name, Object value) {
            try {
                if (shouldRender(name, value)) {
                    ensureFieldsField();
                    generator().writeFieldName(name);
                    renderFieldContents(value);
                }
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void accept(String name, byte[] utf8Data, int offset, int length) {
            try {
                if (shouldRenderUtf8Value(name, length)) {
                    ensureFieldsField();
                    generator().writeFieldName(name);
                    generator().writeUTF8String(utf8Data, offset, length);
                }
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        protected boolean shouldRender(String name, Object value) {
            if (settings.debugRendering) return true;
            if (name.startsWith(VESPA_HIDDEN_FIELD_PREFIX)) return false;
            if (value instanceof CharSequence && ((CharSequence) value).length() == 0) return false;
            // StringFieldValue cannot hold a null, so checking length directly is OK:
            if (value instanceof StringFieldValue && ((StringFieldValue) value).getString().isEmpty()) return false;
            if (value instanceof NanNumber) return false;
            return true;
        }

        protected boolean shouldRenderUtf8Value(String name, int length) {
            if (settings.debugRendering) return true;
            if (name.startsWith(VESPA_HIDDEN_FIELD_PREFIX)) return false;
            if (length == 0) return false;
            return true;
        }

        private Inspector maybeConvertMap(Inspector data) {
            var map = new Value.ObjectValue();
            for (int i = 0; i < data.entryCount(); i++) {
                Inspector obj = data.entry(i);
                if (obj.type() != Type.OBJECT || obj.fieldCount() != 2) {
                    return null;
                }
                Inspector key = obj.field("key");
                Inspector value = obj.field("value");
                if (! key.valid()) return null;
                if (! value.valid()) return null;
                if (key.type() != Type.STRING && !settings.jsonMapsAll) {
                    return null;
                }
                if (settings.convertDeep()) {
                    value = deepMaybeConvert(value);
                }
                if (key.type() == Type.STRING) {
                    map.put(key.asString(), value);
                } else {
                    map.put(JsonRender.render(key, new StringBuilder(), true).toString(), value);
                }
            }
            return map;
        }

        private Inspector maybeConvertWset(Inspector data) {
            var wset = new Value.ObjectValue();
            for (int i = 0; i < data.entryCount(); i++) {
                Inspector obj = data.entry(i);
                if (obj.type() != Type.OBJECT || obj.fieldCount() != 2) {
                    return null;
                }
                Inspector item = obj.field("item");
                Inspector weight = obj.field("weight");
                if (! item.valid()) return null;
                if (! weight.valid()) return null;
                // TODO support non-integer weights?
                if (weight.type() != Type.LONG) return null;
                if (item.type() == Type.STRING) {
                    wset.put(item.asString(), weight.asLong());
                } else if (settings.jsonWsetsAll) {
                    wset.put(JsonRender.render(item, new StringBuilder(), true).toString(), weight.asLong());
                } else {
                    return null;
                }
            }
            return wset;
        }

        private Inspector convertInsideObject(Inspector data) {
            var object = new Value.ObjectValue();
            for (var entry : data.fields()) {
                object.put(entry.getKey(), deepMaybeConvert(entry.getValue()));
            }
            return object;
        }

        private Inspector deepMaybeConvert(Inspector data) {
            if (data.type() == Type.ARRAY) {
                if (settings.jsonDeepMaps) {
                    var map = maybeConvertMap(data);
                    if (map != null) return map;
                }
                if (settings.jsonWsets) {
                    var wset = maybeConvertWset(data);
                    if (wset != null) return wset;
                }
            }
            if (data.type() == Type.OBJECT) {
                return convertInsideObject(data);
            }
            return data;
        }

        private Inspector convertTopLevelArray(Inspector data) {
            if (data.entryCount() > 0) {
                var map = maybeConvertMap(data);
                if (map != null) return map;
                if (settings.jsonWsets) {
                    var wset = maybeConvertWset(data);
                    if (wset != null) return wset;
                }
                if (settings.convertDeep()) {
                    var array = new Value.ArrayValue(data.entryCount());
                    for (int i = 0; i < data.entryCount(); i++) {
                        Inspector obj = data.entry(i);
                        array.add(deepMaybeConvert(obj));
                    }
                    return array;
                }
            }
            return data;
        }

        private Inspector maybeConvertData(Inspector data) {
            if (data.type() == Type.ARRAY) {
                return convertTopLevelArray(data);
            }
            if (settings.convertDeep() && data.type() == Type.OBJECT) {
                return convertInsideObject(data);
            }
            return data;
        }

        private void renderInspector(Inspector data) throws IOException {
            renderInspectorDirect(maybeConvertData(data));
        }

        private void renderInspectorDirect(Inspector data) throws IOException {
            generator().writeRawValue(JsonRender.render(data, new StringBuilder(), true).toString());
        }

        protected void renderFieldContents(Object field) throws IOException {
            if (field instanceof Inspectable && ! (field instanceof FeatureData)) {
                renderInspector(((Inspectable)field).inspect());
            } else {
                accept(field);
            }
        }

        @Override
        public void accept(Object field) throws IOException {
            if (field == null) {
                generator().writeNull();
            } else if (field instanceof Boolean) {
                generator().writeBoolean((Boolean)field);
            } else if (field instanceof Number) {
                renderNumberField((Number) field);
            } else if (field instanceof TreeNode) {
                generator().writeTree((TreeNode) field);
            } else if (field instanceof Tensor) {
                renderTensor(Optional.of((Tensor)field));
            } else if (field instanceof FeatureData) {
                generator().writeRawValue(((FeatureData)field).toJson(settings.tensorShortForm, settings.tensorDirectValues));
            } else if (field instanceof Inspectable) {
                renderInspectorDirect(((Inspectable)field).inspect());
            } else if (field instanceof JsonProducer) {
                generator().writeRawValue(((JsonProducer) field).toJson());
            } else if (field instanceof StringFieldValue) {
                generator().writeString(((StringFieldValue)field).getString());
            } else if (field instanceof TensorFieldValue) {
                renderTensor(((TensorFieldValue)field).getTensor());
            } else if (field instanceof FieldValue) {
                // the null below is the field which has already been written
                ((FieldValue) field).serialize(null, new JsonWriter(generator));
            } else {
                generator().writeString(field.toString());
            }
        }

        private void renderNumberField(Number field) throws IOException {
            if (field instanceof Integer) {
                generator().writeNumber(field.intValue());
            }  else if (field instanceof Float) {
                generator().writeNumber(field.floatValue());
            }  else if (field instanceof Double) {
                generator().writeNumber(field.doubleValue());
            } else if (field instanceof Long) {
                generator().writeNumber(field.longValue());
            } else if (field instanceof Byte || field instanceof Short) {
                generator().writeNumber(field.intValue());
            } else if (field instanceof BigInteger) {
                generator().writeNumber((BigInteger) field);
            } else if (field instanceof BigDecimal) {
                generator().writeNumber((BigDecimal) field);
            } else {
                generator().writeNumber(field.doubleValue());
            }
        }

        private void renderTensor(Optional<Tensor> tensor) throws IOException {
            generator().writeRawValue(new String(JsonFormat.encode(tensor.orElse(Tensor.Builder.of(TensorType.empty).build()),
                                                                   settings.tensorShortForm, settings.tensorDirectValues),
                                                 StandardCharsets.UTF_8));
        }

        private JsonGenerator generator() {
            if (generator == null)
                throw new UnsupportedOperationException("Generator required but not assigned. " +
                                                        "All accept() methods must be overridden when sub-classing FieldConsumer");
            return generator;
        }

    }

}
