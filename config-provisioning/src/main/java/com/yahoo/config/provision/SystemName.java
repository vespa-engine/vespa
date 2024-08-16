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
 * @author mpolden
 */
public enum SystemName {

    /** Continuous deployment system */
    cd(false, true),

    /** Production system */
    main(false, false),

    /** System accessible to the public */
    Public(true, false),

    /** Continuous deployment system for testing the Public system */
    PublicCd(true, true),

    /** Local development system */
    dev(false, false);

    private final boolean isPublic;
    private final boolean isCd;

    SystemName(boolean isPublic, boolean isCd) {
        this.isPublic = isPublic;
        this.isCd = isCd;
    }

    public static SystemName defaultSystem() {
        return main;
    }

    public static SystemName from(String value) {
        return switch (value.toLowerCase()) {
            case "dev" -> dev;
            case "cd" -> cd;
            case "main" -> main;
            case "public" -> Public;
            case "publiccd" -> PublicCd;
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
        };
    }

    /** Whether the system is similar to Public, e.g. PublicCd. */
    public boolean isPublic() { return isPublic; }

    /** Whether the system is used for continuous deployment. */
    public boolean isCd() { return isCd; }

    public static Set<SystemName> all() { return EnumSet.allOf(SystemName.class); }

    public static Set<SystemName> allOf(Predicate<SystemName> predicate) {
        return Stream.of(values()).filter(predicate).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<SystemName> hostedVespa() { return EnumSet.of(main, cd, Public, PublicCd); }

}
