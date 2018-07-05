// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.config.FileReference;

import com.yahoo.filedistribution.fileacquirer.*;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Test of public API of MockFileAcquirer, since it is intended to be used by 3rd parties.
 * Do not place it in the same package as MockFileAcquirer.
 * @author Tony Vaagenes
 */
public class MockFileAcquirerTest {
    @org.junit.Test
    public void testReturnFile() throws Exception {
        File file = new File("/test");
        assertThat(waitFor(MockFileAcquirer.returnFile(file)),
                equalTo(file));
    }

    @org.junit.Test
    public void testReturnFiles() throws Exception {
        File file1 = new File("/test1");
        File file2 = new File("/test2");

        HashMap<String, File> map = new HashMap<String, File>();
        map.put("1", file1);
        map.put("2", file2);

        FileAcquirer fileAcquirer = MockFileAcquirer.returnFiles(map);

        assertThat(waitFor(fileAcquirer, createFileReference("1")),
                equalTo(file1));
        assertThat(waitFor(fileAcquirer, createFileReference("2")),
                equalTo(file2));
    }

    @org.junit.Test(expected = TimeoutException.class)
    public void testThrowTimeoutException() throws Exception {
        waitFor(MockFileAcquirer.throwTimeoutException());
    }

    @org.junit.Test(expected = FileReferenceDoesNotExistException.class)
    public void testThrowFileReferenceDoesNotExistException() throws Exception {
        waitFor(MockFileAcquirer.throwFileReferenceDoesNotExistException());
    }

    private File waitFor(FileAcquirer fileAcquirer) throws InterruptedException {
        return waitFor(fileAcquirer, null);
    }

    private File waitFor(FileAcquirer fileAcquirer, FileReference reference)
            throws InterruptedException {
        return fileAcquirer.waitFor(reference, 100, TimeUnit.SECONDS);
    }

    private FileReference createFileReference(String value) {
        try {
            Constructor<FileReference> constructors = FileReference.class.getDeclaredConstructor(String.class);
            constructors.setAccessible(true);
            return constructors.newInstance(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
