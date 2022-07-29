// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MakeConfigTest {

    private File dest;
    
    @BeforeEach
    public void setUp() {
        dest = new File("/tmp/"+System.currentTimeMillis()+File.separator);
        dest.mkdir();
    }
    
    @AfterEach
    public void tearDown() {
        recursiveDeleteDir(dest);
    }
    
    private boolean recursiveDeleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();

            assert children != null;
            for (String child : children) {
                boolean success = recursiveDeleteDir(new File(dir, child));

                if (!success) return false;
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    @Test
    void testProps() throws PropertyException {
        System.setProperty("config.dumpTree", "true");
        System.setProperty("config.useFramework", "true");
        System.setProperty("config.dest", dest.getAbsolutePath());
        System.setProperty("config.spec", "src/test/resources/configgen.allfeatures.def");
        MakeConfigProperties p = new MakeConfigProperties();
        assertEquals(p.destDir.getAbsolutePath(), dest.getAbsolutePath());
        assertTrue(p.dumpTree);
        assertTrue(p.generateFrameworkCode);
        assertEquals(p.specFiles.size(), 1);
        assertEquals(p.specFiles.get(0).getAbsolutePath(), new File("src/test/resources/configgen.allfeatures.def").getAbsolutePath());

        System.setProperty("config.dumpTree", "false");
        System.setProperty("config.useFramework", "false");
        System.setProperty("config.dest", dest.getAbsolutePath());
        System.setProperty("config.spec", "src/test/resources/configgen.allfeatures.def,src/test/resources/baz.bar.foo.def");
        p = new MakeConfigProperties();
        assertEquals(p.destDir.getAbsolutePath(), dest.getAbsolutePath());
        assertFalse(p.dumpTree);
        assertFalse(p.generateFrameworkCode);
        assertEquals(p.specFiles.size(), 2);
    }

    @Test
    void testMake() throws IOException {
        System.setProperty("config.dumpTree", "true");
        System.setProperty("config.useFramework", "true");
        System.setProperty("config.dest", dest.getAbsolutePath());
        System.setProperty("config.spec", "src/test/resources/configgen.allfeatures.def");
        MakeConfig.main(new String[]{});
    }
    
}
