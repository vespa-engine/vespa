// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;

public class FuncNormalize implements Function {

    public static int INV_NONE = 0;
    public static int INV_INVERSION = 1;
    public static int INV_NEGATION = 2;
	
    private int invertMethod = INV_NONE;
    private String invertedFrom;

    /*
     * The following parameters are type String to preserve precision.
     */
    private boolean pGenNormalize;
    protected String mean0;
    protected String mean1;
    protected String mean2;
    protected String mean3;
    protected String sd0;
    protected String sd1;
    protected String sd2;
    protected String sd3;
    protected String a0;
    protected String a1;
    protected String a2;
    protected String a3;
    protected String b0;
    protected String b1;
    protected String b2;
    protected String b3;

    public FuncNormalize() {}

    public int getInvertMethod() {
        return invertMethod;
    }

    public void setInvertMethod(int inv) {
        this.invertMethod = inv;
    }

    public String getInvertedFrom() {
        return invertedFrom;
    }

    public void setInvertedFrom(String invertedFrom) {
    	this.invertedFrom = invertedFrom;
    }

    public boolean isGenNormalize() {
        return pGenNormalize;
    }

    public void setpDoNormalize(boolean doNormalize) {
        this.pGenNormalize = doNormalize;
    }

    public String getMean0() {
        return mean0;
    }

    public void setMean0(String mean0) {
        this.mean0 = mean0;
    }

    public String getMean1() {
        return mean1;
    }

    public void setMean1(String mean1) {
        this.mean1 = mean1;
    }

    public String getMean2() {
        return mean2;
    }

    public void setMean2(String mean2) {
        this.mean2 = mean2;
    }

    public String getMean3() {
        return mean3;
    }

    public void setMean3(String mean3) {
        this.mean3 = mean3;
    }

    public String getSd0() {
        return sd0;
    }

    public void setSd0(String sd0) {
        this.sd0 = sd0;
    }

    public String getSd1() {
        return sd1;
    }

    public void setSd1(String sd1) {
        this.sd1 = sd1;
    }

    public String getSd2() {
        return sd2;
    }

    public void setSd2(String sd2) {
        this.sd2 = sd2;
    }

    public String getSd3() {
        return sd3;
    }

    public void setSd3(String sd3) {
        this.sd3 = sd3;
    }

    public String getA0() {
        return a0;
    }

    public void setA0(String a0) {
        this.a0 = a0;
    }

    public String getA1() {
        return a1;
    }

    public void setA1(String a1) {
        this.a1 = a1;
    }

    public String getA2() {
        return a2;
    }

    public void setA2(String a2) {
        this.a2 = a2;
    }

    public String getA3() {
        return a3;
    }

    public void setA3(String a3) {
        this.a3 = a3;
    }

    public String getB0() {
        return b0;
    }

    public void setB0(String b0) {
        this.b0 = b0;
    }

    public String getB1() {
        return b1;
    }

    public void setB1(String b1) {
        this.b1 = b1;
    }

    public String getB2() {
        return b2;
    }

    public void setB2(String b2) {
        this.b2 = b2;
    }

    public String getB3() {
        return b3;
    }

    public void setB3(String b3) {
        this.b3 = b3;
    }

    public boolean validateParams() {
        if (mean0 != null) {
            if (mean1 == null || mean2 == null || mean3 == null ||
                    sd0 == null || sd1 == null || sd2 == null || sd3 == null ||
                    a0 == null || a1 == null || a2 == null || a3 == null ||
                    b0 == null || b1 == null || b2 == null || b3 == null) {
                return false;
            } else {
                pGenNormalize = true;
            }
        } else { // mean0 == null
            if (mean1 != null || mean2 != null || mean3 != null ||
                    sd0 != null || sd1 != null || sd2 != null || sd3 != null ||
                    a0 != null || a1 != null || a2 != null || a3 != null ||
                    b0 != null || b1 != null || b2 != null || b3 != null) {
                return false;
            } else {
                pGenNormalize = false;
            }
        }

        return true;
    }
}
