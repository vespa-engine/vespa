// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class GbdtModelTestCase {

    @Test
    public void requireThatFactoryMethodWorks() throws Exception {
        GbdtModel model = GbdtModel.fromXmlFile("src/test/files/gbdt.xml");
        assertEquals(10, model.trees().size());
        String exp = model.toRankingExpression();
        assertEquals(readFile("src/test/files/gbdt.expression").trim(), exp.trim());
        assertNotNull(new RankingExpression(exp));
    }

    @Test
    public void requireThatIllegalXmlThrowsException() throws Exception {
        assertIllegalXml("<Unknown />");
        assertIllegalXml("<DecisionTree />");
        assertIllegalXml("<DecisionTree>" +
                         "    <Unknown />" +
                         "</DecisionTree>");
        assertIllegalXml("<DecisionTree>" +
                         "    <Forest />" +
                         "</DecisionTree>");
        assertIllegalXml("<DecisionTree>" +
                         "    <Forest>" +
                         "        <Unknown />" +
                         "    </Forest>" +
                         "</DecisionTree>");
    }

    private static void assertIllegalXml(String xml) throws Exception {
        try {
            GbdtModel.fromXml(xml);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    private static String readFile(String file) throws IOException {
        StringBuilder ret = new StringBuilder();
        BufferedReader in = new BufferedReader(new FileReader(file));
        while (true) {
            String str = in.readLine();
            if (str == null) {
                break;
            }
            ret.append(str).append("\n");
        }
        return ret.toString();
    }
}
