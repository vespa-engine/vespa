// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import com.yahoo.yolean.Exceptions;

import java.io.FileNotFoundException;

/**
 * @author Simon Thoresen Hult
 */
public class GbdtConverter {

    /**
     * Implements an application main function so that the converter can be used as a command-line tool.
     *
     * @param args List of arguments.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: GbdtConverter <filename>");
            System.exit(1);
        }
        try {
            System.out.println(GbdtModel.fromXmlFile(args[0]).toRankingExpression());
        } catch (FileNotFoundException e) {
            System.err.println("Could not find file '" + args[0] + "'.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("An error occurred while parsing the content of file '" + args[0] + "': " +
                               Exceptions.toMessageString(e));
            System.exit(1);
        }
    }
}
