// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class CompressedApplicationInputStreamTest {

    private static void writeFileToTar(ArchiveOutputStream taos, File file) throws IOException {
        taos.putArchiveEntry(taos.createArchiveEntry(file, file.getName()));
        ByteStreams.copy(new FileInputStream(file), taos);
        taos.closeArchiveEntry();
    }

    private static File createArchiveFile(ArchiveOutputStream taos, File outFile) throws IOException {
        File app = new File("src/test/resources/deploy/validapp");
        writeFileToTar(taos, new File(app, "services.xml"));
        writeFileToTar(taos, new File(app, "hosts.xml"));
        writeFileToTar(taos, new File(app, "deployment.xml"));
        taos.close();
        return outFile;
    }

    public static File createTarFile() throws IOException {
        File outFile = File.createTempFile("testapp", ".tar.gz");
        ArchiveOutputStream archiveOutputStream = new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(outFile)));
        return createArchiveFile(archiveOutputStream, outFile);
    }

    private static File createZipFile() throws IOException {
        File outFile = File.createTempFile("testapp", ".tar.gz");
        ArchiveOutputStream archiveOutputStream = new ZipArchiveOutputStream(new FileOutputStream(outFile));
        return createArchiveFile(archiveOutputStream, outFile);
    }

    private void assertTestApp(File outApp) {
        String [] files = outApp.list();
        assertNotNull(files);
        assertThat(files.length, is(3));
        assertThat(Arrays.asList(files), containsInAnyOrder(ImmutableList.of(is("hosts.xml"), is("services.xml"), is("deployment.xml"))));
    }

    @Test
    public void require_that_valid_tar_application_can_be_unpacked() throws IOException {
        File outFile = createTarFile();
        CompressedApplicationInputStream unpacked = CompressedApplicationInputStream.createFromCompressedStream(new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(outFile))));
        File outApp = unpacked.decompress();
        assertTestApp(outApp);
    }

    @Test
    public void require_that_valid_tar_application_in_subdir_can_be_unpacked() throws IOException {
        File outFile = File.createTempFile("testapp", ".tar.gz");
        ArchiveOutputStream archiveOutputStream = new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(outFile)));

        File app = new File("src/test/resources/deploy/validapp");

        File file = new File(app, "services.xml");
        archiveOutputStream.putArchiveEntry(archiveOutputStream.createArchiveEntry(file, "application/" + file.getName()));
        ByteStreams.copy(new FileInputStream(file), archiveOutputStream);
        archiveOutputStream.closeArchiveEntry();
        file = new File(app, "hosts.xml");
        archiveOutputStream.putArchiveEntry(archiveOutputStream.createArchiveEntry(file, "application/" + file.getName()));
        ByteStreams.copy(new FileInputStream(file), archiveOutputStream);
        archiveOutputStream.closeArchiveEntry();
        file = new File(app, "deployment.xml");
        archiveOutputStream.putArchiveEntry(archiveOutputStream.createArchiveEntry(file, "application/" + file.getName()));
        ByteStreams.copy(new FileInputStream(file), archiveOutputStream);
        archiveOutputStream.closeArchiveEntry();

        archiveOutputStream.close();

        CompressedApplicationInputStream unpacked = CompressedApplicationInputStream.createFromCompressedStream(new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(outFile))));
        File outApp = unpacked.decompress();
        assertThat(outApp.getName(), is("application")); // gets the name of the subdir
        assertTestApp(outApp);
    }

    @Test
    public void require_that_valid_zip_application_can_be_unpacked() throws IOException {
        File outFile = createZipFile();
        CompressedApplicationInputStream unpacked = CompressedApplicationInputStream.createFromCompressedStream(
                new ZipArchiveInputStream(new FileInputStream(outFile)));
        File outApp = unpacked.decompress();
        assertTestApp(outApp);
    }

    @Test
    public void require_that_gnu_tared_file_can_be_unpacked() throws IOException, InterruptedException {
        File tmpTar = File.createTempFile("myapp", ".tar");
        Process p = new ProcessBuilder("tar", "-C", "src/test/resources/deploy/validapp", "--exclude=.svn", "-cvf", tmpTar.getAbsolutePath(), ".").start();
        p.waitFor();
        p = new ProcessBuilder("gzip", tmpTar.getAbsolutePath()).start();
        p.waitFor();
        File gzFile = new File(tmpTar.getAbsolutePath() + ".gz");
        assertTrue(gzFile.exists());
        CompressedApplicationInputStream unpacked = CompressedApplicationInputStream.createFromCompressedStream(
                new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(gzFile))));
        File outApp = unpacked.decompress();
        assertTestApp(outApp);
    }

    @Test
    public void require_that_nested_app_can_be_unpacked() throws IOException, InterruptedException {
        File tmpTar = File.createTempFile("myapp", ".tar");
        Process p = new ProcessBuilder("tar", "-C", "src/test/resources/deploy/advancedapp", "--exclude=.svn", "-cvf", tmpTar.getAbsolutePath(), ".").start();
        p.waitFor();
        p = new ProcessBuilder("gzip", tmpTar.getAbsolutePath()).start();
        p.waitFor();
        File gzFile = new File(tmpTar.getAbsolutePath() + ".gz");
        assertTrue(gzFile.exists());
        CompressedApplicationInputStream unpacked = CompressedApplicationInputStream.createFromCompressedStream(
                new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(gzFile))));
        File outApp = unpacked.decompress();
        List<File> files = Arrays.asList(outApp.listFiles());
        assertThat(files.size(), is(5));
        assertTrue(files.contains(new File(outApp, "services.xml")));
        assertTrue(files.contains(new File(outApp, "hosts.xml")));
        assertTrue(files.contains(new File(outApp, "deployment.xml")));
        assertTrue(files.contains(new File(outApp, "schemas")));
        assertTrue(files.contains(new File(outApp, "external")));
        File sd = files.get(files.indexOf(new File(outApp, "schemas")));
        assertTrue(sd.isDirectory());
        assertThat(sd.listFiles().length, is(1));
        assertThat(sd.listFiles()[0].getAbsolutePath(), is(new File(sd, "keyvalue.sd").getAbsolutePath()));

        File ext = files.get(files.indexOf(new File(outApp, "external")));
        assertTrue(ext.isDirectory());
        assertThat(ext.listFiles().length, is(1));
        assertThat(ext.listFiles()[0].getAbsolutePath(), is(new File(ext, "foo").getAbsolutePath()));

        files = Arrays.asList(ext.listFiles());
        File foo = files.get(files.indexOf(new File(ext, "foo")));
        assertTrue(foo.isDirectory());
        assertThat(foo.listFiles().length, is(1));
        assertThat(foo.listFiles()[0].getAbsolutePath(), is(new File(foo, "bar").getAbsolutePath()));

        files = Arrays.asList(foo.listFiles());
        File bar = files.get(files.indexOf(new File(foo, "bar")));
        assertTrue(bar.isDirectory());
        assertThat(bar.listFiles().length, is(1));
        assertTrue(bar.listFiles()[0].isFile());
        assertThat(bar.listFiles()[0].getAbsolutePath(), is(new File(bar, "lol").getAbsolutePath()));
    }


    @Test(expected = IOException.class)
    public void require_that_invalid_application_returns_error_when_unpacked() throws IOException {
        File app = new File("src/test/resources/deploy/validapp/services.xml");
        CompressedApplicationInputStream.createFromCompressedStream(
                new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(app))));
    }
}
