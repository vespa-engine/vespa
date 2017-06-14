// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
@ExportPackage
@PublicApi package com.yahoo.processing;

// TODO:
// - Look through all instances where we pass executor and consider if we should allow the caller to decide the thread
// - Should data listener use a typed interface rather than runnable`

import com.yahoo.api.annotations.PublicApi;
import com.yahoo.osgi.annotation.ExportPackage;
