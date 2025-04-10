// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.grouping.request.AddFunction;
import com.yahoo.search.grouping.request.AggregatorNode;
import com.yahoo.search.grouping.request.AndFunction;
import com.yahoo.search.grouping.request.ArrayAtLookup;
import com.yahoo.search.grouping.request.AttributeFunction;
import com.yahoo.search.grouping.request.AttributeMapLookupValue;
import com.yahoo.search.grouping.request.AttributeValue;
import com.yahoo.search.grouping.request.AvgAggregator;
import com.yahoo.search.grouping.request.BucketValue;
import com.yahoo.search.grouping.request.CatFunction;
import com.yahoo.search.grouping.request.ConstantValue;
import com.yahoo.search.grouping.request.CountAggregator;
import com.yahoo.search.grouping.request.DateFunction;
import com.yahoo.search.grouping.request.DayOfMonthFunction;
import com.yahoo.search.grouping.request.DayOfWeekFunction;
import com.yahoo.search.grouping.request.DayOfYearFunction;
import com.yahoo.search.grouping.request.DebugWaitFunction;
import com.yahoo.search.grouping.request.DivFunction;
import com.yahoo.search.grouping.request.DocIdNsSpecificValue;
import com.yahoo.search.grouping.request.DoubleValue;
import com.yahoo.search.grouping.request.FixedWidthFunction;
import com.yahoo.search.grouping.request.GroupingExpression;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.request.HourOfDayFunction;
import com.yahoo.search.grouping.request.InfiniteValue;
import com.yahoo.search.grouping.request.InterpolatedLookup;
import com.yahoo.search.grouping.request.LongValue;
import com.yahoo.search.grouping.request.MathACosFunction;
import com.yahoo.search.grouping.request.MathACosHFunction;
import com.yahoo.search.grouping.request.MathASinFunction;
import com.yahoo.search.grouping.request.MathASinHFunction;
import com.yahoo.search.grouping.request.MathATanFunction;
import com.yahoo.search.grouping.request.MathATanHFunction;
import com.yahoo.search.grouping.request.MathCbrtFunction;
import com.yahoo.search.grouping.request.MathCosFunction;
import com.yahoo.search.grouping.request.MathCosHFunction;
import com.yahoo.search.grouping.request.MathExpFunction;
import com.yahoo.search.grouping.request.MathFloorFunction;
import com.yahoo.search.grouping.request.MathHypotFunction;
import com.yahoo.search.grouping.request.MathLog10Function;
import com.yahoo.search.grouping.request.MathLog1pFunction;
import com.yahoo.search.grouping.request.MathLogFunction;
import com.yahoo.search.grouping.request.MathPowFunction;
import com.yahoo.search.grouping.request.MathSinFunction;
import com.yahoo.search.grouping.request.MathSinHFunction;
import com.yahoo.search.grouping.request.MathSqrtFunction;
import com.yahoo.search.grouping.request.MathTanFunction;
import com.yahoo.search.grouping.request.MathTanHFunction;
import com.yahoo.search.grouping.request.MaxAggregator;
import com.yahoo.search.grouping.request.MaxFunction;
import com.yahoo.search.grouping.request.Md5Function;
import com.yahoo.search.grouping.request.MinAggregator;
import com.yahoo.search.grouping.request.MinFunction;
import com.yahoo.search.grouping.request.MinuteOfHourFunction;
import com.yahoo.search.grouping.request.ModFunction;
import com.yahoo.search.grouping.request.MonthOfYearFunction;
import com.yahoo.search.grouping.request.MulFunction;
import com.yahoo.search.grouping.request.NegFunction;
import com.yahoo.search.grouping.request.NormalizeSubjectFunction;
import com.yahoo.search.grouping.request.NowFunction;
import com.yahoo.search.grouping.request.OrFunction;
import com.yahoo.search.grouping.request.PredefinedFunction;
import com.yahoo.search.grouping.request.RawValue;
import com.yahoo.search.grouping.request.RelevanceValue;
import com.yahoo.search.grouping.request.ReverseFunction;
import com.yahoo.search.grouping.request.SecondOfMinuteFunction;
import com.yahoo.search.grouping.request.SizeFunction;
import com.yahoo.search.grouping.request.SortFunction;
import com.yahoo.search.grouping.request.StandardDeviationAggregator;
import com.yahoo.search.grouping.request.StrCatFunction;
import com.yahoo.search.grouping.request.StrLenFunction;
import com.yahoo.search.grouping.request.StringValue;
import com.yahoo.search.grouping.request.SubFunction;
import com.yahoo.search.grouping.request.SumAggregator;
import com.yahoo.search.grouping.request.SummaryValue;
import com.yahoo.search.grouping.request.ToDoubleFunction;
import com.yahoo.search.grouping.request.ToLongFunction;
import com.yahoo.search.grouping.request.ToRawFunction;
import com.yahoo.search.grouping.request.ToStringFunction;
import com.yahoo.search.grouping.request.UcaFunction;
import com.yahoo.search.grouping.request.XorAggregator;
import com.yahoo.search.grouping.request.XorBitFunction;
import com.yahoo.search.grouping.request.XorFunction;
import com.yahoo.search.grouping.request.YearFunction;
import com.yahoo.search.grouping.request.ZCurveXFunction;
import com.yahoo.search.grouping.request.ZCurveYFunction;
import com.yahoo.searchlib.aggregation.AggregationResult;
import com.yahoo.searchlib.aggregation.AverageAggregationResult;
import com.yahoo.searchlib.aggregation.CountAggregationResult;
import com.yahoo.searchlib.aggregation.ExpressionCountAggregationResult;
import com.yahoo.searchlib.aggregation.HitsAggregationResult;
import com.yahoo.searchlib.aggregation.MaxAggregationResult;
import com.yahoo.searchlib.aggregation.MinAggregationResult;
import com.yahoo.searchlib.aggregation.StandardDeviationAggregationResult;
import com.yahoo.searchlib.aggregation.SumAggregationResult;
import com.yahoo.searchlib.aggregation.XorAggregationResult;
import com.yahoo.searchlib.expression.AddFunctionNode;
import com.yahoo.searchlib.expression.AggregationRefNode;
import com.yahoo.searchlib.expression.AndFunctionNode;
import com.yahoo.searchlib.expression.ArrayAtLookupNode;
import com.yahoo.searchlib.expression.AttributeMapLookupNode;
import com.yahoo.searchlib.expression.AttributeNode;
import com.yahoo.searchlib.expression.BucketResultNode;
import com.yahoo.searchlib.expression.CatFunctionNode;
import com.yahoo.searchlib.expression.ConstantNode;
import com.yahoo.searchlib.expression.DebugWaitFunctionNode;
import com.yahoo.searchlib.expression.DivideFunctionNode;
import com.yahoo.searchlib.expression.ExpressionNode;
import com.yahoo.searchlib.expression.FixedWidthBucketFunctionNode;
import com.yahoo.searchlib.expression.FloatBucketResultNode;
import com.yahoo.searchlib.expression.FloatBucketResultNodeVector;
import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.GetDocIdNamespaceSpecificFunctionNode;
import com.yahoo.searchlib.expression.IntegerBucketResultNode;
import com.yahoo.searchlib.expression.IntegerBucketResultNodeVector;
import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.InterpolatedLookupNode;
import com.yahoo.searchlib.expression.MD5BitFunctionNode;
import com.yahoo.searchlib.expression.MathFunctionNode;
import com.yahoo.searchlib.expression.MaxFunctionNode;
import com.yahoo.searchlib.expression.MinFunctionNode;
import com.yahoo.searchlib.expression.ModuloFunctionNode;
import com.yahoo.searchlib.expression.MultiArgFunctionNode;
import com.yahoo.searchlib.expression.MultiplyFunctionNode;
import com.yahoo.searchlib.expression.NegateFunctionNode;
import com.yahoo.searchlib.expression.NormalizeSubjectFunctionNode;
import com.yahoo.searchlib.expression.NumElemFunctionNode;
import com.yahoo.searchlib.expression.OrFunctionNode;
import com.yahoo.searchlib.expression.RangeBucketPreDefFunctionNode;
import com.yahoo.searchlib.expression.RawBucketResultNode;
import com.yahoo.searchlib.expression.RawBucketResultNodeVector;
import com.yahoo.searchlib.expression.RawResultNode;
import com.yahoo.searchlib.expression.RelevanceNode;
import com.yahoo.searchlib.expression.ResultNodeVector;
import com.yahoo.searchlib.expression.ReverseFunctionNode;
import com.yahoo.searchlib.expression.SortFunctionNode;
import com.yahoo.searchlib.expression.StrCatFunctionNode;
import com.yahoo.searchlib.expression.StrLenFunctionNode;
import com.yahoo.searchlib.expression.StringBucketResultNode;
import com.yahoo.searchlib.expression.StringBucketResultNodeVector;
import com.yahoo.searchlib.expression.StringResultNode;
import com.yahoo.searchlib.expression.TimeStampFunctionNode;
import com.yahoo.searchlib.expression.ToFloatFunctionNode;
import com.yahoo.searchlib.expression.ToIntFunctionNode;
import com.yahoo.searchlib.expression.ToRawFunctionNode;
import com.yahoo.searchlib.expression.ToStringFunctionNode;
import com.yahoo.searchlib.expression.UcaFunctionNode;
import com.yahoo.searchlib.expression.XorBitFunctionNode;
import com.yahoo.searchlib.expression.XorFunctionNode;
import com.yahoo.searchlib.expression.ZCurveFunctionNode;

/**
 * This is a helper class for {@link RequestBuilder} that offloads the code to convert {@link GroupingExpression} type
 * objects to back-end specific expressions. This is a straightforward one-to-one conversion.
 *
 * @author Simon Thoresen Hult
 */
class ExpressionConverter {

    public static final String DEFAULT_SUMMARY_NAME = "";
    public static final int DEFAULT_TIME_OFFSET = 0;
    private String defaultSummaryName = DEFAULT_SUMMARY_NAME;
    private int timeOffset = DEFAULT_TIME_OFFSET;

    /**
     * Sets the summary name to use when converting {@link SummaryValue} that was created without an explicit name.
     *
     * @param summaryName The default summary name to use.
     * @return This, to allow chaining.
     */
    public ExpressionConverter setDefaultSummaryName(String summaryName) {
        defaultSummaryName = summaryName;
        return this;
    }

    /**
     * Sets an offset to use for all time-based grouping expressions.
     *
     * @param millis The offset in milliseconds.
     * @return This, to allow chaining.
     */
    public ExpressionConverter setTimeOffset(int millis) {
        this.timeOffset = millis / 1000;
        return this;
    }

    /**
     * Converts the given ast type grouping expression to a corresponding back-end type aggregation result.
     *
     * @param exp The expression to convert.
     * @return The corresponding back-end result.
     * @throws UnsupportedOperationException Thrown if the given expression could not be converted.
     */
    public AggregationResult toAggregationResult(GroupingExpression exp) {
        int level = exp.getLevel();
        // Is aggregating on list of groups?
        if (level > 1) {
            /*
            * The below aggregator operates on lists of groups in the query language world.
            * Internally, it operates on hits (by evaluating the group-by expression for each hit).
            * The group-by expression is passed to the aggregator by RequestBuilder.
            */
            if (exp instanceof CountAggregator) {
                return new ExpressionCountAggregationResult();
            }
            throw new IllegalInputException(
                    "Can not aggregate on " + GroupingOperation.getLevelDesc(level) + ".");
        }
        if (exp instanceof AvgAggregator avgAggregator) {
            return new AverageAggregationResult()
                    .setExpression(toExpressionNode(avgAggregator.getExpression()));
        }
        if (exp instanceof CountAggregator) {
            return new CountAggregationResult()
                    .setExpression(new ConstantNode(new IntegerResultNode(0)));
        }
        if (exp instanceof MaxAggregator aggregator) {
            return new MaxAggregationResult()
                    .setExpression(toExpressionNode(aggregator.getExpression()));
        }
        if (exp instanceof MinAggregator aggregator) {
            return new MinAggregationResult()
                    .setExpression(toExpressionNode(aggregator.getExpression()));
        }
        if (exp instanceof SumAggregator aggregator) {
            return new SumAggregationResult()
                    .setExpression(toExpressionNode(aggregator.getExpression()));
        }
        if (exp instanceof SummaryValue summaryValue) {
            String summaryName = summaryValue.getSummaryName();
            return new HitsAggregationResult()
                    .setSummaryClass(summaryName != null ? summaryName : defaultSummaryName)
                    .setExpression(new ConstantNode(new IntegerResultNode(0)));
        }
        if (exp instanceof StandardDeviationAggregator aggregator) {
            return new StandardDeviationAggregationResult()
                    .setExpression(toExpressionNode(aggregator.getExpression()));
        }
        if (exp instanceof XorAggregator aggregator) {
            return new XorAggregationResult()
                    .setExpression(toExpressionNode(aggregator.getExpression()));
        }
        throw new IllegalInputException("Can not convert '" + exp + "' to an aggregator.");
    }

    /**
     * Converts the given ast type grouping expression to a corresponding back-end type expression.
     *
     * @param exp The expression to convert.
     * @return The corresponding back-end expression.
     * @throws IllegalInputException Thrown if the given expression could not be converted.
     */
    public ExpressionNode toExpressionNode(GroupingExpression exp) {
        if (exp instanceof AddFunction addFunction) {
            return addArguments(new AddFunctionNode(), addFunction);
        }
        if (exp instanceof AggregatorNode) {
            return new AggregationRefNode(toAggregationResult(exp));
        }
        if (exp instanceof AndFunction andFunction) {
            return addArguments(new AndFunctionNode(), andFunction);
        }
        if (exp instanceof AttributeMapLookupValue mapLookup) {
            if (mapLookup.hasKeySourceAttribute()) {
                return AttributeMapLookupNode.fromKeySourceAttribute(mapLookup.getAttributeName(),
                        mapLookup.getKeyAttribute(), mapLookup.getValueAttribute(), mapLookup.getKeySourceAttribute());
            } else {
                return AttributeMapLookupNode.fromKey(mapLookup.getAttributeName(),
                        mapLookup.getKeyAttribute(), mapLookup.getValueAttribute(), mapLookup.getKey());
            }
        }
        if (exp instanceof AttributeValue value) {
            return new AttributeNode(value.getAttributeName());
        }
        if (exp instanceof AttributeFunction function) {
            return new AttributeNode(function.getAttributeName());
        }
        if (exp instanceof CatFunction function) {
            return addArguments(new CatFunctionNode(), function);
        }
        if (exp instanceof DebugWaitFunction waitFunction) {
            return new DebugWaitFunctionNode(toExpressionNode(waitFunction.getArg(0)),
                                             waitFunction.getWaitTime(),
                                             waitFunction.getBusyWait());
        }
        if (exp instanceof DocIdNsSpecificValue) {
            return new GetDocIdNamespaceSpecificFunctionNode();
        }
        if (exp instanceof DoubleValue value) {
            return new ConstantNode(new FloatResultNode(value.getValue()));
        }
        if (exp instanceof DivFunction divFunction) {
            return addArguments(new DivideFunctionNode(), divFunction);
        }
        if (exp instanceof FixedWidthFunction fixedWidthFunction) {
            Number w = fixedWidthFunction.getWidth();
            return new FixedWidthBucketFunctionNode(
                    w instanceof Double ? new FloatResultNode(w.doubleValue()) : new IntegerResultNode(w.longValue()),
                    toExpressionNode(fixedWidthFunction.getArg(0)));
        }
        if (exp instanceof LongValue value) {
            return new ConstantNode(new IntegerResultNode(value.getValue()));
        }
        if (exp instanceof MaxFunction maxFunction) {
            return addArguments(new MaxFunctionNode(), maxFunction);
        }
        if (exp instanceof Md5Function md5Function) {
            return new MD5BitFunctionNode().setNumBits(md5Function.getNumBits())
                                           .addArg(toExpressionNode(md5Function.getArg(0)));
        }
        if (exp instanceof UcaFunction uca) {
            return new UcaFunctionNode(toExpressionNode(uca.getArg(0)), uca.getLocale(), uca.getStrength());
        }
        if (exp instanceof MinFunction minFunction) {
            return addArguments(new MinFunctionNode(), minFunction);
        }
        if (exp instanceof ModFunction modFunction) {
            return addArguments(new ModuloFunctionNode(), modFunction);
        }
        if (exp instanceof MulFunction mulFunction) {
            return addArguments(new MultiplyFunctionNode(), mulFunction);
        }
        if (exp instanceof NegFunction negFunction) {
            return new NegateFunctionNode(toExpressionNode(negFunction.getArg(0)));
        }
        if (exp instanceof NormalizeSubjectFunction normalizeSubjectFunction) {
            return new NormalizeSubjectFunctionNode(toExpressionNode(normalizeSubjectFunction.getArg(0)));
        }
        if (exp instanceof NowFunction) {
            return new ConstantNode(new IntegerResultNode(System.currentTimeMillis() / 1000));
        }
        if (exp instanceof OrFunction orFunction) {
            return addArguments(new OrFunctionNode(), orFunction);
        }
        if (exp instanceof PredefinedFunction predefinedFunction) {
            return new RangeBucketPreDefFunctionNode(toBucketList(predefinedFunction),
                                                     toExpressionNode(predefinedFunction.getArg(0)));
        }
        if (exp instanceof RelevanceValue) {
            return new RelevanceNode();
        }
        if (exp instanceof ReverseFunction reverseFunction) {
            return new ReverseFunctionNode(toExpressionNode(reverseFunction.getArg(0)));
        }
        if (exp instanceof SizeFunction sizeFunction) {
            return new NumElemFunctionNode(toExpressionNode(sizeFunction.getArg(0)));
        }
        if (exp instanceof SortFunction sortFunction) {
            return new SortFunctionNode(toExpressionNode(sortFunction.getArg(0)));
        }
        if (exp instanceof ArrayAtLookup lookup) {
            return new ArrayAtLookupNode(lookup.getAttributeName(), toExpressionNode(lookup.getIndexArgument()));
        }
        if (exp instanceof InterpolatedLookup lookup) {
            return new InterpolatedLookupNode(lookup.getAttributeName(), toExpressionNode(lookup.getLookupArgument()));
        }
        if (exp instanceof StrCatFunction strCatFunction) {
            return addArguments(new StrCatFunctionNode(), strCatFunction);
        }
        if (exp instanceof StringValue value) {
            return new ConstantNode(new StringResultNode(value.getValue()));
        }
        if (exp instanceof StrLenFunction strLenFunction) {
            return new StrLenFunctionNode(toExpressionNode(strLenFunction.getArg(0)));
        }
        if (exp instanceof SubFunction subFunction) {
            return toSubNode(subFunction);
        }
        if (exp instanceof ToDoubleFunction toDoubleFunction) {
            return new ToFloatFunctionNode(toExpressionNode(toDoubleFunction.getArg(0)));
        }
        if (exp instanceof ToLongFunction toLongFunction) {
            return new ToIntFunctionNode(toExpressionNode(toLongFunction.getArg(0)));
        }
        if (exp instanceof ToRawFunction toRawFunction) {
            return new ToRawFunctionNode(toExpressionNode(toRawFunction.getArg(0)));
        }
        if (exp instanceof ToStringFunction toStringFunction) {
            return new ToStringFunctionNode(toExpressionNode(toStringFunction.getArg(0)));
        }
        if (exp instanceof DateFunction dateFunction) {
            StrCatFunctionNode ret = new StrCatFunctionNode();
            GroupingExpression arg = dateFunction.getArg(0);
            ret.addArg(new ToStringFunctionNode(toTime(arg, TimeStampFunctionNode.TimePart.Year)));
            ret.addArg(new ConstantNode(new StringResultNode("-")));
            ret.addArg(new ToStringFunctionNode(toTime(arg, TimeStampFunctionNode.TimePart.Month)));
            ret.addArg(new ConstantNode(new StringResultNode("-")));
            ret.addArg(new ToStringFunctionNode(toTime(arg, TimeStampFunctionNode.TimePart.MonthDay)));
            return ret;
        }
        if (exp instanceof MathSqrtFunction mathSqrtFunction) {
            return new MathFunctionNode(toExpressionNode(mathSqrtFunction.getArg(0)),
                                        MathFunctionNode.Function.SQRT);
        }
        if (exp instanceof MathCbrtFunction mathCbrtFunction) {
            return new MathFunctionNode(toExpressionNode(mathCbrtFunction.getArg(0)),
                                        MathFunctionNode.Function.CBRT);
        }
        if (exp instanceof MathLogFunction mathLogFunction) {
            return new MathFunctionNode(toExpressionNode(mathLogFunction.getArg(0)),
                                        MathFunctionNode.Function.LOG);
        }
        if (exp instanceof MathLog1pFunction mathLog1pFunction) {
            return new MathFunctionNode(toExpressionNode(mathLog1pFunction.getArg(0)),
                                        MathFunctionNode.Function.LOG1P);
        }
        if (exp instanceof MathLog10Function mathLog10Function) {
            return new MathFunctionNode(toExpressionNode(mathLog10Function.getArg(0)),
                                        MathFunctionNode.Function.LOG10);
        }
        if (exp instanceof MathExpFunction mathExpFunction) {
            return new MathFunctionNode(toExpressionNode(mathExpFunction.getArg(0)),
                                        MathFunctionNode.Function.EXP);
        }
        if (exp instanceof MathPowFunction mathPowFunction) {
            return new MathFunctionNode(toExpressionNode(mathPowFunction.getArg(0)),
                                        MathFunctionNode.Function.POW)
                    .addArg(toExpressionNode(mathPowFunction.getArg(1)));
        }
        if (exp instanceof MathHypotFunction mathHypotFunction) {
            return new MathFunctionNode(toExpressionNode(mathHypotFunction.getArg(0)),
                                        MathFunctionNode.Function.HYPOT)
                    .addArg(toExpressionNode(mathHypotFunction.getArg(1)));
        }
        if (exp instanceof MathSinFunction mathSinFunction) {
            return new MathFunctionNode(toExpressionNode(mathSinFunction.getArg(0)),
                                        MathFunctionNode.Function.SIN);
        }
        if (exp instanceof MathASinFunction mathASinFunction) {
            return new MathFunctionNode(toExpressionNode(mathASinFunction.getArg(0)),
                                        MathFunctionNode.Function.ASIN);
        }
        if (exp instanceof MathCosFunction mathCosFunction) {
            return new MathFunctionNode(toExpressionNode(mathCosFunction.getArg(0)),
                                        MathFunctionNode.Function.COS);
        }
        if (exp instanceof MathACosFunction mathACosFunction) {
            return new MathFunctionNode(toExpressionNode(mathACosFunction.getArg(0)),
                                        MathFunctionNode.Function.ACOS);
        }
        if (exp instanceof MathTanFunction mathTanFunction) {
            return new MathFunctionNode(toExpressionNode(mathTanFunction.getArg(0)),
                                        MathFunctionNode.Function.TAN);
        }
        if (exp instanceof MathATanFunction mathATanFunction) {
            return new MathFunctionNode(toExpressionNode(mathATanFunction.getArg(0)),
                                        MathFunctionNode.Function.ATAN);
        }
        if (exp instanceof MathSinHFunction mathSinHFunction) {
            return new MathFunctionNode(toExpressionNode(mathSinHFunction.getArg(0)),
                                        MathFunctionNode.Function.SINH);
        }
        if (exp instanceof MathASinHFunction mathASinHFunction) {
            return new MathFunctionNode(toExpressionNode(mathASinHFunction.getArg(0)),
                                        MathFunctionNode.Function.ASINH);
        }
        if (exp instanceof MathCosHFunction mathCosHFunction) {
            return new MathFunctionNode(toExpressionNode(mathCosHFunction.getArg(0)),
                                        MathFunctionNode.Function.COSH);
        }
        if (exp instanceof MathACosHFunction mathACosHFunction) {
            return new MathFunctionNode(toExpressionNode(mathACosHFunction.getArg(0)),
                                        MathFunctionNode.Function.ACOSH);
        }
        if (exp instanceof MathTanHFunction mathTanHFunction) {
            return new MathFunctionNode(toExpressionNode(mathTanHFunction.getArg(0)),
                                        MathFunctionNode.Function.TANH);
        }
        if (exp instanceof MathATanHFunction mathATanHFunction) {
            return new MathFunctionNode(toExpressionNode(mathATanHFunction.getArg(0)),
                                        MathFunctionNode.Function.ATANH);
        }
        if (exp instanceof MathFloorFunction mathFloorFunction) {
            return new MathFunctionNode(toExpressionNode(mathFloorFunction.getArg(0)),
                                        MathFunctionNode.Function.FLOOR);
        }
        if (exp instanceof ZCurveXFunction zCurveXFunction) {
            return new ZCurveFunctionNode(toExpressionNode(zCurveXFunction.getArg(0)),
                                          ZCurveFunctionNode.Dimension.X);
        }
        if (exp instanceof ZCurveYFunction zCurveYFunction) {
            return new ZCurveFunctionNode(toExpressionNode(zCurveYFunction.getArg(0)),
                                          ZCurveFunctionNode.Dimension.Y);
        }
        if (exp instanceof DayOfMonthFunction dayOfMonthFunction) {
            return toTime(dayOfMonthFunction.getArg(0), TimeStampFunctionNode.TimePart.MonthDay);
        }
        if (exp instanceof DayOfWeekFunction dayOfWeekFunction) {
            return toTime(dayOfWeekFunction.getArg(0), TimeStampFunctionNode.TimePart.WeekDay);
        }
        if (exp instanceof DayOfYearFunction dayOfYearFunction) {
            return toTime(dayOfYearFunction.getArg(0), TimeStampFunctionNode.TimePart.YearDay);
        }
        if (exp instanceof HourOfDayFunction hourOfDayFunction) {
            return toTime(hourOfDayFunction.getArg(0), TimeStampFunctionNode.TimePart.Hour);
        }
        if (exp instanceof MinuteOfHourFunction minuteOfHourFunction) {
            return toTime(minuteOfHourFunction.getArg(0), TimeStampFunctionNode.TimePart.Minute);
        }
        if (exp instanceof MonthOfYearFunction monthOfYearFunction) {
            return toTime(monthOfYearFunction.getArg(0), TimeStampFunctionNode.TimePart.Month);
        }
        if (exp instanceof SecondOfMinuteFunction secondOfMinuteFunction) {
            return toTime(secondOfMinuteFunction.getArg(0), TimeStampFunctionNode.TimePart.Second);
        }
        if (exp instanceof YearFunction yearFunction) {
            return toTime(yearFunction.getArg(0), TimeStampFunctionNode.TimePart.Year);
        }
        if (exp instanceof XorFunction xorFunction) {
            return addArguments(new XorFunctionNode(), xorFunction);
        }
        if (exp instanceof XorBitFunction xorBitFunction) {
            return new XorBitFunctionNode().setNumBits(xorBitFunction.getNumBits())
                                           .addArg(toExpressionNode(xorBitFunction.getArg(0)));
        }
        throw new IllegalInputException("Can not convert '" + exp + "' of class " + exp.getClass().getName() +
                                        " to an expression.");
    }

    private TimeStampFunctionNode toTime(GroupingExpression arg, TimeStampFunctionNode.TimePart timePart) {
        if (timeOffset == 0) {
            return new TimeStampFunctionNode(toExpressionNode(arg), timePart, true);
        }
        AddFunctionNode exp = new AddFunctionNode();
        exp.addArg(toExpressionNode(arg));
        exp.addArg(new ConstantNode(new IntegerResultNode(timeOffset)));
        return new TimeStampFunctionNode(exp, timePart, true);
    }

    private MultiArgFunctionNode addArguments(MultiArgFunctionNode ret, Iterable<GroupingExpression> lst) {
        for (GroupingExpression exp : lst) {
            ret.addArg(toExpressionNode(exp));
        }
        return ret;
    }

    private MultiArgFunctionNode toSubNode(Iterable<GroupingExpression> lst) {
        MultiArgFunctionNode ret = new AddFunctionNode();
        int i = 0;
        for (GroupingExpression exp : lst) {
            ExpressionNode node = toExpressionNode(exp);
            if (++i > 1) {
                node = new NegateFunctionNode(node);
            }
            ret.addArg(node);
        }
        return ret;
    }

    private ResultNodeVector toBucketList(PredefinedFunction fnc) {
        ResultNodeVector ret = null;
        for (int i = 0, len = fnc.getNumBuckets(); i < len; ++i) {
            BucketResultNode bucket = toBucket(fnc.getBucket(i));
            if (ret == null) {
                if (bucket instanceof FloatBucketResultNode) {
                    ret = new FloatBucketResultNodeVector();
                } else if (bucket instanceof IntegerBucketResultNode) {
                    ret = new IntegerBucketResultNodeVector();
                } else if (bucket instanceof RawBucketResultNode) {
                    ret = new RawBucketResultNodeVector();
                } else {
                    ret = new StringBucketResultNodeVector();
                }
            }
            ret.add(bucket);
        }
        return ret;
    }

    private BucketResultNode toBucket(GroupingExpression exp) {
        if (!(exp instanceof BucketValue bucketValue)) {
            throw new IllegalInputException("Can not convert '" + exp + "' to a bucket.");
        }
        ConstantValue<?> begin = bucketValue.getFrom();
        ConstantValue<?> end = bucketValue.getTo();
        if (begin instanceof DoubleValue || end instanceof DoubleValue) {
            return new FloatBucketResultNode(
                    begin instanceof InfiniteValue ? FloatResultNode.getNegativeInfinity().getFloat()
                                                   : Double.valueOf(begin.toString()),
                    end instanceof InfiniteValue ? FloatResultNode.getPositiveInfinity().getFloat()
                                                 : Double.valueOf(end.toString()));
        } else if (begin instanceof LongValue || end instanceof LongValue) {
            return new IntegerBucketResultNode(
                    begin instanceof InfiniteValue ? IntegerResultNode.getNegativeInfinity().getInteger()
                                                   : Long.valueOf(begin.toString()),
                    end instanceof InfiniteValue ? IntegerResultNode.getPositiveInfinity().getInteger()
                                                 : Long.valueOf(end.toString()));
        } else if (begin instanceof StringValue || end instanceof StringValue) {
            return new StringBucketResultNode(
                    begin instanceof InfiniteValue ? StringResultNode.getNegativeInfinity()
                                                   : new StringResultNode((String)begin.getValue()),
                    end instanceof InfiniteValue ? StringResultNode.getPositiveInfinity()
                                                 : new StringResultNode((String)end.getValue()));
        } else {
            return new RawBucketResultNode(
                    begin instanceof InfiniteValue ? RawResultNode.getNegativeInfinity()
                                                   : new RawResultNode(((RawValue)begin).getValue().getBytes()),
                    end instanceof InfiniteValue ? RawResultNode.getPositiveInfinity()
                                                 : new RawResultNode(((RawValue)end).getValue().getBytes()));
        }
    }
}
