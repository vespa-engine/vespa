// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.execution.chain;

import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.ChainedComponent;
import com.yahoo.component.provider.ComponentRegistry;

/**
 * A registry of chains
 *
 * @author Tony Vaagenes
 */
public class ChainRegistry<T extends ChainedComponent> extends ComponentRegistry<Chain<T>> {
}
