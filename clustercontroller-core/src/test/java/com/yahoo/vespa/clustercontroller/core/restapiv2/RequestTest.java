// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InternalFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class RequestTest {

    @Test
    void testGetResultBeforeCompletion() {
        Request<String> r = new Request<>(Request.MasterState.MUST_BE_MASTER) {
            @Override
            public String calculateResult(Context context) {
                return "foo";
            }
        };
        try {
            r.getResult();
            fail();
        } catch (InternalFailure e) {
        } catch (Exception e) {
            fail();
        }
        r.notifyCompleted();
        try {
            r.getResult();
            fail();
        } catch (InternalFailure e) {
        } catch (Exception e) {
            fail();
        }
    }

}
