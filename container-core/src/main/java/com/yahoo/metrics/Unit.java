package com.yahoo.metrics;

/**
 * @author gjoranv
 */
public enum Unit {

    BINARY(BaseUnit.BINARY, "Zero or one. Zero typically indicate \"false\" while one indicate \"true\""),
    BUCKET(BaseUnit.BUCKET),
    BYTE(BaseUnit.BYTE),
    BYTE_PER_SECOND(BaseUnit.BYTE, BaseUnit.SECOND, "Number of bytes per second"),
    CONNECTION(BaseUnit.CONNECTION),
    DOCUMENT(BaseUnit.DOCUMENT),
    DOCUMENTID(BaseUnit.DOCUMENTID),
    FAILURE(BaseUnit.FAILURE),
    FILE(BaseUnit.FILE),
    FRACTION(BaseUnit.FRACTION, "A value in the range [0..1]. Higher values can occur for some metrics, but would indicate the value is outside of the allowed range."),
    HIT(BaseUnit.HIT),
    HIT_PER_QUERY(BaseUnit.HIT, BaseUnit.QUERY),
    INSTANCE(BaseUnit.INSTANCE),
    ITEM(BaseUnit.ITEM),
    MILLISECOND(BaseUnit.MILLISECOND),
    NANOSECOND(BaseUnit.NANOSECOND),
    NODE(BaseUnit.NODE),
    PACKET(BaseUnit.PACKET),
    OPERATION(BaseUnit.OPERATION),
    OPERATION_PER_SECOND(BaseUnit.OPERATION, BaseUnit.SECOND),
    PERCENTAGE(BaseUnit.PERCENTAGE),
    QUERY(BaseUnit.QUERY),
    QUERY_PER_SECOND(BaseUnit.QUERY, BaseUnit.SECOND),
    RECORD(BaseUnit.RECORD),
    REQUEST(BaseUnit.REQUEST),
    RESPONSE(BaseUnit.RESPONSE),
    RESTART(BaseUnit.RESTART),
    SCORE(BaseUnit.SCORE),
    SECOND(BaseUnit.SECOND),
    SESSION(BaseUnit.SESSION),
    TASK(BaseUnit.TASK),
    THREAD(BaseUnit.THREAD),
    VERSION(BaseUnit.VERSION),
    WAKEUP(BaseUnit.WAKEUP);


    private final BaseUnit unit;
    private final BaseUnit perUnit;
    private final String description;

    // TODO: Remove once we have description for all units
    Unit(BaseUnit unit) {
        this(unit, null, null);
    }

    // TODO: Remove once we have description for all units
    Unit(BaseUnit unit, BaseUnit perUnit) {
        this(unit, perUnit, null);
    }

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
        DOCUMENT("document"),
        DOCUMENTID("documentid"),
        FAILURE("failure"),
        FILE("file"),
        FRACTION("fraction"),
        HIT("hit"),
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
        SCORE("score"),
        SECOND("second", "s"),
        SESSION("session"),
        TASK("task"),
        THREAD("thread"),
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
