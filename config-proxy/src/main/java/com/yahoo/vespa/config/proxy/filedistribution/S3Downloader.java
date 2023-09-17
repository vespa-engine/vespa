package com.yahoo.vespa.config.proxy.filedistribution;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class S3Downloader {

    // TODO: Avoid hardcoding
    private static final String ZTS_URL = "https://zts.athenz.ouroath.com:4443/zts/v1";

    Optional<File> downloadFile(String fileName, File targetDir) throws IOException {
        throw new UnsupportedOperationException("Download of S3 urls not implemented");
    }

}
