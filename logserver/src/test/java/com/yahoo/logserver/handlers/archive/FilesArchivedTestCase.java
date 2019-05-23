// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.logserver.handlers.archive;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * @author Arne Juul
 */
public class FilesArchivedTestCase {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private void makeLogfile(File dir, String name, long hours) throws IOException {
        File f = new File(dir, name);
        f.getParentFile().mkdirs();
        new FileWriter(f).write("foo bar baz\n");
        long now = System.currentTimeMillis();
        f.setLastModified(now - (hours * 3600 * 1000));
    }

    void checkExist(File dir, String name) {
        assertTrue(new File(dir, name).isFile());
    }
    void checkNoExist(File dir, String name) {
        assertFalse(new File(dir, name).isFile());
    }

    @Test
    public void testMaintenance() throws java.io.IOException {
        File tmpDir = temporaryFolder.newFolder();

        makeLogfile(tmpDir, "foo/bar", 35*24); // non-matching file

        makeLogfile(tmpDir, "2018/11/20/13-0", 35*24);
        makeLogfile(tmpDir, "2018/11/21/13-0", 34*24);
        makeLogfile(tmpDir, "2018/12/28/13-0", 3*24);
        makeLogfile(tmpDir, "2018/12/29/13-0", 2*24);
        makeLogfile(tmpDir, "2018/12/30/13-0", 1*24);
        makeLogfile(tmpDir, "2018/12/31/14-0", 3);
        makeLogfile(tmpDir, "2018/12/31/16-0", 1);
        makeLogfile(tmpDir, "2018/12/31/17-0", 0);
        dumpFiles(tmpDir, "before archive maintenance");
        FilesArchived a = new FilesArchived(tmpDir);
        dumpFiles(tmpDir, "after archive maintenance");
        checkExist(tmpDir, "foo/bar");
        checkExist(tmpDir, "2018/12/31/17-0");
        checkExist(tmpDir, "2018/12/31/16-0");
        checkExist(tmpDir, "2018/12/31/14-0.gz");
        checkExist(tmpDir, "2018/12/28/13-0.gz");
        checkExist(tmpDir, "2018/12/29/13-0.gz");
        checkExist(tmpDir, "2018/12/30/13-0.gz");

        checkNoExist(tmpDir, "2018/12/31/17-0.gz");
        checkNoExist(tmpDir, "2018/12/31/16-0.gz");
        checkNoExist(tmpDir, "2018/12/31/14-0");
        checkNoExist(tmpDir, "2018/12/28/13-0");
        checkNoExist(tmpDir, "2018/12/29/13-0");
        checkNoExist(tmpDir, "2018/12/30/13-0");

        checkNoExist(tmpDir, "2018/11/20/13-0");
        checkNoExist(tmpDir, "2018/11/20/13-0.gz");
        checkNoExist(tmpDir, "2018/11/21/13-0");
        checkNoExist(tmpDir, "2018/11/21/13-0.gz");

        makeLogfile(tmpDir, "2018/12/31/16-0", 3);
        makeLogfile(tmpDir, "2018/12/31/17-0", 3);
        makeLogfile(tmpDir, "2018/12/31/17-1", 1);
        makeLogfile(tmpDir, "2018/12/31/17-2", 0);

        dumpFiles(tmpDir, "before second archive maintenance");
        a.maintenance();
        dumpFiles(tmpDir, "after second archive maintenance");

        checkExist(tmpDir, "2018/12/31/17-2");
        checkExist(tmpDir, "2018/12/31/17-1");
        checkExist(tmpDir, "2018/12/31/16-0.gz");
        checkExist(tmpDir, "2018/12/31/17-0.gz");

        checkNoExist(tmpDir, "2018/12/31/16-0");
        checkNoExist(tmpDir, "2018/12/31/17-0");
        checkExist(tmpDir, "foo/bar");
    }

    private void dumpFiles(File dir, String header) {
        System.out.println(">>> " + header + " >>> :");
        List<String> seen = scanDir(dir);
        seen.sort(null);
        for (String s : seen) {
            System.err.println("   " + s);
        }
        System.out.println("<<< " + header + " <<<");
    }

    private static List<String> scanDir(File top) {
        List<String> retval = new ArrayList<>();
        String[] names = top.list();
        if (names != null) {
            for (String name : names) {
                File sub = new File(top, name);
                if (sub.isFile()) {
                    retval.add(sub.toString());
                } else if (sub.isDirectory()) {
                    for (String subFile : scanDir(sub)) {
                        retval.add(subFile);
                    }
                }
            }
        }
        return retval;
    }

}
