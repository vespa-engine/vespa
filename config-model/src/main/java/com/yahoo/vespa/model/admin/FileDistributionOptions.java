// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.binaryprefix.BinaryPrefix;
import com.yahoo.binaryprefix.BinaryScaledAmount;
import com.yahoo.cloud.config.filedistribution.FiledistributorConfig;

/**
 * Options for controlling the behavior of the file distribution services.
 *
 * @author tonytv
 */
public class FileDistributionOptions implements FiledistributorConfig.Producer {

    private FileDistributionOptions() {
    }

    private BinaryScaledAmount uploadBitRate = new BinaryScaledAmount();
    private BinaryScaledAmount downloadBitRate = new BinaryScaledAmount();
    private boolean disableFiledistributor = false;


    public void downloadBitRate(BinaryScaledAmount amount) {
        ensureNonNegative(amount);
        downloadBitRate = amount;
    }

    public void uploadBitRate(BinaryScaledAmount amount) {
        ensureNonNegative(amount);
        uploadBitRate = amount;
    }

    public void disableFiledistributor(boolean value) {
        disableFiledistributor = value;
    }

    public boolean disableFiledistributor() {
        return disableFiledistributor;
    }

    private void ensureNonNegative(BinaryScaledAmount amount) {
        if (amount.amount < 0)
            throw new IllegalArgumentException("Expected non-negative number, got " + amount.amount);
    }

    private int byteRate(BinaryScaledAmount bitRate) {
        BinaryScaledAmount byteRate = bitRate.divide(8);
        return (int) byteRate.as(BinaryPrefix.unit);
    }

    @Override
    public void getConfig(FiledistributorConfig.Builder builder) {
        builder.maxuploadspeed((double) byteRate(uploadBitRate));
        builder.maxdownloadspeed((double) byteRate(downloadBitRate));
    }
}
