// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

/**
 * An abstraction of a secret store for e.g passwords.
 * Implementations can be plugged in to provide passwords for various keys.
 *
 * @author bratseth
 */
public interface SecretStore {

    /** Returns the secret for this key */
    String getSecret(String key);

}
