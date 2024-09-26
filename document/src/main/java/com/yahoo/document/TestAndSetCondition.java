// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.api.annotations.Beta;

import java.util.Objects;
import java.util.Optional;

/**
 * The TestAndSetCondition class represents a test and set condition.
 * A test and set condition is an (optional) string representing a
 * document selection (cf. document selection language), which is used
 * to match a document for test and set. If #isPresent evaluates to false,
 * the condition is not present and matches any document.
 *
 * @author Vegard Sjonfjell
 */
public class TestAndSetCondition {

    public static final TestAndSetCondition NOT_PRESENT_CONDITION = new TestAndSetCondition();

    private final String conditionStr;
    private final long requiredPersistenceTimestamp;

    public TestAndSetCondition() {
        this("");
    }

    public TestAndSetCondition(String conditionStr) {
        this.conditionStr = conditionStr;
        this.requiredPersistenceTimestamp = 0;
    }

    @Beta
    public TestAndSetCondition(String conditionStr, long requiredPersistenceTimestamp) {
        this.conditionStr = conditionStr;
        this.requiredPersistenceTimestamp = requiredPersistenceTimestamp;
    }

    @Beta
    public TestAndSetCondition(long requiredPersistenceTimestamp) {
        this.conditionStr = "";
        this.requiredPersistenceTimestamp = requiredPersistenceTimestamp;
    }

    public String getSelection() { return conditionStr; }

    @Beta
    public long getRequiredPersistenceTimestamp() {
        return requiredPersistenceTimestamp;
    }

    public boolean isPresent() { return !conditionStr.isEmpty() || (requiredPersistenceTimestamp != 0); }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestAndSetCondition that = (TestAndSetCondition) o;
        return requiredPersistenceTimestamp == that.requiredPersistenceTimestamp && Objects.equals(conditionStr, that.conditionStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditionStr, requiredPersistenceTimestamp);
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();
        string.append("condition '");
        string.append(conditionStr);
        string.append("'");
        if (requiredPersistenceTimestamp != 0) {
            string.append(", required_persistence_timestamp ");
            string.append(Long.toUnsignedString(requiredPersistenceTimestamp));
        }
        return string.toString();
    }
}
