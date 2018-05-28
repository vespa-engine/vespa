// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.exports;

/**
 * A {@code ConfigSource} is a store of configs that supports retrieval of any
 * particular config by {@link ConfigSupplier}.
 *
 * <p>Examples of possible config sources:
 *
 * <ul>
 *     <li>a directory containing JSON config files
 *     <li>JSON served from ZooKeeper on config server
 *     <li>JSON served by controller or config server REST API
 *     <li>cloud config JDisc components
 *     <li>cloud config served by config server
 *     <li>in-memory config modified through REST API
 *     <li>a constant
 * </ul>
 *
 * <p>A config source that transform one config into another type is called a <em>mapper</em>,
 * and a config source that produces a single config based on the config produced by a set of
 * sub-sources is called a <em>reducer</em>. These can be combined into a config source
 * <em>tree</em>. Config source trees may provide developers, maintainers, and operators
 * flexibility and control they would not otherwise have. For example, rolling out a new
 * risky feature could be done using a feature flag:
 *
 * <pre>{@code
 *   1. binary default -------------------------------disabled--->
 *
 *   2. binary default ---disabled---> +---------+
 *      controller -------(unset)----> | reducer | ---disabled--->
 *                                     +---------+
 *
 *   3. binary default ---disabled---> +---------+
 *      controller --------enabled---> | reducer | ----enabled--->
 *                                     +---------+
 *
 *   4. binary default --------------------------------enabled--->
 * }</pre>
 *
 * <p>Originally, the new feature is not running (1). The new feature is coded but disabled by
 * default (2). Then the feature could be enabled for, say, a testing machine and verified
 * before rolling it out to ever more machines and always being able to disable the feature
 * if any problems is detected (3). Once the feature has been enabled everywhere, the
 * code can flip the default and remove the old code.
 *
 * <p>The {@code ConfigSource} interface is in fact empty, but would have defined a method
 * with a partial signature if that had been allowed by the Java Language Specification (JLS).
 *
 * <p>A {@code ConfigSource} must have a method for creating a {@link ConfigSupplier}
 * for retrieving a particular config. The key used to identify the config is
 * implementation-defined. The method should have the following signature for some generic or
 * reference type T:
 *
 * <pre>{@code
 *     public ... ConfigSupplier<T> newSupplier(...);
 * }</pre>
 *
 * @see FileContentSource
 * @author hakon
 */
public interface ConfigSource {
    // See interface description.
}
