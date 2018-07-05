// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class SimplePredicates {

    public static Predicate newPredicate() {
        return new Predicate() {

            @Override
            protected void appendTo(StringBuilder out) {
                out.append("<anon>");
            }

        };
    }

    public static Predicate newString(String str) {
        return new StringNode(str);
    }

    public static List<Predicate> newStrings(String... arr) {
        List<Predicate> ret = new ArrayList<>(arr.length);
        for (String str : arr) {
            ret.add(newString(str));
        }
        return ret;
    }

    private static class StringNode extends Predicate {

        final String str;

        StringNode(String str) {
            this.str = str;
        }

        @Override
        public int hashCode() {
            return str.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof StringNode)) {
                return false;
            }
            StringNode rhs = (StringNode)obj;
            if (!str.equals(rhs.str)) {
                return false;
            }
            return true;
        }

        @Override
        protected void appendTo(StringBuilder out) {
            out.append(str);
        }

    }
}

