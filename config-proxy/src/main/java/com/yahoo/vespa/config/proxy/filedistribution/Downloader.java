// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author hmusum
 */
interface Downloader {

    Optional<File> downloadFile(String url, File downloadDir) throws IOException;

    default String fileName() { return "contents"; }

    default boolean alreadyDownloaded(Downloader downloader, File downloadDir) {
        File contents = new File(downloadDir, downloader.fileName());
        return contents.exists() && contents.length() > 0;
    }

}
