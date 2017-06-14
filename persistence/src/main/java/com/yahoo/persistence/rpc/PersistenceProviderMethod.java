// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.rpc;

import com.yahoo.jrt.*;

/**
 * Class to represent a JRT method used by PersistenceProviderHandler.
 */
public class PersistenceProviderMethod extends Method {
    int nextReturnDesc = 0;
    int nextParamDesc;

    PersistenceProviderMethod returnDesc(String code, String text) {
        returnDesc(nextReturnDesc, code, text);
        ++nextReturnDesc;
        return this;
    }

    PersistenceProviderMethod paramDesc(String code, String text) {
        paramDesc(nextParamDesc, code, text);
        ++nextParamDesc;
        return this;
    }

    public PersistenceProviderMethod(String name, PersistenceProviderHandler owner, String paramTypes) {
        this(name, owner, paramTypes, "");
    }

    public PersistenceProviderMethod(String name, PersistenceProviderHandler owner) {
        this(name, owner, "", "");
    }

    public PersistenceProviderMethod(String name, PersistenceProviderHandler owner, String paramTypes, String returnTypes) {
        super("vespa.persistence." + name, paramTypes, "bs" + returnTypes, owner, "RPC_" + name);
        returnDesc("code", "Error code, or 0 if successful");
        returnDesc("message", "Error message");
    }

}
