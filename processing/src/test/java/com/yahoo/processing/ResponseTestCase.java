// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing;

import com.yahoo.processing.response.ArrayDataList;
import com.yahoo.processing.response.DataList;
import com.yahoo.processing.test.ProcessorLibrary;
import com.yahoo.processing.test.Responses;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

/**
 * @author  bratseth
 */
@SuppressWarnings("unchecked")
public class ResponseTestCase {

    /**
     * Create a nested async tree of data elements, complete it recursively and check completion order.
     * Check the recursive toString printing along the way.
     * List variable names ends by numbers specifying the index of the list at each level.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testRecursiveCompletionAndToString() throws InterruptedException, ExecutionException {
        // create lists
        Request request = new Request();
        DataList list1 = ArrayDataList.create(request);
        DataList list11 = ArrayDataList.create(request);
        DataList list12 = ArrayDataList.createAsync(request);
        DataList list13 = ArrayDataList.createAsync(request);
        DataList list14 = ArrayDataList.create(request);
        DataList list121 = ArrayDataList.createAsync(request);
        DataList list122 = ArrayDataList.create(request);
        DataList list123 = ArrayDataList.createAsync(request);
        DataList list1231 = ArrayDataList.createAsync(request);
        DataList list1232 = ArrayDataList.create(request);
        // wire tree
        list1.add(list11);
        list1.add(list12);
        list1.add(list13);
        list1.add(list14);
        list12.add(list121);
        list12.add(list122);
        list12.add(list123);
        list123.add(list1231);
        list123.add(list1232);
        // add sync data elements
        list1.add(new ProcessorLibrary.StringData(request,"list1"));
        list12.add(new ProcessorLibrary.StringData(request,"list12"));
        list14.add(new ProcessorLibrary.StringData(request,"list14"));
        list122.add(new ProcessorLibrary.StringData(request,"list122"));
        list1231.add(new ProcessorLibrary.StringData(request,"list1231"));

        assertEqualsIgnoreObjectNumbers("Uncompleted tree, no incoming",uncompletedTreeUncompletedIncoming,Responses.recursiveToString(list1));

        // provide all async incoming data
        list12.incoming().markComplete();
        list121.incoming().addLast(new ProcessorLibrary.StringData(request,"list121async1"));
        list123.incoming().markComplete();
        list1231.incoming().add(new ProcessorLibrary.StringData(request,"list13231async1"));
        list1231.incoming().addLast(new ProcessorLibrary.StringData(request,"list1231async2"));
        list13.incoming().add(new ProcessorLibrary.StringData(request,"list13async1"));
        list13.incoming().addLast(new ProcessorLibrary.StringData(request,"list13async2"));

        assertEqualsIgnoreObjectNumbers("Uncompleted tree, incoming complete", uncompletedTreeCompletedIncoming, Responses.recursiveToString(list1));

        // complete all
        Response.recursiveComplete(list1).get();
        assertEqualsIgnoreObjectNumbers("Completed tree", completedTree, Responses.recursiveToString(list1));
    }

    private void assertEqualsIgnoreObjectNumbers(String explanation,String expected,String actual) {
        assertEquals(explanation,expected,removeObjectNumbers(actual));
    }

    /** Removes all object numbers (occurrences of @hexnumber) */
    private String removeObjectNumbers(String s) {
        return s.replaceAll("@[0-9a-f]+","");
    }

    private static final String uncompletedTreeUncompletedIncoming=
            "com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "    com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "    com.yahoo.processing.response.ArrayDataList [incomplete, incoming: incomplete, data []]\n" +
                    "        com.yahoo.processing.response.ArrayDataList [incomplete, incoming: incomplete, data []]\n" +
                    "        com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "            list122\n" +
                    "        com.yahoo.processing.response.ArrayDataList [incomplete, incoming: incomplete, data []]\n" +
                    "            com.yahoo.processing.response.ArrayDataList [incomplete, incoming: incomplete, data []]\n" +
                    "                list1231\n" +
                    "            com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "        list12\n" +
                    "    com.yahoo.processing.response.ArrayDataList [incomplete, incoming: incomplete, data []]\n" +
                    "    com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "        list14\n" +
                    "    list1\n";

    private static final String uncompletedTreeCompletedIncoming=
                    "com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "    com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "    com.yahoo.processing.response.ArrayDataList [incomplete, incoming: complete, data []]\n" +
                    "        com.yahoo.processing.response.ArrayDataList [incomplete, incoming: complete, data [list121async1]]\n" +
                    "        com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "            list122\n" +
                    "        com.yahoo.processing.response.ArrayDataList [incomplete, incoming: complete, data []]\n" +
                    "            com.yahoo.processing.response.ArrayDataList [incomplete, incoming: complete, data [list13231async1, list1231async2]]\n" +
                    "                list1231\n" +
                    "            com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "        list12\n" +
                    "    com.yahoo.processing.response.ArrayDataList [incomplete, incoming: complete, data [list13async1, list13async2]]\n" +
                    "    com.yahoo.processing.response.ArrayDataList [incomplete, (no incoming)]\n" +
                    "        list14\n" +
                    "    list1\n";

    private static final String completedTree=
                    "com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "    com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "    com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "        com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "            list121async1\n" +
                    "        com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "            list122\n" +
                    "        com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "            com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "                list1231\n" +
                    "                list13231async1\n" +
                    "                list1231async2\n" +
                    "            com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "        list12\n" +
                    "    com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "        list13async1\n" +
                    "        list13async2\n" +
                    "    com.yahoo.processing.response.ArrayDataList [completed]\n" +
                    "        list14\n" +
                    "    list1\n";
}
