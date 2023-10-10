// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch.reference;

import java.util.Arrays;

/**
 * Implementation of optimal string alignment distance which also retains the four subdistances
 * and which uses 2*query length memory rather than field length*query length.
 * This class is not thread safe.
 *
 * @author  bratseth
 */
public class OptimalStringAlignmentDistance {

    /** The cell containg the last calculated edit distance */
    private Cell value=new Cell(0,0,0,0);

    // Temporary variables
    private Cell[] thisRow, previousRow, previousPreviousRow;

    private String[] query, field;

    private boolean printTable=false;

    public void calculate(String queryString,String fieldString) {
        this.query=queryString.split(" ");
        this.field=fieldString.split(" ");

        thisRow=new Cell[query.length+1];
        previousRow=new Cell[query.length+1];
        previousPreviousRow=new Cell[query.length+1];

        for(int i=0; i<=query.length; i++) {
            thisRow[i]=new Cell(i+1,0,0,0);
            previousRow[i]=new Cell(i,0,0,0);
            previousPreviousRow[i]=new Cell(i-1,0,0,0);
        }

        print(previousRow);

        for(int j=1;j<=field.length; j++) {
            thisRow[0].setTo(0,j,0,0);
            for(int i=1; i<=query.length; i++) {
                setCell(i,j);
            }

            print(thisRow);

            // Shift round thisRow -> previousRow -> previousPreviousRow -> thisRow
            Cell[] temporaryRow=thisRow;
            thisRow=previousPreviousRow;
            previousPreviousRow=previousRow;
            previousRow=temporaryRow;
        }
        value=previousRow[query.length];
    }

    private void setCell(int i,int j) {
        Cell thisCell=thisRow[i];
        Cell left=thisRow[i-1];
        Cell above=previousRow[i];
        Cell leftAbove=previousRow[i-1];

        boolean substitution=!query[i-1].equals(field[j-1]);

        int leftCost=left.getTotal()+1;
        int aboveCost=above.getTotal()+1;
        int leftAboveCost=leftAbove.getTotal() + ( substitution ? 1 : 0 );

        if (leftCost<=aboveCost && leftCost<=leftAboveCost) {
            thisCell.setTo(left);
            thisCell.addDeletion();
        }
        else if (aboveCost<=leftCost && aboveCost<=leftAboveCost) {
            thisCell.setTo(above);
            thisCell.addInsertion();
        }
        else {
            thisCell.setTo(leftAbove);
            if (substitution)
                thisCell.addSubstitution();
        }

        if (i>1 && j>1 && query[i-1].equals(field[j-2]) && query[i-2].equals(field[j-1]) ) {
            Cell twoAboveAndLeft=previousPreviousRow[i-2];
            int transpositionCost= + ( substitution ? 1 : 0);
            if (transpositionCost<thisCell.getTotal()) {
                thisCell.setTo(twoAboveAndLeft);
                thisCell.addTransposition();
            }
        }
    }

    private void setCell(Cell thisCell,Cell left, Cell above, Cell leftAbove, boolean substitution) {
        int a=left.getTotal()+1;
        int b=above.getTotal()+1;
        int c=leftAbove.getTotal();

        c+=substitution ? 1 : 0;

        if (a<=b && a<=c) {
            thisCell.setTo(left);
            thisCell.addDeletion();
        }
        else if (b<=a && b<=c) {
            thisCell.setTo(above);
            thisCell.addInsertion();
        }
        else {
            thisCell.setTo(leftAbove);
            if (substitution)
                thisCell.addSubstitution();
        }
    /*
        if(i > 1 and j > 1 and str1[i] = str2[j-1] and str1[i-1] = str2[j]) then
               d[i, j] := minimum(
                                d[i, j],
                                d[i-2, j-2] + cost   // transposition
                             )
                             */
    }

    public float getTotal() { return value.getTotal(); }
    public float getSubstitutions() { return value.getSubstitutions(); }
    public float getDeletions() { return value.getDeletions(); }
    public float getInsertions() { return value.getInsertions(); }
    public float getTranspositions() { return value.getTranspositions(); }

    /** Print the calculated edit distance table as we go */
    public void setPrintTable(boolean printTable) {
        this.printTable=printTable;
    }

    private void print(Cell[] row) {
        if (!printTable) return;
        for (Cell cell : row) {
            System.out.print(cell.toShortString());
            System.out.print(" ");
        }
        System.out.println();
    }

    /** Returns the current state as a string */
    public String toString() {
        StringBuffer b=new StringBuffer();
        b.append("Query: " + Arrays.toString(query) + "\n");
        b.append("Field: " + Arrays.toString(field) + "\n");
        b.append(value);
        return b.toString();
    }

    /** An edit distance table cell */
    public static final class Cell {

        private int deletions, insertions, substitutions, transpositions;

        public Cell(int deletions,int insertions,int substitutions,int transpositions) {
            setTo(deletions,insertions,substitutions,transpositions);
        }

        public void setTo(Cell cell) {
            this.deletions=cell.deletions;
            this.insertions=cell.insertions;
            this.substitutions=cell.substitutions;
            this.transpositions=cell.transpositions;
        }

        public void setTo(int deletions,int insertions,int substitutions,int transpositions) {
            this.deletions=deletions;
            this.insertions=insertions;
            this.substitutions=substitutions;
            this.transpositions=transpositions;
        }

        public int getTotal() {
            return deletions+insertions+substitutions+transpositions;
        }

        public void addDeletion() { deletions++; }
        public void addInsertion() { insertions++; }
        public void addSubstitution() { substitutions++; }
        public void addTransposition() { transpositions++; }

        public int getDeletions() { return deletions; }
        public int getInsertions() { return insertions; }
        public int getSubstitutions() { return substitutions; }
        public int getTranspositions() { return transpositions; }

        public String toString() {
            return "Total: " + getTotal() + ", substitutions: " + substitutions + ", deletions: " +
                    deletions + ", insertions: " + insertions + ", transpositions: " + transpositions + "\n";
        }

        public String toShortString() {
            return "(" + substitutions + "," + deletions + "," + insertions + "," + transpositions + ")";
        }


    }

}
