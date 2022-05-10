// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.deploy.TestProperties;
import org.junit.Test;

/**
 * @author arnej
 */
public class DuplicateStructTestCase extends AbstractExportingTestCase {

    @Test
    public void exact_duplicate_struct_works() throws Exception {
        assertCorrectDeriving("duplicate_struct", "foobar",
                              new TestProperties(),
                              new TestableDeployLogger());
    }

}
