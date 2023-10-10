// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.model.S3Object;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class S3Downloader implements Downloader {

    private final AmazonS3 s3Client;

    S3Downloader() {
        this.s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(System.getenv("VESPA_CLOUD_NATIVE_REGION"))
                .withCredentials(new CredentialsProvider())
                .build();
    }

    @Override
    public Optional<File> downloadFile(String url, File targetDir) throws IOException {
        AmazonS3URI s3URI = new AmazonS3URI(url);
        S3Object s3Object = s3Client.getObject(s3URI.getBucket(), s3URI.getKey());
        File file = new File(targetDir, fileName());
        Files.copy(s3Object.getObjectContent(), file.toPath());
        return Optional.of(file);
    }

    private static class CredentialsProvider implements AWSCredentialsProvider {

        private static final String DEFAULT_CREDENTIALS_PATH = Defaults.getDefaults()
                .underVespaHome("var/vespa/aws/credentials.json");

        private final Path credentialsPath;

        public CredentialsProvider() {
            this.credentialsPath = Path.of(DEFAULT_CREDENTIALS_PATH);
        }

        @Override
        public AWSCredentials getCredentials() { return readCredentials(); }

        @Override
        public void refresh() { readCredentials(); }

        private AWSSessionCredentials readCredentials() {
            try {
                Slime slime = SlimeUtils.jsonToSlime(Files.readAllBytes(credentialsPath));
                Cursor cursor = slime.get();
                String accessKey = cursor.field("awsAccessKey").asString();
                String secretKey = cursor.field("awsSecretKey").asString();
                String sessionToken = cursor.field("sessionToken").asString();
                return new BasicSessionCredentials(accessKey, secretKey, sessionToken);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

}
