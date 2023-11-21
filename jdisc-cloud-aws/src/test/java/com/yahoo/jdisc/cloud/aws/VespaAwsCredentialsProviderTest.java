package com.yahoo.jdisc.cloud.aws;

import com.amazonaws.auth.AWSCredentials;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;


public class VespaAwsCredentialsProviderTest {
    Path credentialsPath = TestFileSystem.create().getPath("/credentials.json");
    ManualClock clock = new ManualClock(Instant.now());

    @Test
    void refreshes_credentials() throws IOException {
        Instant originalExpiry = clock.instant().plus(Duration.ofHours(12));
        writeCredentials(credentialsPath, originalExpiry);
        VespaAwsCredentialsProvider credentialsProvider = new VespaAwsCredentialsProvider(credentialsPath, clock);
        AWSCredentials credentials = credentialsProvider.getCredentials();
        assertExpiryEquals(originalExpiry, credentials);

        Instant updatedExpiry = clock.instant().plus(Duration.ofHours(24));
        writeCredentials(credentialsPath, updatedExpiry);

        // File updated, but old credentials still valid
        credentials = credentialsProvider.getCredentials();
        assertExpiryEquals(originalExpiry, credentials);

        // Credentials refreshes when it is < 30 minutes left until expiry
        clock.advance(Duration.ofHours(11).plus(Duration.ofMinutes(31)));
        credentials = credentialsProvider.getCredentials();
        assertExpiryEquals(updatedExpiry, credentials);

        // Credentials refreshes when they are long expired (since noone asked for them for a long time)
        updatedExpiry = clock.instant().plus(Duration.ofDays(12));
        writeCredentials(credentialsPath, updatedExpiry);
        clock.advance(Duration.ofDays(11));
        credentials = credentialsProvider.getCredentials();
        assertExpiryEquals(updatedExpiry, credentials);
    }

    @Test
    void deserializes_credentials() throws IOException {
        Instant originalExpiry = clock.instant().plus(Duration.ofHours(12));
        writeCredentials(credentialsPath, originalExpiry);
        VespaAwsCredentialsProvider credentialsProvider = new VespaAwsCredentialsProvider(credentialsPath, clock);
        AWSCredentials credentials = credentialsProvider.getCredentials();
        assertExpiryEquals(originalExpiry, credentials);
        Assertions.assertEquals("awsAccessKey", credentials.getAWSAccessKeyId());
        Assertions.assertEquals("awsSecretKey", credentials.getAWSSecretKey());
        Assertions.assertEquals("sessionToken", ((VespaAwsCredentialsProvider.Credentials)credentials).getSessionToken());
    }

    private void writeCredentials(Path path, Instant expiry) throws IOException {
        String content = """
                {
                   "awsAccessKey": "awsAccessKey",
                   "awsSecretKey": "awsSecretKey",
                   "sessionToken": "sessionToken",
                   "expiry": "%s"
                 }""".formatted(expiry.toString());
        Files.writeString(path, content);
    }

    private void assertExpiryEquals(Instant expiry, AWSCredentials credentials) {
        Assertions.assertEquals(expiry, ((VespaAwsCredentialsProvider.Credentials)credentials).expiry());
    }
}
