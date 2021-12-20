// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class UrlCharSequenceNormalizerTest {

    @Test
    public void testNormalization() {
        String text = "xxx+yyy_.dude@mail.com foo bar@baz_bax https://host.tld/path?query=boo a@b §boo@boo";
        assertEquals("  foo  _bax   a@b § ",
                     UrlCharSequenceNormalizer.getInstance().normalize(text));
    }

}
