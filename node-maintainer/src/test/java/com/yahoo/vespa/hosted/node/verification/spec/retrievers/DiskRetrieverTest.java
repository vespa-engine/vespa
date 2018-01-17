// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author sgrostad
 * @author olaaun
 */

public class DiskRetrieverTest {

    private MockCommandExecutor commandExecutor;
    private HardwareInfo hardwareInfo;
    private DiskRetriever diskRetriever;
    private static String CAT_RESOURCE_PATH = "cat src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/";
    private static final double DELTA = 0.1;

    @Before
    public void setup() {
        hardwareInfo = new HardwareInfo();
        commandExecutor = new MockCommandExecutor();
        diskRetriever = new DiskRetriever(hardwareInfo, commandExecutor);
    }

    @Test
    public void updateInfo_should_store_diskType_and_diskSize_in_hardware_info() {
        commandExecutor.addCommand(CAT_RESOURCE_PATH + "DiskTypeFastDisk");
        commandExecutor.addCommand(CAT_RESOURCE_PATH + "filesize");
        diskRetriever.updateInfo();
        assertEquals(DiskType.FAST, hardwareInfo.getDiskType());
        double expectedSize = 1760.0;
        assertEquals(expectedSize, hardwareInfo.getMinDiskAvailableGb(), DELTA);
    }

    @Test
    public void updateDiskType__should_store_diskType_in_hardwareInfo() throws IOException {
        commandExecutor.addCommand(CAT_RESOURCE_PATH + "DiskTypeFastDisk");
        diskRetriever.updateDiskType();
        assertEquals(DiskType.FAST, hardwareInfo.getDiskType());
    }

    @Test
    public void updateDiskSize__should_store_diskSize_in_hardwareInfo() throws IOException {
        commandExecutor.addCommand(CAT_RESOURCE_PATH + "filesize");
        diskRetriever.updateDiskSize();
        double expectedSize = 1760.0;
        assertEquals(expectedSize, hardwareInfo.getMinDiskAvailableGb(), DELTA);
    }

    @Test
    public void parseDiskType_should_find_fast_disk() throws Exception {
        diskRetriever = new DiskRetriever(hardwareInfo, commandExecutor);
        List<String> mockOutput = commandExecutor.outputFromString("Name  Rota \nsda 0");
        ParseResult parseResult = diskRetriever.parseDiskType(mockOutput);
        ParseResult expectedParseResult = new ParseResult("sda", "0");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void parseDiskType_should_not_find_fast_disk() throws Exception {
        List<String> mockOutput = commandExecutor.outputFromString("Name  Rota \nsda 1");
        ParseResult parseResult = diskRetriever.parseDiskType(mockOutput);
        ParseResult expectedParseResult = new ParseResult("sda", "1");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void parseDiskType_with_invalid_outputstream_does_not_contain_searchword_should_throw_exception() throws Exception {
        List<String> mockOutput = commandExecutor.outputFromString("Name  Rota");
        try {
            ParseResult parseResult = diskRetriever.parseDiskType(mockOutput);
            fail("Should have thrown IOException when outputstream doesn't contain search word");
        } catch (IOException e) {
            String expectedExceptionMessage = "Parsing for disk type failed";
            assertEquals(expectedExceptionMessage, e.getMessage());
        }

    }

    @Test
    public void parseDiskSize_should_find_size_from_file_and_insert_into_parseResult() throws Exception {
        String filepath = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/filesize";
        List<String> mockOutput = MockCommandExecutor.readFromFile(filepath);
        List<ParseResult> parseResults = diskRetriever.parseDiskSize(mockOutput);
        ParseResult expectedParseResult1 = new ParseResult("Size", "799.65");
        assertEquals(expectedParseResult1, parseResults.get(0));
        ParseResult expectedParseResult2 = new ParseResult("Size", "960.35");
        assertEquals(expectedParseResult2, parseResults.get(1));
    }

    @Test
    public void setDiskType_invalid_ParseResult_should_set_fastDisk_to_invalid() {
        ParseResult parseResult = new ParseResult("Invalid", "Invalid");
        diskRetriever.setDiskType(parseResult);
        HardwareInfo.DiskType expectedDiskType = HardwareInfo.DiskType.UNKNOWN;
        assertEquals(expectedDiskType, hardwareInfo.getDiskType());
    }

}
