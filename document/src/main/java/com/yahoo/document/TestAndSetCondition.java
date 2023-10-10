// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

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

    public TestAndSetCondition() {
        this("");
    }

    public TestAndSetCondition(String conditionStr) {
        this.conditionStr = conditionStr;
    }

    public String getSelection() { return conditionStr; }

    public boolean isPresent() { return !conditionStr.isEmpty(); }

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
        return conditionStr.equals(that.conditionStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditionStr);
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();
        string.append("condition '");
        string.append(conditionStr);
        string.append("'");

        return string.toString();
    }
}
