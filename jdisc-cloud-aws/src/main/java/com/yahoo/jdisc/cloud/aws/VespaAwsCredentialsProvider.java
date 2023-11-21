// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.cloud.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VespaAwsCredentialsProvider implements AWSCredentialsProvider {

    private static final Logger logger = Logger.getLogger(VespaAwsCredentialsProvider.class.getName());
    private static final String DEFAULT_CREDENTIALS_PATH = "/opt/vespa/var/vespa/aws/credentials.json";
    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(30);
    private final AtomicReference<Credentials> credentials = new AtomicReference<>();
    private final Path credentialsPath;
    private final Clock clock;

    public VespaAwsCredentialsProvider() {
        this(Path.of(DEFAULT_CREDENTIALS_PATH), Clock.systemUTC());
    }

    VespaAwsCredentialsProvider(Path credentialsPath, Clock clock) {
        this.credentialsPath = credentialsPath;
        this.clock = clock;
        refresh();
    }

    @Override
    public AWSCredentials getCredentials() {
        Credentials sessionCredentials = credentials.get();
        if (Duration.between(clock.instant(), sessionCredentials.expiry).compareTo(REFRESH_INTERVAL)<0) {
            refresh();
            sessionCredentials = credentials.get();
        }
        return sessionCredentials;
    }

    @Override
    public void refresh() {
        try {
            logger.log(Level.FINE, "Refreshing credentials from disk");
            credentials.set(readCredentials());
        } catch (Exception e) {
            throw new RuntimeException("Unable to get credentials. Please ensure cluster is configured as exclusive. See: https://cloud.vespa.ai/en/reference/services#nodes");
        }
    }

    private Credentials readCredentials() {
        try {
            Slime slime = SlimeUtils.jsonToSlime(Files.readAllBytes(credentialsPath));
            Cursor cursor = slime.get();
            String accessKey = cursor.field("awsAccessKey").asString();
            String secretKey = cursor.field("awsSecretKey").asString();
            String sessionToken = cursor.field("sessionToken").asString();
            Instant defaultExpiry = Instant.now().plus(Duration.ofHours(1));
            Instant expiry;
            try {
                expiry = SlimeUtils.optionalString(cursor.field("expiry")).map(Instant::parse).orElse(defaultExpiry);
            } catch (Exception e) {
                expiry = defaultExpiry;
                logger.warning("Unable to read expiry from credentials");
            }
            return new Credentials(accessKey, secretKey, sessionToken, expiry);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record Credentials (String awsAccessKey, String awsSecretKey, String sessionToken, Instant expiry) implements AWSSessionCredentials {
        @Override
        public String getSessionToken() {
            return sessionToken;
        }

        @Override
        public String getAWSAccessKeyId() {
            return awsAccessKey;
        }

        @Override
        public String getAWSSecretKey() {
            return awsSecretKey;
        }
    }
}
