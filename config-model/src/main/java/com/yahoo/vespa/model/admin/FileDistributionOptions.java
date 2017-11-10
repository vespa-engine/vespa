// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.binaryprefix.BinaryPrefix;
import com.yahoo.binaryprefix.BinaryScaledAmount;
import com.yahoo.cloud.config.filedistribution.FiledistributorConfig;

/**
 * Options for controlling the behavior of the file distribution services.
 * @author tonytv
 */
public class FileDistributionOptions implements FiledistributorConfig.Producer {
    public static FileDistributionOptions defaultOptions() {
        return new FileDistributionOptions();
    }

    private FileDistributionOptions() {}

    private BinaryScaledAmount uploadbitrate = new BinaryScaledAmount();
    private BinaryScaledAmount downloadbitrate = new BinaryScaledAmount();

    //Called through reflection
    public void downloadbitrate(BinaryScaledAmount amount) {
        ensureNonNegative(amount);
        downloadbitrate = amount;
    }

    //Called through reflection
    public void uploadbitrate(BinaryScaledAmount amount) {
        ensureNonNegative(amount);
        uploadbitrate = amount;
    }

    private void ensureNonNegative(BinaryScaledAmount amount) {
        if (amount.amount < 0)
            throw new IllegalArgumentException("Expected non-negative number, got " + amount.amount);
    }

    private int byteRate(BinaryScaledAmount bitRate) {
        BinaryScaledAmount byteRate = bitRate.divide(8);
        return (int)byteRate.as(BinaryPrefix.unit);
    }

    @Override
    public void getConfig(FiledistributorConfig.Builder builder) {
        builder.maxuploadspeed((double)byteRate(uploadbitrate));
        builder.maxdownloadspeed((double)byteRate(downloadbitrate));
    }
}
