// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.time.Duration;

/**
 * Backup configuration for a content cluster, derived from deployment.xml.
 *
 * @author olaa
 */
public record BackupConfig(Duration frequency, Granularity granularity) {

    public enum Granularity { cluster, group }

}
