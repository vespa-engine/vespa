// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing;

import com.yahoo.component.chain.ChainedComponent;
import com.yahoo.processing.execution.Execution;

/**
 * Superclass of chainable components processing Requests to create Responses.
 * <p>
 * Processors typically changes the Request and/or the Response. It may also make multiple
 * forward requests, in series or parallel, or manufacture the response content itself or by calling
 * an external service.
 * <p>
 * Typical usage:
 * <code>
 * public class MyProcessor extends Processor {
 *
 *     &#64;Override
 *     public Response process(Request request, Execution execution) {
 *         // process the request here
 *         Response response = execution.process(request); // Pass along to get the Response
 *         // process (or fill in) Data/DataList items on the response here
 *         return response;
 *     }
 *
 * }
 * </code>
 *
 * @author bratseth
 */
public abstract class Processor extends ChainedComponent {

    /**
     * Performs a processing request and returns the response
     *
     * @return a Response instance - never null - containing the data produced by this processor
     *         and those it forwards the request to
     */
    public abstract Response process(Request request, Execution execution);


}
