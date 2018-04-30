// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author thomasg
 */
public class SumMetricTest {

    private final double delta = 0.000000001;

    @Test
    public void testSummedCountMetric() {
        MetricSet parent = new SimpleMetricSet("parent", "", "");
        SumMetric sum = new SumMetric("foo", "", "foodesc", parent);

        CountMetric v1 = new CountMetric("aa", "", "", parent);
        CountMetric v2 = new CountMetric("bb", "", "", parent);
        CountMetric v3 = new CountMetric("cc", "", "", parent);

        sum.addMetricToSum(v1);
        sum.addMetricToSum(v2);
        sum.addMetricToSum(v3);

        // Give them some values
        v1.inc(3);
        v2.inc(7);
        v3.inc(5);

        assertEquals("<foo description=\"foodesc\" count=\"15\"/>\n", sum.toXml(0, 2));
        assertEquals(15, sum.getLongValue("value"));

        v3.inc(5);

        assertEquals("<foo description=\"foodesc\" count=\"20\"/>\n", sum.toXml(0, 2));
        assertEquals(20, sum.getLongValue("value"));
    }

    @Test
    public void testSummedValueMetric() {
        MetricSet parent = new SimpleMetricSet("parent", "", "");
        SumMetric sum = new SumMetric("foo", "", "foodesc", parent);

        SummedDoubleValueMetric v1 = new SummedDoubleValueMetric("aa", "", "", parent);
        SummedDoubleValueMetric v2 = new SummedDoubleValueMetric("bb", "", "", parent);
        SummedDoubleValueMetric v3 = new SummedDoubleValueMetric("cc", "", "", parent);

        sum.addMetricToSum(v1);
        sum.addMetricToSum(v2);
        sum.addMetricToSum(v3);

        v1.addValue(3.0);
        v1.addValue(2.0);

        v2.addValue(7.0);

        v3.addValue(5.0);
        v3.addValue(10.0);

        assertEquals("<foo description=\"foodesc\" average=\"17.00\" last=\"19.00\" min=\"2.00\" max=\"10.00\" count=\"5\" total=\"85.00\"/>\n", sum.toXml(0,2));

        assertEquals(19, sum.getLongValue("value"));
        assertEquals(2, sum.getLongValue("min"));
        assertEquals(10, sum.getLongValue("max"));
    }

    @Test
    public void testAveragedValueMetric() {
        MetricSet parent = new SimpleMetricSet("parent", "", "");
        SumMetric sum = new SumMetric("foo", "", "foodesc", parent);

        AveragedDoubleValueMetric v1 = new AveragedDoubleValueMetric("aa", "", "", parent);
        AveragedDoubleValueMetric v2 = new AveragedDoubleValueMetric("bb", "", "", parent);
        AveragedDoubleValueMetric v3 = new AveragedDoubleValueMetric("cc", "", "", parent);

        sum.addMetricToSum(v1);
        sum.addMetricToSum(v2);
        sum.addMetricToSum(v3);

        v1.addValue(3.0);
        v1.addValue(2.0);

        v2.addValue(7.0);

        v3.addValue(5.0);
        v3.addValue(10.0);

        assertEquals("<foo description=\"foodesc\" average=\"5.40\" last=\"10.00\" min=\"2.00\" max=\"10.00\" count=\"5\" total=\"27.00\"/>\n", sum.toXml(0,2));

        assertEquals(5.40, sum.getDoubleValue("value"), delta);
        assertEquals(2.0, sum.getDoubleValue("min"), delta);
        assertEquals(10.0, sum.getDoubleValue("max"), delta);
    }

    @Test
    public void testMetricSet() {
        MetricSet parent = new SimpleMetricSet("parent", "", "");
        SumMetric sum = new SumMetric("foo", "", "bar", parent);

        MetricSet set1 = new SimpleMetricSet("a", "", "", parent);
        MetricSet set2 = new SimpleMetricSet("b", "", "", parent);

        SummedLongValueMetric v1 = new SummedLongValueMetric("c", "", "", set1);
        SummedLongValueMetric v2 = new SummedLongValueMetric("c", "", "", set2);
        CountMetric v3 = new CountMetric("e", "", "", set1);
        CountMetric v4 = new CountMetric("e", "", "", set2);

        sum.addMetricToSum(set1);
        sum.addMetricToSum(set2);

        // Give them some values
        v1.addValue((long)3);
        v2.addValue((long)7);
        v3.inc(2);
        v4.inc();

        // Verify XML output. Should be in register order.
        assertEquals(
                "<foo description=\"bar\">\n\n" +
                        "  <c average=\"10.00\" last=\"10\" min=\"3\" max=\"7\" count=\"2\" total=\"20\"/>\n\n" +
                        "  <e count=\"3\"/>\n\n" +
                        "</foo>\n", sum.toXml(0,2));

    }

    @Test
    public void testRemove() {
        MetricSet parent = new SimpleMetricSet("parent", "", "");
        SumMetric sum = new SumMetric("foo", "", "foodesc", parent);

        SummedDoubleValueMetric v1 = new SummedDoubleValueMetric("aa", "", "", parent);
        SummedDoubleValueMetric v2 = new SummedDoubleValueMetric("bb", "", "", parent);
        SummedDoubleValueMetric v3 = new SummedDoubleValueMetric("cc", "", "", parent);

        sum.addMetricToSum(v1);
        sum.addMetricToSum(v2);
        sum.addMetricToSum(v3);

        v1.addValue(3.0);
        v1.addValue(2.0);

        v2.addValue(7.0);

        v3.addValue(5.0);
        v3.addValue(10.0);

        sum.removeMetricFromSum(v1);

        assertEquals("<foo description=\"foodesc\" average=\"14.50\" last=\"17.00\" min=\"5.00\" max=\"10.00\" count=\"3\" total=\"43.50\"/>\n", sum.toXml(0,2));
   }

   @Test
   public void testEmpty() {
       SumMetric sum = new SumMetric("foo", "", "foodesc", null);
       assertEquals("<foo description=\"foodesc\"/>\n", sum.toXml(0,2));
   }

}
