// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.Optional;

/**
 * Represents service response from /state/v1/config collected by configserver from services.
 *
 * @param currentGeneration is a config generation currently used by the service.
 * @param applyOnRestart indicates that a service received a new config and is waiting to apply it on restart. 
 *                       Empty if a service does not support apply on restart.
 * @author glebashnik
 */
public record ServiceConfigState(long currentGeneration, Optional<Boolean> applyOnRestart) {}
