// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;

import java.util.List;

/**
 * Interface for requests to a {@link NameService}.
 *
 * @author mpolden
 */
public interface NameServiceRequest {

    /** Send this to given name service */
    void dispatchTo(NameService nameService);

    /** Returns the records affected by executing this */
    List<Record> affectedRecords();

}
