// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * Address formats are quite diverse across the world both in therms of what fields are used, named and
 * the order of them.
 *
 * To be generic a little future proof the address fields here are a mix of free text (address lines) and fixed fields.
 * The address lines can be street address, P.O box, c/o name, apartment, suite, unit, building floor etc etc.
 *
 * All fields are mandatory but can be an empty string (ie. not null)
 *
 * @author smorgrav
 */
public class TenantInfoAddress {

    private final String addressLines;
    private final String postalCodeOrZip;
    private final String city;
    private final String stateRegionProvince;
    private final String country;

    TenantInfoAddress(String addressLines, String postalCodeOrZip, String city, String country, String stateRegionProvince) {
        this.addressLines =  Objects.requireNonNull(addressLines);;
        this.city = Objects.requireNonNull(city);
        this.postalCodeOrZip = Objects.requireNonNull(postalCodeOrZip);
        this.country = Objects.requireNonNull(country);
        this.stateRegionProvince = Objects.requireNonNull(stateRegionProvince);
    }

    public static final TenantInfoAddress EMPTY = new TenantInfoAddress("","","", "", "");

    public String addressLines() {
        return addressLines;
    }

    public String postalCodeOrZip() {
        return postalCodeOrZip;
    }

    public String city() {
        return city;
    }

    public String country() {
        return country;
    }

    public String stateRegionProvince() {
        return stateRegionProvince;
    }

    public TenantInfoAddress withAddressLines(String newAddressLines) {
        return new TenantInfoAddress(newAddressLines, postalCodeOrZip, city, country, stateRegionProvince);
    }

    public TenantInfoAddress withPostalCodeOrZip(String newPostalCodeOrZip) {
        return new TenantInfoAddress(addressLines, newPostalCodeOrZip, city, country, stateRegionProvince);
    }

    public TenantInfoAddress withCity(String newCity) {
        return new TenantInfoAddress(addressLines, postalCodeOrZip, newCity, country, stateRegionProvince);
    }

    public TenantInfoAddress withCountry(String newCountry) {
        return new TenantInfoAddress(addressLines, postalCodeOrZip, city, newCountry, stateRegionProvince);
    }

    public TenantInfoAddress withStateRegionProvince(String newStateRegionProvince) {
        return new TenantInfoAddress(addressLines, postalCodeOrZip, city, country, newStateRegionProvince);
    }

    public boolean isEmpty() {
        return this.equals(EMPTY);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantInfoAddress that = (TenantInfoAddress) o;
        return addressLines.equals(that.addressLines) &&
                postalCodeOrZip.equals(that.postalCodeOrZip) &&
                city.equals(that.city) &&
                stateRegionProvince.equals(that.stateRegionProvince) &&
                country.equals(that.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addressLines, postalCodeOrZip, city, stateRegionProvince, country);
    }
}
