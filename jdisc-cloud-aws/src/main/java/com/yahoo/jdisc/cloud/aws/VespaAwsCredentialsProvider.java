// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.cloud.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class VespaAwsCredentialsProvider implements AWSCredentialsProvider {

    private static final String DEFAULT_CREDENTIALS_PATH = "/opt/vespa/var/container-data/opt/vespa/conf/vespa/credentials.json";

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
            credentials.set(readCredentials());
        } catch (Exception e) {
            throw new RuntimeException("Unable to get credentials in " + credentialsPath.toString(), e);
        }
    }

    private AWSSessionCredentials readCredentials() {
        try {
            Slime slime = SlimeUtils.jsonToSlime(Files.readAllBytes(credentialsPath));
            Cursor cursor = slime.get();
            String accessKey = cursor.field("awsAccessKey").asString();
            String secretKey = cursor.field("awsSecretKey").asString();
            String sessionToken = cursor.field("sessionToken").asString();
            return new BasicSessionCredentials(accessKey, secretKey, sessionToken);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
