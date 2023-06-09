// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

public class UsingBothPublicApiAndNonPublicApiPackages {

    ai.vespa.lib.non_public.Foo    non_public_ai_vespa = null;

    ai.vespa.lib.public_api.Foo    public_ai_vespa = null;

    com.yahoo.lib.non_public.Foo   non_public_com_yahoo = null;

    com.yahoo.lib.public_api.Foo   public_com_yahoo = null;

}
