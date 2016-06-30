// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel

import com.fasterxml.jackson.annotation.JsonValue

/**
 * @author bakksjo
 */
case class ServiceClusterKey(clusterId: ClusterId, serviceType: ServiceType) {
  // Jackson's StdKeySerializer uses toString() (and ignores annotations) for objects used as Map keys.
  // Therefore, we use toString() as the JSON-producing method, which is really sad.
  @JsonValue
  override def toString(): String = s"${clusterId}:${serviceType}"
}

