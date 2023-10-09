// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.vespa.config.content.PersistenceConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.utils.Duration;

/**
 * Serves engines config for storage clusters.
 */
public class PersistenceProducer implements PersistenceConfig.Producer {

    public static class Builder {
        public PersistenceProducer build(ModelElement element) {
            ModelElement persistence = element.child("engine");
            if (persistence == null) {
                return new PersistenceProducer();
            }

            return new PersistenceProducer(
                    persistence.childAsBoolean("fail-partition-on-error"),
                    persistence.childAsDuration("recovery-time"),
                    persistence.childAsDuration("revert-time"));
        }
    }

    Boolean failOnError;
    Duration recoveryPeriod;
    Duration revertTimePeriod;

    public PersistenceProducer() {}

    public PersistenceProducer(Boolean failOnError, Duration recoveryPeriod, Duration revertTimePeriod) {
        this.failOnError = failOnError;
        this.recoveryPeriod = recoveryPeriod;
        this.revertTimePeriod = revertTimePeriod;
    }

    @Override
    public void getConfig(PersistenceConfig.Builder builder) {
        if (failOnError != null) {
            builder.fail_partition_on_error(failOnError);
        }
        if (recoveryPeriod != null) {
            builder.keep_remove_time_period((int)recoveryPeriod.getSeconds());
        }
        if (revertTimePeriod != null) {
            builder.revert_time_period((int)revertTimePeriod.getSeconds());
        }
    }
}
