// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

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
            throw new UnsupportedOperationException(
                    "Can not aggregate on " + GroupingOperation.getLevelDesc(level) + ".");
        }
        if (exp instanceof AvgAggregator) {
            return new AverageAggregationResult()
                    .setExpression(toExpressionNode(((AvgAggregator)exp).getExpression()));
        }
        if (exp instanceof CountAggregator) {
            return new CountAggregationResult()
                    .setExpression(new ConstantNode(new IntegerResultNode(0)));
        }
        if (exp instanceof MaxAggregator) {
            return new MaxAggregationResult()
                    .setExpression(toExpressionNode(((MaxAggregator)exp).getExpression()));
        }
        if (exp instanceof MinAggregator) {
            return new MinAggregationResult()
                    .setExpression(toExpressionNode(((MinAggregator)exp).getExpression()));
        }
        if (exp instanceof SumAggregator) {
            return new SumAggregationResult()
                    .setExpression(toExpressionNode(((SumAggregator)exp).getExpression()));
        }
        if (exp instanceof SummaryValue) {
            String summaryName = ((SummaryValue)exp).getSummaryName();
            return new HitsAggregationResult()
                    .setSummaryClass(summaryName != null ? summaryName : defaultSummaryName)
                    .setExpression(new ConstantNode(new IntegerResultNode(0)));
        }
        if (exp instanceof StandardDeviationAggregator) {
            return new StandardDeviationAggregationResult()
                    .setExpression(toExpressionNode(((StandardDeviationAggregator) exp).getExpression()));
        }
        if (exp instanceof XorAggregator) {
            return new XorAggregationResult()
                    .setExpression(toExpressionNode(((XorAggregator)exp).getExpression()));
        }
        throw new UnsupportedOperationException("Can not convert '" + exp + "' to an aggregator.");
    }

    /**
     * Converts the given ast type grouping expression to a corresponding back-end type expression.
     *
     * @param exp The expression to convert.
     * @return The corresponding back-end expression.
     * @throws UnsupportedOperationException Thrown if the given expression could not be converted.
     */
    public ExpressionNode toExpressionNode(GroupingExpression exp) {
        if (exp instanceof AddFunction) {
            return addArguments(new AddFunctionNode(), (AddFunction)exp);
        }
        if (exp instanceof AggregatorNode) {
            return new AggregationRefNode(toAggregationResult(exp));
        }
        if (exp instanceof AndFunction) {
            return addArguments(new AndFunctionNode(), (AndFunction)exp);
        }
        if (exp instanceof AttributeMapLookupValue) {
            AttributeMapLookupValue mapLookup = (AttributeMapLookupValue) exp;
            if (mapLookup.hasKeySourceAttribute()) {
                return AttributeMapLookupNode.fromKeySourceAttribute(mapLookup.getAttributeName(),
                        mapLookup.getKeyAttribute(), mapLookup.getValueAttribute(), mapLookup.getKeySourceAttribute());
            } else {
                return AttributeMapLookupNode.fromKey(mapLookup.getAttributeName(),
                        mapLookup.getKeyAttribute(), mapLookup.getValueAttribute(), mapLookup.getKey());
            }
        }
        if (exp instanceof AttributeValue) {
            return new AttributeNode(((AttributeValue)exp).getAttributeName());
        }
        if (exp instanceof AttributeFunction) {
            return new AttributeNode(((AttributeFunction)exp).getAttributeName());
        }
        if (exp instanceof CatFunction) {
            return addArguments(new CatFunctionNode(), (CatFunction)exp);
        }
        if (exp instanceof DebugWaitFunction) {
            return new DebugWaitFunctionNode(toExpressionNode(((DebugWaitFunction)exp).getArg(0)),
                                             ((DebugWaitFunction)exp).getWaitTime(),
                                             ((DebugWaitFunction)exp).getBusyWait());
        }
        if (exp instanceof DocIdNsSpecificValue) {
            return new GetDocIdNamespaceSpecificFunctionNode();
        }
        if (exp instanceof DoubleValue) {
            return new ConstantNode(new FloatResultNode(((DoubleValue)exp).getValue()));
        }
        if (exp instanceof DivFunction) {
            return addArguments(new DivideFunctionNode(), (DivFunction)exp);
        }
        if (exp instanceof FixedWidthFunction) {
            Number w = ((FixedWidthFunction)exp).getWidth();
            return new FixedWidthBucketFunctionNode(
                    w instanceof Double ? new FloatResultNode(w.doubleValue()) : new IntegerResultNode(w.longValue()),
                    toExpressionNode(((FixedWidthFunction)exp).getArg(0)));
        }
        if (exp instanceof LongValue) {
            return new ConstantNode(new IntegerResultNode(((LongValue)exp).getValue()));
        }
        if (exp instanceof MaxFunction) {
            return addArguments(new MaxFunctionNode(), (MaxFunction)exp);
        }
        if (exp instanceof Md5Function) {
            return new MD5BitFunctionNode().setNumBits(((Md5Function)exp).getNumBits())
                                           .addArg(toExpressionNode(((Md5Function)exp).getArg(0)));
        }
        if (exp instanceof UcaFunction) {
            UcaFunction uca = (UcaFunction)exp;
            return new UcaFunctionNode(toExpressionNode(uca.getArg(0)), uca.getLocale(), uca.getStrength());
        }
        if (exp instanceof MinFunction) {
            return addArguments(new MinFunctionNode(), (MinFunction)exp);
        }
        if (exp instanceof ModFunction) {
            return addArguments(new ModuloFunctionNode(), (ModFunction)exp);
        }
        if (exp instanceof MulFunction) {
            return addArguments(new MultiplyFunctionNode(), (MulFunction)exp);
        }
        if (exp instanceof NegFunction) {
            return new NegateFunctionNode(toExpressionNode(((NegFunction)exp).getArg(0)));
        }
        if (exp instanceof NormalizeSubjectFunction) {
            return new NormalizeSubjectFunctionNode(toExpressionNode(((NormalizeSubjectFunction)exp).getArg(0)));
        }
        if (exp instanceof NowFunction) {
            return new ConstantNode(new IntegerResultNode(System.currentTimeMillis() / 1000));
        }
        if (exp instanceof OrFunction) {
            return addArguments(new OrFunctionNode(), (OrFunction)exp);
        }
        if (exp instanceof PredefinedFunction) {
            return new RangeBucketPreDefFunctionNode(toBucketList((PredefinedFunction)exp),
                                                     toExpressionNode(((PredefinedFunction)exp).getArg(0)));
        }
        if (exp instanceof RelevanceValue) {
            return new RelevanceNode();
        }
        if (exp instanceof ReverseFunction) {
            return new ReverseFunctionNode(toExpressionNode(((ReverseFunction)exp).getArg(0)));
        }
        if (exp instanceof SizeFunction) {
            return new NumElemFunctionNode(toExpressionNode(((SizeFunction)exp).getArg(0)));
        }
        if (exp instanceof SortFunction) {
            return new SortFunctionNode(toExpressionNode(((SortFunction)exp).getArg(0)));
        }
        if (exp instanceof ArrayAtLookup) {
            ArrayAtLookup aal = (ArrayAtLookup) exp;
            return new ArrayAtLookupNode(aal.getAttributeName(), toExpressionNode(aal.getIndexArgument()));
        }
        if (exp instanceof InterpolatedLookup) {
            InterpolatedLookup sarl = (InterpolatedLookup) exp;
            return new InterpolatedLookupNode(sarl.getAttributeName(), toExpressionNode(sarl.getLookupArgument()));
        }
        if (exp instanceof StrCatFunction) {
            return addArguments(new StrCatFunctionNode(), (StrCatFunction)exp);
        }
        if (exp instanceof StringValue) {
            return new ConstantNode(new StringResultNode(((StringValue)exp).getValue()));
        }
        if (exp instanceof StrLenFunction) {
            return new StrLenFunctionNode(toExpressionNode(((StrLenFunction)exp).getArg(0)));
        }
        if (exp instanceof SubFunction) {
            return toSubNode((SubFunction)exp);
        }
        if (exp instanceof ToDoubleFunction) {
            return new ToFloatFunctionNode(toExpressionNode(((ToDoubleFunction)exp).getArg(0)));
        }
        if (exp instanceof ToLongFunction) {
            return new ToIntFunctionNode(toExpressionNode(((ToLongFunction)exp).getArg(0)));
        }
        if (exp instanceof ToRawFunction) {
            return new ToRawFunctionNode(toExpressionNode(((ToRawFunction)exp).getArg(0)));
        }
        if (exp instanceof ToStringFunction) {
            return new ToStringFunctionNode(toExpressionNode(((ToStringFunction)exp).getArg(0)));
        }
        if (exp instanceof DateFunction) {
            StrCatFunctionNode ret = new StrCatFunctionNode();
            GroupingExpression arg = ((DateFunction)exp).getArg(0);
            ret.addArg(new ToStringFunctionNode(toTime(arg, TimeStampFunctionNode.TimePart.Year)));
            ret.addArg(new ConstantNode(new StringResultNode("-")));
            ret.addArg(new ToStringFunctionNode(toTime(arg, TimeStampFunctionNode.TimePart.Month)));
            ret.addArg(new ConstantNode(new StringResultNode("-")));
            ret.addArg(new ToStringFunctionNode(toTime(arg, TimeStampFunctionNode.TimePart.MonthDay)));
            return ret;
        }
        if (exp instanceof MathSqrtFunction) {
            return new MathFunctionNode(toExpressionNode(((MathSqrtFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.SQRT);
        }
        if (exp instanceof MathCbrtFunction) {
            return new MathFunctionNode(toExpressionNode(((MathCbrtFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.CBRT);
        }
        if (exp instanceof MathLogFunction) {
            return new MathFunctionNode(toExpressionNode(((MathLogFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.LOG);
        }
        if (exp instanceof MathLog1pFunction) {
            return new MathFunctionNode(toExpressionNode(((MathLog1pFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.LOG1P);
        }
        if (exp instanceof MathLog10Function) {
            return new MathFunctionNode(toExpressionNode(((MathLog10Function)exp).getArg(0)),
                                        MathFunctionNode.Function.LOG10);
        }
        if (exp instanceof MathExpFunction) {
            return new MathFunctionNode(toExpressionNode(((MathExpFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.EXP);
        }
        if (exp instanceof MathPowFunction) {
            return new MathFunctionNode(toExpressionNode(((MathPowFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.POW)
                    .addArg(toExpressionNode(((MathPowFunction)exp).getArg(1)));
        }
        if (exp instanceof MathHypotFunction) {
            return new MathFunctionNode(toExpressionNode(((MathHypotFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.HYPOT)
                    .addArg(toExpressionNode(((MathHypotFunction)exp).getArg(1)));
        }
        if (exp instanceof MathSinFunction) {
            return new MathFunctionNode(toExpressionNode(((MathSinFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.SIN);
        }
        if (exp instanceof MathASinFunction) {
            return new MathFunctionNode(toExpressionNode(((MathASinFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.ASIN);
        }
        if (exp instanceof MathCosFunction) {
            return new MathFunctionNode(toExpressionNode(((MathCosFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.COS);
        }
        if (exp instanceof MathACosFunction) {
            return new MathFunctionNode(toExpressionNode(((MathACosFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.ACOS);
        }
        if (exp instanceof MathTanFunction) {
            return new MathFunctionNode(toExpressionNode(((MathTanFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.TAN);
        }
        if (exp instanceof MathATanFunction) {
            return new MathFunctionNode(toExpressionNode(((MathATanFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.ATAN);
        }
        if (exp instanceof MathSinHFunction) {
            return new MathFunctionNode(toExpressionNode(((MathSinHFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.SINH);
        }
        if (exp instanceof MathASinHFunction) {
            return new MathFunctionNode(toExpressionNode(((MathASinHFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.ASINH);
        }
        if (exp instanceof MathCosHFunction) {
            return new MathFunctionNode(toExpressionNode(((MathCosHFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.COSH);
        }
        if (exp instanceof MathACosHFunction) {
            return new MathFunctionNode(toExpressionNode(((MathACosHFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.ACOSH);
        }
        if (exp instanceof MathTanHFunction) {
            return new MathFunctionNode(toExpressionNode(((MathTanHFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.TANH);
        }
        if (exp instanceof MathATanHFunction) {
            return new MathFunctionNode(toExpressionNode(((MathATanHFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.ATANH);
        }
        if (exp instanceof MathFloorFunction) {
            return new MathFunctionNode(toExpressionNode(((MathFloorFunction)exp).getArg(0)),
                                        MathFunctionNode.Function.FLOOR);
        }
        if (exp instanceof ZCurveXFunction) {
            return new ZCurveFunctionNode(toExpressionNode(((ZCurveXFunction)exp).getArg(0)),
                                          ZCurveFunctionNode.Dimension.X);
        }
        if (exp instanceof ZCurveYFunction) {
            return new ZCurveFunctionNode(toExpressionNode(((ZCurveYFunction)exp).getArg(0)),
                                          ZCurveFunctionNode.Dimension.Y);
        }
        if (exp instanceof DayOfMonthFunction) {
            return toTime(((DayOfMonthFunction)exp).getArg(0), TimeStampFunctionNode.TimePart.MonthDay);
        }
        if (exp instanceof DayOfWeekFunction) {
            return toTime(((DayOfWeekFunction)exp).getArg(0), TimeStampFunctionNode.TimePart.WeekDay);
        }
        if (exp instanceof DayOfYearFunction) {
            return toTime(((DayOfYearFunction)exp).getArg(0), TimeStampFunctionNode.TimePart.YearDay);
        }
        if (exp instanceof HourOfDayFunction) {
            return toTime(((HourOfDayFunction)exp).getArg(0), TimeStampFunctionNode.TimePart.Hour);
        }
        if (exp instanceof MinuteOfHourFunction) {
            return toTime(((MinuteOfHourFunction)exp).getArg(0), TimeStampFunctionNode.TimePart.Minute);
        }
        if (exp instanceof MonthOfYearFunction) {
            return toTime(((MonthOfYearFunction)exp).getArg(0), TimeStampFunctionNode.TimePart.Month);
        }
        if (exp instanceof SecondOfMinuteFunction) {
            return toTime(((SecondOfMinuteFunction)exp).getArg(0), TimeStampFunctionNode.TimePart.Second);
        }
        if (exp instanceof YearFunction) {
            return toTime(((YearFunction)exp).getArg(0), TimeStampFunctionNode.TimePart.Year);
        }
        if (exp instanceof XorFunction) {
            return addArguments(new XorFunctionNode(), (XorFunction)exp);
        }
        if (exp instanceof XorBitFunction) {
            return new XorBitFunctionNode().setNumBits(((XorBitFunction)exp).getNumBits())
                                           .addArg(toExpressionNode(((XorBitFunction)exp).getArg(0)));
        }
        throw new UnsupportedOperationException("Can not convert '" + exp + "' of class " + exp.getClass().getName() +
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
        if (!(exp instanceof BucketValue)) {
            throw new UnsupportedOperationException("Can not convert '" + exp + "' to a bucket.");
        }
        ConstantValue<?> begin = ((BucketValue)exp).getFrom();
        ConstantValue<?> end = ((BucketValue)exp).getTo();
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
