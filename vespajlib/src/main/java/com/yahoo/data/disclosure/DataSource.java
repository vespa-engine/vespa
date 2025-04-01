// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.disclosure;

/**
 * A data source that can emit its data to a {@link DataSink}.
 *
 * @author havardpe
 * @author bjorncs
 */
public interface DataSource {

    void emit(DataSink sink);

}
