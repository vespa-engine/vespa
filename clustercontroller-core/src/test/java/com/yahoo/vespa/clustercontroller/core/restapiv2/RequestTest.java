// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InternalFailure;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import junit.framework.TestCase;

public class RequestTest extends TestCase {

    public void testGetResultBeforeCompletion() {
        Request<String> r = new Request<String>(Request.MasterState.MUST_BE_MASTER) {
            @Override
            public String calculateResult(Context context) throws StateRestApiException {
                return "foo";
            }
        };
        try{
            r.getResult();
            assertTrue(false);
        } catch (InternalFailure e) {
        } catch (Exception e) {
            assertTrue(false);
        }
        r.notifyCompleted();
        try{
            r.getResult();
            assertTrue(false);
        } catch (InternalFailure e) {
        } catch (Exception e) {
            assertTrue(false);
        }
    }
}
