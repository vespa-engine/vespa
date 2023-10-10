// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.select.parser.SelectInput;
import com.yahoo.document.select.parser.SelectParser;
import com.yahoo.document.select.parser.TokenMgrException;
import com.yahoo.document.select.simple.SelectionParser;

/**
 * This class is used to find out in which locations a document might be in, if
 * it matches a given document selection string.
 *
 * @author <a href="mailto:humbe@yahoo-inc.com">H&aring;kon Humberset</a>
 */
public class BucketSelector {

    // A local reference to the factory used by the current application.
    private BucketIdFactory factory;

    /**
     * The bucket selector needs to be instantiated to be used, as it will
     * depend on config.
     *
     * @param factory The bucket factory is needed to get information of how
     *                bucket ids are put together.
     */
    public BucketSelector(BucketIdFactory factory) {
        this.factory = factory;
    }

    /**
     * Get the set of buckets that may contain documents that match the given
     * document selection, as long as the document selection does not result in
     * an unknown set of buckets. If it does, <code>null</code> will be
     * returned. This requires the caller to be aware of the meaning of these
     * return values, but also removes the need for redundant space utilization
     * when dealing with unknown bucket sets.
     *
     * @param selector The document selection string
     * @return a list of buckets with arbitrary number of location bits set,
     *         <i>or</i>, <code>null</code> if the document selection resulted
     *         in an unknown set
     * @throws ParseException if <code>selector</code> couldn't be parsed
     */
    public BucketSet getBucketList(String selector) throws ParseException {
         try {
             SelectionParser simple = new SelectionParser();
             if (simple.parse(selector) && (simple.getRemaining().length() == 0)) {
                 return simple.getNode().getBucketSet(factory);
             } else {
                SelectParser parser = new SelectParser(new SelectInput(selector));
                return parser.expression().getBucketSet(factory);
             }
        } catch (TokenMgrException e) {
            ParseException t = new ParseException();
            throw (ParseException) t.initCause(e);
        } catch (RuntimeException e) {
            ParseException t = new ParseException(
                    "Unexpected exception while parsing '" + selector + "'.");
            throw (ParseException) t.initCause(e);
        }
    }
}
