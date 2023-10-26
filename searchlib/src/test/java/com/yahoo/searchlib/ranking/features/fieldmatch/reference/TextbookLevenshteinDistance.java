// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch.reference;

/**
 * Textbook implementation from
 * <a href="http://en.wikibooks.org/wiki/Algorithm_implementation/Strings/Levenshtein_distance#Java">
 * Wikipedia algorithms</a>
 * Licensed under the Creative Commons Attribution-ShareAlike License
 */
public class TextbookLevenshteinDistance {

    private static int minimum(int a, int b, int c){
        if (a<=b && a<=c)
            return a;
        if (b<=a && b<=c)
            return b;
        return c;
    }

    public static int computeLevenshteinDistance(char[] str1, char[] str2) {
        int[][] distance = new int[str1.length+1][];

        for(int i=0; i<=str1.length; i++){
            distance[i] = new int[str2.length+1];
            distance[i][0] = i;
        }
        for(int j=0; j<=str2.length; j++)
            distance[0][j]=j;

        for(int i=1; i<=str1.length; i++)
            for(int j=1;j<=str2.length; j++)
                  distance[i][j]= minimum(distance[i-1][j]+1, distance[i][j-1]+1,
                                          distance[i-1][j-1]+((str1[i-1]==str2[j-1])?0:1));

        return distance[str1.length][str2.length];
    }

}
