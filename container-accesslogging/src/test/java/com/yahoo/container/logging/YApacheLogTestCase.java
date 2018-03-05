// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.container.core.AccessLogConfig;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author tonytv
 */
public class YApacheLogTestCase {

    private static String ipAddress = "152.200.54.243";
    private static final String EMPTY_REFERRER = "";
    private static final String EMPTY_USERAGENT = "";

    @Test
    public void testIt() throws Exception {
        AccessLogEntry entry = new AccessLogEntry();
        addCommonEntries(entry);

        entry.setAdSpaceID("676817");

        AccessLogEntry.AdInfo ad1 = new AccessLogEntry.AdInfo();
        AccessLogEntry.AdInfo ad2 = new AccessLogEntry.AdInfo();
        AccessLogEntry.AdInfo ad3 = new AccessLogEntry.AdInfo();

        ad1.setAdID("134263");
        ad1.setMatchID("29213.323310.2048738.221486");

        ad2.setAdID("127077");
        ad2.setMatchID("26036.316814.2030021.40354");

        ad3.setAdID("127611");
        ad3.setMatchID("26036.330708.2043270.64665");

        entry.addAdInfo(ad1);
        entry.addAdInfo(ad2);
        entry.addAdInfo(ad3);

        entry.setUserAgent("Mozilla/4.05 [en] (Win95; I)");
        entry.setWebfactsDigitalSignature("Wk6_cAzC`4IKP\7&)$");

        entry.setRemoteAddress("17.6.5.4");

         String expectedOutput =
            "98c836f3" +
            "36e38385" +
            "0001dc90" +
            "00002693" +
            "/Business/Companies/Financial_Services/Investment_Services/Mutual_Funds/" +
            "\u0005gMozilla/4.05 [en] (Win95; I)" +
            "\u0005dWk6_cAzC`4IKP\7&)$" +
            "\u0005AA17.6.5.4\u0001B12345" +
            "\u0005b\u0001676817" + //adinfo
            "\u0002134263" + "\u000329213.323310.2048738.221486" +
            "\u0002127077" + "\u000326036.316814.2030021.40354" +
            "\u0002127611" + "\u000326036.330708.2043270.64665";

        assertEquals(expectedOutput, new YApacheFormatter(entry).format());
    }

    private void addCommonEntries(AccessLogEntry entry) throws URISyntaxException {
        entry.setIpV4Address(ipAddress);
        entry.setTimeStamp(920880005L*1000);
        entry.setDurationBetweenRequestResponse(122);
        entry.setReturnedContentSize(9875);
        entry.setURI(new URI("/Business/Companies/Financial_Services/Investment_Services/Mutual_Funds/"));
        entry.setRemotePort(12345);
    }

    @Test
    public void test_remote_address_different_from_ip_address() throws Exception {
        AccessLogEntry entry = new AccessLogEntry();
        addCommonEntries(entry);

        entry.setRemoteAddress("FE80:0000:0000:0000:0202:B3FF:FE1E:8329");

        assertEquals("98c836f336e383850001dc9000002693/Business/Companies/Financial_Services/Investment_Services/Mutual_Funds/\u0005AAFE80:0000:0000:0000:0202:B3FF:FE1E:8329\u0001B12345",
                new YApacheFormatter(entry).format());
    }

    @Test
    public void test_remote_address_same_as_ip_address_does_not_cause_double_adding() throws Exception {
        AccessLogEntry entry = new AccessLogEntry();
        addCommonEntries(entry);
        entry.setRemoteAddress(ipAddress);

        assertThat(new YApacheFormatter(entry).format(), not(containsString(ipAddress)));
    }

    @Test
    public void test_status_code_stored_as_decimal() throws Exception {
        AccessLogEntry entry = new AccessLogEntry();
        addCommonEntries(entry);
        entry.setStatusCode(404);

        assertThat(new YApacheFormatter(entry).format(), containsString("s404"));
    }

    /**
     * author someone-else. Please rewrite this.
     */
    @Test
    public void testYApacheAccessLogWithDateNamingScheme() {
        AccessLogConfig.Builder builder = new AccessLogConfig.Builder().
                fileHandler(new AccessLogConfig.FileHandler.Builder().
                        pattern("yapachetest/testaccess.%Y%m%d%H%M%S").
                        symlink("testaccess"));
        AccessLogConfig config = new AccessLogConfig(builder);
        YApacheAccessLog accessLog = new YApacheAccessLog(config);
        try {
            final AccessLogEntry entry = newAccessLogEntry("hans");
            accessLog.log(entry);

            // wait for the log writing thread to do all its work, then check it did it right

            // check that symlink appears
            int waitTimeMs=0;
            while ( ! new File("yapachetest/testaccess").exists()) {
                Thread.sleep(2);
                waitTimeMs+=2;
                if (waitTimeMs>40*1000)
                    throw new RuntimeException("Waited 40 seconds for the configured symlink to be created, giving up");
            }
            // ..and check that the log entry is written
            waitTimeMs=0;
            while ( ! containsExpectedLine("00000000000000010000271000000008?query=hans","yapachetest/testaccess",0)) {
                Thread.sleep(2);
                waitTimeMs+=2;
                if (waitTimeMs>40*1000)
                    throw new RuntimeException("Waited 40 seconds for a log file entry to be written, giving up");
            }
        }
        catch (IOException e) {
            throw new RuntimeException("yapache log io exception",e);
        }
        catch (InterruptedException e) {
            throw new RuntimeException("Interruption",e);
        }
        finally {
            accessLog.shutdown();
            deleteDirectory("yapachetest");
        }
    }

    @Test
    public void testThatQueryWithEncodedCharactersIsLoggedInEncodedForm() {
        final String query = "%5E%3B%22";
        final AccessLogEntry entry = new AccessLogEntry();
        entry.setURI(newQueryUri(query));
        assertThat(new YApacheFormatter(entry).format(), containsString(query));
    }

    private AccessLogEntry newAccessLogEntry(final String query) {
        final AccessLogEntry entry = new AccessLogEntry();
        entry.setIpV4Address("0.0.0.0");
        entry.setUser("user");
        entry.setHttpMethod("GET");
        entry.setURI(newQueryUri(query));
        entry.setHttpVersion("HTTP/1.1");
        entry.setReferer(EMPTY_REFERRER);
        entry.setUserAgent(EMPTY_USERAGENT);
        entry.setRemoteAddress(new InetSocketAddress(0));
        entry.setTimeStamp(1000);
        entry.setDurationBetweenRequestResponse(10);
        entry.setReturnedContentSize(8);
        entry.setHitCounts(new HitCounts(0, 10, 1234, 0, 10));
        entry.setStatusCode(200);
        return entry;
    }

    /**
     * author someone-else. Please rewrite this.
     */
    @Test
    public void testYApacheAccessLogWithSequenceNamingScheme() throws IOException, InterruptedException {
        // try without existing files
        assertCorrectSequenceBehavior(1);

        // try with existing files
        try {
            new File("yapachetest2").mkdir();
            new File("yapachetest2/access.1").createNewFile();
            new File("yapachetest2/access.2").createNewFile();
            assertCorrectSequenceBehavior(3);
        }
        finally {
            deleteDirectory("yapachetest2");
        }
    }

    // Prefixes that don't collide with any in the specified log format.
    private static final char FIELD_KEY_REQUEST_EXTRA = '@';
    private static final char FIELD_KEY_RESPONSE_EXTRA = '#';


    private static List<String> subArrayAsList(final String[] fields, final int fromIndex) {
        return Arrays.asList(fields).subList(fromIndex, fields.length);
    }

    private static Map<Character, String> makeFieldMap(final Iterable<String> fields) {
        final Map<Character, String> fieldMap = new HashMap<>();
        fields.forEach(field -> {
            final String existingValue = fieldMap.putIfAbsent(field.charAt(0), field.substring(1));
            MatcherAssert.assertThat("Attempt to insert field " + field + " would overwrite value", existingValue, is(nullValue()));
        });
        return fieldMap;
    }

    /**
     * author someone-else. Please rewrite this.
     */
    private void assertCorrectSequenceBehavior(int startN) throws IOException, InterruptedException {
        AccessLogConfig.Builder builder = new AccessLogConfig.Builder().
                fileHandler(new AccessLogConfig.FileHandler.Builder().
                        pattern("yapachetest2/access").
                        rotateScheme(AccessLogConfig.FileHandler.RotateScheme.Enum.SEQUENCE));

        AccessLogConfig config = new AccessLogConfig(builder);
        YApacheAccessLog accessLog = new YApacheAccessLog(config);
        try {
            // log and rotate trice
            accessLog.log(newAccessLogEntry("query1"));
            accessLog.rotateNow();
            accessLog.log(newAccessLogEntry("query2"));
            accessLog.rotateNow();
            accessLog.log(newAccessLogEntry("query3.1"));
            accessLog.log(newAccessLogEntry("query3.2"));
            accessLog.rotateNow();
            accessLog.log(newAccessLogEntry("query4"));

            // wait for the last rotation, which should cause us to have an "access" file containing query4
            int waitTimeMs=0;
            while ( ! containsExpectedLine("00000000000000010000271000000008?query=query4","yapachetest2/access",0)) {
                Thread.sleep(2);
                waitTimeMs+=2;
                if (waitTimeMs>40*1000)
                    throw new RuntimeException("Waited 40 seconds for the right log file entry to be written, giving up");
            }

            // Should now have 3 rotated away files
            assertTrue(containsExpectedLine("00000000000000010000271000000008?query=query1","yapachetest2/access." + (startN+0),0));
            assertTrue(containsExpectedLine("00000000000000010000271000000008?query=query2","yapachetest2/access." + (startN+1),0));
            assertTrue(containsExpectedLine("00000000000000010000271000000008?query=query3.1","yapachetest2/access." + (startN+2),0));
            assertTrue(containsExpectedLine("00000000000000010000271000000008?query=query3.2","yapachetest2/access." + (startN+2),1));
        }
        finally {
            accessLog.shutdown();
            deleteDirectory("yapachetest2");
        }
    }

    private void deleteDirectory(String name) {
        File dir=new File(name);
        if (! dir.exists()) return;
        for (File f : dir.listFiles())
            f.delete();
        dir.delete();
    }

    /**
     * Returns whether this file contains this line as the first one.
     * If line is null: Checks that the file is empty.
     *
     * author someone-else. Please rewrite this.
     */
    private boolean containsExpectedLine(String line,String file,int lineNumber) throws IOException {
        BufferedReader reader=null;
        try {
            reader=new BufferedReader(new FileReader(file));
            if (line==null) return reader.readLine()==null;
            while (lineNumber-- > 0) {
                String l = reader.readLine();
            }
            String l = reader.readLine();
            return l != null && l.startsWith(line);
        }
        catch (FileNotFoundException e) {
            return false;
        }
        finally {
            if (reader!=null)
                reader.close();
        }
    }

    private static URI newQueryUri(final String query) {
        return URI.create("http://localhost?query=" + query);
    }

}
