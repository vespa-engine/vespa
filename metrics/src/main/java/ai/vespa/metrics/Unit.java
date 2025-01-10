// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics;

/**
 * @author gjoranv
 */
public enum Unit {

    BINARY(BaseUnit.BINARY, "Zero or one. Zero typically indicate \"false\" while one indicate \"true\""),
    BUCKET(BaseUnit.BUCKET, "A chunk of documents managed by a distributor service"),
    BYTE(BaseUnit.BYTE, "A collection of 8 bits"),
    BYTE_PER_SECOND(BaseUnit.BYTE, BaseUnit.SECOND, "A unit of storage capable of holding 8 bits"),
    CONNECTION(BaseUnit.CONNECTION, "A link used for communication between a client and a server"),
    DEPLOYMENT(BaseUnit.DEPLOYMENT, "A deployment on hosted Vespa"),
    DOCUMENT(BaseUnit.DOCUMENT, "Vespa document, a collection of fields defined in a schema file"),
    DOCUMENTID(BaseUnit.DOCUMENTID, "A unique document identifier"),
    DOLLAR(BaseUnit.DOLLAR, "US dollar"),
    DOLLAR_PER_HOUR(BaseUnit.DOLLAR, BaseUnit.HOUR, "Total current cost of the cluster in $/hr"),
    FAILURE(BaseUnit.FAILURE, "Failures, typically for requests, operations or nodes"),
    FILE(BaseUnit.FILE, "Data file stored on the disk on a node"),
    FRACTION(BaseUnit.FRACTION, "A value in the range [0..1]. Higher values can occur for some metrics, but would indicate the value is outside the allowed range."),
    GENERATION(BaseUnit.GENERATION,"Typically, generation of configuration or application package"),
    GIGABYTE(BaseUnit.GIGABYTE,"One billion bytes"),
    HIT(BaseUnit.HIT, "Document that meets the filtering/restriction criteria specified by a given query"),
    HIT_PER_QUERY(BaseUnit.HIT, BaseUnit.QUERY, "Number of hits per query over a period of time"),
    HOST(BaseUnit.HOST, "Bare metal computer that contain nodes"),
    INSTANCE(BaseUnit.INSTANCE, "Typically, tenant or application"),
    ITEM(BaseUnit.ITEM, "Object or unit maintained in e.g. a queue"),
    MILLISECOND(BaseUnit.MILLISECOND, "Millisecond, 1/1000 of a second"),
    NANOSECOND(BaseUnit.NANOSECOND, "Nanosecond, 1/1000.000.000 of a second"),
    NODE(BaseUnit.NODE, "(Virtual) computer that is part of a Vespa cluster"),
    PACKET(BaseUnit.PACKET, "Collection of data transmitted over the network as a single unit"),
    OPERATION(BaseUnit.OPERATION, "A clearly defined task"),
    OPERATION_PER_SECOND(BaseUnit.OPERATION, BaseUnit.SECOND, "Number of operations per second"),
    PERCENTAGE(BaseUnit.PERCENTAGE, "A number expressed as a fraction of 100, normally in the range [0..100]."),
    QUERY(BaseUnit.QUERY, "A request for matching, grouping and/or scoring documents stored in Vespa"),
    QUERY_PER_SECOND(BaseUnit.QUERY, BaseUnit.SECOND, "Number of queries per second."),
    RECORD(BaseUnit.RECORD, "A collection of information, typically a set of key/value, e.g. stored in a transaction log"),
    REQUEST(BaseUnit.REQUEST, "A request sent from a client to a server"),
    RESPONSE(BaseUnit.RESPONSE, "A response from a server to a client, typically as a response to a request"),
    RESTART(BaseUnit.RESTART, "A service or node restarts"),
    ROTATION(BaseUnit.ROTATION, "Routing rotation"),
    SCORE(BaseUnit.SCORE, "Relevance score for a document"),
    SECOND(BaseUnit.SECOND, "Time span of 1 second"),
    SECONDS_SINCE_EPOCH(BaseUnit.SECONDS_SINCE_EPOCH,"Seconds since Unix Epoch"),
    SESSION(BaseUnit.SESSION, "A set of operations taking place during one connection or as part of a higher level operation"),
    TASK(BaseUnit.TASK, "Piece of work executed by a server, e.g. to perform back-ground data maintenance"),
    TENANT(BaseUnit.TENANT, "Tenant that owns zero or more applications in a managed Vespa system"),
    THREAD(BaseUnit.THREAD, "Computer thread for executing e.g. tasks, operations or queries"),
    VCPU(BaseUnit.VCPU,"Virtual CPU"),

    VERSION(BaseUnit.VERSION, "Software or config version"),
    WAKEUP(BaseUnit.WAKEUP, "Computer thread wake-ups for doing some work");


    private final BaseUnit unit;
    private final BaseUnit perUnit;
    private final String description;

    Unit(BaseUnit unit, String description) {
        this(unit, null, description);
    }

    Unit(BaseUnit unit, BaseUnit perUnit, String description) {
        this.unit = unit;
        this.perUnit = perUnit;
        this.description = description;
    }

    public String fullName() {
        return perUnit == null ?
                unit.fullName() :
                unit.fullName() + "/" + perUnit.fullName();
    }

    public String shortName() {
        return perUnit == null ?
                unit.shortName :
                unit.shortName + "/" + perUnit.shortName;
    }

    public String getDescription() {
        return description;
    }

    private enum BaseUnit {

        AREA("area"),
        BINARY("binary"),
        BUCKET("bucket"),
        BYTE("byte"),
        CONNECTION("connection"),
        DEPLOYMENT("deployment"),
        DOCUMENT("document"),
        DOCUMENTID("documentid"),
        DOLLAR("dollar"),
        FAILURE("failure"),
        FILE("file"),
        FRACTION("fraction"),
        GENERATION("generation"),
        GIGABYTE("gigabyte"),
        HIT("hit"),
        HOST("host"),
        HOUR("hour"),
        INSTANCE("instance"),
        ITEM("item"),
        MILLISECOND("millisecond", "ms"),
        NANOSECOND("nanosecond", "ns"),
        NODE("node"),
        OPERATION("operation"),
        PACKET("packet"),
        PERCENTAGE("percentage"),
        QUERY("query"),
        RECORD("record"),
        REQUEST("request"),
        RESPONSE("response"),
        RESTART("restart"),
        ROTATION("routing rotation"),
        SCORE("score"),
        SECOND("second", "s"),
        SECONDS_SINCE_EPOCH("seconds since epoch"),
        SESSION("session"),
        TASK("task"),
        TENANT("tenant"),
        THREAD("thread"),
        VCPU("vcpu"),
        VERSION("version"),
        WAKEUP("wakeup");

        private final String fullName;
        private final String shortName;

        BaseUnit(String fullName) {
            this(fullName, fullName);
        }

        BaseUnit(String fullName, String shortName) {
            this.fullName = fullName;
            this.shortName = shortName;
        }

        public String fullName() {
            return fullName;
        }

        public String shortName() {
            return shortName;
        }

    }
}
