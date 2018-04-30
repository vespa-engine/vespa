// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch.reference.test;

import com.yahoo.searchlib.ranking.features.fieldmatch.reference.OptimalStringAlignmentDistance;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author  bratseth
 */
public class OptimalStringAlignmentTestCase {

    private final double delta = 0.0000000001;

    @Test
    public void testEditDistance() {
        // Edit distance, substitution, deletion, insertion, transposition, query, field, print?

        boolean print = false;
        assertEditDistance(0,0,0,0,0,"niels bohr","niels bohr",print);
        assertEditDistance(1,1,0,0,0,"niels","bohr",print);
        assertEditDistance(1,0,0,1,0,"niels","niels bohr",print);
        assertEditDistance(1,0,1,0,0,"niels bohr","bohr",print);
        assertEditDistance(1,0,0,0,1,"niels bohr","bohr niels",print);
        assertEditDistance(1,0,0,1,0,"niels bohr","niels henrik bohr",print);
        assertEditDistance(2,0,0,1,1,"niels bohr","bohr niels henrik",print);
        assertEditDistance(4,1,0,3,0,"niels bohr","niels henrik bor i kopenhagen",print);
        assertEditDistance(3,2,0,1,0,"niels bohr i kopenhagen","niels henrik bor i stockholm",print);
    }

    @Test
    public void testEditDistanceAsRelevance() {
        boolean print = false;
        assertEditDistance(2,0,0,2,0,"niels bohr","niels blah blah bohr",print);
        assertEditDistance(4,0,1,3,0,"niels bohr","bohr blah blah niels",print); // Not desired
        assertEditDistance(4,2,0,2,0,"niels bohr","koko blah blah bahia",print);
    }

    private void assertEditDistance(int total,int substitution,int deletion,int insertion,int transposition,String query,String field,boolean printResult) {
        assertEditDistance(total,substitution,deletion,insertion,transposition,query,field,printResult,false);
    }

    private void assertEditDistance(int total,int substitution,int deletion,int insertion,int transposition,String query,String field,boolean printResult,boolean printTable) {
        OptimalStringAlignmentDistance e=new OptimalStringAlignmentDistance();
        e.setPrintTable(printTable);
        e.calculate(query,field);

        if (printResult) {
            System.out.print(e.toString());
            System.out.println();
        }

        assertEquals("Substitutions",(float)substitution,e.getSubstitutions(), delta);
        assertEquals("Deletions",(float)deletion,e.getDeletions(), delta);
        assertEquals("Insertions",(float)insertion,e.getInsertions(), delta);
        assertEquals("Transpositions",(float)transposition,e.getTranspositions(), delta);
        assertEquals("Total",(float)total,e.getTotal(), delta);
    }

}
