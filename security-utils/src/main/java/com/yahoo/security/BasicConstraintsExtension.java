// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

/**
 * @author bjorncs
 */
class BasicConstraintsExtension {
    final boolean isCritical, isCertAuthorityCertificate;

    BasicConstraintsExtension(boolean isCritical, boolean isCertAuthorityCertificate) {
        this.isCritical = isCritical;
        this.isCertAuthorityCertificate = isCertAuthorityCertificate;
    }
}
