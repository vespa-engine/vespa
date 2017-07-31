package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.ParseResult;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * Created by olaa on 06/07/2017.
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
        double expectedSize = 63D;
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
        double expectedSize = 63D;
        assertEquals(expectedSize, hardwareInfo.getMinDiskAvailableGb(), DELTA);
    }

    @Test
    public void parseDiskType_should_find_fast_disk() throws Exception {
        diskRetriever = new DiskRetriever(hardwareInfo, commandExecutor);
        ArrayList<String> mockOutput = commandExecutor.outputFromString("Name  Rota \nsda 0");
        ParseResult parseResult = diskRetriever.parseDiskType(mockOutput);
        ParseResult expectedParseResult = new ParseResult("sda", "0");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void parseDiskType_should_not_find_fast_disk() throws Exception {
        ArrayList<String> mockOutput = commandExecutor.outputFromString("Name  Rota \nsda 1");
        ParseResult parseResult = diskRetriever.parseDiskType(mockOutput);
        ParseResult expectedParseResult = new ParseResult("sda", "1");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void parseDiskType_with_invalid_output_stream_should_not_find_disk_type() throws Exception {
        ArrayList<String> mockOutput = commandExecutor.outputFromString("Name  Rota \nsda x");
        ParseResult parseResult = diskRetriever.parseDiskType(mockOutput);
        ParseResult expectedParseResult = new ParseResult("sda", "x");
        assertEquals(expectedParseResult, parseResult);
        mockOutput = commandExecutor.outputFromString("Name  Rota");
        parseResult = diskRetriever.parseDiskType(mockOutput);
        expectedParseResult = new ParseResult("invalid", "invalid");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void parseDiskSize_should_find_size_from_file_and_insert_into_parseResult() throws Exception {
        String filepath = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/filesize";
        ArrayList<String> mockOutput = MockCommandExecutor.readFromFile(filepath);
        ParseResult parseResult = diskRetriever.parseDiskSize(mockOutput);
        ParseResult expectedParseResult = new ParseResult("44G", "63G");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void setDiskType_invalid_ParseResult_should_set_fastDisk_to_invalid() {
        ParseResult parseResult = new ParseResult("Invalid", "Invalid");
        diskRetriever.setDiskType(parseResult);
        HardwareInfo.DiskType expectedDiskType = HardwareInfo.DiskType.UNKNOWN;
        assertEquals(expectedDiskType, hardwareInfo.getDiskType());
    }

}