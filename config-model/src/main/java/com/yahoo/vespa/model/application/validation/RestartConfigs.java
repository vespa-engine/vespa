// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.model.application.validation.change.ConfigValueChangeValidator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Services should use this annotation to list all consumed configurations which contain definitions flagged with restart.
 * This annotation is required for the {@link ConfigValueChangeValidator}
 * to detect config changes that will require restart of some services. The {@link com.yahoo.config.ConfigInstance}
 * values are inherited; any configs annotated on a service will be inherited to all sub-classes of that service.
 * These sub-classes can supplement with more ConfigInstances (in addition to the inherited one) with the annotation.
 * This is different the inheritance that {@link java.lang.annotation.Inherited} provides, where sub-classes can either
 * inherit or override the annotation from the super class.
 *
 * NOTE: This annotation will only have effect on subclasses of {@link com.yahoo.vespa.model.Service}.
 *       Do not use this annotation on other types config producers.
 *
 * @author bjorncs
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RestartConfigs {
    Class<? extends ConfigInstance>[] value() default {};
}
