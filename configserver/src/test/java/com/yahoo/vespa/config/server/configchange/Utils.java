// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.application.api.ValidationId;

/**
 * @author geirst
 * @since 5.44
 */
public class Utils {

    final static ValidationId CHANGE_ID = ValidationId.fieldTypeChange;
    final static ValidationId CHANGE_ID_2 = ValidationId.indexingChange;
    final static String CHANGE_MSG = "change";
    final static String CHANGE_MSG_2 = "other change";
    final static String DOC_TYPE = "music";
    final static String DOC_TYPE_2 = "book";
    final static String CLUSTER = "foo";
    final static String CLUSTER_2 = "bar";
    final static String CLUSTER_TYPE = "search";
    final static String CLUSTER_TYPE_2 = "content";
    final static String SERVICE_TYPE = "searchnode";
    final static String SERVICE_TYPE_2 = "distributor";
    final static String SERVICE_NAME = "baz";
    final static String SERVICE_NAME_2 = "qux";
}
