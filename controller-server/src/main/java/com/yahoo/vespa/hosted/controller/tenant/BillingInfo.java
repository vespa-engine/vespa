package com.yahoo.vespa.hosted.controller.tenant;

/**
 * Information pertinent to billing a tenant for use of hosted Vespa services.
 *
 * @author jonmv
 */
public class BillingInfo {

    private final String data;

    /** Creates a new BillingInfo with the given data. */
    public BillingInfo(String data) {
        this.data = requireValid(data);
    }

    /** Returns the data stored in this. */
    public String data() { return data; }

    static String requireValid(String data) {
        if (data.isBlank())
            throw new IllegalArgumentException("Invalid billing information '" + data + "'.");
        return data;
    }

}
