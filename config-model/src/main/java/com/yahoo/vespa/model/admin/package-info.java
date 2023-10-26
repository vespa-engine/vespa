// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Provides the classes for the admin components of the Vespa config
 * model.
 *
 * The {@link com.yahoo.vespa.model.admin.Admin Admin} class is
 * the natural starting point. It takes the admin part of the
 * user-defined application specification as input argument to its
 * constructor. The services given in the specification are
 * instantiated, in addition to some mandatory services (e.g. {@link
 * com.yahoo.vespa.model.admin.Slobrok Slobrok}) that will be created
 * even if the user does not specify them.
 */
@ExportPackage
package com.yahoo.vespa.model.admin;

import com.yahoo.osgi.annotation.ExportPackage;
