// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression;

import com.yahoo.api.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.parser.RankingExpressionParser;
import com.yahoo.searchlib.rankingexpression.parser.TokenMgrException;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.text.Utf8;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Encapsulates the production rule 'featureList()' in the RankingExpressionParser.
 *
 * @author Simon Thoresen Hult
 */
@Beta
public class FeatureList implements Iterable<ReferenceNode> {

    private final List<ReferenceNode> features = new ArrayList<>();

    /**
     * Creates a new feature list by consuming from a reader object.
     *
     * @param reader The reader that contains the string to parse.
     * @throws ParseException Thrown if the string could not be parsed.
     */
    public FeatureList(Reader reader) throws ParseException {
        features.addAll(parse(reader));
    }

    /**
     * Creates a new feature list by parsing a string.
     *
     * @param list The string to parse.
     * @throws ParseException Thrown if the string could not be parsed.
     */
    public FeatureList(String list) throws ParseException {
        features.addAll(parse(new StringReader(list)));
    }

    /**
     * Creates a new feature list by reading the content of a file.
     *
     * @param file The file whose content to parse.
     * @throws ParseException        Thrown if the string could not be parsed.
     * @throws FileNotFoundException Thrown if the file specified could not be found.
     */
    public FeatureList(File file) throws ParseException, FileNotFoundException {
        features.addAll(parse(Utf8.createReader(file)));
    }

    /**
     * Parses the content of a reader object as a list of feature nodes.
     *
     * @param reader A reader object that contains an feature list.
     * @return A list of those features named in the string.
     * @throws ParseException if the string could not be parsed.
     */
    private static List<ReferenceNode> parse(Reader reader) throws ParseException {
        List<ReferenceNode> lst;
        try {
            lst = new RankingExpressionParser(reader).featureList();
        }
        catch (TokenMgrException e) {
            ParseException t = new ParseException();
            throw (ParseException)t.initCause(e);
        }
        return lst;
    }

    /**
     * Returns the number of features in this list.
     *
     * @return The size.
     */
    public int size() {
        return features.size();
    }

    /**
     * Returns the feature at the given index.
     *
     * @param i the index of the feature to return.
     * @return the feature at the given index.
     */
    public ReferenceNode get(int i) {
        return features.get(i);
    }

    @Override
    public int hashCode() {
        int ret = 0;
        for (ReferenceNode node : features) {
            ret += node.hashCode() * 17;
        }
        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FeatureList)) {
            return false;
        }
        FeatureList lst = (FeatureList)obj;
        if (features.size() != lst.features.size()) {
            return false;
        }
        for (int i = 0; i < features.size(); ++i) {
            if (!features.get(i).equals(lst.features.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        for (ReferenceNode node : this) {
            ret.append(node).append(" ");
        }
        return ret.toString();
    }

    @Override
    public Iterator<ReferenceNode> iterator() {
        return features.iterator();
    }

}
