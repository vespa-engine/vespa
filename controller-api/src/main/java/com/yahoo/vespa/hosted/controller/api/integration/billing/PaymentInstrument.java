// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * @author olaa
 */
public class PaymentInstrument {

    private final String id;
    private final String nameOnCard;
    private final String displayText;
    private final String brand;
    private final String type;
    private final String endingWith;
    private final String expiryDate;
    private final String addressLine1;
    private final String addressLine2;
    private final String city;
    private final String state;
    private final String zip;
    private final String country;

    public PaymentInstrument(String id, String nameOnCard, String displayText, String brand, String type, String endingWith, String expiryDate, String addressLine1, String addressLine2, String zip, String city, String state, String country) {
        this.id = id;
        this.nameOnCard = nameOnCard;
        this.displayText = displayText;
        this.brand = brand;
        this.type = type;
        this.endingWith = endingWith;
        this.expiryDate = expiryDate;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.zip = zip;
        this.city = city;
        this.state = state;
        this.country = country;
    }

    public String getId() {
        return id;
    }

    public String getNameOnCard() {
        return nameOnCard;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getBrand() {
        return brand;
    }

    public String getType() {
        return type;
    }

    public String getEndingWith() {
        return endingWith;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getZip() {
        return zip;
    }

    public String getCountry() {
        return country;
    }

}
