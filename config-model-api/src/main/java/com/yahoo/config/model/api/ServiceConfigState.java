// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.Optional;

/**
 * Represents service response from /state/v1/config collected by configserver from services, e.g. jdisc in container node.
 *
 * @param currentGeneration is a config generation currently used by a service, e.g. jdisc in container node
 * @param applyOnRestart indicates that a service received a new config (newer than {@code currentGeneration})
 *                       but is waiting to use it until the next restart. It is {@code Optional} 
 *                       for backwards compatibility, when this field is missing from /state/v1/config response.
 * @author glebashnik
 */
public record ServiceConfigState(long currentGeneration, Optional<Boolean> applyOnRestart) {}
