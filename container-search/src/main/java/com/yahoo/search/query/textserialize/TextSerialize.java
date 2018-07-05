// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize;

import com.yahoo.prelude.query.Item;
import com.yahoo.search.query.textserialize.item.ItemContext;
import com.yahoo.search.query.textserialize.item.ItemFormHandler;
import com.yahoo.search.query.textserialize.parser.ParseException;
import com.yahoo.search.query.textserialize.parser.Parser;
import com.yahoo.search.query.textserialize.parser.TokenMgrError;
import com.yahoo.search.query.textserialize.serializer.QueryTreeSerializer;

import java.io.StringReader;

/**
 * @author Tony Vaagenes
 * Facade
 * Allows serializing/deserializing  a query to the programmatic format.
 */
public class TextSerialize {
    public static Item parse(String serializedQuery) {
        try {
            ItemContext context = new ItemContext();
            Object result = new Parser(new StringReader(serializedQuery.replace("'", "\"")), new ItemFormHandler(), context).start();
            context.connectItems();

            if (!(result instanceof Item)) {
                throw new RuntimeException("The serialized query '" + serializedQuery + "' did not evaluate to an Item" +
                        "(type = " + result.getClass() + ")");
            }
            return (Item) result;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (TokenMgrError e) {
            throw new RuntimeException(e);
        }
    }

    public static String serialize(Item item) {
        return new QueryTreeSerializer().serialize(item);
    }
}
