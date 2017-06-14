// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

public class ContentBaseTest {
    public static String getHosts() {
        return "<?xml version='1.0' encoding='utf-8' ?>" +
                "<hosts>  " +
                "  <host name='foo'>" +
                "    <alias>node0</alias>" +
                "  </host>" +
                "</hosts>";
    }
}
