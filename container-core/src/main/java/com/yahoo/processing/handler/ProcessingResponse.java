// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.handler;

import com.google.common.collect.ImmutableList;
import com.yahoo.container.jdisc.AsyncHttpResponse;
import com.yahoo.container.jdisc.VespaHeaders;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.execution.Execution.Trace.LogValue;
import com.yahoo.processing.rendering.AsynchronousRenderer;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A response from running a request through processing. This response is just a
 * wrapper of the knowhow needed to render the Response from processing.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class ProcessingResponse extends AsyncHttpResponse {

    private final com.yahoo.processing.Request processingRequest;
    private final com.yahoo.processing.Response processingResponse;
    private final Execution execution;
    private final Renderer renderer;

    /** True if the return status has been set explicitly and should not be further changed */
    private boolean explicitStatusSet = false;

    public ProcessingResponse(int status, com.yahoo.processing.Request processingRequest,
                              com.yahoo.processing.Response processingResponse,
                              Renderer renderer,
                              Executor renderingExecutor, Execution execution) {
        super(status);
        this.processingRequest = processingRequest;
        this.processingResponse = processingResponse;
        this.execution = execution;
        this.renderer = renderer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void render(OutputStream stream, ContentChannel channel, 
                       CompletionHandler completionHandler) throws IOException {
        if (renderer instanceof AsynchronousRenderer) {
            AsynchronousRenderer asyncRenderer = (AsynchronousRenderer)renderer;
            asyncRenderer.setNetworkWiring(channel, completionHandler);
        }
        renderer.renderResponse(stream, processingResponse, execution, processingRequest);
        // the stream is closed in AsynchronousSectionedRenderer, after all data
        // has arrived
    }

    @Override
    public String getContentType() {
        return renderer.getMimeType();
    }

    @Override
    public String getCharacterEncoding() {
        return renderer.getEncoding();
    }

    @Override
    public void complete() {
        // Add headers
        addHeadersAndStatusFrom(processingResponse.data());

        if ( ! explicitStatusSet) {
            // Set status from errors TODO: This could be decomplicated a bit
            List<ErrorMessage> errors = flattenErrors(processingResponse);
            boolean isSuccess = !(processingResponse.data().asList().isEmpty() && !errors.isEmpty()); // NOT success if ( no data AND are errors )
            setStatus(getHttpResponseStatus(isSuccess, processingRequest, errors.size() == 0 ? null : errors.get(0), errors));
        }
    }

    /**
     * This sets header and status from special Data items used for the purpose.
     * Do both at once to avoid traversing the data tree twice.
     */
    @SuppressWarnings("unchecked")
    private void addHeadersAndStatusFrom(DataList<Data> dataList) {
        for (Data data : dataList.asList()) {
            if (data instanceof ResponseHeaders) {
                headers().addAll(((ResponseHeaders) data).headers());
            }
            else if ( ! explicitStatusSet && (data instanceof ResponseStatus)) {
                setStatus(((ResponseStatus)data).code());
                explicitStatusSet = true;
            }
            else if (data instanceof DataList) {
                addHeadersAndStatusFrom((DataList) data);
            }
        }
    }

    private List<ErrorMessage> flattenErrors(Response processingResponse) {
        Set<ErrorMessage> errors = flattenErrors(null, processingResponse.data());
        if (errors == null) return Collections.emptyList();
        return ImmutableList.copyOf(errors);
    }

    @SuppressWarnings("unchecked")
    private Set<ErrorMessage> flattenErrors(Set<ErrorMessage> errors, Data data) {
        if (data.request() == null) return Collections.EMPTY_SET; // Not allowed, but handle anyway
        errors = addTo(errors, data.request().errors());

        if (data instanceof DataList) {
            for (Data item : ((DataList<Data>) data).asList())
                errors = flattenErrors(errors, item);
        }

        return errors;
    }

    private Set<ErrorMessage> addTo(Set<ErrorMessage> allErrors, List<ErrorMessage> errors) {
        if (errors.isEmpty()) return allErrors;

        if (allErrors == null)
            allErrors = new LinkedHashSet<>();
        allErrors.addAll(errors);
        return allErrors;
    }

    private int getHttpResponseStatus(boolean isSuccess, Request request,
                                      ErrorMessage mainError, List<ErrorMessage> errors) {
        if (isBenchmarking(request)) return VespaHeaders.getEagerErrorStatus(mainError,errors.iterator());
        return VespaHeaders.getStatus(isSuccess, mainError, errors.iterator());
    }

    private boolean isBenchmarking(Request request) {
        com.yahoo.container.jdisc.HttpRequest httpRequest = (com.yahoo.container.jdisc.HttpRequest)request.properties().get(Request.JDISC_REQUEST);
        if (httpRequest == null) return false;
        return VespaHeaders.benchmarkOutput(httpRequest);
    }

    @Override
    public Iterable<LogValue> getLogValues() {
        return execution.trace()::logValueIterator;
    }

}
