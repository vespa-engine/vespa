// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.api.annotations.Beta;

import java.util.Objects;
import java.util.Optional;

/**
 * <p>
 * The TestAndSetCondition class represents a test and set condition.
 * A test and set condition is an (optional) string representing a
 * document selection (cf. document selection language), which is used
 * to match a document for test and set.
 * </p><p>
 * If #isPresent evaluates to false, the condition is not present and
 * matches any document.
 * </p>
 * @author Vegard Sjonfjell
 * @author @vekterli
 */
public class TestAndSetCondition {

    public static final TestAndSetCondition NOT_PRESENT_CONDITION = new TestAndSetCondition();

    private final String conditionStr;
    private final long requiredTimestamp;

    public TestAndSetCondition() {
        this("");
    }

    public TestAndSetCondition(String conditionStr) {
        this.conditionStr = conditionStr;
        this.requiredTimestamp = 0;
    }

    private TestAndSetCondition(long requiredTimestamp, String conditionStr) {
        this.conditionStr = conditionStr;
        this.requiredTimestamp = requiredTimestamp;
    }

    private TestAndSetCondition(long requiredTimestamp) {
        this.conditionStr = "";
        this.requiredTimestamp = requiredTimestamp;
    }

    public String getSelection() { return conditionStr; }

    /**
     * <p>Returns the timestamp that the document stored in the backend must match exactly
     * for the associated document operation to go through, or 0 if no timestamp requirement
     * is present (i.e. only the selection condition, if present, is checked).</p>
     *
     * <p>Note: the timestamp should be compared with Long.compareUnsigned(), as it
     * reflects an unsigned 64-bit integer in the backend.</p>
     *
     * <p><strong>Note:</strong> this API is currently in Beta.</p>
     */
    @Beta
    public long requiredTimestamp() {
        return requiredTimestamp;
    }

    public boolean isPresent() { return !conditionStr.isEmpty() || (requiredTimestamp != 0); }

    /**
     * Maps and optional test and set condition string to a TestAndSetCondition.
     * If the condition string is not present, a "not present" condition is returned
     * @param conditionString test and set condition string (document selection)
     * @return a TestAndSetCondition representing the condition string or a "not present" condition
     */
    public static TestAndSetCondition fromConditionString(Optional<String> conditionString) {
        return conditionString
                .map(TestAndSetCondition::new)
                .orElse(TestAndSetCondition.NOT_PRESENT_CONDITION);
    }

    /**
     * <p>Returns a TestAndSetCondition with a required timestamp, but which has a transparent
     * fallback to evaluating a selection string if the backend does not support timestamp conditions.
     * This has the following semantics:</p>
     * <ul>
     *     <li>If the backend supports test and set conditions with timestamp requirements,
     *         the condition passes iff the currently persisted timestamp exactly matches
     *         the timestamp in the condition. The selection condition string is completely
     *         <em>ignored</em> in this case.</li>
     *     <li>If the backend is too old to support timestamp requirements the condition
     *         selection string is evaluated as if there was <em>no</em> required timestamp
     *         present.</li>
     * </ul>
     *
     * <p><strong>Note:</strong> this API is currently in Beta.</p>
     *
     * @param requiredTimestamp required timestamp, or 0 for no timestamp requirement.
     * @param selectionFallback condition selection string that will be evaluated by nodes
     *                          that are on a version too old to support timestamp requirements.
     */
    @Beta
    public static TestAndSetCondition ofRequiredTimestampWithSelectionFallback(long requiredTimestamp, String selectionFallback) {
        return new TestAndSetCondition(requiredTimestamp, selectionFallback);
    }

    /**
     * <p>Returns a TestAndSetCondition that requires the currently persisted document in the
     * backend to have a timestamp exactly matching the provided timestamp value.</p>
     *
     * <p>Version compatibility note: older versions of Vespa that do not support timestamp
     * requirements as part of test and set conditions will observe a condition with only
     * a timestamp set as if the condition is <em>empty</em>, i.e. the operation will be
     * applied unconditionally.</p>
     *
     * <p><strong>Note:</strong> this API is currently in Beta</p>
     *
     * @param requiredTimestamp An (unsigned) timestamp corresponding exactly to the persisted
     *                          backend timestamp of the document to update.
     */
    @Beta
    public static TestAndSetCondition ofRequiredTimestamp(long requiredTimestamp) {
        return new TestAndSetCondition(requiredTimestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestAndSetCondition that = (TestAndSetCondition) o;
        return requiredTimestamp == that.requiredTimestamp &&
                Objects.equals(conditionStr, that.conditionStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditionStr, requiredTimestamp);
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();
        string.append("condition '");
        string.append(conditionStr);
        string.append("'");
        if (requiredTimestamp != 0) {
            string.append(", required_timestamp ");
            string.append(Long.toUnsignedString(requiredTimestamp));
        }
        return string.toString();
    }
}
