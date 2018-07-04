// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This abstract class is a factory for timestamp functions in a {@link GroupingExpression}. Apart from offering
 * per-function factory methods, this class also contains a {@link #newInstance(com.yahoo.search.grouping.request.TimeFunctions.Type,
 * GroupingExpression)} method which is useful for runtime construction of grouping requests.
 *
 * @author Simon Thoresen Hult
 */
public abstract class TimeFunctions {

    /**
     * Defines the different types of timestamps-functions that are available.
     */
    public enum Type {
        DATE,
        DAY_OF_MONTH,
        DAY_OF_WEEK,
        DAY_OF_YEAR,
        HOUR_OF_DAY,
        MINUTE_OF_HOUR,
        MONTH_OF_YEAR,
        SECOND_OF_MINUTE,
        YEAR
    }

    /**
     * Creates a new timestamp-function of the specified type for the given {@link GroupingExpression}.
     *
     * @param type The type of function to create.
     * @param exp  The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static FunctionNode newInstance(Type type, GroupingExpression exp) {
        switch (type) {
        case DATE:
            return newDate(exp);
        case DAY_OF_MONTH:
            return newDayOfMonth(exp);
        case DAY_OF_WEEK:
            return newDayOfWeek(exp);
        case DAY_OF_YEAR:
            return newDayOfYear(exp);
        case HOUR_OF_DAY:
            return newHourOfDay(exp);
        case MINUTE_OF_HOUR:
            return newMinuteOfHour(exp);
        case MONTH_OF_YEAR:
            return newMonthOfYear(exp);
        case SECOND_OF_MINUTE:
            return newSecondOfMinute(exp);
        case YEAR:
            return newYear(exp);
        }
        throw new UnsupportedOperationException("Time function '" + type + "' not supported.");
    }

    /**
     * Creates a new instance of {@link DateFunction} for the given {@link GroupingExpression}.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static DateFunction newDate(GroupingExpression exp) {
        return new DateFunction(exp);
    }

    /**
     * Creates a new instance of {@link DayOfMonthFunction} for the given {@link GroupingExpression}.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static DayOfMonthFunction newDayOfMonth(GroupingExpression exp) {
        return new DayOfMonthFunction(exp);
    }

    /**
     * Creates a new instance of {@link DayOfWeekFunction} for the given {@link GroupingExpression}.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static DayOfWeekFunction newDayOfWeek(GroupingExpression exp) {
        return new DayOfWeekFunction(exp);
    }

    /**
     * Creates a new instance of {@link DayOfYearFunction} for the given {@link GroupingExpression}.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static DayOfYearFunction newDayOfYear(GroupingExpression exp) {
        return new DayOfYearFunction(exp);
    }

    /**
     * Creates a new instance of {@link HourOfDayFunction} for the given {@link GroupingExpression}.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static HourOfDayFunction newHourOfDay(GroupingExpression exp) {
        return new HourOfDayFunction(exp);
    }

    /**
     * Creates a new instance of {@link MinuteOfHourFunction} for the given {@link GroupingExpression}.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static MinuteOfHourFunction newMinuteOfHour(GroupingExpression exp) {
        return new MinuteOfHourFunction(exp);
    }

    /**
     * Creates a new instance of {@link MonthOfYearFunction} for the given {@link GroupingExpression}.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static MonthOfYearFunction newMonthOfYear(GroupingExpression exp) {
        return new MonthOfYearFunction(exp);
    }

    /**
     * Creates a new instance of {@link SecondOfMinuteFunction} for the given {@link GroupingExpression}.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static SecondOfMinuteFunction newSecondOfMinute(GroupingExpression exp) {
        return new SecondOfMinuteFunction(exp);
    }

    /**
     * Creates a new instance of {@link YearFunction} for the given {@link GroupingExpression}.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     * @return The created function node.
     */
    public static YearFunction newYear(GroupingExpression exp) {
        return new YearFunction(exp);
    }
}
