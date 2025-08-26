// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Systems in hosted Vespa
 *
 * @author Martin Polden
 * @author bjorncs
 */
public enum SystemName {

    /** Continuous deployment system */
    cd,

    /** Production system */
    main,

    /** System accessible to the public */
    Public,

    /** Continuous deployment system for testing the Public system */
    PublicCd,

    /** Local development system */
    dev,

    /** Kubernetes */
    kubernetes;

    public static SystemName defaultSystem() {
        return main; // TODO the default shouldn't be main but rather a 'default' system
    }

    public static SystemName from(String value) {
        return switch (value.toLowerCase()) {
            case "dev" -> dev;
            case "cd" -> cd;
            case "main" -> main;
            case "public" -> Public;
            case "publiccd" -> PublicCd;
            case "kubernetes" -> kubernetes;
            default -> throw new IllegalArgumentException(String.format("'%s' is not a valid system", value));
        };
    }

    public String value() {
        return switch (this) {
            case dev -> "dev";
            case cd -> "cd";
            case main -> "main";
            case Public -> "public";
            case PublicCd -> "publiccd";
            case kubernetes -> "kubernetes";
        };
    }

    /** Whether the system is similar to Public, e.g. PublicCd. */
    public boolean isPublicLike() { return this == Public || this == PublicCd; }

    public boolean isMainLike() { return this == main || this == cd; }

    public boolean isKubernetes() { return this == kubernetes; }

    /** Whether the system is used for continuous deployment. */
    public boolean isCd() { return this == cd || this == PublicCd; }

    public static Set<SystemName> all() { return EnumSet.allOf(SystemName.class); }

    public static Set<SystemName> allOf(Predicate<SystemName> predicate) {
        return Stream.of(values()).filter(predicate).collect(Collectors.toUnmodifiableSet());
    }

    /** Managed systems hosted by Vespa.ai */
    public static Set<SystemName> hostedVespa() { return EnumSet.of(main, cd, Public, PublicCd); }

}
