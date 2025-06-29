// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.treenet;

import com.yahoo.searchlib.treenet.parser.TreeNetParser;
import com.yahoo.text.Utf8;

import java.io.FileNotFoundException;

/**
 * @author Simon Thoresen Hult
 */
public class TreeNetConverter {

    /**
     * Implements an application main function so that the converter can be used as a command-line tool.
     *
     * @param args List of arguments.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: TreeNetConverter <filename>");
            System.exit(1);
        }
        try {
            TreeNetParser parser = new TreeNetParser(Utf8.createReader(args[0]));
            System.out.println(parser.treeNet().toRankingExpression());
        } catch (FileNotFoundException e) {
            System.err.println("Could not find file '" + args[0] + "'.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("An error occured while parsing the content of file '" + args[0] + "': " + e);
            System.exit(1);
        }
    }
}
