// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.cloud.impl;

import ai.vespa.hosted.cd.internal.TestRuntimeProvider;
import com.yahoo.component.AbstractComponent;

/**
 * @author mortent
 */
public class VespaTestRuntimeProvider extends AbstractComponent implements TestRuntimeProvider {

    @Override
    public void initialize(byte[] config) {
        VespaTestRuntime vespaTestRuntime = new VespaTestRuntime(config);
        updateReference(vespaTestRuntime);
    }

}
