// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search;

import com.google.common.collect.ImmutableMap;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.Version;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.fs4.MapEncoder;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.query.Highlight;
import com.yahoo.prelude.query.QueryException;
import com.yahoo.prelude.query.textualrepresentation.TextualQueryRepresentation;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.federation.FederationSearcher;
import com.yahoo.search.query.Model;
import com.yahoo.search.query.ParameterParser;
import com.yahoo.search.query.Presentation;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.Ranking;
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
import com.yahoo.search.query.properties.RequestContextProperties;
import com.yahoo.search.yql.NullItemException;
import com.yahoo.search.yql.VespaSerializer;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.yolean.Exceptions;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

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
 * The properties has three sources
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
        YQL(6, "yql");

        private final int intValue;
        private final String stringValue;

        Type(int intValue,String stringValue) {
            this.intValue = intValue;
            this.stringValue = stringValue;
        }

        /** Converts a type argument value into a query type */
        public static Type getType(String typeString) {
            for (Type type:Type.values())
                if(type.stringValue.equals(typeString))
                    return type;
            return ALL;
        }

        public int asInt() { return intValue; }

        public String toString() { return stringValue; }

    }

    //--------------  Query properties treated as fields in Query ---------------

    /** The offset from the most relevant hits found from this query */
    private int offset = 0;

    /** The number of hits to return */
    private int hits = 10;

    /** The query context level, 0 means no tracing */
    private int traceLevel = 0;

    // The timeout to be used when dumping rank features
    private static final long dumpTimeout = (6 * 60 * 1000); // 6 minutes
    private static final long defaultTimeout = 5000;
    /** The timeout of the query, in milliseconds */
    private long timeout = defaultTimeout;


    /** Whether this query is forbidden to access cached information */
    private boolean noCache=false;

    /** Whether or not grouping should use a session cache */
    private boolean groupingSessionCache=false;

    //--------------  Generic property containers --------------------------------

    /**
     * The synchronous view of the JDisc request causing this query.
     *
     * @since 5.1
     */
    private final HttpRequest httpRequest;

    /** The context, or null if there is no context */
    private QueryContext context = null;

    /** Used for downstream session caches */
    private UniqueRequestId requestId = null;

    //--------------- Owned sub-objects containing query properties ----------------

    /** The ranking requested in this query */
    private Ranking ranking = new Ranking(this);

    /** The query query and/or query program declaration */
    private Model model = new Model(this);

    /** How results of this query should be presented */
    private Presentation presentation = new Presentation(this);

    //---------------- Tracing ----------------------------------------------------

    private static Logger log = Logger.getLogger(Query.class.getName());

    /** The time this query was created */
    private long startTime;

    //---------------- Static property handling ------------------------------------

    public static final CompoundName OFFSET = new CompoundName("offset");
    public static final CompoundName HITS = new CompoundName("hits");

    public static final CompoundName SEARCH_CHAIN = new CompoundName("searchChain");
    public static final CompoundName TRACE_LEVEL = new CompoundName("traceLevel");
    public static final CompoundName NO_CACHE = new CompoundName("noCache");
    public static final CompoundName GROUPING_SESSION_CACHE = new CompoundName("groupingSessionCache");
    public static final CompoundName TIMEOUT = new CompoundName("timeout");

    private static QueryProfileType argumentType;
    static {
        argumentType=new QueryProfileType("native");
        argumentType.setBuiltin(true);

        argumentType.addField(new FieldDescription(OFFSET.toString(), "integer", "offset start"));
        argumentType.addField(new FieldDescription(HITS.toString(), "integer", "hits count"));
        // TODO: Should this be added to com.yahoo.search.query.properties.QueryProperties? If not, why not?
        argumentType.addField(new FieldDescription(SEARCH_CHAIN.toString(), "string"));
        argumentType.addField(new FieldDescription(TRACE_LEVEL.toString(), "integer", "tracelevel"));
        argumentType.addField(new FieldDescription(NO_CACHE.toString(), "boolean", "nocache"));
        argumentType.addField(new FieldDescription(GROUPING_SESSION_CACHE.toString(), "boolean", "groupingSessionCache"));
        argumentType.addField(new FieldDescription(TIMEOUT.toString(), "string", "timeout"));
        argumentType.addField(new FieldDescription(FederationSearcher.SOURCENAME.toString(),"string"));
        argumentType.addField(new FieldDescription(FederationSearcher.PROVIDERNAME.toString(),"string"));
        argumentType.addField(new FieldDescription(Presentation.PRESENTATION,new QueryProfileFieldType(Presentation.getArgumentType())));
        argumentType.addField(new FieldDescription(Ranking.RANKING,new QueryProfileFieldType(Ranking.getArgumentType())));
        argumentType.addField(new FieldDescription(Model.MODEL,new QueryProfileFieldType(Model.getArgumentType())));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    /** The aliases of query properties, these are always the same */
    // Note: Don't make static for now as GSM calls this through reflection
    private static Map<String,CompoundName> propertyAliases;
    static {
        Map<String,CompoundName> propertyAliasesBuilder = new HashMap<>();
        addAliases(Query.getArgumentType(), propertyAliasesBuilder);
        addAliases(Ranking.getArgumentType(), propertyAliasesBuilder);
        addAliases(Model.getArgumentType(), propertyAliasesBuilder);
        addAliases(Presentation.getArgumentType(), propertyAliasesBuilder);
        propertyAliases = ImmutableMap.copyOf(propertyAliasesBuilder);
    }
    private static void addAliases(QueryProfileType arguments,Map<String,CompoundName> aliases) {
        String prefix=getPrefix(arguments);
        for (FieldDescription field : arguments.fields().values()) {
            for (String alias : field.getAliases())
                aliases.put(alias,new CompoundName(prefix+field.getName()));
        }
    }
    private static String getPrefix(QueryProfileType type) {
        if (type.getId().getName().equals("native")) return ""; // The arguments of this directly
        return type.getId().getName() + ".";
    }

    public static void addNativeQueryProfileTypesTo(QueryProfileTypeRegistry registry) {
        // Add modifiable copies to allow query profile types in this to add to these
        registry.register(Query.getArgumentType().unfrozen());
        registry.register(Ranking.getArgumentType().unfrozen());
        registry.register(Model.getArgumentType().unfrozen());
        registry.register(Presentation.getArgumentType().unfrozen());
        registry.register(DefaultProperties.argumentType.unfrozen());
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
     * @param queryProfile the query profile to use for this query, or null if none.
     */
    public Query(HttpRequest request, CompiledQueryProfile queryProfile) {
        super(new QueryPropertyAliases(propertyAliases));
        this.httpRequest = request;
        init(request.propertyMap(), queryProfile);
    }

    /**
     * Creates a query from a request
     *
     * @param request the HTTP request from which this is created
     */
    public Query(HttpRequest request) {
        this(request, null);
    }

    private void init(Map<String, String> requestMap, CompiledQueryProfile queryProfile) {
        startTime = System.currentTimeMillis();
        if (queryProfile != null) {
            // Move all request parameters to the query profile just to validate that the parameter settings are legal
            Properties queryProfileProperties=new QueryProfileProperties(queryProfile);
            properties().chain(queryProfileProperties);
            // TODO: Just checking legality rather than actually setting would be faster
            setPropertiesFromRequestMap(requestMap, properties()); // Adds errors to the query for illegal set attempts

            // Create the full chain
            properties().chain(new QueryProperties(this, queryProfile.getRegistry())).
                         chain(new ModelObjectMap()).
                         chain(new RequestContextProperties(requestMap)).
                         chain(queryProfileProperties).
                         chain(new DefaultProperties());

            // Pass the values from the query profile which maps through a field in the Query object model
            // through the property chain to cause those values to be set in the Query object model
            setFieldsFrom(queryProfileProperties, requestMap);
        }
        else { // bypass these complications if there is no query profile to get values from and validate against
            properties().
                    chain(new QueryProperties(this, CompiledQueryProfileRegistry.empty)).
                    chain(new PropertyMap()).
                    chain(new DefaultProperties());
            setPropertiesFromRequestMap(requestMap, properties());
        }

        properties().setParentQuery(this);
        traceProperties();
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
     * Creates a new query from another query, but with time sensitive
     * fields reset.
     *
     * @return new query
     */
    public static Query createNewQuery(Query query) {
        return new Query(query, System.currentTimeMillis());
    }

    /**
     * Calls properties().set on each value in the given properties which is declared in this query or
     * one of its dependent objects. This will ensure the appropriate setters are called on this and all
     * dependent objects for the appropriate subset of the given property values
     */
    private void setFieldsFrom(Properties properties, Map<String,String> context) {
        setFrom(properties,Query.getArgumentType(), context);
        setFrom(properties,Model.getArgumentType(), context);
        setFrom(properties,Presentation.getArgumentType(), context);
        setFrom(properties,Ranking.getArgumentType(), context);
    }

    /**
     * For each field in the given query profile type, take the corresponding value from originalProperties
     * (if any) set it to properties().
     */
    private void setFrom(Properties originalProperties,QueryProfileType arguments,Map<String,String> context) {
        String prefix=getPrefix(arguments);
        for (FieldDescription field : arguments.fields().values()) {
            String fullName=prefix + field.getName();
            if (field.getType() == FieldType.genericQueryProfileType) {
                for (Map.Entry<String, Object> entry : originalProperties.listProperties(fullName,context).entrySet()) {
                    try {
                        properties().set(fullName + "." + entry.getKey(), entry.getValue(), context);
                    } catch (IllegalArgumentException e) {
                        throw new QueryException("Invalid request parameter", e);
                    }
                }
            } else {
                Object value=originalProperties.get(fullName,context);
                if (value!=null) {
                    try {
                        properties().set(fullName,value,context);
                    } catch (IllegalArgumentException e) {
                        throw new QueryException("Invalid request parameter", e);
                    }
                }
            }
        }
    }

    /** Calls properties.set on all entries in requestMap */
    private void setPropertiesFromRequestMap(Map<String, String> requestMap, Properties properties) {
        for (Map.Entry<String, String> entry : requestMap.entrySet()) {
            try {
                if (entry.getKey().equals("queryProfile")) continue;
                properties.set(entry.getKey(), entry.getValue(), requestMap);
            }
            catch (IllegalArgumentException e) {
                throw new QueryException("Invalid request parameter", e);
            }
        }
    }

    /** Returns the properties of this query. The properties are modifiable */
    @Override
    public Properties properties() { return (Properties)super.properties(); }

    /**
     * Traces how properties was resolved and from where. Done after the fact to avoid special handling
     * of tracelevel, which is the property deciding whether this needs to be done
     */
    private void traceProperties() {
        if (traceLevel == 0) return;
        CompiledQueryProfile profile=null;
        QueryProfileProperties profileProperties=properties().getInstance(QueryProfileProperties.class);
        if (profileProperties!=null)
            profile=profileProperties.getQueryProfile();

        if (profile==null)
            trace("No query profile is used", false, 1);
        else
            trace("Using " + profile.toString(), false, 1);
        if (traceLevel < 4) return;

        StringBuilder b = new StringBuilder("Resolved properties:\n");
        Set<String> mentioned = new HashSet<>();
        for (Map.Entry<String,String> requestProperty : requestProperties().entrySet() ) {
            Object resolvedValue = properties().get(requestProperty.getKey(), requestProperties());
            if (resolvedValue == null && requestProperty.getKey().equals("queryProfile"))
                resolvedValue = requestProperty.getValue();

            b.append(requestProperty.getKey());
            b.append("=");
            b.append(String.valueOf(resolvedValue)); // (may be null)
            b.append(" (");

            if (profile != null && ! profile.isOverridable(new CompoundName(requestProperty.getKey()), requestProperties()))
                b.append("value from query profile - unoverridable, ignoring request value");
            else
                b.append("value from request");
            b.append(")\n");
            mentioned.add(requestProperty.getKey());
        }
        if (profile!=null) {
            appendQueryProfileProperties(profile,mentioned,b);
        }
        trace(b.toString(),false,4);
    }

    private Map<String, String> requestProperties() {
        return httpRequest.propertyMap();
    }

    private void appendQueryProfileProperties(CompiledQueryProfile profile,Set<String> mentioned,StringBuilder b) {
        for (Map.Entry<String,Object> property : profile.listValues("",requestProperties()).entrySet()) {
            if ( ! mentioned.contains(property.getKey()))
                b.append(property.getKey() + "=" + property.getValue() + " (value from query profile)<br/>\n");
        }
    }

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
        if (! queryProfileProperties.isComplete(missingName, httpRequest.propertyMap()))
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
     **/
    public long getTimeLeft() {
        return getTimeout() - getDurationTime();
    }

    public boolean requestHasProperty(String name) {
        return httpRequest.hasProperty(name);
    }

    /**
     * Returns the number of milliseconds to wait for a response from a search backend
     * before timing it out. Default is 5000.
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
     * before time out. Default is 5000.
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

    /**
     * Sets the context level of this query, 0 means no tracing
     * Higher numbers means increasingly more tracing
     */
    public void setTraceLevel(int traceLevel) { this.traceLevel = traceLevel; }

    /**
     * Returns the context level of this query, 0 means no tracing
     * Higher numbers means increasingly more tracing
     */
    public int getTraceLevel() { return traceLevel; }

    /**
     * Returns the context level of this query, 0 means no tracing
     * Higher numbers means increasingly more tracing
     */
    public final boolean isTraceable(int level) { return traceLevel >= level; }


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
            throw new IllegalArgumentException("Must be a positive number");
        this.hits = hits;
    }

    /**
     * Set the hit offset. Can not be less than 0. Default is 0.
     */
    public void setOffset(int offset) {
        if (offset < 0)
            throw new IllegalArgumentException("Must be a positive number");
        this.offset = offset;
    }

    /** Convenience method to set both the offset and the number of hits to return */
    public void setWindow(int offset,int hits) {
        setOffset(offset);
        setHits(hits);
    }

    /**
     * This is ignored - compression is controlled at the network level.
     *
     * @deprecated this is ignored
     */
    @Deprecated
    public void setCompress(boolean ignored) { }

    /**
     * Returns false.
     *
     * @deprecated this always returns false
     */
    @Deprecated
    public boolean getCompress() { return false; }

    /** Returns a string describing this query */
    @Override
    public String toString() {
        String queryTree;
        // getQueryTree isn't exception safe
        try {
            queryTree = model.getQueryTree().toString();
        } catch (Exception e) {
            queryTree = "[Could not parse user input: " + model.getQueryString() + "]";
        }
        return "query '" + queryTree + "'";
    }

    /** Returns a string describing this query in more detail */
    public String toDetailString() {
        String queryTree;
        // getQueryTree isn't exception safe
        try {
            queryTree = model.getQueryTree().toString();
        } catch (Exception e) {
            queryTree = "Could not parse user input: " + model.getQueryString();
        }
        return "query=[" + queryTree + "]" + " offset=" + getOffset() + " hits=" + getHits() + "]";
    }

    /**
     * Encodes this query onto the given buffer
     *
     * @param buffer The buffer to encode the query to
     * @return the number of encoded items
     */
    public int encode(ByteBuffer buffer) {
        return model.getQueryTree().encode(buffer);
    }

    /**
     * Adds a context message to this query and to the info log,
     * if the context level of the query is sufficiently high.
     * The context information will be carried over to the result at creation.
     * The message parameter will be included <i>with</i> XML escaping.
     *
     * @param message      the message to add
     * @param traceLevel   the context level of the message, this method will do nothing
     *                     if the traceLevel of the query is lower than this value
     */
    public void trace(String message, int traceLevel) {
        trace(message, false, traceLevel);
    }

    /**
     * Adds a trace message to this query
     * if the trace level of the query is sufficiently high.
     *
     * @param message      the message to add
     * @param includeQuery true to append the query root stringValue
     *        at the end of the message
     * @param traceLevel   the context level of the message, this method will do nothing
     *                     if the traceLevel of the query is lower than this value
     */
    public void trace(String message, boolean includeQuery, int traceLevel) {
        if ( ! isTraceable(traceLevel)) return;

        if (includeQuery)
            message += ": [" + queryTreeText() + "]";

        log.log(LogLevel.DEBUG,message);

        // Pass 0 as traceLevel as the trace level check is already done above,
        // and it is not propagated to trace until execution has started
        // (it is done in the execution.search method)
        getContext(true).trace(message, 0);
    }

    /**
     * Adds a trace message to this query
     * if the trace level of the query is sufficiently high.
     *
     * @param includeQuery true to append the query root stringValue at the end of the message
     * @param traceLevel   the context level of the message, this method will do nothing
     *                     if the traceLevel of the query is lower than this value
     * @param messages     the messages whose toStrings will be concatenated into the trace message.
     *                     Concatenation will only happen if the trace level is sufficiently high.
     */
    public void trace(boolean includeQuery, int traceLevel, Object... messages) {
        if ( ! isTraceable(traceLevel)) return;

        StringBuilder concatenated = new StringBuilder();
        for (Object message : messages)
            concatenated.append(String.valueOf(message));
        trace(concatenated.toString(), includeQuery, traceLevel);
    }

    /**
     * Set the context information for another query to be part of this query's
     * context information. This is to be used if creating fresh query objects as
     * part of a plug-in's execution. The query should be attached before it is
     * used, in case an exception causes premature termination. This is enforced
     * by an IllegalStateException. In other words, intended use is create the
     * new query, and attach the context to the invoking query as soon as the new
     * query is properly initialized.
     *
     * <p>
     * This method will always set the argument query's context level to the context
     * level of this query.
     *
     * @param query
     *                The query which should be traced as a part of this query.
     * @throws IllegalStateException
     *                 If the query given as argument already has context
     *                 information.
     */
    public void attachContext(Query query) throws IllegalStateException {
        query.setTraceLevel(getTraceLevel());
        if (context == null) {
            // Nothing to attach to. This is about the same as
            // getTraceLevel() == 0,
            // but is a direct test of what will make the function superfluous.
            return;
        }
        if (query.getContext(false) != null) {
            // If we added the other query's context info as a subnode in this
            // query's context tree, we would have to check for loops in the
            // context graph. If we simply created a new node without checking,
            // we might silently overwrite useful information.
            throw new IllegalStateException("Query to attach already has context information stored.");
        }
        query.context = context;
    }

    private String queryTreeText() {
        QueryTree root = getModel().getQueryTree();

        if (getTraceLevel() < 2)
            return root.toString();
        if (getTraceLevel() < 6)
            return yqlRepresentation();
        else
            return "\n" + yqlRepresentation() + "\n" + new TextualQueryRepresentation(root.getRoot()) + "\n";
    }

    /**
     * Serialize this query as YQL+. This method will never throw exceptions,
     * but instead return a human readable error message if a problem occured
     * serializing the query. Hits and offset information will be included if
     * different from default, while linguistics metadata are not added.
     *
     * @return a valid YQL+ query string or a human readable error message
     * @see Query#yqlRepresentation(Tuple2, boolean)
     */
    public String yqlRepresentation() {
        try {
            return yqlRepresentation(null, true);
        } catch (NullItemException e) {
            return "Query currently a placeholder, NullItem encountered.";
        } catch (RuntimeException e) {
            return "Failed serializing query as YQL+, please file a ticket including the query causing this: "
                    + Exceptions.toMessageString(e);
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
     * @param segmenterVersion
     *            linguistics metadata used in federation, set to null if the
     *            annotation is not necessary
     * @param includeHitsAndOffset
     *            whether to include hits and offset parameters converted to a
     *            offset/limit slice
     * @return a valid YQL+ query string
     * @throws RuntimeException if there is a problem serializing the query tree
     */
    public String yqlRepresentation(@Nullable Tuple2<String, Version> segmenterVersion, boolean includeHitsAndOffset) {
        String q = VespaSerializer.serialize(this);

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
        if (segmenterVersion != null) {
            yql.append("[{\"segmenter\": {\"version\": \"")
                    .append(segmenterVersion.second.toString())
                    .append("\", \"backend\": \"")
                    .append(segmenterVersion.first).append("\"}}](");
        }
        yql.append(q);
        if (segmenterVersion != null) {
            yql.append(')');
        }
        if (getRanking().getSorting() != null && getRanking().getSorting().fieldOrders().size() > 0) {
            serializeSorting(yql);
        }
        if (includeHitsAndOffset) {
            if (getOffset() != 0) {
                yql.append(" limit ")
                        .append(Integer.toString(getHits() + getOffset()))
                        .append(" offset ")
                        .append(Integer.toString(getOffset()));
            } else if (getHits() != 10) {
                yql.append(" limit ").append(Integer.toString(getHits()));
            }
        }
        if (getTimeout() != 5000L) {
            yql.append(" timeout ").append(Long.toString(getTimeout()));
        }
        yql.append(';');
        return yql.toString();
    }

    private void serializeSorting(StringBuilder yql) {
        yql.append(" order by ");
        int initLen = yql.length();
        for (FieldOrder f : getRanking().getSorting().fieldOrders()) {
            if (yql.length() > initLen) {
                yql.append(", ");
            }
            final Class<? extends AttributeSorter> sorterType = f.getSorter()
                    .getClass();
            if (sorterType == Sorting.RawSorter.class) {
                yql.append("[{\"").append(YqlParser.SORTING_FUNCTION)
                        .append("\": \"").append(Sorting.RAW).append("\"}]");
            } else if (sorterType == Sorting.LowerCaseSorter.class) {
                yql.append("[{\"").append(YqlParser.SORTING_FUNCTION)
                        .append("\": \"").append(Sorting.LOWERCASE)
                        .append("\"}]");
            } else if (sorterType == Sorting.UcaSorter.class) {
                Sorting.UcaSorter uca = (Sorting.UcaSorter) f.getSorter();
                String ucaLocale = uca.getLocale();
                Sorting.UcaSorter.Strength ucaStrength = uca.getStrength();
                yql.append("[{\"").append(YqlParser.SORTING_FUNCTION)
                        .append("\": \"").append(Sorting.UCA).append("\"");
                if (ucaLocale != null) {
                    yql.append(", \"").append(YqlParser.SORTING_LOCALE)
                            .append("\": \"").append(ucaLocale).append('"');
                }
                if (ucaStrength != Sorting.UcaSorter.Strength.UNDEFINED) {
                    yql.append(", \"").append(YqlParser.SORTING_STRENGTH)
                            .append("\": \"").append(ucaStrength.name())
                            .append('"');
                }
                yql.append("}]");
            }
            yql.append(f.getFieldName());
            if (f.getSortOrder() == Order.DESCENDING) {
                yql.append(" desc");
            }
        }
    }

    /** Returns the context of this query, possibly creating it if missing. Returns the context, or null */
    public QueryContext getContext(boolean create) {
        if (context==null && create)
            context=new QueryContext(getTraceLevel(),this);
        return context;
    }

    /** Returns a hash of this query based on (some of) its content. */
    @Override
    public int hashCode() {
        return ranking.hashCode()+3*presentation.hashCode()+5* model.hashCode()+ 11*offset+ 13*hits;
    }

    /** Returns whether the given query is equal to this */
    @Override
    public boolean equals(Object other) {
        if (this==other) return true;

        if ( ! (other instanceof Query)) return false;
        Query q = (Query) other;

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
        clone.ranking = (Ranking) ranking.clone();
        clone.presentation = (Presentation) presentation.clone();
        clone.context = getContext(true).cloneFor(clone);

        // Correct the Query instance in properties
        clone.properties().setParentQuery(clone);
        assert (clone.properties().getParentQuery() == clone);

        clone.setTraceLevel(getTraceLevel());
        clone.setHits(getHits());
        clone.setOffset(getOffset());
        clone.setNoCache(getNoCache());
        clone.setGroupingSessionCache(getGroupingSessionCache());
        clone.requestId = null; // Each clone should have their own requestId.
    }

    /** Returns the presentation to be used for this query, never null */
    public Presentation getPresentation() { return presentation; }

    /** Returns the ranking to be used for this query, never null */
    public Ranking getRanking() { return ranking; }

    /** Returns the query representation model to be used for this query, never null */
    public Model getModel() { return model; }

    /**
     * Return the HTTP request which caused this query. This will never be null
     * when running with queries from the network.
     */
     public HttpRequest getHttpRequest() { return httpRequest; }

    /**
     * Returns the unique and stable session id of this query.
     *
     * @param create if true this is created if not already set
     * @return the session id of this query, or null if not set and create is false
     */
    public SessionId getSessionId(boolean create) {
        if (requestId == null && ! create) return null;

        if (requestId == null && create) {
            requestId = UniqueRequestId.next();
        }

        return new SessionId(requestId, getRanking().getProfile());
    }

    public boolean hasEncodableProperties() {
        if ( ! ranking.getProperties().isEmpty()) return true;
        if ( ! ranking.getFeatures().isEmpty()) return true;
        if ( ranking.getFreshness() != null) return true;
        if ( model.getSearchPath() != null) return true;
        if ( model.getDocumentDb() != null) return true;
        if ( presentation.getHighlight() != null && ! presentation.getHighlight().getHighlightItems().isEmpty()) return true;
        return false;
    }

    /**
     * Encodes properties of this query.
     *
     * @param buffer the buffer to encode to
     * @param encodeQueryData true to encode all properties, false to only include session information, not actual query data
     * @return the encoded length
     */
    public int encodeAsProperties(ByteBuffer buffer, boolean encodeQueryData) {
        // Make sure we don't encode anything here if we have turned the property feature off
        // Due to sendQuery we sometimes end up turning this feature on and then encoding a 0 int as the number of
        // property maps - that's ok (probably we should simplify by just always turning the feature on)
        if (! hasEncodableProperties()) return 0;

        int start = buffer.position();

        int mapCountPosition = buffer.position();
        buffer.putInt(0); // map count will go here

        int mapCount = 0;

        // TODO: Push down
        mapCount += ranking.getProperties().encode(buffer, encodeQueryData);
        if (encodeQueryData) mapCount += ranking.getFeatures().encode(buffer);

        // TODO: Push down
        if (encodeQueryData && presentation.getHighlight() != null) mapCount += MapEncoder.encodeStringMultiMap(Highlight.HIGHLIGHTTERMS, presentation.getHighlight().getHighlightTerms(), buffer);

        // TODO: Push down
        if (encodeQueryData) mapCount += MapEncoder.encodeSingleValue("model", "searchpath", model.getSearchPath(), buffer);
        mapCount += MapEncoder.encodeSingleValue(DocumentDatabase.MATCH_PROPERTY, DocumentDatabase.SEARCH_DOC_TYPE_KEY, model.getDocumentDb(), buffer);

        mapCount += MapEncoder.encodeMap("caches", createCacheSettingMap(), buffer);

        buffer.putInt(mapCountPosition, mapCount);

        return buffer.position() - start;
    }

    private Map<String, Boolean> createCacheSettingMap() {
        if (getGroupingSessionCache() && ranking.getQueryCache()) {
            Map<String, Boolean> cacheSettingMap = new HashMap<>();
            cacheSettingMap.put("grouping", true);
            cacheSettingMap.put("query", true);
            return cacheSettingMap;
        }
        if (getGroupingSessionCache())
            return Collections.singletonMap("grouping", true);
        if (ranking.getQueryCache())
            return Collections.singletonMap("query", true);
        return Collections.<String,Boolean>emptyMap();
    }

    /**
     * Prepares this for binary serialization.
     * <p>
     * This must be invoked after all changes have been made to this query before it is passed
     * on to a receiving backend. Calling it is somewhat expensive, so it should only happen once.
     * If a prepared query is cloned, it stays prepared.
     */
    public void prepare() {
        getModel().prepare(getRanking());
        getPresentation().prepare();
        getRanking().prepare();
    }

}
