// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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


    public PaymentInstrument(String id, String nameOnCard, String displayText, String brand, String type, String endingWith, String expiryDate) {
        this.id = id;
        this.nameOnCard = nameOnCard;
        this.displayText = displayText;
        this.brand = brand;
        this.type = type;
        this.endingWith = endingWith;
        this.expiryDate = expiryDate;
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
}
