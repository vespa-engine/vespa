// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.aws;

import com.yahoo.container.jdisc.secretstore.SecretStore;

public class AwsParameterStore implements SecretStore {


    @Override
    public String getSecret(String key) {
        return null;
    }

    @Override
    public String getSecret(String key, int version) {
        return null;
    }
}
