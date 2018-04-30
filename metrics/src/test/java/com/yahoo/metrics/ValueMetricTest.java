// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ValueMetricTest {

    private static final double delta = 0.000000001;

    @Test
    public void testAveragedDoubleValueMetric() {
        AveragedDoubleValueMetric m = new AveragedDoubleValueMetric("test", "tag", "description", null);
        m.addValue(100.0);
        assertEquals("count=\"1\" min=\"100.00\" max=\"100.00\" last=\"100.00\" total=\"100.00\" average=\"100.00\"", m.toString());
        m.addValue(100.0);
        assertEquals("count=\"2\" min=\"100.00\" max=\"100.00\" last=\"100.00\" total=\"200.00\" average=\"100.00\"", m.toString());
        m.addValue(40.0);
        assertEquals("count=\"3\" min=\"40.00\" max=\"100.00\" last=\"40.00\" total=\"240.00\" average=\"80.00\"", m.toString());

        AveragedDoubleValueMetric m2 = new AveragedDoubleValueMetric(m, Metric.CopyType.CLONE, null);
        assertEquals("count=\"3\" min=\"40.00\" max=\"100.00\" last=\"40.00\" total=\"240.00\" average=\"80.00\"", m.toString());
        assertEquals("count=\"3\" min=\"40.00\" max=\"100.00\" last=\"40.00\" total=\"240.00\" average=\"80.00\"", m2.toString());
        m.reset();
        assertEquals("count=\"0\" min=\"0\" max=\"0\" last=\"0\" total=\"0\" average=\"0.00\"", m.toString());
        assertEquals("count=\"3\" min=\"40.00\" max=\"100.00\" last=\"40.00\" total=\"240.00\" average=\"80.00\"", m2.toString());

        AveragedDoubleValueMetric m3 = new AveragedDoubleValueMetric("test", "tag", "description", null);
        m3.addValue(200.0);
        m3.addValue(100.0);
        m3.addValue(400.0);

        AveragedDoubleValueMetric m4 = new AveragedDoubleValueMetric("test", "tag", "description", null);
        m3.addValue(50.0);
        m3.addValue(100.0);
        m3.addValue(2000.0);

        AveragedDoubleValueMetric sum = new AveragedDoubleValueMetric(m2, Metric.CopyType.INACTIVE, null);
        m3.addToPart(sum);
        m4.addToPart(sum);
        assertEquals("count=\"9\" min=\"40.00\" max=\"2000.00\" last=\"2000.00\" total=\"3090.00\" average=\"343.33\"", sum.toString());

        AveragedDoubleValueMetric snapshot = new AveragedDoubleValueMetric(m2, Metric.CopyType.INACTIVE, null);
        m3.addToSnapshot(snapshot);
        m4.addToSnapshot(snapshot);
        assertEquals("count=\"9\" min=\"40.00\" max=\"2000.00\" last=\"2000.00\" total=\"3090.00\" average=\"343.33\"", snapshot.toString());

        assertEquals("<test description=\"description\" average=\"343.33\" last=\"2000.00\" min=\"40.00\" max=\"2000.00\" count=\"9\" total=\"3090.00\"/>\n", sum.toXml(0, 2));

        assertEquals(80.0, m2.getDoubleValue("value"), delta);
        assertEquals(80.0, m2.getDoubleValue("average"), delta);
        assertEquals(40.0, m2.getDoubleValue("min"), delta);
        assertEquals(100.0, m2.getDoubleValue("max"), delta);
        assertEquals(40.0, m2.getDoubleValue("last"), delta);
        assertEquals(3.0, m2.getDoubleValue("count"), delta);
        assertEquals(240.0, m2.getDoubleValue("total"), delta);

        assertEquals(80, m2.getLongValue("value"));
        assertEquals(80, m2.getLongValue("average"));
        assertEquals(40, m2.getLongValue("min"));
        assertEquals(100, m2.getLongValue("max"));
        assertEquals(40, m2.getLongValue("last"));
        assertEquals(3, m2.getLongValue("count"));
        assertEquals(240, m2.getLongValue("total"));
    }

    @Test
    public void testDoubleValueMetricNotUpdatedOnNaN() {
        AveragedDoubleValueMetric m = new AveragedDoubleValueMetric("test", "tag", "description", null);
        m.addValue(Double.NaN);
        assertEquals("count=\"0\" min=\"0\" max=\"0\" last=\"0\" total=\"0\" average=\"0.00\"", m.toString());
    }

    @Test
    public void testDoubleValueMetricNotUpdatedOnInfinity() {
        AveragedDoubleValueMetric m = new AveragedDoubleValueMetric("test", "tag", "description", null);
        m.addValue(Double.POSITIVE_INFINITY);
        assertEquals("count=\"0\" min=\"0\" max=\"0\" last=\"0\" total=\"0\" average=\"0.00\"", m.toString());
    }

    @Test
    public void testSummedDoubleValueMetric() {
        SummedDoubleValueMetric m = new SummedDoubleValueMetric("test", "tag", "description", null);
        m.addValue(100.0);
        assertEquals("count=\"1\" min=\"100.00\" max=\"100.00\" last=\"100.00\" total=\"100.00\" average=\"100.00\"", m.toString());
        m.addValue(100.0);
        assertEquals("count=\"2\" min=\"100.00\" max=\"100.00\" last=\"100.00\" total=\"200.00\" average=\"100.00\"", m.toString());
        m.addValue(40.0);
        assertEquals("count=\"3\" min=\"40.00\" max=\"100.00\" last=\"40.00\" total=\"240.00\" average=\"80.00\"", m.toString());

        SummedDoubleValueMetric m2 = new SummedDoubleValueMetric(m, Metric.CopyType.CLONE, null);
        assertEquals("count=\"3\" min=\"40.00\" max=\"100.00\" last=\"40.00\" total=\"240.00\" average=\"80.00\"", m.toString());
        assertEquals("count=\"3\" min=\"40.00\" max=\"100.00\" last=\"40.00\" total=\"240.00\" average=\"80.00\"", m2.toString());
        m.reset();
        assertEquals("count=\"0\" min=\"0\" max=\"0\" last=\"0\" total=\"0\" average=\"0.00\"", m.toString());
        assertEquals("count=\"3\" min=\"40.00\" max=\"100.00\" last=\"40.00\" total=\"240.00\" average=\"80.00\"", m2.toString());

        SummedDoubleValueMetric m3 = new SummedDoubleValueMetric("test", "tag", "description", null);
        m3.addValue(200.0);
        m3.addValue(100.0);
        m3.addValue(400.0);

        SummedDoubleValueMetric m4 = new SummedDoubleValueMetric("test", "tag", "description", null);
        m4.addValue(2000.0);

        SummedDoubleValueMetric sum = new SummedDoubleValueMetric(m2, Metric.CopyType.INACTIVE, null);
        m3.addToPart(sum);
        m4.addToPart(sum);
        assertEquals("count=\"7\" min=\"40.00\" max=\"2000.00\" last=\"2440.00\" total=\"16193.33\" average=\"2313.33\"", sum.toString());

        SummedDoubleValueMetric snapshot = new SummedDoubleValueMetric(m2, Metric.CopyType.INACTIVE, null);
        m3.addToSnapshot(snapshot);
        m4.addToSnapshot(snapshot);
        assertEquals("count=\"7\" min=\"40.00\" max=\"2000.00\" last=\"2000.00\" total=\"2940.00\" average=\"420.00\"", snapshot.toString());

        assertEquals("<test description=\"description\" average=\"2313.33\" last=\"2440.00\" min=\"40.00\" max=\"2000.00\" count=\"7\" total=\"16193.33\"/>\n", sum.toXml(0, 2));

        assertEquals(40.0, m2.getDoubleValue("value"), delta);
        assertEquals(80.0, m2.getDoubleValue("average"), delta);
        assertEquals(40.0, m2.getDoubleValue("min"), delta);
        assertEquals(100.0, m2.getDoubleValue("max"), delta);
        assertEquals(40.0, m2.getDoubleValue("last"), delta);
        assertEquals(3.0, m2.getDoubleValue("count"), delta);
        assertEquals(240.0, m2.getDoubleValue("total"), delta);

        assertEquals(40, m2.getLongValue("value"));
        assertEquals(80, m2.getLongValue("average"));
        assertEquals(40, m2.getLongValue("min"));
        assertEquals(100, m2.getLongValue("max"));
        assertEquals(40, m2.getLongValue("last"));
        assertEquals(3, m2.getLongValue("count"));
        assertEquals(240, m2.getLongValue("total"));
    }

    @Test
    public void testAveragedLongValueMetric() {
        AveragedLongValueMetric m = new AveragedLongValueMetric("test", "tag", "description", null);

        assertEquals(0L, m.getLongValue("max"));
        assertEquals(0L, m.getLongValue("min"));

        m.addValue((long)100);
        assertEquals("count=\"1\" min=\"100\" max=\"100\" last=\"100\" total=\"100\" average=\"100.00\"", m.toString());
        m.addValue((long)100);
        assertEquals("count=\"2\" min=\"100\" max=\"100\" last=\"100\" total=\"200\" average=\"100.00\"", m.toString());
        m.addValue((long)40);
        assertEquals("count=\"3\" min=\"40\" max=\"100\" last=\"40\" total=\"240\" average=\"80.00\"", m.toString());

        AveragedLongValueMetric m2 = new AveragedLongValueMetric(m, Metric.CopyType.CLONE, null);
        assertEquals("count=\"3\" min=\"40\" max=\"100\" last=\"40\" total=\"240\" average=\"80.00\"", m.toString());
        assertEquals("count=\"3\" min=\"40\" max=\"100\" last=\"40\" total=\"240\" average=\"80.00\"", m2.toString());
        m.reset();
        assertEquals("count=\"0\" min=\"0\" max=\"0\" last=\"0\" total=\"0\" average=\"0.00\"", m.toString());
        assertEquals("count=\"3\" min=\"40\" max=\"100\" last=\"40\" total=\"240\" average=\"80.00\"", m2.toString());

        AveragedLongValueMetric m3 = new AveragedLongValueMetric("test", "tag", "description", null);
        m3.addValue((long)200);
        m3.addValue((long)100);
        m3.addValue((long)400);

        AveragedLongValueMetric m4 = new AveragedLongValueMetric("test", "tag", "description", null);
        m3.addValue((long)50);
        m3.addValue((long)100);
        m3.addValue((long)2000);

        AveragedLongValueMetric sum = new AveragedLongValueMetric(m2, Metric.CopyType.INACTIVE, null);
        m3.addToPart(sum);
        m4.addToPart(sum);
        assertEquals("count=\"9\" min=\"40\" max=\"2000\" last=\"2000\" total=\"3090\" average=\"343.33\"", sum.toString());

        AveragedLongValueMetric snapshot = new AveragedLongValueMetric(m2, Metric.CopyType.INACTIVE, null);
        m3.addToSnapshot(snapshot);
        m4.addToSnapshot(snapshot);
        assertEquals("count=\"9\" min=\"40\" max=\"2000\" last=\"2000\" total=\"3090\" average=\"343.33\"", snapshot.toString());

        assertEquals("<test description=\"description\" average=\"343.33\" last=\"2000\" min=\"40\" max=\"2000\" count=\"9\" total=\"3090\"/>\n", sum.toXml(0, 2));

        assertEquals(80, m2.getLongValue("value"));
        assertEquals(80, m2.getLongValue("average"));
        assertEquals(40, m2.getLongValue("min"));
        assertEquals(100, m2.getLongValue("max"));
        assertEquals(40, m2.getLongValue("last"));
        assertEquals(3, m2.getLongValue("count"));
        assertEquals(240, m2.getLongValue("total"));
    }

}
