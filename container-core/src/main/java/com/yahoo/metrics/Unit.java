package com.yahoo.metrics;

/**
 * @author gjoranv
 */
public enum Unit {

    BYTE(BaseUnit.BYTE),
    DOCUMENT(BaseUnit.DOCUMENT),
    DOCUMENT_PER_SECOND(BaseUnit.DOCUMENT, BaseUnit.SECOND),
    FRACTION(BaseUnit.FRACTION),
    HIT(BaseUnit.HIT),
    HIT_PER_QUERY(BaseUnit.HIT, BaseUnit.QUERY),
    MILLISECOND(BaseUnit.MILLISECOND),
    OPERATION_PER_SECOND(BaseUnit.OPERATION, BaseUnit.SECOND),
    QUERY(BaseUnit.QUERY),
    QUERY_PER_SECOND(BaseUnit.QUERY, BaseUnit.SECOND),
    RESPONSE(BaseUnit.RESPONSE),
    RESPONSE_PER_SECOND(BaseUnit.RESPONSE, BaseUnit.SECOND),
    SECOND(BaseUnit.SECOND),
    THREAD(BaseUnit.THREAD);


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

    public enum BaseUnit {

        BYTE("byte"),
        DOCUMENT("document"),
        FRACTION("fraction"),
        HIT("hit"),
        MILLISECOND("millisecond", "ms"),
        OPERATION("operation"),
        QUERY("query"),
        RESPONSE("response"),
        SECOND("second", "s"),
        THREAD("thread");

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
