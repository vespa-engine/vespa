// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This package provides an API for building Vespa or jDisc applications programmatically or from
 * an application package source, and instantiating those applications inside the current Java runtime.
 * Currently only a single jDisc cluster and no content clusters are supported.
 */
@ExportPackage
@PublicApi
package com.yahoo.application;

import com.yahoo.api.annotations.PublicApi;
import com.yahoo.osgi.annotation.ExportPackage;
