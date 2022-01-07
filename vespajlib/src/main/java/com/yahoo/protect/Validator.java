// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.protect;

/**
 * Static utility methods for validating input.
 *
 * @author bratseth
 */
public abstract class Validator {

    /** Throws NullPointerException if the argument is null */
    public static void ensureNotNull(String argumentDescription, Object argument) {
        if (argument == null)
            throw new NullPointerException(argumentDescription + " can not be null");
    }

    /** Throws NullPointerException if the argument is null */
    public static void ensureNonEmpty(String argumentDescription, String argument) {
        if (argument.isEmpty())
            throw new IllegalArgumentException(argumentDescription + " can not be empty");
    }

    /**
     * Throws an IllegalStateException if the given field value
     * is initialized (not null)
     */
    public static void ensureNotInitialized(String fieldDescription, Object fieldOwner, Object fieldValue) {
        if (fieldValue != null) {
            throw new IllegalStateException(fieldDescription + " of " + fieldOwner +
                                            " cannot be changed, it is already set " + "to " + fieldValue);
        }
    }

    /**
     * Throws an IllegalArgumentException if the given argument is not
     * in the given range
     *
     * @param argumentDescription a description of the argument
     * @param from the range start, inclusive
     * @param to the range end, inclusive
     * @param argument the argument value to check
     */
    public static void ensureInRange(String argumentDescription, int from, int to, int argument) {
        if (argument < from || argument > to) {
            throw new IllegalArgumentException(argumentDescription + " is " + argument +
                                               " but must be between " + from + " and " + to);
        }
    }

    /**
     * Throws an IllegalArgumentException if the first argument is not strictly
     * smaller than the second argument
     *
     * @param smallDescription description of the smallest argument
     * @param small the smallest argument
     * @param largeDescription description of the lergest argument
     * @param large the largest argument
     */
    public static void ensureSmaller(String smallDescription, int small, String largeDescription, int large) {
        if (small >= large) {
            throw new IllegalArgumentException(smallDescription + " is " + small + " but should be " +
                                               "less than " + largeDescription + " " + large);
        }
    }

    /**
     * Throws an IllegalArgumentException if the first argument is not strictly
     * smaller than the second argument
     *
     * @param smallDescription description of the smallest argument
     * @param small the smallest argument
     * @param largeDescription description of the largest argument
     * @param large the largest argument
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void ensureSmaller(String smallDescription, Comparable small, String largeDescription, Comparable large) {
        if (small.compareTo(large) >= 0) {
            throw new IllegalArgumentException(smallDescription + " is " + small + " but should be " +
                                               "less than " + largeDescription + " " + large);
        }
    }

    /**
     * Ensures that the given argument is true
     *
     * @param description of what is the case if the condition is false
     * @param condition the condition to ensure is true
     * @throws IllegalArgumentException if the given condition was false
     */
    public static void ensure(String description, boolean condition) {
        if ( ! condition) {
            throw new IllegalArgumentException(description);
        }
    }

    /**
     * Ensure the given argument is true, if not throw IllegalArgumentException
     * concatenating the String representation of the description arguments.
     */
    public static void ensure(boolean condition, Object... description) {
        if ( ! condition) {
            StringBuilder msg = new StringBuilder();
            for (Object part : description) {
                msg.append(part.toString());
            }
            throw new IllegalArgumentException(msg.toString());
        }
    }

    /**
     * Ensures that an item is of a particular class
     *
     * @param description a description of the item to be checked
     * @param item the item to check the type of
     * @param type the type the given item should be instanceof
     * @throws IllegalArgumentException if the given item is not of the correct type
     */
    public static void ensureInstanceOf(String description, Object item, Class<?> type) {
        if ( ! type.isAssignableFrom(item.getClass())) {
            throw new IllegalArgumentException(description + " " + item + " should be an instance of " + type +
                                               " but is " + item.getClass());
        }
    }

    /**
     * Ensures that an item is not of a particular class
     *
     * @param description a description of the item to be checked
     * @param item the item to check the type of
     * @param type the type the given item should NOT be instanceof
     * @throws IllegalArgumentException if the given item is of the wrong type
     */
    public static void ensureNotInstanceOf(String description, Object item, Class<?> type) {
        if ( type.isAssignableFrom(item.getClass())) {
            throw new IllegalArgumentException(description + " " + item + " should NOT be an instance of " + type +
                    " but is " + item.getClass());
        }
    }

}
