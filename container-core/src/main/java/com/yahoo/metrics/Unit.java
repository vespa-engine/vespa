package com.yahoo.metrics;

/**
 * @author gjoranv
 */
public enum Unit {

    BINARY(BaseUnit.BINARY),
    BYTE(BaseUnit.BYTE),
    CONNECTION(BaseUnit.CONNECTION),
    DOCUMENT(BaseUnit.DOCUMENT),
    DOCUMENT_PER_SECOND(BaseUnit.DOCUMENT, BaseUnit.SECOND),
    FRACTION(BaseUnit.FRACTION),
    HIT(BaseUnit.HIT),
    HIT_PER_QUERY(BaseUnit.HIT, BaseUnit.QUERY),
    ITEM(BaseUnit.ITEM),
    MILLISECOND(BaseUnit.MILLISECOND),
    NANOSECOND(BaseUnit.NANOSECOND),
    NODE(BaseUnit.NODE),
    OPERATION(BaseUnit.OPERATION),
    OPERATION_PER_SECOND(BaseUnit.OPERATION, BaseUnit.SECOND),
    QUERY(BaseUnit.QUERY),
    QUERY_PER_SECOND(BaseUnit.QUERY, BaseUnit.SECOND),
    REQUEST(BaseUnit.REQUEST),
    RESPONSE(BaseUnit.RESPONSE),
    RESPONSE_PER_SECOND(BaseUnit.RESPONSE, BaseUnit.SECOND),
    SCORE(BaseUnit.SCORE),
    SECOND(BaseUnit.SECOND),
    THREAD(BaseUnit.THREAD),
    VERSION(BaseUnit.VERSION);


    private final BaseUnit unit;
    private final BaseUnit perUnit;

    Unit(BaseUnit unit) {
        this(unit, null);
    }

    Unit(BaseUnit unit, BaseUnit perUnit) {
        this.unit = unit;
        this.perUnit = perUnit;
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

    private enum BaseUnit {

        BINARY("binary"),
        BYTE("byte"),
        CONNECTION("connection"),
        DOCUMENT("document"),
        FRACTION("fraction"),
        HIT("hit"),
        ITEM("item"),
        MILLISECOND("millisecond", "ms"),
        NANOSECOND("nanosecond", "ns"),
        NODE("node"),
        OPERATION("operation"),
        QUERY("query"),
        REQUEST("request"),
        RESPONSE("response"),
        SCORE("score"),
        SECOND("second", "s"),
        THREAD("thread"),
        VERSION("version");

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
