// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * A generic address container that tries to make as few assumptions about addresses as possible.
 * Most addresses have some of these fields, but with different names (e.g. postal code vs zip code).
 *
 * When consuming data from this class, do not make any assumptions about which fields have content.
 * An address might be still valid with surprisingly little information.
 *
 * All fields are non-null, but might be empty strings.
 *
 * @author ogronnesby
 */
public class TenantAddress {
    private final String address;
    private final String code;
    private final String city;
    private final String region;
    private final String country;

    TenantAddress(String address, String code, String city, String region, String country) {
        this.address = Objects.requireNonNull(address, "'address' was null");
        this.code = Objects.requireNonNull(code, "'code' was null");
        this.city = Objects.requireNonNull(city, "'city' was null");
        this.region = Objects.requireNonNull(region, "'region' was null");
        this.country = Objects.requireNonNull(country, "'country' was null");
    }

    public static TenantAddress empty() {
        return new TenantAddress("", "", "", "", "");
    }

    /** Multi-line fields that has the contents of the street address (or similar) */
    public String address() { return address; }

    /** The ZIP or postal code part of the address */
    public String code() { return code; }

    /** The city of the address */
    public String city() { return city; }

    /** The region part of the address - e.g. a state, county, or province */
    public String region() { return region; }

    /** The country part of the address.  Its name, not a code */
    public String country() { return country; }

    public boolean isEmpty() {
        return this.equals(empty());
    }

    public TenantAddress withAddress(String address) {
        return new TenantAddress(address, code, city, region, country);
    }

    public TenantAddress withCode(String code) {
        return new TenantAddress(address, code, city, region, country);
    }

    public TenantAddress withCity(String city) {
        return new TenantAddress(address, code, city, region, country);
    }

    public TenantAddress withRegion(String region) {
        return new TenantAddress(address, code, city, region, country);
    }

    public TenantAddress withCountry(String country) {
        return new TenantAddress(address, code, city, region, country);
    }

    @Override
    public String toString() {
        return "TenantAddress{" +
                "address='" + address + '\'' +
                ", code='" + code + '\'' +
                ", city='" + city + '\'' +
                ", region='" + region + '\'' +
                ", country='" + country + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantAddress that = (TenantAddress) o;
        return Objects.equals(address, that.address) && Objects.equals(code, that.code) && Objects.equals(city, that.city) && Objects.equals(region, that.region) && Objects.equals(country, that.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, code, city, region, country);
    }
}
