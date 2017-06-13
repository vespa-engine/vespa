// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter.entity;


public abstract class MlrFunction {
    protected String functionName;
    protected String funcId; // numeric function id
    protected String featureDefFile;
    protected Epilog epilog;

    public String getFunctionName() {
        return functionName;
    }

    public String getFunctionId() {
        return funcId;
    }

    public String getFeatureDefFile() {
        return featureDefFile;
    }

    public Epilog getEpilog() {
        return epilog;
    }

    public void setEpilog(Epilog epilog) {
        this.epilog = epilog;
    }

}
