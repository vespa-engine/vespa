// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing;

import com.yahoo.component.provider.FreezableClass;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.request.Properties;
import com.yahoo.processing.request.properties.PropertyMap;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic processing request.
 * The request contains a set of properties that are used to communicate information from the client making the
 * processing request (e.g http parameters), and as a blackboard to pass information between processors.
 *
 * @author bratseth
 */
public class Request extends FreezableClass implements Cloneable {

    private Properties properties;

    /**
     * The errors encountered while processing this request
     */
    private List<ErrorMessage> errors = new ArrayList<>(0);

    /**
     * The name of the chain of Processor instances which will be invoked when
     * executing a request.
     */
    public static final CompoundName CHAIN = CompoundName.from("chain");

    /**
     * The name of the request property used in the processing framework to
     * store the incoming JDisc request.
     */
    public static final CompoundName JDISC_REQUEST = CompoundName.from("jdisc.request");

    /**
     * Creates a request with no properties
     */
    public Request() {
        this(new PropertyMap());
    }

    /**
     * Create a request with the given properties.
     * This Request gains ownership of the given properties and may edit them in the future.
     *
     * @param properties the properties owner by this
     */
    public Request(Properties properties) {
        this.properties = properties;
    }

    /**
     * Returns the properties set on this request.
     * Processors may add properties to send messages to downstream processors.
     */
    public Properties properties() {
        return properties;
    }

    /**
     * Returns the list of errors encountered while processing this request, never null.
     * This is a live reference to the modifiable list of errors of this.
     */
    public List<ErrorMessage> errors() {
        return errors;
    }

    /**
     * Returns a clone of this request.
     * <p>
     * The properties are logically deeply cloned such that changes to properties in the clone are independent.
     * <p>
     * The errors of the original request <b>are not</b> cloned into the new instance:
     * It will have an empty list of errors.
     */
    @Override
    public Request clone() {
        Request clone = (Request) super.clone();
        clone.properties = properties.clone();
        clone.errors = new ArrayList<>(0);
        return clone;
    }

}
