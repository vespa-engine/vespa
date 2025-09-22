// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Systems in hosted Vespa
 *
 * @author Martin Polden
 * @author hakon
 * @author bjorncs
 */
public enum SystemName {

    /** Yahoo Private Cloud CD */
    cd,

    /** Yahoo Private Cloud Production */
    main,

    /** Public Vespa Cloud Production */
    Public,

    /** Public Vespa Cloud CD */
    PublicCd,

    /** Local development system */
    dev,

    /** Kubernetes Production */
    kubernetes,

    /** Kubernetes CD */
    kubernetesCd,

    /** Default system (for unit tests and non-hosted) */
    Default;

    public static SystemName defaultSystem() {
        return Default;
    }

    public static SystemName from(String value) {
        return Arrays.stream(values())
                .filter(systemName -> systemName.value().equals(value))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("'%s' is not a valid system".formatted(value)));
    }

    public String value() {
        return switch (this) {
            case dev -> "dev";
            case cd -> "cd";
            case main -> "main";
            case Public -> "public";
            case PublicCd -> "publiccd";
            case kubernetes -> "kubernetes";
            case kubernetesCd -> "kubernetescd";
            case Default -> "default";
        };
    }

    /** Whether the system is similar to Public, e.g. PublicCd. */
    public boolean isPublicCloudLike() { return this == Public || this == PublicCd; }

    // TODO Remove and use isPublicCloudLike() instead
    public boolean isPublicLike() { return isPublicCloudLike(); }

    // TODO Remove and use isPublicCloudLike() instead
    public boolean isPublic() { return isPublicCloudLike(); }

    // TODO Remove and use isYahooLike() instead
    public boolean isMainLike() { return isYahooLike(); }

    public boolean isYahooLike() { return this == main || this == cd; }

    // TODO Remove and use isKubernetesLike() instead
    public boolean isKubernetes() { return isKubernetesLike(); }

    public boolean isKubernetesLike() { return this == kubernetes || this == kubernetesCd; }

    /** Whether the system is used for continuous deployment. */
    // TODO Remove and use isCdLike() instead
    public boolean isCd() { return isCdLike(); }

    public boolean isCdLike() { return this == cd || this == PublicCd || this == kubernetesCd; }

    public boolean isProductionLike() { return this == main || this == Public || this == kubernetes; }

    public static Set<SystemName> all() { return EnumSet.allOf(SystemName.class); }

    public static Set<SystemName> allOf(Predicate<SystemName> predicate) {
        return Stream.of(values()).filter(predicate).collect(Collectors.toUnmodifiableSet());
    }

    /** Managed systems hosted by Vespa.ai */
    public static Set<SystemName> hostedVespa() { return EnumSet.of(main, cd, Public, PublicCd); }

}
