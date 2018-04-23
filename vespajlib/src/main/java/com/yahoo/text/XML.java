// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Static XML utility methods
 *
 * @author Bjorn Borud
 * @author Vegard Havdal
 * @author bratseth
 * @author Steinar Knutsen
 */
public class XML {

    /**
     * The point of this weird class and the jumble of abstract methods is
     * linking the scan for characters that must be quoted into the quoting
     * table, and making it actual work to make them go out of sync again.
     */
    private static abstract class LegalCharacters {

        // To quote http://www.w3.org/TR/REC-xml/ :
        // Char ::= #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] |
        // [#x10000-#x10FFFF]
        final boolean isLegal(int codepoint, boolean escapeLow, int stripCodePoint, boolean isAttribute) {
            if (codepoint == stripCodePoint) {
                return removeCodePoint();
            } else if (codepoint < ' ') {
                if (!escapeLow) {
                    return true;
                }
                switch (codepoint) {
                case 0x09:
                case 0x0a:
                case 0x0d:
                    return true;
                default:
                    return ctrlEscapeCodePoint(codepoint);
                }
            } else if (codepoint >= 0x20 && codepoint <= 0xd7ff) {
                switch (codepoint) {
                case '&':
                    return ampCodePoint();
                case '<':
                    return ltCodePoint();
                case '>':
                    return gtCodePoint();
                case '"':
                    return quotCodePoint(isAttribute);
                default:
                    return true;
                }
            } else if ((codepoint >= 0xe000 && codepoint <= 0xfffd)
                    || (codepoint >= 0x10000 && codepoint <= 0x10ffff)) {
                return true;
            } else {
                return filterCodePoint(codepoint);

            }
        }

        private boolean quotCodePoint(boolean isAttribute) {
            if (isAttribute) {
                quoteQuot();
                return false;
            } else {
                return true;
            }
        }

        private boolean filterCodePoint(int codepoint) {
            replace(codepoint);
            return false;
        }

        private boolean gtCodePoint() {
            quoteGt();
            return false;
        }

        private boolean ltCodePoint() {
            quoteLt();
            return false;
        }

        private boolean ampCodePoint() {
            quoteAmp();
            return false;
        }

        private boolean ctrlEscapeCodePoint(int codepoint) {
            ctrlEscape(codepoint);
            return false;
        }

        private boolean removeCodePoint() {
            remove();
            return false;
        }

        protected abstract void quoteQuot();

        protected abstract void quoteGt();

        protected abstract void quoteLt();

        protected abstract void quoteAmp();

        protected abstract void remove();

        protected abstract void ctrlEscape(int codepoint);

        protected abstract void replace(int codepoint);
    }

    private static final class Quote extends LegalCharacters {

        char[] lastQuoted;
        private static final char[] EMPTY = new char[0];
        private static final char[] REPLACEMENT_CHARACTER = "\ufffd".toCharArray();
        private static final char[] AMP = "&amp;".toCharArray();
        private static final char[] LT = "&lt;".toCharArray();
        private static final char[] GT = "&gt;".toCharArray();
        private static final char[] QUOT = "&quot;".toCharArray();

        @Override
        protected void remove() {
            lastQuoted = EMPTY;
        }

        @Override
        protected void replace(final int codepoint) {
            lastQuoted = REPLACEMENT_CHARACTER;
        }

        @Override
        protected void quoteQuot() {
            lastQuoted = QUOT;
        }

        @Override
        protected void quoteGt() {
            lastQuoted = GT;
        }

        @Override
        protected void quoteLt() {
            lastQuoted = LT;
        }

        @Override
        protected void quoteAmp() {
            lastQuoted = AMP;
        }

        @Override
        protected void ctrlEscape(final int codepoint) {
            lastQuoted = REPLACEMENT_CHARACTER;
        }
    }

    private static final class Scan extends LegalCharacters {

        @Override
        protected void quoteQuot() {
        }

        @Override
        protected void quoteGt() {
        }

        @Override
        protected void quoteLt() {
        }

        @Override
        protected void quoteAmp() {
        }

        @Override
        protected void remove() {
        }

        @Override
        protected void ctrlEscape(final int codepoint) {
        }

        @Override
        protected void replace(final int codepoint) {
        }
    }

    private static final Scan scanner = new Scan();

    /**
     * Replaces the characters that need to be escaped with their corresponding
     * character entities.
     *
     * @param s1
     *            String possibly containing characters that need to be escaped
     *            in XML
     *
     * @return Returns the input string with special characters that need to be
     *         escaped replaced by character entities.
     */
    public static String xmlEscape(String s1) {
        return xmlEscape(s1, true, true, null, -1);
    }

    /**
     * Replaces the characters that need to be escaped with their corresponding
     * character entities.
     *
     * @param s1
     *            String possibly containing characters that need to be escaped
     *            in XML
     * @param isAttribute
     *            Is the input string to be used as an attribute?
     *
     * @return Returns the input string with special characters that need to be
     *         escaped replaced by character entities
     */
    public static String xmlEscape(String s1, boolean isAttribute) {
        return xmlEscape(s1, isAttribute, true, null, -1);
    }

    /**
     * Replaces the characters that need to be escaped with their corresponding
     * character entities.
     *
     * @param s1
     *            String possibly containing characters that need to be escaped
     *            in XML
     * @param isAttribute
     *            Is the input string to be used as an attribute?
     *
     *
     * @param stripCharacter
     *            any occurrence of this character is removed from the string
     *
     * @return Returns the input string with special characters that need to be
     *         escaped replaced by character entities
     */
    public static String xmlEscape(String s1, boolean isAttribute, char stripCharacter) {
        return xmlEscape(s1, isAttribute, true, null, (int) stripCharacter);
    }

    /**
     * Replaces the characters that need to be escaped with their corresponding
     * character entities.
     *
     * @param s1
     *            String possibly containing characters that need to be escaped
     *            in XML
     * @param isAttribute
     *            Is the input string to be used as an attribute?
     *
     * @param escapeLowAscii
     *            Should ascii characters below 32 be escaped as well
     *
     * @return Returns the input string with special characters that need to be
     *         escaped replaced by character entities
     */
    public static String xmlEscape(String s1, boolean isAttribute, boolean escapeLowAscii) {
        return xmlEscape(s1, isAttribute, escapeLowAscii, null, -1);
    }

    /**
     * Replaces the characters that need to be escaped with their corresponding
     * character entities.
     *
     * @param s1
     *            String possibly containing characters that need to be escaped
     *            in XML
     * @param isAttribute
     *            Is the input string to be used as an attribute?
     *
     * @param escapeLowAscii
     *            Should ascii characters below 32 be escaped as well
     *
     * @param stripCharacter
     *            any occurrence of this character is removed from the string
     *
     * @return Returns the input string with special characters that need to be
     *         escaped replaced by character entities
     */
    public static String xmlEscape(String s1, boolean isAttribute, boolean escapeLowAscii, char stripCharacter) {
        return xmlEscape(s1, isAttribute, escapeLowAscii, null, (int) stripCharacter);
    }

    /**
     * Replaces the following:
     * <ul>
     * <li>all ascii codes less than 32 except 9 (tab), 10 (nl) and 13 (cr)
     * <li>ampersand (&amp;)
     * <li>less than (&lt;)
     * <li>larger than (&gt;)
     * <li>double quotes (&quot;) if isAttribute is <code>true</code>
     * </ul>
     * with character entities.
     *
     */
    public static String xmlEscape(String string, boolean isAttribute, StringBuilder buffer) {
        return xmlEscape(string, isAttribute, true, buffer, -1);
    }

    /**
     * Replaces the following:
     * <ul>
     * <li>all ascii codes less than 32 except 9 (tab), 10 (nl) and 13 (cr) if
     * escapeLowAscii is <code>true</code>
     * <li>ampersand (&amp;)
     * <li>less than (&lt;)
     * <li>larger than (&gt;)
     * <li>double quotes (&quot;) if isAttribute is <code>true</code>
     * </ul>
     * with character entities.
     *
     */
    public static String xmlEscape(String string, boolean isAttribute, boolean escapeLowAscii, StringBuilder buffer) {
        return xmlEscape(string, isAttribute, escapeLowAscii, buffer, -1);
    }

    /**
     * Replaces the following:
     * <ul>
     * <li>all ascii codes less than 32 except 9 (tab), 10 (nl) and 13 (cr) if
     * escapeLowAscii is <code>true</code>
     * <li>ampersand (&amp;)
     * <li>less than (&lt;)
     * <li>larger than (&gt;)
     * <li>double quotes (&quot;) if isAttribute is <code>true</code>
     * </ul>
     * with character entities.
     *
     * @param stripCodePoint any occurrence of this character is removed from the string
     */
    public static String xmlEscape(String string, boolean isAttribute, boolean escapeLowAscii,
                                   StringBuilder buffer, int stripCodePoint) {
        // buffer and stripCodePoint changed order in the signature compared to
        // the char based API to avoid wrong method being called

        // This is inner loop stuff, so we sacrifice a little for speed -
        // no copying will occur until a character needing escaping is found
        boolean legalCharacter = true;
        Quote escaper;
        int i = 0;

        for (i = 0; i < string.length() && legalCharacter; i = string.offsetByCodePoints(i, 1)) {
            legalCharacter = scanner.isLegal(string.codePointAt(i), escapeLowAscii, stripCodePoint, isAttribute);
        }
        if (legalCharacter) {
            return string;
        }

        i = string.offsetByCodePoints(i, -1); // Back to the char needing escaping
        escaper = new Quote();

        if (buffer == null) {
            buffer = new StringBuilder((int) (string.length() * 1.2));
        }

        // ugly appending zero length strings
        if (i > 0) {
            buffer.append(string.substring(0, i));
        }

        // i is at the first codepoint which needs replacing
        // Don't guard against double-escaping, as:
        // don't try to be clever (LCJ).
        for (; i < string.length(); i = string.offsetByCodePoints(i, 1)) {
            int codepoint = string.codePointAt(i);
            if (escaper.isLegal(codepoint, escapeLowAscii, stripCodePoint, isAttribute)) {
                buffer.appendCodePoint(codepoint);
            } else {
                buffer.append(escaper.lastQuoted);
            }
        }
        return buffer.toString();
    }

    /**
     * Returns the Document of an XML file reader
     *
     * @throws RuntimeException if the root Document cannot be returned
     */
    public static Document getDocument(Reader reader) {
        try {
            return getDocumentBuilder().parse(new InputSource(reader));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read '" + reader + "'", e);
        } catch (SAXParseException e) {
            throw new IllegalArgumentException("Could not parse '" + reader + "', error at line " + e.getLineNumber() + ", column " + e.getColumnNumber(), e);
        } catch (SAXException e) {
            throw new IllegalArgumentException("Could not parse '" + reader + "'", e);
        }
    }

    /**
     * Returns the Document of the string XML payload
     */
    public static Document getDocument(String string) {
        return getDocument(new StringReader(string));
    }

    /**
     * Creates a new XML DocumentBuilder
     *
     * @return a DocumentBuilder
     * @throws RuntimeException
     *             if we fail to create one
     */
    public static DocumentBuilder getDocumentBuilder() {
        return getDocumentBuilder("com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", null);
    }

    /**
     * Creates a new XML DocumentBuilder
     *
     * @param implementation
     *            which jaxp implementation should be used
     * @param classLoader
     *            which class loader should be used when getting a new
     *            DocumentBuilder
     * @throws RuntimeException
     *             if we fail to create one
     * @return a DocumentBuilder
     */
    public static DocumentBuilder getDocumentBuilder(String implementation, ClassLoader classLoader) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(implementation, classLoader);
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(true);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Could not create an XML builder");
        }
    }

    /**
     * Returns the child Element objects from a w3c dom spec
     *
     * @return List of elements. Empty list (never null) if none found or if the
     *         given element is null
     */
    public static List<Element> getChildren(Element spec) {
        List<Element> children = new ArrayList<>();
        if (spec == null) {
            return children;
        }

        NodeList childNodes = spec.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element) {
                children.add((Element) child);
            }
        }
        return children;
    }

    /**
     * Returns the child Element objects with given name from a w3c dom spec
     *
     * @return List of elements. Empty list (never null) if none found or the
     *         given element is null
     */
    public static List<Element> getChildren(Element spec, String name) {
        List<Element> ret = new ArrayList<>();
        if (spec == null) {
            return ret;
        }

        NodeList children = spec.getChildNodes();
        if (children == null) {
            return ret;
        }
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child != null && child instanceof Element) {
                if (child.getNodeName().equals(name)) {
                    ret.add((Element) child);
                }
            }
        }
        return ret;
    }

    /**
     * Gets the string contents of the given Element. Returns "", never null if
     * the element is null, or has no content
     */
    public static String getValue(Element e) {
        if (e == null) {
            return "";
        }
        Node child = e.getFirstChild();
        if (child == null) {
            return "";
        }
        return child.getNodeValue();
    }

    /** Returns the first child with the given name, or null if none */
    public static Element getChild(Element e, String name) {
        return (getChildren(e, name).size() >= 1) ? getChildren(e, name).get(0) : null;
    }

    /**
     * Returns the path to the given xml node, where each node name is separated
     * by the given separator string.
     *
     * @param n
     *            The xml node to find path to
     * @param sep
     *            The separator string
     * @return The path to the xml node as a String
     */
    public static String getNodePath(Node n, String sep) {
        if (n == null) {
            return "";
        }
        StringBuffer ret = new StringBuffer(n.getNodeName());
        while ((n.getParentNode() != null) && !(n.getParentNode() instanceof Document)) {
            n = n.getParentNode();
            ret.insert(0, sep).insert(0, n.getNodeName());
        }
        return ret.toString();
    }


    private static boolean inclusiveWithin(int x, int low, int high) {
        return low <= x && x <= high;
    }

    private static boolean nameStartSet(int codepoint) {
        // NameStartChar ::= ":" | [A-Z] | "_" | [a-z] | [#xC0-#xD6] |
        // [#xD8-#xF6] | [#xF8-#x2FF] | [#x370-#x37D] | [#x37F-#x1FFF] |
        // [#x200C-#x200D] | [#x2070-#x218F] | [#x2C00-#x2FEF] | [#x3001-#xD7FF]
        // | [#xF900-#xFDCF] | [#xFDF0-#xFFFD] | [#x10000-#xEFFFF]

        boolean valid;
        if (codepoint < 0xC0) {
            valid = inclusiveWithin(codepoint, 'a', 'z')
                    || inclusiveWithin(codepoint, 'A', 'Z') || codepoint == '_'
                    || codepoint == ':';
        } else {
            valid = inclusiveWithin(codepoint, 0xC0, 0xD6)
                    || inclusiveWithin(codepoint, 0xD8, 0xF6)
                    || inclusiveWithin(codepoint, 0xF8, 0x2FF)
                    || inclusiveWithin(codepoint, 0x370, 0x37D)
                    || inclusiveWithin(codepoint, 0x37F, 0x1FFF)
                    || inclusiveWithin(codepoint, 0x200C, 0x200D)
                    || inclusiveWithin(codepoint, 0x2070, 0x218F)
                    || inclusiveWithin(codepoint, 0x2C00, 0x2FEF)
                    || inclusiveWithin(codepoint, 0x3001, 0xD7FF)
                    || inclusiveWithin(codepoint, 0xF900, 0xFDCF)
                    || inclusiveWithin(codepoint, 0xFDF0, 0xFFFD)
                    || inclusiveWithin(codepoint, 0x10000, 0xEFFFF);
        }
        return valid;
    }

    private static boolean nameSetExceptStart(int codepoint) {
        // "-" | "." | [0-9] | #xB7 | [#x0300-#x036F] | [#x203F-#x2040]
        boolean valid;
        if (codepoint < 0xB7) {
            valid = inclusiveWithin(codepoint, '0', '9') || codepoint == '-'
                    || codepoint == '.';
        } else {

            valid = codepoint == '\u00B7'
                    || inclusiveWithin(codepoint, 0x300, 0x36F)
                    || inclusiveWithin(codepoint, 0x023F, 0x2040);
        }
        return valid;
    }

    private static boolean nameChar(int codepoint, boolean first) {
        // NameChar ::= NameStartChar | "-" | "." | [0-9] | #xB7 | [#x0300-#x036F] | [#x203F-#x2040]
        boolean valid = nameStartSet(codepoint);
        return first ? valid : valid || nameSetExceptStart(codepoint);
    }


    /**
     * Check whether the name of a tag or attribute conforms to <a
     * href="http://www.w3.org/TR/2006/REC-xml11-20060816/#sec-common-syn">XML
     * 1.1 (Second Edition)</a>. This does not check against reserved names, it
     * only checks the set of characters used.
     *
     * @param possibleName
     *            a possibly valid XML name
     * @return true if the name may be used as an XML tag or attribute name
     */
    public static boolean isName(CharSequence possibleName) {
        final int barrier = possibleName.length();
        int i = 0;
        boolean valid = true;
        boolean first = true;

        if (barrier < 1) {
            valid = false;
        }

        while (valid && i < barrier) {
            char c = possibleName.charAt(i++);
            if (Character.isHighSurrogate(c)) {
                valid = nameChar(Character.toCodePoint(c, possibleName.charAt(i++)), first);
            } else {
                valid = nameChar((int) c, first);
            }
            first = false;
        }
        return valid;
    }

}
