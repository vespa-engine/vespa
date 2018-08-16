package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployTester;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class LogSerializerTest {

    private static final LogRecordSerializer serializer = new LogRecordSerializer();
    private static final Path logsFile = Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/persistence/testdata/logs.json");

    @Test
    public void testSerialization() throws IOException {
        // Local, because it's not supposed to be used for anything else than verifying equality here!
        class EgalitarianLogRecord extends LogRecord {
            private EgalitarianLogRecord(Level level, String msg) {
                super(level, msg);
            }
            @Override
            public boolean equals(Object o) {
                if ( ! (o instanceof LogRecord)) return false;
                LogRecord record = (LogRecord) o;
                return    getSequenceNumber() == record.getSequenceNumber()
                          && getLevel() == record.getLevel()
                          && getMillis() == record.getMillis()
                          && getMessage().equals(record.getMessage());
            }
            @Override
            public int hashCode() { throw new AssertionError(); }
        }

        LogRecord  first = new EgalitarianLogRecord(LogLevel.INFO,     "First");   first.setMillis(   0);   first.setSequenceNumber(1);
        LogRecord second = new EgalitarianLogRecord(LogLevel.INFO,    "Second");  second.setMillis(   0);  second.setSequenceNumber(2);
        LogRecord  third = new EgalitarianLogRecord(LogLevel.DEBUG,    "Third");   third.setMillis(1000);   third.setSequenceNumber(3);
        LogRecord fourth = new EgalitarianLogRecord(LogLevel.WARNING, "Fourth");  fourth.setMillis(2000);  fourth.setSequenceNumber(4);

        Map<Step, List<LogRecord>> expected = ImmutableMap.of(deployReal, ImmutableList.of(first, third),
                                                              deployTester, ImmutableList.of(second, fourth));

        Map<Step, List<LogRecord>> stepRecords = serializer.recordsFromSlime(SlimeUtils.jsonToSlime(Files.readAllBytes(logsFile)));
        assertEquals(expected, stepRecords);

        assertEquals(expected, serializer.recordsFromSlime(serializer.recordsToSlime(stepRecords)));
    }

}
