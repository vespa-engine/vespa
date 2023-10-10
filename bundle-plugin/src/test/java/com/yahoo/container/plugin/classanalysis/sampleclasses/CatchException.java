// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis.sampleclasses;

import javax.security.auth.login.LoginException;

/**
 * @author Tony Vaagenes
 */
public class CatchException {
    void ignored() throws Exception{
        try {
            throw new Exception("dummy");
        } catch(LoginException classToDetect) {}
    }
}
