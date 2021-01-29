// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.cloud.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.PropertiesCredentials;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class VespaAwsCredentialsProvider implements AWSCredentialsProvider {

    private static final String DEFAULT_CREDENTIALS_PATH = "/opt/vespa/var/container-data/opt/vespa/conf/credentials.properties";

    private final AtomicReference<AWSCredentials> credentials = new AtomicReference<>();
    private final Path credentialsPath;

    public VespaAwsCredentialsProvider() {
        this.credentialsPath = Path.of(DEFAULT_CREDENTIALS_PATH);
        refresh();
    }

    @Override
    public AWSCredentials getCredentials() {
        return credentials.get();
    }

    @Override
    public void refresh() {
        try {
            // TODO : implement reading from json file
            PropertiesCredentials propertiesCredentials = new PropertiesCredentials(this.credentialsPath.toFile());
            credentials.set(propertiesCredentials);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get credentials in " + credentialsPath.toString(), e);
        }
    }
}
