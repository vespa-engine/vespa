// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ${package};

import com.yahoo.processing.Request;
import com.yahoo.processing.response.AbstractData;

/** A simple response data type */
public class StringData extends AbstractData {

    private String string;

    public StringData(Request request, String string) {
	super(request);
	this.string = string;
    }

    public void setString(String string) {
	this.string = string;
    }

    @Override
    public String toString() {
        return string;
    }

}