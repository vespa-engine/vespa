// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.SerializationContext;
import com.yahoo.text.Utf8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * <p>A function defined by a ranking expression</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 * @author bratseth
 */
public class ExpressionFunction {

    private final String name;
    private final ImmutableList<String> arguments;
    private final RankingExpression body;

    /**
     * <p>Constructs a new function</p>
     *
     * @param name the name of this function
     * @param arguments its argument names
     * @param body the ranking expression that defines this function
     */
    public ExpressionFunction(String name, List<String> arguments, RankingExpression body) {
        this.name = name;
        this.arguments = arguments==null ? ImmutableList.of() : ImmutableList.copyOf(arguments);
        this.body = body;
    }

    public String getName() { return name; }

    /** Returns an immutable list of the arguments of this */
    public List<String> arguments() { return arguments; }

    public RankingExpression getBody() { return body; }

    /**
     * <p>Create and return an instance of this function based on the given
     * arguments. If function calls are nested, this call might produce
     * additional scripts.</p>
     *
     * @param context the context used to expand this
     * @param arguments the arguments to instantiate on.
     * @param path the expansion path leading to this.
     * @return the script function instance created.
     */
    public Instance expand(SerializationContext context, List<ExpressionNode> arguments, Deque<String> path) {
        Map<String, String> argumentBindings = new HashMap<>();
        for (int i = 0; i < this.arguments.size() && i < arguments.size(); ++i) {
            argumentBindings.put(this.arguments.get(i), arguments.get(i).toString(context, path, null));
        }
        return new Instance(toSymbol(argumentBindings), body.getRoot().toString(context.createBinding(argumentBindings), path, null));
    }

    /**
     * Returns a symbolic string that represents this function with a given
     * list of arguments. The arguments are mangled by hashing the string
     * representation of the argument expressions, so we might need to revisit
     * this if we start seeing collisions.
     *
     * @param  argumentBindings the bound arguments to include in the symbolic name.
     * @return the symbolic name for an instance of this function
     */
    private String toSymbol(Map<String, String> argumentBindings) {
        if (argumentBindings.isEmpty()) return name;

        StringBuilder ret = new StringBuilder();
        ret.append(name).append("@");
        for (Map.Entry<String,String> argumentBinding : argumentBindings.entrySet()) {
            ret.append(Long.toHexString(symbolCode(argumentBinding.getKey() + "=" + argumentBinding.getValue())));
            ret.append(".");
        }
        if (ret.toString().endsWith("."))
            ret.setLength(ret.length()-1);
        return ret.toString();
    }


    /**
     * <p>Returns a more unique hash code than what Java's own {@link
     * String#hashCode()} method would produce.</p>
     *
     * @param str The string to hash.
     * @return A 64 bit long hash code.
     */
    private static long symbolCode(String str) {
        try {
            MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] buf = md.digest(Utf8.toBytes(str));
            if (buf.length >= 8) {
                long ret = 0;
                for (int i = 0; i < 8; ++i) {
                    ret = (ret << 8) + (buf[i] & 0xff);
                }
                return ret;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new Error("java must always support SHA-1 message digest format", e);
        }
        return str.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
    
    /**
     * An instance of a serialization of this function, using a particular serialization context (by {@link
     * ExpressionFunction#expand})
     */
    public class Instance {

        private final String name;
        private final String expressionString;

        public Instance(String name, String expressionString) {
            this.name = name;
            this.expressionString = expressionString;
        }

        public String getName() {
            return name;
        }

        public String getExpressionString() {
            return expressionString;
        }

    }
}
