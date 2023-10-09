// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.TestAndSetCondition;

/**
 * This class represents messages having an optional "test and set" condition
 *
 * @author Vegard Sjonfjell
 */
public abstract class TestAndSetMessage extends DocumentMessage  {
    public abstract void setCondition(TestAndSetCondition condition);
    public abstract TestAndSetCondition getCondition();
}
