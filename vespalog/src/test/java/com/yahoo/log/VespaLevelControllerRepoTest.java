// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
@SuppressWarnings({"deprecation"})
public class VespaLevelControllerRepoTest {

    static int findControlString(RandomAccessFile f, String s) {
        try {
            String toFind = "\n." + s + ": ";
            byte[] contents = new byte[(int) f.length()];
            f.seek(0);
            f.read(contents);
            f.seek(0);
            String c_as_s = new String(contents, StandardCharsets.US_ASCII);
            int off = c_as_s.indexOf(toFind);
            if (off < 0) {
                System.err.println("did not find control line for level '"+s+"' in logcontrol file:");
                System.err.println(c_as_s);
                throw new RuntimeException("bad state in logcontrol file");
            }
            off += toFind.length();
            while ((off % 4) != 0) ++off;
            return off;
        } catch (IOException e) {
            throw new RuntimeException("problem reading logcontrol file", e);
        }
    }

    @Test
    public void testLogCtl () throws InterruptedException, IOException {
        File lcf = new File("./my-testilol-config-id.logcontrol");
        try {
            lcf.delete();
            Logger.getLogger("com.yahoo.log.test").setLevel(null);
            assertNull(Logger.getLogger("com.yahoo.log.test").getLevel());

            LevelControllerRepo repo = new VespaLevelControllerRepo(lcf.getName(), "all -debug -spam", "TST");

            long timeout = System.currentTimeMillis() + 60000;
            while (System.currentTimeMillis() < timeout) {
                if (Level.CONFIG == Logger.getLogger("com.yahoo.log.test").getLevel())
                    break;
                Thread.sleep(100);
            }

            RandomAccessFile lcfile = new RandomAccessFile(lcf, "rw");

            lcfile.seek(VespaLevelControllerRepo.controlFileHeaderLength+1);
            assertEquals(lcfile.readByte(), '\n');
            lcfile.seek(VespaLevelControllerRepo.controlFileHeaderLength+2);
            assertEquals(lcfile.readByte(), 'd');
            lcfile.seek(VespaLevelControllerRepo.controlFileHeaderLength+2 + 7);
            assertEquals(lcfile.readByte(), ':');
            assertEquals(0, (VespaLevelControllerRepo.controlFileHeaderLength+13) % 4);
            lcfile.seek(VespaLevelControllerRepo.controlFileHeaderLength + 13);
            assertEquals(0x20204f4e, lcfile.readInt());

            int off = findControlString(lcfile, "com.yahoo.log.test");
            lcfile.seek(off);
            assertEquals(0x20204f4e, lcfile.readInt());
            assertEquals(0x20204f4e, lcfile.readInt());
            assertEquals(0x20204f4e, lcfile.readInt());
            assertEquals(0x20204f4e, lcfile.readInt());
            assertEquals(0x20204f4e, lcfile.readInt());
            assertEquals(0x20204f4e, lcfile.readInt());

            assertEquals(0x204f4646, lcfile.readInt());
            assertEquals(0x204f4646, lcfile.readInt());
            lcfile.seek(off);
            lcfile.writeInt(0x204f4646);
            lcfile.writeInt(0x204f4646);
            lcfile.writeInt(0x204f4646);
            lcfile.writeInt(0x204f4646);
            lcfile.writeInt(0x204f4646);
            lcfile.writeInt(0x204f4646);

            lcfile.writeInt(0x20204f4e);
            lcfile.writeInt(0x20204f4e);
            lcfile.close();
            assertFalse(repo.getLevelController("com.yahoo.log.test").shouldLog(LogLevel.FATAL));
            assertFalse(repo.getLevelController("com.yahoo.log.test").shouldLog(LogLevel.ERROR));
            assertFalse(repo.getLevelController("com.yahoo.log.test").shouldLog(LogLevel.WARNING));
            assertFalse(repo.getLevelController("com.yahoo.log.test").shouldLog(LogLevel.INFO));
            assertFalse(repo.getLevelController("com.yahoo.log.test").shouldLog(LogLevel.EVENT));
            assertFalse(repo.getLevelController("com.yahoo.log.test").shouldLog(LogLevel.CONFIG));
            assertTrue(repo.getLevelController("com.yahoo.log.test").shouldLog(LogLevel.DEBUG));
            assertTrue(repo.getLevelController("com.yahoo.log.test").shouldLog(LogLevel.SPAM));
        } finally {
            lcf.delete();
        }
    }
}
