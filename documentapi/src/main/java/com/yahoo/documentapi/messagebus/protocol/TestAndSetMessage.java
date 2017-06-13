// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.google.common.annotations.Beta;
import com.yahoo.document.TestAndSetCondition;

/**
 * This class represents messages having an optional "test and set" condition
 *
 * @author Vegard Sjonfjell
 */
@Beta
public abstract class TestAndSetMessage extends DocumentMessage  {
    public abstract void setCondition(TestAndSetCondition condition);
    public abstract TestAndSetCondition getCondition();
}
