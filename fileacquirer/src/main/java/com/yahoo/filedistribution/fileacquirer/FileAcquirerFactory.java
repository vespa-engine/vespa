// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.filedistribution.fileacquirer;

/**
 * Hides the real file acquirer type from 3rd party developers.
 * Not intended to be used by 3rd parties.
 *
 * @author Tony Vaagenes
 */
public class FileAcquirerFactory {

    public static FileAcquirer create() {
        return new FileAcquirerImpl();
    }

}
