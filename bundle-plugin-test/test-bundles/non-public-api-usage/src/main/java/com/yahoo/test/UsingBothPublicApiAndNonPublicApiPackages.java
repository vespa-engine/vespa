// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

public class UsingBothPublicApiAndNonPublicApiPackages {

    com.yahoo.vespa.defaults.Defaults publicFromDefaults = null;

    com.yahoo.text.BooleanParser publicFromVespajlib = null;


    ai.vespa.http.DomainName nonPublic1 = null;

    com.yahoo.io.ByteWriter nonPublic2 = null;

}
