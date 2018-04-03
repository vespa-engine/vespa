// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Utf8;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;

public class FileReceiverTest {
    private File root;
    private File tempDir;
    private final XXHash64 hasher = XXHashFactory.fastestInstance().hash64();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        root = temporaryFolder.newFolder("root");
        tempDir = temporaryFolder.newFolder("tmp");
    }

    @Test
    public void receiveMultiPartFile() throws IOException{
        String [] parts  = new String[3];
        parts[0] = "first part\n";
        parts[1] = "second part\n";
        parts[2] = "third part\n";
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            sb.append(s);
        }
        String all = sb.toString();
        transferPartsAndAssert(new FileReference("ref-a"), "myfile-1", all, 1);
        transferPartsAndAssert(new FileReference("ref-a"), "myfile-2", all, 2);
        transferPartsAndAssert(new FileReference("ref-a"), "myfile-3", all, 3);
    }

    @Test
    public void receiveCompressedParts() throws IOException{
        File dirWithFiles = temporaryFolder.newFolder("files");
        FileWriter writerA = new FileWriter(new File(dirWithFiles, "a"));
        writerA.write("1");
        writerA.close();
        FileWriter writerB = new FileWriter(new File(dirWithFiles, "b"));
        writerB.write("2");
        writerB.close();

        byte[] data = CompressedFileReference.compress(dirWithFiles);
        transferCompressedData(new FileReference("ref"), "a", data);
        File downloadDir = new File(root, "ref");
        assertEquals("1", IOUtils.readFile(new File(downloadDir, "a")));
        assertEquals("2", IOUtils.readFile(new File(downloadDir, "b")));
    }

    private void transferPartsAndAssert(FileReference ref, String fileName, String all, int numParts) throws IOException {
        byte [] allContent = Utf8.toBytes(all);

        FileReceiver.Session session = new FileReceiver.Session(root, tempDir, 1, ref,
                FileReferenceData.Type.file, fileName, allContent.length);
        int partSize = (allContent.length+(numParts-1))/numParts;
        ByteBuffer bb = ByteBuffer.wrap(allContent);
        for (int i = 0, pos = 0; i < numParts; i++) {
            byte [] buf = new byte[Math.min(partSize, allContent.length - pos)];
            bb.get(buf);
            session.addPart(i, buf);
            // Small numbers, so need a large delta
            assertEquals((double)(i+1)/(double)numParts, session.percentageReceived(), 0.04);
            pos += buf.length;
        }
        File file = session.close(hasher.hash(ByteBuffer.wrap(Utf8.toBytes(all)), 0));

        byte [] allReadBytes = Files.readAllBytes(file.toPath());
        file.delete();
        assertEquals(all, Utf8.toString(allReadBytes));
    }

    private void transferCompressedData(FileReference ref, String fileName, byte[] data) {
        FileReceiver.Session session =
                new FileReceiver.Session(root, tempDir, 1, ref, FileReferenceData.Type.compressed, fileName, data.length);
        session.addPart(0, data);
        session.close(hasher.hash(ByteBuffer.wrap(data), 0));
    }
}
