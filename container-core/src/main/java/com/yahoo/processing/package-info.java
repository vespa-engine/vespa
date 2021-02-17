// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Java library for request-response data processing.
 *
 * This library defines request-response processing as an operation which
 * accepts a Request and produces a Response containing Data by executing
 * a Chain of processing components in a single worker thread using method
 * calls for chaining, i.e a synchronous processing model.
 * Data for the Response may optionally be produced asynchronously.
 *
 * The processing model can be implemented by subtyping in frameworks defining
 * a processing model (with a richer, more specific API) for a particular domain.
 */
@ExportPackage
@PublicApi package com.yahoo.processing;

// TODO:
// - Look through all instances where we pass executor and consider if we should allow the caller to decide the thread
// - Should data listener use a typed interface rather than runnable`

import com.yahoo.api.annotations.PublicApi;
import com.yahoo.osgi.annotation.ExportPackage;
