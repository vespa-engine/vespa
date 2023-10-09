// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public abstract class ApplicationFileTest {

    protected void writeAppTo(File destFolder) throws IOException {
        createFiles(destFolder, "vespa-services.xml", "vespa-hosts.xml");
        createFolders(destFolder, "searchdefinitions", "components", "files", "templates");
        File sds = new File(destFolder, "searchdefinitions");
        createFiles(sds, "sock.sd");
        File files = new File(destFolder, "files");
        createFiles(files, "foo.json");
        IOUtils.writeFile(new File(files, "foo.json"), "foo : foo\n", false);
        File templates = new File(destFolder, "templates");
        createFolders(templates, "basic", "simple_html", "text");
        createFiles(templates, "basic/error.templ", "basic/header.templ", "basic/hit.templ", "simple_html/footer.templ", "simple_html/header.templ", "simple_html/hit.templ", "text/error.templ", "text/footer.templ", "text/header.templ", "text/hit.templ", "text/nohits.templ");
        File components = new File(destFolder, "components");
        createFiles(components, "defs-only.jar", "file.txt");
    }

    private void createFiles(File destFolder, String ... names) throws IOException {
        for (String name : names) {
            File f = new File(destFolder, name);
            assertTrue(f.createNewFile());
            IOUtils.writeFile(f, "foo", false);
        }
    }

    private void createFolders(File destFolder, String ... names) {
        for (String name : names) {
            new File(destFolder, name).mkdirs();
        }
    }

    @Test
    public void testApplicationFile() throws Exception {
        Path p1 = Path.fromString("foo/bar/baz");
        ApplicationFile f1 = getApplicationFile(p1);
        ApplicationFile f2 = getApplicationFile(p1);
        assertEquals(p1, f1.getPath());
        assertEquals(p1, f2.getPath());
    }

    @Test
    public void testApplicationFileEquals() throws Exception {
        Path p1 = Path.fromString("foo/bar/baz");
        Path p2 = Path.fromString("foo/bar");
        ApplicationFile f1 = getApplicationFile(p1);
        ApplicationFile f2 = getApplicationFile(p2);

        assertEquals(f1, f1);
        assertNotEquals(f1, f2);

        assertNotEquals(f2, f1);
        assertEquals(f2, f2);
    }

    @Test
    public void testApplicationFileIsDirectory() throws Exception {
        assertFalse(getApplicationFile(Path.fromString("vespa-services.xml")).isDirectory());
        assertTrue(getApplicationFile(Path.fromString("searchdefinitions")).isDirectory());
        assertFalse(getApplicationFile(Path.fromString("searchdefinitions/sock.sd")).isDirectory());
        assertFalse(getApplicationFile(Path.fromString("doesnotexist")).isDirectory());
    }

    @Test
    public void testApplicationFileExists() throws Exception {
        assertTrue(getApplicationFile(Path.fromString("vespa-services.xml")).exists());
        assertTrue(getApplicationFile(Path.fromString("searchdefinitions")).exists());
        assertTrue(getApplicationFile(Path.fromString("searchdefinitions/sock.sd")).exists());
        assertFalse(getApplicationFile(Path.fromString("doesnotexist")).exists());
    }

    @Test
    public void testApplicationFileReadContent() throws Exception {
        assertFileContent("foo : foo\n", "files/foo.json");
    }

    @Test (expected = FileNotFoundException.class)
    public void testApplicationFileReadContentInvalidFile() throws Exception {
        assertFileContent("foo : foo\n", "doesnotexist");
    }

    @Test
    public void testApplicationFileCreateDirectory() throws Exception {
        ApplicationFile file = getApplicationFile(Path.fromString("/notyet/exists/here"));
        assertFalse(file.exists());
        file.createDirectory();
        assertTrue(file.exists());
        assertTrue(file.isDirectory());
        file = getApplicationFile(Path.fromString("myDir")).createDirectory();
        assertTrue(file.isDirectory());
        file = getApplicationFile(Path.fromString("myDir/sub")).createDirectory();
        file = getApplicationFile(Path.fromString("myDir/sub")).createDirectory();
        assertTrue(file.isDirectory());
        file = getApplicationFile(Path.fromString("searchdefinitions/myDir2/")).createDirectory();
        assertTrue(file.isDirectory());
        file = getApplicationFile(Path.fromString("myDir3/myDir4/myDir5")).createDirectory();
        assertTrue(file.exists());
        assertTrue(file.isDirectory());
    }

    @Test (expected = IllegalArgumentException.class)
    public void testApplicationFileCreateDirectoryOverFile() throws Exception {
        getApplicationFile(Path.fromString("vespa-services.xml")).createDirectory();
    }

    @Test
    public void testApplicationFileCreateFile() throws Exception {
        ApplicationFile file = getApplicationFile(Path.fromString("newfile.txt"));
        assertFalse(file.exists());
        file.writeFile(new StringReader("foobar"));
        assertTrue(file.exists());
        assertFalse(file.isDirectory());
        assertEquals("foobar", com.yahoo.io.IOUtils.readAll(file.createReader()));
    }

    @Test
    public void testApplicationFileCreateFileWithPath() throws Exception {
        ApplicationFile file = getApplicationFile(Path.fromString("subdir/newfile.txt"));
        assertFalse(file.exists());
        file.writeFile(new StringReader("foobar"));
        assertTrue(file.exists());
        assertFalse(file.isDirectory());
        assertEquals("foobar", com.yahoo.io.IOUtils.readAll(file.createReader()));
    }
    
    @Test
    public void testApplicationFileListFiles() throws Exception {
        ApplicationFile file = getApplicationFile(Path.createRoot());
        assertTrue(file.exists());
        assertTrue(file.isDirectory());
        List<ApplicationFile> list = file.listFiles();
        assertEquals(6, list.size());
        assertTrue(listContains(list, "vespa-services.xml"));
        assertTrue(listContains(list, "vespa-hosts.xml"));
        assertTrue(listContains(list, "components/"));
        assertTrue(listContains(list, "searchdefinitions/"));
        assertTrue(listContains(list, "templates/"));
        assertTrue(listContains(list, "files/"));

        list = getApplicationFile(Path.fromString("templates")).listFiles(false);
        assertTrue(listContains(list, "templates/basic/"));
        assertTrue(listContains(list, "templates/simple_html/"));
        assertTrue(listContains(list, "templates/text/"));

        list = getApplicationFile(Path.fromString("components")).listFiles(false);
        assertTrue(listContains(list, "components/defs-only.jar"));
        assertTrue(listContains(list, "components/file.txt"));

        list = getApplicationFile(Path.fromString("components")).listFiles(true);
        assertTrue(listContains(list, "components/defs-only.jar"));
        assertTrue(listContains(list, "components/file.txt"));

        list = getApplicationFile(Path.fromString("templates")).listFiles(true);
        assertEquals(14, list.size());
        assertTrue(listContains(list, "templates/basic/"));
        assertTrue(listContains(list, "templates/basic/error.templ"));
        assertTrue(listContains(list, "templates/basic/header.templ"));
        assertTrue(listContains(list, "templates/basic/hit.templ"));
        assertTrue(listContains(list, "templates/simple_html/"));
        assertTrue(listContains(list, "templates/simple_html/footer.templ"));
        assertTrue(listContains(list, "templates/simple_html/header.templ"));
        assertTrue(listContains(list, "templates/simple_html/hit.templ"));
        assertTrue(listContains(list, "templates/text/"));
        assertTrue(listContains(list, "templates/text/error.templ"));
        assertTrue(listContains(list, "templates/text/footer.templ"));
        assertTrue(listContains(list, "templates/text/header.templ"));
        assertTrue(listContains(list, "templates/text/hit.templ"));
        assertTrue(listContains(list, "templates/text/nohits.templ"));

        list = getApplicationFile(Path.createRoot()).listFiles(true);
        assertTrue(listContains(list, "components/"));
        assertTrue(listContains(list, "files/"));
        assertTrue(listContains(list, "searchdefinitions/"));
        assertTrue(listContains(list, "templates/"));
        assertTrue(listContains(list, "vespa-hosts.xml"));
        assertTrue(listContains(list, "vespa-services.xml"));
        assertTrue(listContains(list, "templates/text/"));
        assertTrue(listContains(list, "templates/text/error.templ"));
        assertTrue(listContains(list, "templates/text/footer.templ"));
        assertTrue(listContains(list, "templates/text/header.templ"));
        assertTrue(listContains(list, "templates/text/hit.templ"));
        assertTrue(listContains(list, "templates/text/nohits.templ"));

        list = getApplicationFile(Path.createRoot()).listFiles(new ApplicationFile.PathFilter() {
            @Override
            public boolean accept(Path path) {
                return path.getName().endsWith(".xml");
            }
        });
        assertEquals(2, list.size());
        assertFalse(listContains(list, "components/"));
        assertFalse(listContains(list, "files/"));
        assertFalse(listContains(list, "searchdefinitions/"));
        assertFalse(listContains(list, "templates/"));
        assertTrue(listContains(list, "vespa-hosts.xml"));
        assertTrue(listContains(list, "vespa-services.xml"));
    }

    private boolean listContains(List<ApplicationFile> list, String s) {
        for (ApplicationFile file : list) {
            String actual = file.getPath().toString();
            if (file.isDirectory()) {
                actual += "/";
            }
            if (actual.equals(s)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testApplicationFileCanBeDeleted() throws Exception {
        ApplicationFile file = getApplicationFile(Path.fromString("file1.txt"));
        file.writeFile(new StringReader("file1"));
        assertEquals("file1.txt", file.getPath().getName());
        file.delete();
        assertFalse(file.exists());
        assertFalse(file.isDirectory());
        List<ApplicationFile> files = file.listFiles(true);
        assertTrue(files.isEmpty());
        
        file = getApplicationFile(Path.fromString("subdir/file2.txt"));
        file.writeFile(new StringReader("file2"));
        assertEquals("file2.txt", file.getPath().getName());
        file.delete();
        assertFalse(file.exists());
        assertFalse(file.isDirectory());
        files = file.listFiles(true);
        assertTrue(files.isEmpty());
    }
    
    @Test
    public void getGetMetaPath() throws Exception {
        ApplicationFile file = getApplicationFile(Path.fromString("file1.txt"));
        assertEquals(".meta/file1.txt", file.getMetaPath().toString());

        file = getApplicationFile(Path.fromString("dir/file1.txt"));
        assertEquals("dir/.meta/file1.txt", file.getMetaPath().toString());

        file = getApplicationFile(Path.fromString("dir"));
        assertEquals(".meta/dir", file.getMetaPath().toString());

        file = getApplicationFile(Path.fromString(""));
        assertEquals(".meta/.root", file.getMetaPath().toString());
    }

    @Test
    public void getGetMetaContent() throws Exception {
        String testFileName = "file1.txt";
        ApplicationFile file = getApplicationFile(Path.fromString(testFileName));
        assertEquals(".meta/" + testFileName, file.getMetaPath().toString());
        String input = "a";
        file.writeFile(new StringReader(input));
        assertEquals(ApplicationFile.ContentStatusNew, file.getMetaData().getStatus());
        assertEquals(ConfigUtils.getMd5(input), file.getMetaData().getMd5());

        testFileName = "foo";
        ApplicationFile fooDir = getApplicationFile(Path.fromString(testFileName));
        fooDir.createDirectory();
        assertEquals(ApplicationFile.ContentStatusNew, fooDir.getMetaData().getStatus());
        assertTrue(fooDir.getMetaData().getMd5().isEmpty());

        testFileName = "foo/file2.txt";
        file = getApplicationFile(Path.fromString(testFileName));
        input = "a";
        file.writeFile(new StringReader(input));
        assertEquals(ApplicationFile.ContentStatusNew, file.getMetaData().getStatus());
        assertEquals(ConfigUtils.getMd5(input), file.getMetaData().getMd5());

        file.delete();
        assertEquals(ApplicationFile.ContentStatusDeleted, file.getMetaData().getStatus());
        assertTrue(file.getMetaData().getMd5().isEmpty());

        fooDir.delete();
        assertEquals(ApplicationFile.ContentStatusDeleted, fooDir.getMetaData().getStatus());
        assertTrue(file.getMetaData().getMd5().isEmpty());

        // non-existing file
        testFileName = "non-existing";
        file = getApplicationFile(Path.fromString(testFileName));
        assertNull(file.getMetaData());
    }

    @Test(expected = RuntimeException.class)
    public void testApplicationFileCantDeleteDirNotEmpty() throws Exception {
        getApplicationFile(Path.fromString("searchdefinitions")).delete();
    }

    @Test
    public void testReadingFromInputStream() throws Exception {
        String data = IOUtils.readAll(getApplicationFile(Path.fromString("files/foo.json")).createReader());
        assertTrue(data.contains("foo : foo"));
    }
    
    private void assertFileContent(String expected, String path) throws Exception {
        ApplicationFile file = getApplicationFile(Path.fromString(path));
        String actual = com.yahoo.io.IOUtils.readAll(file.createReader());
        assertEquals(expected, actual);
    }
    
    public abstract ApplicationFile getApplicationFile(Path path) throws Exception;

}
