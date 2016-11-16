// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel

/**
 * TODO: What is this
 *
 * @author bakksjo
 */
case class ApplicationInstance[S](
  tenantId: TenantId,
  applicationInstanceId: ApplicationInstanceId,

  // TODO: What is this for?
  serviceClusters: java.util.Set[ServiceCluster[S]]) {

  def reference = ApplicationInstanceReference(tenantId, applicationInstanceId)
}
