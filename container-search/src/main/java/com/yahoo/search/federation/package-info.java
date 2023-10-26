// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * The federation layer on top of the search container. This contains
 *
 * <ul>
 * <li>A model of Sources which can be selected in and for a Query and which are implemented
 * by a Search Chain, and Providers which represents the connection to specific backends (these
 * two are often 1-1 but not always)
 * <li>The federation searcher responsible for forking a query to multiple sources in parallel
 * <li>A simple searcher which can talk to other vespa services
 * </ul>
 */
@ExportPackage
package com.yahoo.search.federation;

import com.yahoo.osgi.annotation.ExportPackage;
