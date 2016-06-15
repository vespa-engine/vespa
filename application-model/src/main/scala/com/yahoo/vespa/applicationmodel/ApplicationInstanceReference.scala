// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel

import com.fasterxml.jackson.annotation.JsonValue

/**
 * @author <a href="mailto:bakksjo@yahoo-inc.com">Oyvind Bakksjo</a>
 */
 // TODO: Remove this and use ApplicationId instead (if you need it for the JSON stuff move it to that layer and don't let it leak)
case class ApplicationInstanceReference(tenantId: TenantId, applicationInstanceId: ApplicationInstanceId) {
  // Jackson's StdKeySerializer uses toString() (and ignores annotations) for objects used as Map keys.
  // Therefore, we use toString() as the JSON-producing method, which is really sad.
  @JsonValue
  override def toString(): String = s"${tenantId.s}:${applicationInstanceId.s}"
}
