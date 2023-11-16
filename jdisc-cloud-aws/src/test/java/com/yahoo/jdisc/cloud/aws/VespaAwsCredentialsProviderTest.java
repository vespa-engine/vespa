package com.yahoo.jdisc.cloud.aws;

import com.amazonaws.auth.AWSCredentials;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class VespaAwsCredentialsProviderTest {

    @Test
    void refreshes_credentials() throws IOException {
        Path credentialsPath = TestFileSystem.create().getPath("/credentials.json");
        ManualClock clock = new ManualClock(Instant.now());

        Instant originalExpiry = clock.instant().plus(Duration.ofHours(12));
        writeCredentials(credentialsPath, originalExpiry);
        VespaAwsCredentialsProvider credentialsProvider = new VespaAwsCredentialsProvider(credentialsPath, clock);

        AWSCredentials credentials = credentialsProvider.getCredentials();
        Assertions.assertEquals(originalExpiry.toString(), credentials.getAWSAccessKeyId());

        Instant updatedExpiry = clock.instant().plus(Duration.ofHours(24));
        writeCredentials(credentialsPath, updatedExpiry);
        // File updated, but old credentials still valid
        credentials = credentialsProvider.getCredentials();
        Assertions.assertEquals(originalExpiry.toString(), credentials.getAWSAccessKeyId());

        // Credentials refreshes when it is < 30 minutes left until expiry
        clock.advance(Duration.ofHours(11).plus(Duration.ofMinutes(31)));
        credentials = credentialsProvider.getCredentials();
        Assertions.assertEquals(updatedExpiry.toString(), credentials.getAWSAccessKeyId());

    }

    private void writeCredentials(Path path, Instant expiry) throws IOException {
        String content = """
                {
                   "awsAccessKey": "%1$s",
                   "awsSecretKey": "%1$s",
                   "sessionToken": "%1$s",
                   "expiry": "%1$s"
                 }""".formatted(expiry.toString());
        Files.writeString(path, content);
    }
}
