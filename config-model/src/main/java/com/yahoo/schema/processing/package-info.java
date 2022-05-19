// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Classes in this package (processors) implements some search
 * definition features by reducing them to simpler features.
 * The processors are run after parsing of the search definition,
 * before creating the derived model.
 *
 * For simplicity, features should always be implemented here
 * rather than in the derived model if possible.
 *
 * New processors must be added to the list in Processing.
 */
@com.yahoo.api.annotations.PackageMarker
package com.yahoo.schema.processing;
