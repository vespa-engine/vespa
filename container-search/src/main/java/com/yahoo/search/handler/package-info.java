// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * The search handler, which handles search request to the Container by translating the Request into a Query, invoking the
 * chosen Search Chain to get a Result, which it translates to a Response which is returned to the Container.
 */
@ExportPackage
@PublicApi
package com.yahoo.search.handler;

import com.yahoo.api.annotations.PublicApi;
import com.yahoo.osgi.annotation.ExportPackage;
