// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.tool;

import com.yahoo.jrt.Request;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class RpcInvokerTest {

    @Test
    public void test0Args() {
        assertCorrectArguments("");
    }

    @Test
    public void test1StringShorthanArgs() {
        assertCorrectArguments("foo");
    }

    @Test
    public void test2StringArgs() {
        assertCorrectArguments("s:foo s:bar");
    }

    @Test
    public void test2StringShorthandArgs() {
        assertCorrectArguments("foo bar");
    }

    protected void assertCorrectArguments(String argString) {
        RpcInvoker invoker=new RpcInvoker();
        List<String> args=toList(argString);
        Request request=invoker.createRequest("testmethod",args);
        for (int i=0; i<args.size(); i++) {
            // Strip type here if present
            String arg=args.get(i);
            if (arg.length()>=1 && arg.charAt(1)==':')
                arg=arg.substring(2);
            assertEquals(arg,request.parameters().get(i).toString());
        }
    }

    private List<String> toList(String argsString) {
        List<String> argsList=new ArrayList<String>();
        String[] argsArray=argsString.split(" ");
        for (String arg : argsArray) {
            if (arg.trim().length()==0) continue;
            argsList.add(arg);
        }
        return argsList;
    }

}
