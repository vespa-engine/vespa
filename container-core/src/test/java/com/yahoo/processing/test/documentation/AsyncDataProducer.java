// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.test.documentation;

import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.response.ArrayDataList;
import com.yahoo.processing.response.DataList;
import com.yahoo.processing.response.IncomingData;
import com.yahoo.processing.test.ProcessorLibrary.StringData;

/**
 * A data producer which producer data which will receive asynchronously.
 * This is not a realistic, thread safe implementation as only the incoming data
 * from the last created incoming data can be completed.
 */
public class AsyncDataProducer extends Processor {

    private IncomingData incomingData;

    @SuppressWarnings("unchecked")
    @Override
    public Response process(Request request, Execution execution) {
        DataList dataList = ArrayDataList.createAsync(request); // Default implementation
        incomingData=dataList.incoming();
        return new Response(dataList);
    }

    /** Called by some other data producing thread, later */
    @SuppressWarnings("unchecked")
    public void completeLateData() {
        incomingData.addLast(new StringData(incomingData.getOwner().request(),
                                            "A late hello, world!"));
    }

}
