// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Query Profiles provide nested sets of named (and optionally typed) key-values which can be referenced in a Query
 * to proviode initial values of Query properties. Values in nested query profiles can be looked up from
 * the query properties by dotting the names. Query profiles supports inheritance to allow variations
 * for, e.g different buckets, client types, markets etc. */
@ExportPackage
@PublicApi
package com.yahoo.search.query.profile.compiled;

import com.yahoo.api.annotations.PublicApi;
import com.yahoo.osgi.annotation.ExportPackage;
