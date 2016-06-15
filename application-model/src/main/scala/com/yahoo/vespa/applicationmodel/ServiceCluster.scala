// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel

/**
 * Represents a collection of service instances that together make up a service with a single cluster id.
 *
 * @author <a href="mailto:bakksjo@yahoo-inc.com">Oyvind Bakksjo</a>
 */
case class ServiceCluster[S](
  clusterId: ClusterId,
  serviceType: ServiceType,
  serviceInstances: java.util.Set[ServiceInstance[S]])
