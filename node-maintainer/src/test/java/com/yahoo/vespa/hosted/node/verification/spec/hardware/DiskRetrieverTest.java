package com.yahoo.vespa.hosted.node.verification.spec.hardware;

import com.yahoo.vespa.hosted.node.verification.spec.parse.ParseResult;
import com.yahoo.vespa.hosted.node.verification.spec.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Created by olaa on 06/07/2017.
 */
public class DiskRetrieverTest {

    private MockCommandExecutor commandExecutor;
    private HardwareInfo hardwareInfo;
    private DiskRetriever diskRetriever;
    private String CAT_RESOURCE_PATH = "cat src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/";

    @Before
    public void setup() {
        hardwareInfo = new HardwareInfo();
        commandExecutor = new MockCommandExecutor();
        diskRetriever = new DiskRetriever(hardwareInfo, commandExecutor);
    }

    @Test
    public void test_updateInfo_should_store_diskType_and_diskSize_in_hardware_info() {
        commandExecutor.addCommand(CAT_RESOURCE_PATH + "DiskTypeFastDisk");
        commandExecutor.addCommand(CAT_RESOURCE_PATH + "filesize");
        diskRetriever.updateInfo();
        assertTrue(hardwareInfo.getFastDisk());
        double expectedSize = 63D;
        double delta = 0.1;
        assertEquals(expectedSize, hardwareInfo.getMinDiskAvailableGb(), delta);
    }

    @Test
    public void test_updateDiskType__should_store_diskType_in_hardwareInfo() throws IOException{
        commandExecutor.addCommand(CAT_RESOURCE_PATH + "DiskTypeFastDisk");
        diskRetriever.updateDiskType();
        assertTrue(hardwareInfo.getFastDisk());
    }

    @Test
    public void test_updateDiskSize__should_store_diskSize_in_hardwareInfo() throws IOException{
        commandExecutor.addCommand(CAT_RESOURCE_PATH + "filesize");
        diskRetriever.updateDiskSize();
        double expectedSize = 63D;
        double delta = 0.1;
        assertEquals(expectedSize, hardwareInfo.getMinDiskAvailableGb(), delta);
    }

    @Test
    public void test_parseDiskType_should_find_fast_disk() throws Exception {
        diskRetriever = new DiskRetriever(hardwareInfo, commandExecutor);
        ArrayList<String> mockOutput = commandExecutor.outputFromString("Name  Rota \nsda 0");
        ParseResult parseResult = diskRetriever.parseDiskType(mockOutput);
        ParseResult expectedParseResult = new ParseResult("sda","0");
        assertEquals(expectedParseResult ,parseResult);
    }

    @Test
    public void test_parseDiskType_should_not_find_fast_disk() throws Exception {
        ArrayList<String> mockOutput = commandExecutor.outputFromString("Name  Rota \nsda 1");
        ParseResult parseResult = diskRetriever.parseDiskType(mockOutput);
        ParseResult expectedParseResult = new ParseResult("sda","1");;
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void test_parseDiskType_with_invalid_output_stream_should_not_find_disk_type() throws Exception {
        ArrayList<String> mockOutput = commandExecutor.outputFromString("Name  Rota \nsda x");
        ParseResult parseResult = diskRetriever.parseDiskType(mockOutput);
        ParseResult expectedParseResult = new ParseResult("sda","x");
        assertEquals(expectedParseResult, parseResult);
        mockOutput = commandExecutor.outputFromString("Name  Rota");
        parseResult = diskRetriever.parseDiskType(mockOutput);
        expectedParseResult = new ParseResult("invalid","invalid");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void test_parseDiskSize_should_find_size_from_file_and_insert_into_parseResult() throws Exception{
        String filepath = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/filesize";
        ArrayList<String> mockOutput = commandExecutor.readFromFile(filepath);
        ParseResult parseResult = diskRetriever.parseDiskSize(mockOutput);
        ParseResult expectedParseResult = new ParseResult("44G","63G");
        assertEquals(expectedParseResult, parseResult);
    }

}