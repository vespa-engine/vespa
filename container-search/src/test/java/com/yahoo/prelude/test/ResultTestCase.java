// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import java.util.Iterator;

import com.yahoo.search.Query;
import com.yahoo.search.result.Hit;
import com.yahoo.search.Result;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the result class
 *
 * @author bratseth
 */
public class ResultTestCase {

    @Test
    public void testHitOrdering() {
        Result result=new Result(new Query("dummy"));
        result.hits().add(new Hit("test:hit1",80));
        result.hits().add(new Hit("test:hit2",90));
        result.hits().add(new Hit("test:hit3",70));

        Iterator<?> hits= result.hits().deepIterator();
        assertEquals(new Hit("test:hit2",90),hits.next());
        assertEquals(new Hit("test:hit1",80),hits.next());
        assertEquals(new Hit("test:hit3",70),hits.next());
    }

    private void resultInit(Result result){
        result.hits().add(new Hit("test:hit1",80));
        result.hits().add(new Hit("test:hit2",90));
        result.hits().add(new Hit("test:hit3",70));
        result.hits().add(new Hit("test:hit4",40));
        result.hits().add(new Hit("test:hit5",50));
        result.hits().add(new Hit("test:hit6",20));
        result.hits().add(new Hit("test:hit7",20));
        result.hits().add(new Hit("test:hit8",55));
        result.hits().add(new Hit("test:hit9",75));
    }

    @Test
    public void testHitTrimming(){
        Result result=new Result(new Query("dummy"));

        //case 1: keep some hits in the middle
        resultInit(result);
        result.hits().trim(3, 3);
        assertEquals(3,result.getHitCount());
        Iterator<Hit> hits= result.hits().deepIterator();
        assertEquals(new Hit("test:hit3",70),hits.next());
        assertEquals(new Hit("test:hit8",55),hits.next());
        assertEquals(new Hit("test:hit5",50),hits.next());
        assertEquals(false,hits.hasNext());

        //case 2: keep some hits at the end
        result=new Result(new Query("dummy"));
        resultInit(result);
        result.hits().trim(5, 4);
        hits= result.hits().deepIterator();
        assertEquals(new Hit("test:hit5",50),hits.next());
        assertEquals(new Hit("test:hit4",40),hits.next());
        assertEquals(new Hit("test:hit6",20),hits.next());
        assertEquals(new Hit("test:hit7",20),hits.next());
        assertEquals(false,hits.hasNext());


        //case 3: keep some hits at the beginning
        result=new Result(new Query("dummy"));
        resultInit(result);
        result.hits().trim(0, 4);
        hits= result.hits().deepIterator();
        assertEquals(new Hit("test:hit2",90),hits.next());
        assertEquals(new Hit("test:hit1",80),hits.next());
        assertEquals(new Hit("test:hit9",75),hits.next());
        assertEquals(new Hit("test:hit3",70),hits.next());
        assertEquals(false,hits.hasNext());

        //case 4: keep no hits
        result=new Result(new Query("dummy"));
        resultInit(result);
        result.hits().trim(10, 4);
        hits= result.hits().deepIterator();
        assertEquals(false,hits.hasNext());
    }

    //This test is broken
    /*
    public void testNavigationalLinks() {
        Query query = new Query("/abc?query=dummy&def=ghi");
        Result result=new Result(query);
        result.setTotalHitCount(500);
        result.add(new Hit("test:hit1",80,1,null,true));
        assertEquals("/abc?query=dummy&def=ghi&offset=1",
                     result.getNextResultURL());
        assertEquals("/abc?query=dummy&def=ghi&offset=0",
                     result.getPreviousResultURL());
    }*/

}
