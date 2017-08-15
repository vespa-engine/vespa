package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * Created by olaa on 03/07/2017.
 */
public class CPURetrieverTest {

    private static final String FILENAME = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/cpuinfoTest";
    private HardwareInfo hardwareInfo;
    private MockCommandExecutor commandExecutor;
    private CPURetriever cpu;
    private static final double DELTA = 0.1;

    @Before
    public void setup() {
        hardwareInfo = new HardwareInfo();
        commandExecutor = new MockCommandExecutor();
        cpu = new CPURetriever(hardwareInfo, commandExecutor);
    }

    @Test
    public void updateInfo_should_write_numOfCpuCores_to_hardware_info() throws Exception {
        commandExecutor.addCommand("cat " + FILENAME);
        cpu.updateInfo();
        double expectedAmountOfCores = 4;
        assertEquals(expectedAmountOfCores, hardwareInfo.getMinCpuCores(), DELTA);
    }

    @Test
    public void parseCPUInfoFile_should_return_valid_ArrayList() throws IOException {
        ArrayList<String> commandOutput = MockCommandExecutor.readFromFile(FILENAME);
        ArrayList<ParseResult> ParseResults = cpu.parseCPUInfoFile(commandOutput);
        String expectedSearchWord = "cpu MHz";
        String expectedValue = "2493.821";

        assertEquals(expectedSearchWord, ParseResults.get(0).getSearchWord());
        assertEquals(expectedValue, ParseResults.get(0).getValue());

        assertEquals(expectedSearchWord, ParseResults.get(1).getSearchWord());
        assertEquals(expectedValue, ParseResults.get(1).getValue());

        assertEquals(expectedSearchWord, ParseResults.get(2).getSearchWord());
        assertEquals(expectedValue, ParseResults.get(2).getValue());

        assertEquals(expectedSearchWord, ParseResults.get(3).getSearchWord());
        assertEquals(expectedValue, ParseResults.get(3).getValue());
    }

    @Test
    public void setCpuCores_counts_cores_correctly() {
        ArrayList<ParseResult> parseResults = new ArrayList<>();
        parseResults.add(new ParseResult("cpu MHz", "2000"));
        parseResults.add(new ParseResult("cpu MHz", "2000"));
        parseResults.add(new ParseResult("cpu MHz", "2000"));
        cpu.setCpuCores(parseResults);
        int expectedCpuCores = 3;
        assertEquals(expectedCpuCores, hardwareInfo.getMinCpuCores());
    }

}
