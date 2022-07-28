// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests streaming configuration deriving
 *
 * @author bratseth
 */
public class MailTestCase extends AbstractExportingTestCase {

    @Test
    void testMail() throws IOException, ParseException {
        String dir = "src/test/derived/mail/";
        ApplicationBuilder sb = new ApplicationBuilder();
        sb.addSchemaFile(dir + "mail.sd");
        assertCorrectDeriving(sb, dir, new TestableDeployLogger());
    }

}
