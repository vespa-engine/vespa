// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.lang.reflect.Method;

/**
 * Some useful matchers.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public final class Matchers {

    /**
     * A match is found if at least <em>one</em> item in the Iterable has the given method, <em>and</em> the return value
     * when calling it equals the given expected value.
     *
     * @param expected The expected value.
     * @param methodName The name of the method to call. The method must take no parameters.
     * @return A Matcher to match against an Iterable.
     */
    @SuppressWarnings("rawtypes")
    public static org.hamcrest.Matcher<java.lang.Iterable> hasItemWithMethod(final Object expected, final String methodName) {
        return hasItemWithMethod(new MethodResult(methodName, expected));
    }

    /**
     * A match is found if at least <em>one</em> item in the Iterable has <em>all</em> given methods, <em>and</em>
     * the return values when calling them equals the given expected values.
     *
     * @param results The pairs of method names and expected results to compare.
     * @return A Matcher to match against an Iterable.
     */
    @SuppressWarnings("rawtypes")
    public static org.hamcrest.Matcher<java.lang.Iterable> hasItemWithMethod(final MethodResult... results) {
        return new BaseMatcher<Iterable>() {
            @Override
            public boolean matches(Object item) {
                Iterable components = (Iterable) item;
                if (!components.iterator().hasNext()) {
                    //empty collection
                    return false;
                }
                for (Object componentInList : components) {
                    boolean allMethodsMatch = false;
                    for (MethodResult result : results) {
                        Object strToMatch;
                        try {
                            Method method = componentInList.getClass().getMethod(result.getMethodName());
                            strToMatch = method.invoke(componentInList);
                        } catch (Exception e) {
                            allMethodsMatch = false;
                            break;
                        }
                        if (result.getExpectedResult().equals(strToMatch)) {
                            allMethodsMatch = true;
                        } else {
                            allMethodsMatch = false;
                            break;
                        }
                    }
                    if (allMethodsMatch) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                StringBuilder b = new StringBuilder();
                for (MethodResult result : results) {
                    b.append(result).append(". ");
                }
                description.appendText(b.toString());
            }
        };
    }

    public static final class MethodResult {
        private final String methodName;
        private final Object expectedResult;

        public MethodResult(String methodName, Object expectedResult) {
            this.methodName = methodName;
            this.expectedResult = expectedResult;
        }

        public Object getExpectedResult() {
            return expectedResult;
        }

        public String getMethodName() {
            return methodName;
        }

        @Override
        public String toString() {
            return "Method: '" + methodName + "\', expected result: '" + expectedResult + '\'';
        }
    }
}
