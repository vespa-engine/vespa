// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.component.Version;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.Routable;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public abstract class MessagesTestBase {

    protected enum Language {
        JAVA,
        CPP
    }
    protected static final Set<Language> LANGUAGES = EnumSet.allOf(Language.class);

    protected final DocumentTypeManager docMan = new DocumentTypeManager();
    protected final LoadTypeSet loadTypes = new LoadTypeSet();
    protected final DocumentProtocol protocol = new DocumentProtocol(docMan, null, loadTypes);

    public MessagesTestBase() {
        DocumentTypeManagerConfigurer.configure(docMan, "file:./test/cfg/testdoc.cfg");
        loadTypes.addLoadType(34, "foo", DocumentProtocol.Priority.NORMAL_2);
    }

    @Test
    public void requireThatTestsPass() throws Exception {
        Map<Integer, RunnableTest> tests = new TreeMap<>();
        registerTests(tests);
        for (Map.Entry<Integer, RunnableTest> entry : tests.entrySet()) {
            entry.getValue().run();
        }
        if (shouldTestCoverage()) {
            assertCoverage(protocol.getRoutableTypes(version()), new ArrayList<>(tests.keySet()));
        }
    }

    /**
     * Returns the version to use for serialization.
     *
     * @return The version.
     */
    protected abstract Version version();

    /**
     * Registers the tests to run.
     */
    protected abstract void registerTests(Map<Integer, RunnableTest> out);

    /**
     * Returns whether or not to test message test coverage.
     */
    protected abstract boolean shouldTestCoverage();

    /**
     * Encodes the given routable using the current version of the test case.
     *
     * @param routable The routable to encode.
     * @return The encoded data.
     */
    public byte[] encode(Routable routable) {
        return protocol.encode(version(), routable);
    }

    /**
     * Decodes the given byte array using the current version of the test case.
     *
     * @param data The data to decode.
     * @return The decoded routable.
     */
    public Routable decode(byte[] data) {
        return protocol.decode(version(), data);
    }

    public String getPath(String filename) {
        return TestFileUtil.getPath(filename);
    }

    private boolean fileContentIsUnchanged(String path, byte[] dataToWrite) throws IOException {
        if (!Files.exists(Paths.get(path))) {
            return false;
        }
        byte[] existingData = TestFileUtil.readFile(path);
        return Arrays.equals(existingData, dataToWrite);
    }

    /**
     * Writes the content of the given routable to the given file.
     *
     * @param filename The name of the file to write to.
     * @param routable The routable to serialize.
     * @return The size of the written file.
     */
    public int serialize(String filename, Routable routable) {
        Version version = version();
        String path = getPath(version + "-java-" + filename + ".dat");
        byte[] data = protocol.encode(version, routable);
        assertNotNull(data);
        assertTrue(data.length > 0);
        try {
            if (fileContentIsUnchanged(path, data)) {
                System.out.println(String.format("Serialization for '%s' is unchanged; not overwriting it", path));
            } else {
                System.out.println(String.format("Serializing to '%s'..", path));
                // This only happens when protocol encoding has changed and takes place
                // during local development, not regular test runs.
                TestFileUtil.writeToFile(path, data);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertEquals(routable.getType(), protocol.decode(version, data).getType());
        return data.length;
    }

    /**
     * Reads the content of the given file and creates a corresponding routable.
     *
     * @param filename The name of the file to read from.
     * @param classId  The type that the routable must decode as.
     * @param lang     The language constant that dictates what file format to read from.
     * @return The decoded routable.
     */
    public Routable deserialize(String filename, int classId, Language lang) {
        Version version = version();
        String path = getPath(version + "-" + (lang == Language.JAVA ? "java" : "cpp") + "-" + filename + ".dat");
        System.out.println("Deserializing from '" + path + "'..");
        byte[] data;
        try {
            data = TestFileUtil.readFile(path);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        Routable ret = protocol.decode(version, data);
        assertNotNull(ret);
        assertEquals(classId, ret.getType());
        return ret;
    }

    private static void assertCoverage(List<Integer> registered, List<Integer> tested) {
        boolean ok = true;
        List<Integer> lst = new ArrayList<>(tested);
        for (Integer type : registered) {
            if (!lst.contains(type)) {
                System.err.println("Routable type " + type + " is registered in DocumentProtocol but not tested.");
                ok = false;
            } else {
                lst.remove(type);
            }
        }
        if (!lst.isEmpty()) {
            for (Integer type : lst) {
                System.err.println("Routable type " + type + " is tested but not registered in DocumentProtocol.");
            }
            ok = false;
        }
        assertTrue(ok);
    }

    protected static interface RunnableTest {

        public void run() throws Exception;
    }
}
