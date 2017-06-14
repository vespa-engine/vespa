// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

import com.yahoo.collections.FreezableArrayList;
import com.yahoo.processing.Request;

import java.util.List;

/**
 * A data list backed by an array.
 * This implementation supports subclassing.
 *
 * @author bratseth
 */
public class ArrayDataList<DATATYPE extends Data> extends AbstractDataList<DATATYPE> {

    private final FreezableArrayList<DATATYPE> dataList = new FreezableArrayList<>(true);

    /**
     * Creates a simple data list which does not allow late incoming data
     *
     * @param request the request which created this data list
     */
    protected ArrayDataList(Request request) {
        super(request);
    }

    /**
     * Creates a simple data list which receives incoming data in the given instance
     *
     * @param request      the request which created this data list, never null
     * @param incomingData the recipient of incoming data to this list, never null
     */
    protected ArrayDataList(Request request, IncomingData<DATATYPE> incomingData) {
        this(request, incomingData, true, true);
    }

    /**
     * Creates a simple data list which receives incoming data in the given instance
     *
     * @param request      the request which created this data list, never null
     * @param incomingData the recipient of incoming data to this list, never null
     */
    protected ArrayDataList(Request request, IncomingData<DATATYPE> incomingData, boolean ordered, boolean streamed) {
        super(request, incomingData, ordered, streamed);
    }

    /**
     * Creates a simple data list which does not allow late incoming data
     *
     * @param request the request which created this data list
     */
    public static <DATATYPE extends Data> ArrayDataList<DATATYPE> create(Request request) {
        return new ArrayDataList<>(request);
    }

    /**
     * Creates an instance of this which supports incoming data through the default mechanism (DefaultIncomingData)
     */
    public static <DATATYPE extends Data> ArrayDataList<DATATYPE> createAsync(Request request) {
        DefaultIncomingData<DATATYPE> incomingData = new DefaultIncomingData<>();
        ArrayDataList<DATATYPE> dataList = new ArrayDataList<>(request, incomingData);
        incomingData.assignOwner(dataList);
        return dataList;
    }

    /**
     * Creates an instance of this which supports incoming data through the default mechanism (DefaultIncomingData),
     * and where this data can be rendered in any order.
     */
    public static <DATATYPE extends Data> ArrayDataList<DATATYPE> createAsyncUnordered(Request request) {
        DefaultIncomingData<DATATYPE> incomingData = new DefaultIncomingData<>();
        ArrayDataList<DATATYPE> dataList = new ArrayDataList<>(request, incomingData, false, true);
        incomingData.assignOwner(dataList);
        return dataList;
    }

    /**
     * Creates an instance of this which supports incoming data through the default mechanism (DefaultIncomingData)
     * and where this data cannot be returned to clients until this is completed.
     */
    public static <DATATYPE extends Data> ArrayDataList<DATATYPE> createAsyncNonstreamed(Request request) {
        DefaultIncomingData<DATATYPE> incomingData = new DefaultIncomingData<>();
        ArrayDataList<DATATYPE> dataList = new ArrayDataList<>(request, incomingData, true, false);
        incomingData.assignOwner(dataList);
        return dataList;
    }

    public DATATYPE add(DATATYPE data) {
        dataList.add(data);
        return data;
    }

    /**
     * Returns the data element at index
     */
    public DATATYPE get(int index) {
        return dataList.get(index);
    }

    /**
     * Returns a reference to the list backing this. The list may be modified freely,
     * unless this is frozen. If frozen, the only permissible write operations are those that
     * add new items to the end of the list.
     */
    public List<DATATYPE> asList() {
        return dataList;
    }

    @Override
    public void addDataListener(Runnable runnable) {
        dataList.addListener(runnable);
    }

    /**
     * Irreversibly prevent further changes to the items of this.
     * This allows the processing engine to start streaming the current content of this list back to the
     * client (if applicable).
     * <p>
     * Adding new items to the end of this list is permitted even after freeze.
     * If frozen, those items may be streamed back to the client immediately on add.
     * <p>
     * Calling this on a frozen list has no effect.
     */
    public void freeze() {
        super.freeze();
        dataList.freeze();
    }

}
