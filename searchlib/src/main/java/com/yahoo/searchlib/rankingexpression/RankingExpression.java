// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression;

import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.parser.RankingExpressionParser;
import com.yahoo.searchlib.rankingexpression.parser.TokenMgrError;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.SerializationContext;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * <p>A ranking expression. Ranking expressions are used to calculate a rank score for a searched instance from a set of
 * <i>rank features</i>.</p>
 *
 * <p>A ranking expression wraps a expression node tree and may also optionally have a name.</p>
 *
 * <p>The identity of a ranking expression is decided by both its name and expression tree. Two expressions which
 * looks the same in string form are the same.</p>
 *
 * <h3>Simple usage</h3>
<pre><code>
try {
    MapContext context=new MapContext();
    context.put("one",1d);
    RankingExpression expression=new RankingExpression("10*if(i&gt;35,if(i&gt;one,if(i&gt;=670,4,8),if(i&gt;8000,5,3)),if(i==478,90,91))");
    double result=expression.evaluate(context);
   }
catch (ParseException e) {
    throw new RuntimeException(e);
}
</code></pre>
 *
 * <h3>Or, usage optimized for repeated evaluation of the same expression</h3>
<pre><code>
// Members in a class living across multiple evaluations
RankingExpression expression;
ArrayContext contextPrototype;

...

// Initialization of the above members (once)
// Create reusable, gbdt optimized expression and context.
// The expression is multithread-safe while the context created is not
try {
    RankingExpression expression=new RankingExpression("10*if(i&gt;35,if(i&gt;one,if(i&gt;=670,4,8),if(i&gt;8000,5,3)),if(i==478,90,91))");
    ArrayContext contextPrototype=new ArrayContext(expression);
    ExpressionOptimizer optimizer=new ExpressionOptimizer(); // Increases evaluation speed of gbdt form expressions by 3-4x
    OptimizationReport triviaAboutTheOptimization=optimizer.optimize(expression,contextPrototype);
}
catch (ParseException e) {
    throw new RuntimeException(e);
}

...

// Execution (many)
context=contextPrototype.clone(); // If evaluation is multithreaded - skip this if execution is single-threaded
context.put("one",1d);
double result=expression.evaluate(context);
</code></pre>
 *
 * @author Simon Thoresen
 * @author bratseth
 */
public class RankingExpression implements Serializable {

    private String name = "";
    private ExpressionNode root;

    /** Creates an anonymous ranking expression by consuming from the reader */
    public RankingExpression(Reader reader) throws ParseException {
        root = parse(reader);
    }

    /**
     * Creates a new ranking expression by consuming from the reader
     *
     * @param name the name of the ranking expression
     * @param reader the reader that contains the string to parse.
     * @throws ParseException if the string could not be parsed.
     */
    public RankingExpression(String name, Reader reader) throws ParseException {
        this.name = name;
        root = parse(reader);
    }

    /**
     * Creates a new ranking expression by consuming from the reader
     *
     * @param name the name of the ranking expression
     * @param expression the expression to parse.
     * @throws ParseException if the string could not be parsed.
     */
    public RankingExpression(String name, String expression) throws ParseException {
        try {
            this.name = name;
            if (expression == null || expression.length() == 0) {
                throw new IllegalArgumentException("Empty ranking expressions are not allowed");
            }
            root = parse(new StringReader(expression));
        }
        catch (ParseException e) {
            ParseException p = new ParseException("Could not parse '" + expression + "'");
            p.initCause(e);
            throw p;
        }
    }

    /**
     * Creates a ranking expression from a string
     *
     * @param expression The reader that contains the string to parse.
     * @throws ParseException if the string could not be parsed.
     */
    public RankingExpression(String expression) throws ParseException {
        this("", expression);
    }

    /**
     * Creates a ranking expression from a file. For convenience, the file.getName() up to any dot becomes the name of
     * this expression.
     *
     * @param file the name of the file whose content to parse.
     * @throws ParseException           if the string could not be parsed.
     * @throws IllegalArgumentException if the file could not be found
     */
    public RankingExpression(File file) throws ParseException {
        try {
            name = file.getName().split("\\.")[0];
            root = parse(new FileReader(file));
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Could not create a ranking expression", e);
        }
    }

    /**
     * Creates a named ranking expression from an expression root node.
     */
    public RankingExpression(String name, ExpressionNode root) {
        this.name = name;
        this.root = root;
    }

    /**
     * Creates a ranking expression from an expression root node.
     *
     * @param root The root node.
     */
    public RankingExpression(ExpressionNode root) {
        this.root = root;
    }

    /**
     * Parses the content of the reader object as an expression string.
     *
     * @param reader A reader object that contains an expression string.
     * @return An expression node that corresponds to the given string.
     * @throws ParseException if the string could not be parsed.
     */
    private static ExpressionNode parse(Reader reader) throws ParseException {
        try {
            return new RankingExpressionParser(reader).rankingExpression();
        }
        catch (TokenMgrError e) {
            throw new ParseException(e.getMessage());
        }
    }

    /**
     * Returns the name of this ranking expression, or "" if no name is set.
     *
     * @return The name of this expression.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this ranking expression.
     *
     * @param name The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the root of the expression tree of this expression.
     *
     * @return The root node.
     */
    public ExpressionNode getRoot() {
        return root;
    }

    /**
     * Sets the root of the expression tree of this expression.
     *
     * @param root The root node to set.
     */
    public void setRoot(ExpressionNode root) {
        this.root = root;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RankingExpression && toString().equals(obj.toString());
    }

    @Override
    public String toString() {
        if ("".equals(name)) {
            return root.toString();
        } else {
            return name + ": " + root.toString();
        }
    }

    /**
     * Creates the necessary rank properties required to implement this expression.
     *
     * @param macros the expression macros to expand.
     * @return a list of named rank properties required to implement this expression.
     */
    public Map<String, String> getRankProperties(List<ExpressionFunction> macros) {
        Map<String, ExpressionFunction> arg = new HashMap<>();
        for (ExpressionFunction function : macros) {
            arg.put(function.getName(), function);
        }
        Deque<String> path = new LinkedList<>();
        SerializationContext context = new SerializationContext(macros);
        String serializedRoot = root.toString(context, path, null);
        Map<String, String> serializedExpressions = context.serializedFunctions();
        serializedExpressions.put(propertyName(name), serializedRoot);
        return serializedExpressions;
    }

    /**
     * Returns the rank-property name for a given expression name.
     *
     * @param expressionName The expression name to mangle.
     * @return The property name.
     */
    public static String propertyName(String expressionName) {
        return "rankingExpression(" + expressionName + ").rankingScript";
    }

    /**
     * Validates the type correctness of the given expression with the given context and
     * returns the type this expression will produce from the given type context
     *
     * @throws IllegalArgumentException if this expression is not type correct in this context
     */
    public TensorType type(TypeContext context) {
        return root.type(context);
    }

    /**
     * Returns the value of evaluating this expression over the given context.
     *
     * @param context The variable bindings to use for this evaluation.
     * @return The evaluation result.
     * @throws IllegalArgumentException if there are variables which are not bound in the given map
     */
    public Value evaluate(Context context) {
        return root.evaluate(context);
    }

    /**
     * Creates a ranking expression from a string
     *
     * @throws IllegalArgumentException if the string is not a valid ranking expression
     */
    public static RankingExpression from(String expression) {
        try {
            return new RankingExpression(expression);
        }
        catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

}
