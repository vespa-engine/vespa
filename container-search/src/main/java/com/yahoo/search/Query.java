// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search;

import ai.vespa.cloud.ZoneInfo;
import com.google.common.collect.ImmutableMap;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.language.process.Embedder;
import com.yahoo.prelude.query.SerializationContext;
import com.yahoo.prelude.query.textualrepresentation.TextualQueryRepresentation;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.federation.FederationSearcher;
import com.yahoo.search.query.Model;
import com.yahoo.search.query.Trace;
import com.yahoo.search.query.ParameterParser;
import com.yahoo.search.query.Presentation;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.Select;
import com.yahoo.search.query.SessionId;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.query.Sorting.AttributeSorter;
import com.yahoo.search.query.Sorting.FieldOrder;
import com.yahoo.search.query.Sorting.Order;
import com.yahoo.search.query.UniqueRequestId;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.search.query.profile.ModelObjectMap;
import com.yahoo.search.query.profile.QueryProfileProperties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.search.query.properties.DefaultProperties;
import com.yahoo.search.query.properties.PropertyMap;
import com.yahoo.search.query.properties.QueryProperties;
import com.yahoo.search.query.properties.QueryPropertyAliases;
import com.yahoo.search.query.properties.RankProfileInputProperties;
import com.yahoo.search.query.properties.RequestContextProperties;
import com.yahoo.search.query.ranking.RankFeatures;
import com.yahoo.search.yql.NullItemException;
import com.yahoo.search.yql.VespaSerializer;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.yolean.Exceptions;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A search query containing all the information required to produce a Result.
 * <p>
 * The Query contains:
 * <ul>
 * <li>the selection criterion received in the request - which may be a structured boolean tree of operators,
 * an annotated piece of natural language text received from a user, or a combination of both
 * <li>a set of field containing the additional general parameters of a query - number of hits,
 * ranking, presentation etc.
 * <li>a Map of properties, which can be of any object type
 * </ul>
 *
 * <p>
 * The properties have three sources
 * <ol>
 * <li>They may be set in some Searcher component already executed for this Query - the properties acts as
 * a blackboard for communicating arbitrary objects between Searcher components.
 * <li>Properties set in the search Request received - the properties acts as a way to parametrize Searcher
 * components from the Request.
 * <li>Properties defined in the selected {@link com.yahoo.search.query.profile.QueryProfile} - this provides
 * defaults for the parameters to Searcher components. Note that by using query profile types, the components may
 * define the set of parameters they support.
 * </ol>
 * When looked up, the properties are accessed in the priority order listed above.
 * <p>
 * The identity of a query is determined by its content.
 *
 * @author Arne Bergene Fossaa
 * @author bratseth
 */
public class Query extends com.yahoo.processing.Request implements Cloneable {

    // Note to developers: If you think you should add something here you are probably wrong
    // To add state to the query: Do properties.set("myNewState",new MyNewState()) instead.

    /** The type of the query */
    public enum Type {

        ALL(0,"all"),
        ANY(1,"any"),
        PHRASE(2,"phrase"),
        ADVANCED(3,"adv"),
        WEB(4,"web"),
        PROGRAMMATIC(5, "prog"),
        YQL(6, "yql"),
        SELECT(7, "select"),
        WEAKAND(8, "weakAnd"),
        TOKENIZE(9, "tokenize"),
        LINGUISTICS(10, "linguistics");

        private final int intValue;
        private final String stringValue;

        Type(int intValue, String stringValue) {
            this.intValue = intValue;
            this.stringValue = stringValue;
        }

        /** Converts a type argument value into a query type */
        public static Type getType(String typeString) {
            for (Type type : Type.values())
                if (type.stringValue.equals(typeString))
                    return type;
            throw new IllegalArgumentException("No query type '" + typeString + "'");
        }

        public int asInt() { return intValue; }

        public String toString() { return stringValue; }

    }

    /** The time this query was created */
    private long startTime;

    //--------------  Query properties treated as fields in Query ---------------

    /** The offset from the most relevant hits found from this query */
    private int offset = 0;

    /** The number of hits to return */
    private int hits = 10;

    // The timeout to be used when dumping rank features
    private static final long dumpTimeout = (6 * 60 * 1000); // 6 minutes
    private static final long defaultTimeout = 500;
    /** The timeout of the query, in milliseconds */
    private long timeout = defaultTimeout;

    /** Whether this query is forbidden to access cached information */
    private boolean noCache = false;

    /** Whether grouping should use a session cache */
    private boolean groupingSessionCache = true;

    //--------------  Generic property containers --------------------------------

    /** The synchronous view of the JDisc request causing this query */
    private final HttpRequest httpRequest;

    /** The context, or null if there is no context */
    private QueryContext context = null;

    /** Used for downstream session caches */
    private UniqueRequestId requestId = null;

    //--------------- Owned sub-objects containing query properties ----------------

    /** The ranking requested in this query */
    private Ranking ranking = new Ranking(this);

    /** The query and/or query program declaration */
    private Model model = new Model(this);

    /** How results of this query should be presented */
    private Presentation presentation = new Presentation(this);

    /** The selection of where-clause and grouping */
    private Select select = new Select(this);

    /** How this query should be traced */
    public Trace trace = new Trace(this);

    //---------------- Static property handling ------------------------------------

    public static final CompoundName OFFSET = CompoundName.from("offset");
    public static final CompoundName HITS = CompoundName.from("hits");

    public static final CompoundName QUERY_PROFILE = CompoundName.from("queryProfile");
    public static final CompoundName SEARCH_CHAIN = CompoundName.from("searchChain");

    public static final CompoundName NO_CACHE = CompoundName.from("noCache");
    public static final CompoundName GROUPING_SESSION_CACHE = CompoundName.from("groupingSessionCache");
    public static final CompoundName TIMEOUT = CompoundName.from("timeout");

    /** @deprecated use Trace.LEVEL */
    @Deprecated // TODO: Remove on Vespa 9
    public static final CompoundName TRACE_LEVEL = CompoundName.from("traceLevel");

    /** @deprecated use Trace.EXPLAIN_LEVEL */
    @Deprecated // TODO: Remove on Vespa 9
    public static final CompoundName EXPLAIN_LEVEL = CompoundName.from("explainLevel");

    private static final QueryProfileType argumentType;
    static {
        argumentType = new QueryProfileType("native");
        argumentType.setBuiltin(true);

        // Note: Order here matters as fields are set in this order, and rank feature conversion depends
        //       on other fields already being set (see RankProfileInputProperties)
        argumentType.addField(new FieldDescription(OFFSET.toString(), "integer", "offset start"));
        argumentType.addField(new FieldDescription(HITS.toString(), "integer", "hits count"));
        argumentType.addField(new FieldDescription(QUERY_PROFILE.toString(), "string"));
        argumentType.addField(new FieldDescription(SEARCH_CHAIN.toString(), "string"));
        argumentType.addField(new FieldDescription(NO_CACHE.toString(), "boolean", "nocache"));
        argumentType.addField(new FieldDescription(GROUPING_SESSION_CACHE.toString(), "boolean", "groupingSessionCache"));
        argumentType.addField(new FieldDescription(TIMEOUT.toString(), "string", "timeout"));
        argumentType.addField(new FieldDescription(FederationSearcher.SOURCENAME.toString(),"string"));
        argumentType.addField(new FieldDescription(FederationSearcher.PROVIDERNAME.toString(),"string"));
        argumentType.addField(new FieldDescription(Model.MODEL, new QueryProfileFieldType(Model.getArgumentType())));
        argumentType.addField(new FieldDescription(Select.SELECT, new QueryProfileFieldType(Select.getArgumentType())));
        argumentType.addField(new FieldDescription(Dispatcher.DISPATCH, new QueryProfileFieldType(Dispatcher.getArgumentType())));
        argumentType.addField(new FieldDescription(Ranking.RANKING, new QueryProfileFieldType(Ranking.getArgumentType())));
        argumentType.addField(new FieldDescription(Presentation.PRESENTATION, new QueryProfileFieldType(Presentation.getArgumentType())));
        argumentType.addField(new FieldDescription(Trace.TRACE, new QueryProfileFieldType(Trace.getArgumentType())));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    /** The aliases of query properties */
    private static final Map<String, CompoundName> propertyAliases;
    static {
        Map<String, CompoundName> propertyAliasesBuilder = new HashMap<>();
        addAliases(Query.getArgumentType(), CompoundName.empty, propertyAliasesBuilder);
        propertyAliases = ImmutableMap.copyOf(propertyAliasesBuilder);
    }
    private static void addAliases(QueryProfileType arguments, CompoundName prefix, Map<String, CompoundName> aliases) {
        for (FieldDescription field : arguments.fields().values()) {
            for (String alias : field.getAliases())
                aliases.put(alias, prefix.append(field.getName()));
            if (field.getType() instanceof QueryProfileFieldType) {
                var type = ((QueryProfileFieldType) field.getType()).getQueryProfileType();
                if (type != null)
                    addAliases(type, prefix.append(type.getComponentIdAsCompoundName().toString()), aliases);
            }
        }
    }

    private static CompoundName getPrefix(QueryProfileType type) {
        if (type.getId().getName().equals("native")) return CompoundName.empty; // The arguments of this directly
        return type.getComponentIdAsCompoundName();
    }

    public static void addNativeQueryProfileTypesTo(QueryProfileTypeRegistry registry) {
        // Add modifiable copies to allow query profile types in this to add to these
        registry.register(Query.getArgumentType().unfrozen());
        registry.register(Model.getArgumentType().unfrozen());
        registry.register(Select.getArgumentType().unfrozen());
        registry.register(Ranking.getArgumentType().unfrozen());
        registry.register(Presentation.getArgumentType().unfrozen());
        registry.register(Trace.getArgumentType().unfrozen());
        registry.register(DefaultProperties.argumentType.unfrozen());
    }

    /** Returns an unmodifiable list of all the native properties under a Query */
    public static final List<CompoundName> nativeProperties =
            List.copyOf(namesUnder(CompoundName.empty, Query.getArgumentType()));

    private static List<CompoundName> namesUnder(CompoundName prefix, QueryProfileType type) {
        if (type == null) return List.of(); // Names not known statically
        List<CompoundName> names = new ArrayList<>();
        for (Map.Entry<String, FieldDescription> field : type.fields().entrySet()) {
            var name = prefix.append(field.getKey());
            if (field.getValue().getType() instanceof QueryProfileFieldType) {
                names.addAll(namesUnder(name, ((QueryProfileFieldType) field.getValue().getType()).getQueryProfileType()));
            }
            else {
                names.add(name);
            }
        }
        return names;
    }

    //---------------- Construction ------------------------------------

    /**
     * Constructs an empty (null) query
     */
    public Query() {
        this("");
    }

    /**
     * Construct a query from a string formatted in the http style, e.g <code>?query=test&amp;offset=10&amp;hits=13</code>
     * The query must be uri encoded.
     */
    public Query(String query) {
        this(query, null);
    }

    /**
     * Creates a query from a request
     *
     * @param request the HTTP request from which this is created
     */
    public Query(HttpRequest request) {
        this(request, null);
    }

    /**
     * Construct a query from a string formatted in the http style, e.g <code>?query=test&amp;offset=10&amp;hits=13</code>
     * The query must be uri encoded.
     */
    public Query(String query, CompiledQueryProfile queryProfile) {
        this(HttpRequest.createTestRequest(query, com.yahoo.jdisc.http.HttpRequest.Method.GET), queryProfile);
    }

    /**
     * Creates a query from a request
     *
     * @param request the HTTP request from which this is created
     * @param queryProfile the query profile to use for this query, or null if none
     */
    public Query(HttpRequest request, CompiledQueryProfile queryProfile) {
        this(request, request.propertyMap(), queryProfile);
    }

    /**
     * Creates a query from a request
     *
     * @param request the HTTP request from which this is created
     * @param requestMap the property map of the query
     * @param queryProfile the query profile to use for this query, or null if none
     */
    public Query(HttpRequest request, Map<String, String> requestMap, CompiledQueryProfile queryProfile) {
        super(new QueryPropertyAliases(propertyAliases));
        this.httpRequest = request;
        init(requestMap, queryProfile, Embedder.throwsOnUse.asMap(), ZoneInfo.defaultInfo(), SchemaInfo.empty());
    }

    // TODO: Deprecate most constructors above here

    private Query(Builder builder) {
        this(builder.getRequest(),
             builder.getRequestMap(),
             builder.getQueryProfile(),
             builder.getEmbedders(),
             builder.getZoneInfo(),
             builder.getSchemaInfo());
    }

    private Query(HttpRequest request,
                  Map<String, String> requestMap,
                  CompiledQueryProfile queryProfile,
                  Map<String, Embedder> embedders,
                  ZoneInfo zoneInfo,
                  SchemaInfo schemaInfo) {
        super(new QueryPropertyAliases(propertyAliases));
        this.httpRequest = request;
        init(requestMap, queryProfile, embedders, zoneInfo, schemaInfo);
    }

    private void init(Map<String, String> requestMap,
                      CompiledQueryProfile queryProfile,
                      Map<String, Embedder> embedders,
                      ZoneInfo zoneInfo,
                      SchemaInfo schemaInfo) {
        startTime = httpRequest.creationTime(TimeUnit.MILLISECONDS);
        if (queryProfile != null) {
            // Move all request parameters to the query profile
            Properties queryProfileProperties = new QueryProfileProperties(queryProfile, embedders, zoneInfo);
            properties().chain(queryProfileProperties);
            setPropertiesFromRequestMap(requestMap, properties(), true);

            // Create the full chain
            properties().chain(new RankProfileInputProperties(schemaInfo, this, embedders))
                        .chain(new QueryProperties(this, queryProfile.getRegistry(), embedders))
                        .chain(new ModelObjectMap())
                        .chain(new RequestContextProperties(requestMap))
                        .chain(queryProfileProperties)
                        .chain(new DefaultProperties());

            // Pass values from the query profile which maps to a field in the Query object model
            // through the property chain to cause those values to be set in the Query object model with
            // the right types according to query profiles
            setFieldsFrom(queryProfileProperties, requestMap);

            // We need special handling for "select" because it can be both the prefix of the nested JSON select
            // parameters, and a plain select expression. The latter will be disallowed by query profile types
            // since they contain the former.
            Object select = requestMap.get(Select.SELECT);
            if (select == null)
                select = queryProfile.get(Select.SELECT, requestMap);
            if (select != null)
                properties().set(Select.SELECT, select.toString());
        }
        else { // bypass these complications if there is no query profile to get values from and validate against
            properties().
                    chain(new RankProfileInputProperties(schemaInfo, this, embedders)).
                    chain(new QueryProperties(this, CompiledQueryProfileRegistry.empty, embedders)).
                    chain(new PropertyMap()).
                    chain(new DefaultProperties());
            setPropertiesFromRequestMap(requestMap, properties(), false);
        }

        properties().setParentQuery(this);
        trace.traceProperties();
    }

    public Query(Query query) {
        this(query, query.getStartTime());
    }

    private Query(Query query, long startTime) {
        super(query.properties().clone());
        this.startTime = startTime;
        this.httpRequest = query.httpRequest;
        query.copyPropertiesTo(this);
    }

    /**
     * Creates a new query from another query, but with time sensitive fields reset.
     */
    public static Query createNewQuery(Query query) {
        return new Query(query, System.currentTimeMillis());
    }

    /**
     * Calls properties().set on each value in the given properties which is declared in this query or
     * one of its dependent objects. This will ensure the appropriate setters are called on this and all
     * dependent objects for the appropriate subset of the given property values
     */
    private void setFieldsFrom(Properties properties, Map<String, String> context) {
        setFrom("", properties, Query.getArgumentType(), context);
    }

    private static String append(String a, String b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + "." + b;
    }

    /**
     * For each field in the given query profile type, take the corresponding value from sourceProperties and
     * (if any) set it to properties(), recursively.
     */
    private void setFrom(String prefix, Properties sourceProperties, QueryProfileType arguments,
                         Map<String, String> context) {
        prefix = append(prefix, getPrefix(arguments).toString());
        for (FieldDescription field : arguments.fields().values()) {
            if (field.getType() == FieldType.genericQueryProfileType) {
                setGenericMapFrom(prefix, sourceProperties, field, context);
            }
            else if (field.getType() instanceof QueryProfileFieldType) { // Nested arguments
                setFrom(prefix, sourceProperties, ((QueryProfileFieldType)field.getType()).getQueryProfileType(), context);
            }
            else {
                setFieldFrom(prefix, sourceProperties, field, context);
            }
        }
    }

    private void setGenericMapFrom(String prefix, Properties sourceProperties, FieldDescription field,
                                   Map<String, String> context) {
        var fullName = CompoundName.from(append(prefix, field.getCompoundName().toString()));
        setAllValuesFrom(sourceProperties, fullName, fullName, true, context);
        if (fullName.size() == 2 && fullName.first().equals("ranking")) {
            if (fullName.get(1).equals("features")) {
                setAllValuesFrom(sourceProperties, CompoundName.from("input"), fullName, false, context);
                setAllValuesFrom(sourceProperties, CompoundName.from("rankfeature"), fullName, false, context);
            }
            else if (fullName.get(1).equals("properties")) {
                setAllValuesFrom(sourceProperties, CompoundName.from("rankproperty"), fullName, false, context);
            }
        }
    }

    private void setAllValuesFrom(Properties sourceProperties, CompoundName sourceName, CompoundName fullName, boolean includeRoot,
                                  Map<String, String> context) {
        for (Map.Entry<String, Object> entry : sourceProperties.listProperties(sourceName, context).entrySet()) {
            if (! includeRoot && entry.getKey().isEmpty()) continue;
            properties().set(fullName.append(entry.getKey()), entry.getValue(), context);
        }
    }

    private void setFieldFrom(String prefix, Properties sourceProperties, FieldDescription field,
                              Map<String, String> context) {
        CompoundName fullName = prefix.isEmpty()
                                ? field.getCompoundName()
                                : CompoundName.from(append(prefix, field.getCompoundName().toString()));
        if (setFieldFrom(sourceProperties, fullName, fullName, context)) return;
        for (String alias : field.getAliases()) {
            if (setFieldFrom(sourceProperties, new CompoundName(alias), fullName, context)) return;
        }
    }

    private boolean setFieldFrom(Properties sourceProperties, CompoundName sourceName, CompoundName name,
                                 Map<String, String> context) {
        Object value = sourceProperties.get(sourceName, context);
        if (value == null) return false;
        properties().set(name, value, context);
        return true;
    }

    /** Calls properties#set on all entries in requestMap */
    private void setPropertiesFromRequestMap(Map<String, String> requestMap, Properties properties, boolean ignoreSelect) {
        var entrySet = requestMap.entrySet();
        for (var entry : entrySet) {
            if (ignoreSelect && entry.getKey().equals(Select.SELECT)) continue;
            if (RankFeatures.isFeatureName(entry.getKey())) continue; // Set these last
            properties.set(CompoundName.from(entry.getKey()), entry.getValue(), requestMap);
        }
        for (var entry : entrySet) {
            if ( ! RankFeatures.isFeatureName(entry.getKey())) continue;
            properties.set(CompoundName.from(entry.getKey()), entry.getValue(), requestMap);
        }
    }

    /** Returns the properties of this query. The properties are modifiable */
    @Override
    public Properties properties() { return (Properties)super.properties(); }

    /**
     * Validates this query
     *
     * @return the reason if it is invalid, null if it is valid
     */
    public String validate() {
        // Validate the query profile
        QueryProfileProperties queryProfileProperties = properties().getInstance(QueryProfileProperties.class);
        if (queryProfileProperties == null) return null; // Valid
        StringBuilder missingName = new StringBuilder();
        if ( ! queryProfileProperties.isComplete(missingName, httpRequest.propertyMap()))
            return "Incomplete query: Parameter '" + missingName + "' is mandatory in " +
                   queryProfileProperties.getQueryProfile() + " but is not set";
        else
            return null; // is valid
    }

    /** Returns the time (in milliseconds since epoch) when this query was started */
    public long getStartTime() { return startTime; }

    /** Returns the time (in milliseconds) since the query was started/created */
    public long getDurationTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Get the appropriate timeout for the query.
     *
     * @return timeout in milliseconds
     */
    public long getTimeLeft() {
        return getTimeout() - getDurationTime();
    }

    /**
     * Returns the number of milliseconds to wait for a response from a search backend
     * before timing it out. Default is 500.
     * <p>
     * Note: If Ranking.RANKFEATURES is turned on, this is hardcoded to 6 minutes.
     *
     * @return timeout in milliseconds.
     */
    public long getTimeout() {
        return properties().getBoolean(Ranking.RANKFEATURES, false) ? dumpTimeout : timeout;
    }

    /**
     * Sets the number of milliseconds to wait for a response from a search backend
     * before time out. Default is 500 ms.
     */
    public void setTimeout(long timeout) {
        if (timeout > 1000000000 || timeout < 0)
            throw new IllegalArgumentException("'timeout' must be positive and smaller than 1000000000 ms but was " + timeout);
        this.timeout = timeout;
    }

    /**
     * Sets timeout from a string which will be parsed as a
     */
    public void setTimeout(String timeoutString) {
        setTimeout(ParameterParser.asMilliSeconds(timeoutString, timeout));
    }

    /**
     * Resets the start time of the query. This will ensure that the query will run
     * for the same amount of time as a newly created query.
     */
    public void resetTimeout() { this.startTime = System.currentTimeMillis(); }

    /** @deprecated use getTrace().setLevel(level) */
    @Deprecated // TODO: Remove on Vespa 9
    public void setTraceLevel(int traceLevel) { trace.setLevel(traceLevel); }

    /** @deprecated use getTrace().setExplainLevel(level) */
    @Deprecated // TODO: Remove on Vespa 9
    public void setExplainLevel(int explainLevel) { trace.setExplainLevel(explainLevel); }

    /** @deprecated use getTrace().setLevel(level) */
    @Deprecated // TODO: Remove on Vespa 9
    public int getTraceLevel() { return trace.getLevel(); }

    /** @deprecated use getTrace().getExplainLevel(level) */
    @Deprecated // TODO: Remove on Vespa 9
    public int getExplainLevel() { return getTrace().getExplainLevel(); }

    /**
     * Returns the context level of this query, 0 means no tracing
     * Higher numbers means increasingly more tracing
     *
     * @deprecated use getTrace().isTraceable(level)
     */
    @Deprecated // TODO: Remove on Vespa 9
    public final boolean isTraceable(int level) { return trace.isTraceable(level); }

    /** Returns whether this query should never be served from a cache. Default is false */
    public boolean getNoCache() { return noCache; }

    /** Sets whether this query should never be server from a cache. Default is false */
    public void setNoCache(boolean noCache) { this.noCache = noCache; }

    /** Returns whether this query should use the grouping session cache. Default is false */
    public boolean getGroupingSessionCache() { return groupingSessionCache; }

    /** Sets whether this query should use the grouping session cache. Default is false */
    public void setGroupingSessionCache(boolean groupingSessionCache) { this.groupingSessionCache = groupingSessionCache; }

    /**
     * Returns the offset from the most relevant hits requested by the submitter
     * of this query.
     * Default is 0 - to return the most relevant hits
     */
    public int getOffset() { return offset; }

    /**
     * Returns the number of hits requested by the submitter of this query.
     * The default is 10.
     */
    public int getHits() { return hits; }

    /**
     * Sets the number of hits requested. If hits is less than 0, an
     * IllegalArgumentException is thrown. Default number of hits is 10.
     */
    public void setHits(int hits) {
        if (hits < 0)
            throw new IllegalArgumentException("'hits' must be a positive number, not " + hits);
        this.hits = hits;
    }

    /**
     * Set the hit offset. Can not be less than 0. Default is 0.
     */
    public void setOffset(int offset) {
        if (offset < 0)
            throw new IllegalArgumentException("'offset' must be a positive number, not " + offset);
        this.offset = offset;
    }

    /** Convenience method to set both the offset and the number of hits to return */
    public void setWindow(int offset, int hits) {
        setOffset(offset);
        setHits(hits);
    }

    /** Returns a string describing this query */
    @Override
    public String toString() {
        String queryTree;
        // getQueryTree isn't exception safe
        try {
            queryTree = model.getQueryTree().toString();
        } catch (Exception | StackOverflowError e) {
            queryTree = "[Could not parse user input: " + model.getQueryString() + "]";
        }
        return "query '" + queryTree + "'";
    }

    /** Returns a string describing this query in more detail */
    public String toDetailString() {
        return "query=[" + new TextualQueryRepresentation(getModel().getQueryTree().getRoot()) + "]" +
               " offset=" + getOffset() + " hits=" + getHits() +
               " sources=" + getModel().getSources() +
               " restrict= " + getModel().getRestrict() +
               " rank profile=" + getRanking().getProfile();
    }

    /**
     * Encodes this query tree into the given buffer
     *
     * @param buffer the buffer to encode the query to
     * @return the number of encoded query tree items
     */
    public int encode(ByteBuffer buffer, SerializationContext context) {
        return model.getQueryTree().encode(buffer, context);
    }

    /** Calls getTrace().trace(message, traceLevel). */
    public void trace(String message, int traceLevel) {
        trace.trace(message, traceLevel);
    }

    /** Calls getTrace().trace(message, traceLevel). */
    public void trace(Object message, int traceLevel) {
        trace.trace(message, traceLevel);
    }

    /** Calls getTrace().trace(message, includeQuery, traceLevel). */
    public void trace(String message, boolean includeQuery, int traceLevel) {
        trace.trace(message, includeQuery, traceLevel);
    }

    /** Calls getTrace().trace(message, traceLevel, messages). */
    public void trace(boolean includeQuery, int traceLevel, Object... messages) {
        trace.trace(includeQuery, traceLevel, messages);
    }

    /**
     * Set the context information for another query to be part of this query's
     * context information. This is to be used if creating fresh query objects as
     * part of a plug-in's execution. The query should be attached before it is
     * used, in case an exception causes premature termination. This is enforced
     * by an IllegalStateException. In other words, intended use is create the
     * new query, and attach the context to the invoking query as soon as the new
     * query is properly initialized.
     * <p>
     * This method will always set the argument query's context level to the context
     * level of this query.
     *
     * @param query the query which should be traced as a part of this query
     * @throws IllegalStateException if the query given as argument already has context information
     */
    public void attachContext(Query query) throws IllegalStateException {
        query.getTrace().setLevel(getTrace().getLevel());
        query.getTrace().setExplainLevel(getTrace().getExplainLevel());
        query.getTrace().setProfileDepth(getTrace().getProfileDepth());
        if (context == null) return;
        if (query.getContext(false) != null) {
            // If we added the other query's context info as a subnode in this
            // query's context tree, we would have to check for loops in the
            // context graph. If we simply created a new node without checking,
            // we might silently overwrite useful information.
            throw new IllegalStateException("Query to attach already has context information stored");
        }
        query.context = context;
    }

    /**
     * Serialize this query as YQL+. This method will never throw exceptions,
     * but instead return a human-readable error message if a problem occurred while
     * serializing the query. Hits and offset information will be included if
     * different from default, while linguistics metadata are not added.
     *
     * @return a valid YQL+ query string or a human-readable error message
     * @see Query#yqlRepresentation(boolean)
     */
    public String yqlRepresentation() {
        try {
            return yqlRepresentation(true);
        } catch (NullItemException e) {
            return "Query currently a placeholder, parsing is deferred";
        } catch (IllegalArgumentException e) {
            return "Invalid query: " + Exceptions.toMessageString(e);
        } catch (RuntimeException e) {
            return "Unexpected error parsing or serializing query: " + Exceptions.toMessageString(e);
        }
    }

    private void commaSeparated(StringBuilder yql, Set<String> fields) {
        int initLen = yql.length();
        for (String field : fields) {
            if (yql.length() > initLen) {
                yql.append(", ");
            }
            yql.append(field);
        }
    }

    /**
     * Serialize this query as YQL+. This will create a string representation
     * which should always be legal YQL+. If a problem occurs, a
     * RuntimeException is thrown.
     *
     * @param includeHitsAndOffset whether to include hits and offset parameters converted to a offset/limit slice
     * @return a valid YQL+ query string
     * @throws RuntimeException if there is a problem serializing the query tree
     */
    public String yqlRepresentation(boolean includeHitsAndOffset) {
        Set<String> sources = getModel().getSources();
        Set<String> fields = getPresentation().getSummaryFields();
        StringBuilder yql = new StringBuilder("select ");
        if (fields.isEmpty()) {
            yql.append('*');
        } else {
            commaSeparated(yql, fields);
        }
        yql.append(" from ");
        if (sources.isEmpty()) {
            yql.append("sources *");
        } else {
            if (sources.size() > 1) {
                yql.append("sources ");
            }
            commaSeparated(yql, sources);
        }
        yql.append(" where ");
        String insert = serializeSortingAndLimits(includeHitsAndOffset);
        yql.append(VespaSerializer.serialize(this, insert));
        return yql.toString();
    }

    private String serializeSortingAndLimits(boolean includeHitsAndOffset) {
        StringBuilder insert = new StringBuilder();
        if (getRanking().getSorting() != null && !getRanking().getSorting().fieldOrders().isEmpty()) {
            serializeSorting(insert);
        }
        if (includeHitsAndOffset) {
            if (getOffset() != 0) {
                insert.append(" limit ").append(getHits() + getOffset())
                    .append(" offset ").append(getOffset());
            } else if (getHits() != 10) {
                insert.append(" limit ").append(getHits());
            }
        }
        if (getTimeout() != defaultTimeout) {
            insert.append(" timeout ").append(getTimeout());
        }
        return insert.toString();
    }

    private void serializeSorting(StringBuilder yql) {
        yql.append(" order by ");
        int initLen = yql.length();
        for (FieldOrder f : getRanking().getSorting().fieldOrders()) {
            if (yql.length() > initLen) {
                yql.append(", ");
            }
            Class<? extends AttributeSorter> sorterType = f.getSorter().getClass();
            if (sorterType == Sorting.RawSorter.class) {
                yql.append("[{\"")
                   .append(YqlParser.SORTING_FUNCTION)
                   .append("\": \"")
                   .append(Sorting.RAW)
                   .append("\"}]");
            } else if (sorterType == Sorting.LowerCaseSorter.class) {
                yql.append("[{\"")
                   .append(YqlParser.SORTING_FUNCTION)
                   .append("\": \"")
                   .append(Sorting.LOWERCASE)
                   .append("\"}]");
            } else if (sorterType == Sorting.UcaSorter.class) {
                Sorting.UcaSorter uca = (Sorting.UcaSorter) f.getSorter();
                String ucaLocale = uca.getLocale();
                Sorting.UcaSorter.Strength ucaStrength = uca.getStrength();
                yql.append("[{\"")
                   .append(YqlParser.SORTING_FUNCTION)
                   .append("\": \"")
                   .append(Sorting.UCA)
                   .append("\"");
                if (ucaLocale != null) {
                    yql.append(", \"")
                       .append(YqlParser.SORTING_LOCALE)
                       .append("\": \"")
                       .append(ucaLocale)
                       .append('"');
                }
                if (ucaStrength != Sorting.UcaSorter.Strength.UNDEFINED) {
                    yql.append(", \"")
                       .append(YqlParser.SORTING_STRENGTH)
                       .append("\": \"")
                       .append(ucaStrength.name())
                       .append('"');
                }
                yql.append("}]");
            }
            yql.append(maybeQuote(f.getFieldName()));
            if (f.getSortOrder() == Order.DESCENDING) {
                yql.append(" desc");
            }
        }
    }

    private static String maybeQuote(String sortField) {
        if (sortField.startsWith("[")) {
            return '"' + sortField + '"';
        }
        return sortField;
    }

    /** Returns the context of this query, possibly creating it if missing. Returns the context, or null */
    public QueryContext getContext(boolean create) {
        if (context == null && create)
            context = new QueryContext(getTraceLevel(),this);
        return context;
    }

    /** Returns a hash of this query based on (some of) its content. */
    @Override
    public int hashCode() {
        return Objects.hash(ranking, presentation, model, offset, hits);
    }

    /** Returns whether the given query is equal to this */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;

        if ( ! (other instanceof Query q)) return false;

        if (getOffset() != q.getOffset()) return false;
        if (getHits() != q.getHits()) return false;
        if ( ! getPresentation().equals(q.getPresentation())) return false;
        if ( ! getRanking().equals(q.getRanking())) return false;
        if ( ! getModel().equals(q.getModel())) return false;

        // TODO: Compare property settings

        return true;
    }

    /** Returns a clone of this query */
    @Override
    public Query clone() {
        Query clone = (Query) super.clone();
        copyPropertiesTo(clone);
        return clone;
    }

    private void copyPropertiesTo(Query clone) {
        clone.model = model.cloneFor(clone);
        clone.select = select.cloneFor(clone);
        clone.ranking = ranking.cloneFor(clone);
        clone.trace = trace.cloneFor(clone);
        clone.presentation = (Presentation) presentation.clone();
        clone.context = getContext(true).cloneFor(clone);

        // Correct the Query instance in properties
        clone.properties().setParentQuery(clone);
        assert (clone.properties().getParentQuery() == clone);

        clone.setTimeout(getTimeout());
        clone.setHits(getHits());
        clone.setOffset(getOffset());
        clone.setNoCache(getNoCache());
        clone.setGroupingSessionCache(getGroupingSessionCache());
        clone.requestId = null; // Each clone should have their own requestId.
    }

    /** Returns the presentation to be used for this query, never null */
    public Presentation getPresentation() { return presentation; }

    /** Returns the select to be used for this query, never null */
    public Select getSelect() { return select; }

    /** Sets the select (grouping) parameter from a string. */
    public void setSelect(String groupingString) { select.setGroupingExpressionString(groupingString); }

    /** Returns the ranking to be used for this query, never null */
    public Ranking getRanking() { return ranking; }

    /** Returns the query representation model to be used for this query, never null */
    public Model getModel() { return model; }

    /** Returns the trace settings and facade API. */
    public Trace getTrace() { return trace; }

    /**
     * Return the HTTP request which caused this query. This will never be null
     * when running with queries from the network.
     */
    public HttpRequest getHttpRequest() { return httpRequest; }

    public URI getUri() { return httpRequest != null ? httpRequest.getUri() : null; }

    /** Returns the session id of this query, or null if none is assigned */
    public SessionId getSessionId() {
        if (requestId == null) return null;
        return new SessionId(requestId, getRanking().getProfile());
    }

    /** Returns the session id of this query, and creates and assigns it if not already present */
    public SessionId getSessionId(String serverId) {
        if (requestId == null)
            requestId = UniqueRequestId.next(serverId);
        return new SessionId(requestId, getRanking().getProfile());
    }

    /**
     * Prepares this for binary serialization.
     *
     * This must be invoked after all changes have been made to this query before it is passed
     * on to a receiving backend. Calling it is somewhat expensive, so it should only happen once.
     * If a prepared query is cloned, it stays prepared.
     */
    public void prepare() {
        getModel().prepare(getRanking());
        getPresentation().prepare();
        getRanking().prepare();
    }

    public static class Builder {

        private HttpRequest request = null;
        private Map<String, String> requestMap = null;
        private CompiledQueryProfile queryProfile = null;
        private Map<String, Embedder> embedders = Embedder.throwsOnUse.asMap();
        private ZoneInfo zoneInfo = ZoneInfo.defaultInfo();
        private SchemaInfo schemaInfo = SchemaInfo.empty();

        public Builder setRequest(String query) {
            request = HttpRequest.createTestRequest(query, com.yahoo.jdisc.http.HttpRequest.Method.GET);
            return this;
        }

        public Builder setRequest(HttpRequest request) {
            this.request = request;
            return this;
        }

        public HttpRequest getRequest() {
            if (request == null)
                return HttpRequest.createTestRequest("", com.yahoo.jdisc.http.HttpRequest.Method.GET);
            return request;
        }

        /** Sets the request mao to use explicitly. If not set, the request map will be getRequest().propertyMap() */
        public Builder setRequestMap(Map<String, String> requestMap) {
            this.requestMap = requestMap;
            return this;
        }

        public Map<String, String> getRequestMap() {
            if (requestMap == null)
                return getRequest().propertyMap();
            return requestMap;
        }

        public Builder setQueryProfile(CompiledQueryProfile queryProfile) {
            this.queryProfile = queryProfile;
            return this;
        }

        /** Returns the query profile of this query, or null if none. */
        public CompiledQueryProfile getQueryProfile() { return queryProfile; }

        public Builder setEmbedder(Embedder embedder) {
            return setEmbedders(Map.of(Embedder.defaultEmbedderId, embedder));
        }

        public Builder setEmbedders(Map<String, Embedder> embedders) {
            this.embedders = embedders;
            return this;
        }

        public Embedder getEmbedder() {
            if (embedders.size() != 1) {
                throw new IllegalArgumentException("Attempt to get single embedder but multiple exists.");
            }
            return embedders.entrySet().stream().findFirst().get().getValue();
        }

        public Map<String, Embedder> getEmbedders() { return embedders; }

        public Builder setZoneInfo(ZoneInfo zoneInfo) {
            this.zoneInfo = zoneInfo;
            return this;
        }

        public ZoneInfo getZoneInfo() { return zoneInfo; }

        public Builder setSchemaInfo(SchemaInfo schemaInfo) {
            this.schemaInfo = schemaInfo;
            return this;
        }

        public SchemaInfo getSchemaInfo() { return schemaInfo; }

        /** Creates a new query from this builder. No properties are required to before calling this. */
        public Query build() { return new Query(this); }

    }

}
