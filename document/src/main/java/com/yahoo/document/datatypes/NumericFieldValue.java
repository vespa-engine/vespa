// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.data.disclosure.DataSource;

public abstract class NumericFieldValue extends FieldValue implements DataSource {

    public abstract Number getNumber();

}
