// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import java.util.List;

/**
 * All clusters of a deployed application.
 *
 * @author Steinar Knutsen
 */
public class ApplicationView {

    public List<ClusterView> clusters;

}
