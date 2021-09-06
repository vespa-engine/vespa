package com.yahoo.config.subscription.impl;

public class PayloadChecksum {


    private final String checksum;
    private final Type type;

    public PayloadChecksum(String checksum) {
        this.checksum = checksum;
        this.type = Type.MD5;
    }

    public static PayloadChecksum empty() {
        return new PayloadChecksum("");
    }

    public String asString() { return checksum; }

    public Type type() { return type; }

    enum Type { MD5, XXHASH64 /* not in use yet */ }

}
