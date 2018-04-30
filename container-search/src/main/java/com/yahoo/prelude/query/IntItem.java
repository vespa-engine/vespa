// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


import java.math.BigInteger;
import java.nio.ByteBuffer;


/**
 * This represents either
 * <ul>
 * <li>a number (integer or floating point)
 * <li>a partial range, given by "&lt;number" or "&gt;number", where the numbers are exclusive, or
 * <li>a full or open range "[number;number], "[number;]" or "[;number]" where the numbers are inclusive,
 * or exclusive if a square bracket is replaced with a pointy one
 * </ul>
 *
 * If a range is specified in brackets, it is also permissible to add a third number specifying the number of hits this
 * will match on each node - [from;to;hitLimit]
 *
 * @author  bratseth
 */
public class IntItem extends TermItem {

    /** The inclusive lower end of this range */
    private Limit from;

    /** The inclusive upper end of this range */
    private Limit to;

    private int hitLimit = 0;

    /** The number expression of this */
    private String expression;

    /**
     * Creates an int item which must be equal to the given int number -
     * that is both the lower and upper limit is this number
     */
    public IntItem(int number, String indexName) {
        this(new Limit(number, true), new Limit(number, true), indexName);
    }

    /**
     * Creates an int item which must be equal to the given long number -
     * that is both the lower and upper limit is this number
     */
    public IntItem(long number, String indexName) {
        this(new Limit(number, true), new Limit(number, true), indexName);
    }

    public IntItem(Limit from, Limit to, String indexName) {
        super(indexName, false);
        this.from = from;
        this.to = to;
        expression = toExpression(from, to, 0);
    }

    /** Returns the simplest expression matching this */
    private String toExpression(Limit from, Limit to, int hitLimit) {
        if (from.equals(to) && hitLimit == 0) return from.number().toString();

        String expression = from.toRangeStart() + ";" + to.toRangeEnd();
        if (hitLimit == 0) return expression;

        // Insert ;hitLimit at the end inside the brackets
        return expression.substring(0, expression.length()-1) + ";" + hitLimit + expression.substring(expression.length()-1);
    }

    public IntItem(String expression) {
        this(expression, "");
    }

    public IntItem(String expression, boolean isFromQuery) {
        this(expression, "", isFromQuery);
    }

    public IntItem(String expression, String indexName) {
        this(expression, indexName, false);
    }

    public IntItem(String expression, String indexName, boolean isFromQuery) {
        super(indexName, isFromQuery);
        setNumber(expression);
    }

    public IntItem(Limit from, Limit to, int hitLimit, String indexName, boolean isFromQuery) {
        super(indexName, isFromQuery);
        setLimits(from, to);
        this.hitLimit = hitLimit;
        this.expression = toExpression(from, to, hitLimit);
    }

    /** Sets limit and flip them if "from" is greater than "to" */
    private final void setLimits(Limit from, Limit to) {
        if (from.number().doubleValue() > to.number().doubleValue()) {
            this.from = to;
            this.to = from;
        }
        else {
            this.from = from;
            this.to = to;
        }
    }

    /** Sets the number expression of this - a number or range following the syntax specified in the class javadoc */
    public void setNumber(String expression) {
        try {
            this.expression = expression;
            parseAndAssignLimits(expression.trim());
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + expression + "' is not an int item expression: " +
                    "Expected NUMBER, '<'NUMBER, '>'NUMBER or ('['|'<')NUMBER;NUMBER(;NUMBER)?(']'|'>')", e);

        }
    }

    private void parseAndAssignLimits(String e) {
        if (e.startsWith("<") && ! e.contains(";")) {
            from = Limit.NEGATIVE_INFINITY;
            to = new Limit(asNumber(e.substring(1)), false);
        }
        else if (e.startsWith(">")) {
            from = new Limit(asNumber(e.substring(1)), false);
            to = Limit.POSITIVE_INFINITY;
        }
        else if (e.startsWith("[") || e.startsWith("<")) {
            if ( ! (e.endsWith("]") || e.endsWith(">"))) throw new IllegalArgumentException("No closing bracket");

            boolean inclusiveStart = e.startsWith("[");
            boolean inclusiveEnd = e.startsWith("[");

            String[] limits = e.substring(1, e.length()-1).split(";");
            if (limits.length < 1 || limits.length > 3) throw new IllegalArgumentException("Unexpected bracket content");
            Limit from = new Limit(getOr(Double.NEGATIVE_INFINITY, 0, limits), inclusiveStart);
            Limit to = new Limit(getOr(Double.POSITIVE_INFINITY, 1, limits), inclusiveEnd);
            setLimits(from, to);
            hitLimit = getOr(0, 2, limits).intValue();
        }
        else {
            to = from = new Limit(asNumber(e), true);
        }
    }

    private Number getOr(Number defaultValue, int valueIndex, String[] values) {
        if (valueIndex >= values.length) return defaultValue;
        if (values[valueIndex] == null) return defaultValue;
        if (values[valueIndex].isEmpty()) return defaultValue;
        return asNumber(values[valueIndex]);
    }

    private Number asNumber(String numberString) {
        try {
            if (!numberString.contains(".")) return Long.valueOf(numberString);
        }
        catch (NumberFormatException e) {
            return new BigInteger(numberString);
        }
        return Double.valueOf(numberString);
    }

    /** Sets the number expression of this - a number or range */
    public String getNumber() { return expression; }

    /** Returns the lower limit of this range, which may be negative infinity */
    public final Limit getFromLimit() {
        return from;
    }

    /** Returns the upper limit of this range, which may be positive infinity */
    public final Limit getToLimit() {
        return to;
    }

    /**
     * Returns the number of hits this will match, or 0 if all should be matched.
     * If this number is positive, the hits closest to <code>from</code> are returned, and if
     * this number is negative the hits closest to <code>to</code> are returned.
     */
    public final int getHitLimit() {
        return hitLimit;
    }

    /**
     * Sets the number of hits this will match, or 0 if all should be
     * matched. If this number is positive, the hits closest to
     * <code>from</code> are returned, and if this number is negative the hits
     * closest to <code>to</code> are returned.
     *
     * @param hitLimit
     *            number of hits to match for this operator
     */
    public final void setHitLimit(int hitLimit) {
        this.hitLimit = hitLimit;
        this.expression = toExpression(from, to, hitLimit);
    }

    @Override
    public String getRawWord() {
        return getNumber();
    }

    @Override
    public ItemType getItemType() {
        return ItemType.INT;
    }

    @Override
    public String getName() {
        return "INT";
    }

    @Override
    public String stringValue() {
        return expression;
    }

    /** Same as {@link #setNumber} */
    @Override
    public void setValue(String value) { setNumber(value); }

    /** Int items uses a empty heading instead of "INT " */
    protected void appendHeadingString(StringBuilder buffer) {}

    @Override
    public int hashCode() {
        return super.hashCode() + 199 * expression.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if ( ! super.equals(object)) return false;

        IntItem other = (IntItem) object; // Ensured by superclass
        if ( ! getFromLimit().equals(other.getFromLimit())) return false;
        if ( ! getToLimit().equals(other.getToLimit())) return false;
        if ( getHitLimit() != other.getHitLimit()) return false;
        return true;
    }

    /** Returns the number for encoding; the number expression as-is. */
    protected String getEncodedInt() {
        return getIndexedString();
    }

    @Override
    public String getIndexedString() {
        return expression;
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer); // takes care of index bytes
        putString(getEncodedInt(), buffer);
    }

    @Override
    public int getNumWords() {
        return 1;
    }

    @Override
    public boolean isStemmed() {
        return true;
    }

    @Override
    public boolean isWords() {
        return false;
    }

    /**
     * Creates an int item from arguments.
     * This will return an instance of the RankItem subclass if either <code>hitLimit</code> or both <code>from</code>
     * and <code>to</code> is set to a value other than defaults (respectively 0, double negative and positive infinity).
     * And different from each other.
     *
     * @param indexName the index this searches
     * @param from the lower limit (inclusive) on hits
     * @param to the higher limit (inclusive) on hits
     * @param hitLimit the number of hits to match, or 0 to return all
     */
    public static IntItem from(String indexName, Limit from, Limit to, int hitLimit) {
        if (hitLimit == 0 && (from.equals(Limit.NEGATIVE_INFINITY) || to.equals(Limit.POSITIVE_INFINITY) || from.equals(to)))
            return new IntItem(from, to, indexName);
        else {
            return new RangeItem(from, to, hitLimit, indexName, false);
        }
    }

}
