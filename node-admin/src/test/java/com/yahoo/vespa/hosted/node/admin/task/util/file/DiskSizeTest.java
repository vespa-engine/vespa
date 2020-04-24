package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class DiskSizeTest {

    @Test
    public void bytes_to_display_count_test() {
        assertEquals("-1 bytes", DiskSize.of(-1).asString());
        assertEquals("123 bytes", DiskSize.of(123).asString());
        assertEquals("1 kB", DiskSize.of(1_000).asString());
        assertEquals("15 MB", DiskSize.of(15_000_000).asString());
        assertEquals("123 GB", DiskSize.of(123_456_789_012L).asString());
        assertEquals("988 TB", DiskSize.of(987_654_321_098_765L).asString());
        assertEquals("987.7 TB", DiskSize.of(987_654_321_098_765L).asString(1));
        assertEquals("987.65 TB", DiskSize.of(987_654_321_098_765L).asString(2));
        assertEquals("2 PB", DiskSize.of(2_000_000_000_000_000L).asString());
        assertEquals("9 EB", DiskSize.of(Long.MAX_VALUE).asString());
    }

    @Test
    public void measure_non_existent_path() {
        var fs = TestFileSystem.create();
        assertEquals(DiskSize.ZERO, DiskSize.measure(fs.getPath("/fake/path"), null));
    }

    @Test
    public void partition_size() {
        var terminal = new TestTerminal();
        var fs = TestFileSystem.create();
        var output = "Filesystem           1024-blocks     Used Available Capacity Mounted on\n" +
                     "/dev/mapper/sys-root   423301760 15866680 390085824       4% /\n";
        terminal.expectCommand("df --portability --local --block-size 1K / 2>&1", 0, output);

        var partitionSize = DiskSize.partition(fs.getPath("/"), terminal.newCommandLine(new TestTaskContext()));
        assertEquals(DiskSize.of(423301760, DiskSize.Unit.kiB), partitionSize.total());
        assertEquals(DiskSize.of(390085824, DiskSize.Unit.kiB), partitionSize.available());
        assertEquals(DiskSize.of(15866680, DiskSize.Unit.kiB), partitionSize.used());
    }

}
