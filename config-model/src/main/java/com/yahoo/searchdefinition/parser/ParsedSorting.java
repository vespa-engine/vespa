
package com.yahoo.searchdefinition.parser;

/**
 * This class holds the extracted information after parsing a "sorting"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
class ParsedSorting {

    enum Function { RAW, LOWERCASE, UCA }

    enum Strength { PRIMARY, SECONDARY, TERTIARY, QUATERNARY, IDENTICAL }

    void setAscending() {}
    void setDescending() {}

    void setLocale(String locale) {}

    void setFunction(Function func) {}
    void setStrength(Strength strength) {}
}
