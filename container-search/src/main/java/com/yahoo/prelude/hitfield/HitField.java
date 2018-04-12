// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.yahoo.prelude.searcher.JuniperSearcher;
import com.yahoo.text.XML;

/**
 * Represents a tokenized string field in a Hit. The original raw content and the field
 * name cannot be modified. But the tokenized version can be retrieved and set.
 *
 * @author Lars Christian Jensen
 */
public class HitField {

    private final String name;
    private final String rawContent;
    private final boolean isCJK;

    private boolean xmlProperty;

    private List<FieldPart> tokenizedContent = null;
    private String content = null;


    private Object original;

    /**
     * @param f The field name
     * @param c The field content
     */
    public HitField(String f, String c) {
        this(f, c, c.indexOf(JuniperSearcher.RAW_HIGHLIGHT_CHAR) > -1);
    }

    /**
     * @param f The field name
     * @param c The field content
     */
    public HitField(String f, XMLString c) {
        this(f, c, c.toString().indexOf(JuniperSearcher.RAW_HIGHLIGHT_CHAR) > -1);
    }

    /**
     * @param f The field name
     * @param c The field content
     * @param cjk true if this is a cjk-document
     */
    public HitField(String f, String c, boolean cjk) {
        this(f, c, cjk, false);
    }

    /**
     * @param f The field name
     * @param c The field content
     * @param cjk true if this is a cjk-document
     */
    public HitField(String f, XMLString c, boolean cjk) {
        this(f, c.toString(), cjk, true);
    }

    /**
     * @param f The field name
     * @param c The field content
     * @param cjk true if this is a cjk-document
     * @param xmlProperty true if this should not quote XML syntax
     */
    public HitField(String f, String c, boolean cjk, boolean xmlProperty) {
        name = f;
        rawContent = c;
        content = null;
        isCJK = cjk;
        this.xmlProperty = xmlProperty;
    }


    /**
     * @return the name of this field
     */
    public String getName() {
        return name;
    }

    /**
     * @return the raw/original content of this field
     */
    public String getRawContent() {
        return rawContent;
    }

    private List<FieldPart> tokenizeUnknown() {
        List<FieldPart> pre = new ArrayList<>();
        if (rawContent.length() == 0)
            return pre;
        int i = 0;
        int j = 0;
        i = rawContent.indexOf('\u001E');
        if (i == 0) {
            pre.add(new SeparatorFieldPart(rawContent.substring(0,1)));
            j = 1;
            i = rawContent.indexOf('\u001E', j);
        }
        while(i != -1) {
            tokenizeSnippet(pre, rawContent.substring(j, i));
            pre.add(new SeparatorFieldPart(rawContent.substring(i,i+1)));
            i++;
            j = i;
            i = rawContent.indexOf('\u001E', j);
        }
        if (j < rawContent.length()) {
            tokenizeSnippet(pre, rawContent.substring(j));
        }
        return pre;
    }

    private boolean isAnnotationChar(char c) {
        return  c == AnnotateStringFieldPart.RAW_ANNOTATE_BEGIN_CHAR ||
                c == AnnotateStringFieldPart.RAW_ANNOTATE_SEPARATOR_CHAR ||
                c == AnnotateStringFieldPart.RAW_ANNOTATE_END_CHAR;
    }

    private void tokenizeSnippet(List<FieldPart> resultParts, String content) {
        int head = 0;
        int tail = 0;
        boolean justFinishedIncompleteAnnotation = false;
        int numRawHighLightChars = 0;
        List<FieldPart> localParts = new ArrayList<>();
        if (content.length() == 0) {
            return;
        }

        boolean prevHeadLetterOrDigital = Character.isLetterOrDigit(content.charAt(0));

        for ( ;head < content.length(); head++) {
            char headChar = content.charAt(head);
            if (isAnnotationChar(headChar)) {
                if (headChar == AnnotateStringFieldPart.RAW_ANNOTATE_BEGIN_CHAR) {
                    int nextHead = content.indexOf(AnnotateStringFieldPart.RAW_ANNOTATE_END_CHAR, head);
                    boolean incompleteAnnotation = (nextHead == -1);
                    boolean skippedInvalidHighlightChar = false;
                    if (head > tail) {
                        int currHead = head;
                        if (incompleteAnnotation &&
                            content.charAt(head-1) == JuniperSearcher.RAW_HIGHLIGHT_CHAR &&
                            numRawHighLightChars % 2 == 1)
                        {
                            currHead--; // skip invalid highlight char
                            skippedInvalidHighlightChar = true;
                        }
                        localParts.add(createToken(content.substring(tail, currHead), prevHeadLetterOrDigital));
                    }
                    if (!skippedInvalidHighlightChar) {
                        localParts.add(new AnnotateStringFieldPart(content, head));
                    }
                    head = nextHead;
                } else if (headChar == AnnotateStringFieldPart.RAW_ANNOTATE_SEPARATOR_CHAR) {
                    localParts.clear();
                    head = content.indexOf(AnnotateStringFieldPart.RAW_ANNOTATE_END_CHAR, head);
                    justFinishedIncompleteAnnotation = true;
                } else if (headChar == AnnotateStringFieldPart.RAW_ANNOTATE_END_CHAR) {
                    localParts.clear();
                    justFinishedIncompleteAnnotation = true;
                }
                if (head == -1) {
                    head = content.length();
                } else {
                    if (head + 1 < content.length()) {
                        prevHeadLetterOrDigital = Character.isLetterOrDigit(content.charAt(head + 1));
                    }
                }
                tail = head + 1;
            } else {
                if (headChar == JuniperSearcher.RAW_HIGHLIGHT_CHAR) {
                    if (justFinishedIncompleteAnnotation) {
                        tail = head + 1; // skip invalid highlight char
                    } else {
                        ++numRawHighLightChars;
                    }
                }
                boolean currHeadLetterOrDigital = Character.isLetterOrDigit(headChar);
                if (currHeadLetterOrDigital != prevHeadLetterOrDigital & head > tail) {
                    localParts.add(createToken(content.substring(tail, head), prevHeadLetterOrDigital));
                    tail = head;
                    prevHeadLetterOrDigital = currHeadLetterOrDigital;
                }
                justFinishedIncompleteAnnotation = false;
            }
        }
        if (head > tail) {
            localParts.add(createToken(content.substring(tail), prevHeadLetterOrDigital));
        }
        resultParts.addAll(localParts);
    }

    private FieldPart createToken(String substring, boolean isToken) {
        if (xmlProperty) {
            // TODO: Model this with something better than ImmutableFieldPart
            return new ImmutableFieldPart(substring, isToken);
        } else {
            return new StringFieldPart(substring, isToken);
        }
    }

    private List<FieldPart> tokenizePretokenized() {
        String[] pre = rawContent.split("\u001F+");
        List<FieldPart> tokenized = new ArrayList<>(pre.length);
        for (int i = 0; i < pre.length; i++) {
            tokenized.add(createToken(pre[i], true));
        }
        return tokenized;
    }

    private void tokenizeContent() {
        List<FieldPart> pre;
        if (isCJK) {
            pre = tokenizePretokenized();
        } else {
            pre = tokenizeUnknown();
        }
        setTokenizedContentUnchecked(pre);
    }
    /**
     * Get a list representation of the tokens in the content. This is
     * only a copy, changes here will not affect the HitField.
     *
     * @return a list containing the content in tokenized form.
     */
    public List<FieldPart> getTokenizedContent() {
        List<FieldPart> l = new ArrayList<>();
        for (ListIterator<FieldPart> i = tokenIterator(); i.hasNext(); ) {
            l.add(i.next());
        }
        return l;
    }

    private List<FieldPart> ensureTokenized() {
        if (tokenizedContent == null) {
            tokenizeContent();
        }
        return tokenizedContent;
    }
    /**
     * Return an iterator for the tokens, delimiters and markup elements
     * of the field.
     */
    public ListIterator<FieldPart> listIterator() {
        return new FieldIterator(ensureTokenized(),
                this);
    }

    /**
     * Return an iterator for the tokens in the field
     */
    public ListIterator<FieldPart> tokenIterator() {
        return new TokenFieldIterator(ensureTokenized(),
                this);
    }

    /**
     * Only FieldPart objects must be present in the list.
     *
     * @param list contains the new content of this HitField in tokenized form.
     */
    public void setTokenizedContent(List<FieldPart> list) {
        tokenizedContent = new ArrayList<>(list.size());
        for (Iterator<FieldPart> i = list.iterator(); i.hasNext(); ) {
            tokenizedContent.add(i.next());
        }
        // Must null content reference _before_ calling getContent()
        content = null;
    }

    private void setTokenizedContentUnchecked(List<FieldPart> list) {
        tokenizedContent = list;
        // Must null content reference _before_ calling getContent()
        content = null;
    }
    /**
     * @return the content of this field
     */
    public String getContent() {
        if (content == null) {
            StringBuilder buf = new StringBuilder();
            Iterator<FieldPart> iter = ensureTokenized().iterator();
            while(iter.hasNext()) {
                buf.append(iter.next().getContent());
            }
            content = buf.toString();
        }
        return content;
    }

    /**
     * @return the content of this field, using the arguments as bolding
     * tags
     */
    public String getContent(String boldOpenTag,
                             String boldCloseTag,
                             String separatorTag) {
        StringBuilder buf = new StringBuilder();
        Iterator<FieldPart> iter = ensureTokenized().iterator();
        while(iter.hasNext()) {
            FieldPart f = iter.next();
            if (f instanceof BoldOpenFieldPart
                && boldOpenTag != null
                && boldOpenTag.length() > 0)
                buf.append(boldOpenTag);
            else if (f instanceof BoldCloseFieldPart
                     && boldCloseTag != null
                     && boldCloseTag.length() > 0)
                buf.append(boldCloseTag);
            else if (f instanceof SeparatorFieldPart
                     && separatorTag != null
                     && separatorTag.length() > 0)
                buf.append(separatorTag);
            else
                buf.append(f.getContent());
        }
        return buf.toString();
    }

    public void markDirty() {
        content = null;
    }

    /**
     * @param inAttribute whether to quote quotation marks
     * @return the content of this field as an XML string
     */
    public String quotedContent(boolean inAttribute) {
        StringBuilder xml = new StringBuilder();
        Iterator<FieldPart> iter = ensureTokenized().iterator();
        while(iter.hasNext()) {
            FieldPart f = iter.next();
            if (f.isFinal())
                xml.append(f.getContent());
            else
                xml.append(XML.xmlEscape(f.getContent(), inAttribute));
        }
        return xml.toString();
    }

    /** Returns the content of this field, using the arguments as bolding tags, as an XML string */
    public String quotedContent(String boldOpenTag,
                                String boldCloseTag,
                                String separatorTag,
                                boolean inAttribute) {
        StringBuilder xml = new StringBuilder();
        Iterator<FieldPart> iter = ensureTokenized().iterator();
        while(iter.hasNext()) {
            FieldPart f = iter.next();
            if (f instanceof BoldOpenFieldPart
                && boldOpenTag != null
                && boldOpenTag.length() > 0)
                xml.append(boldOpenTag);
            else if (f instanceof BoldCloseFieldPart
                     && boldCloseTag != null
                     && boldCloseTag.length() > 0)
                xml.append(boldCloseTag);
            else if (f instanceof SeparatorFieldPart
                     && separatorTag != null
                     && separatorTag.length() > 0)
                xml.append(separatorTag);
            else if (f.isFinal())
                xml.append(f.getContent());
            else
                xml.append(XML.xmlEscape(f.getContent(), inAttribute));
        }
        return xml.toString();
    }
    /**
     * @return the content of the field, stripped of markup
     */
    public String bareContent(boolean XMLQuote, boolean inAttribute) {
        StringBuilder bareContent = new StringBuilder();
        Iterator<FieldPart> iter = ensureTokenized().iterator();
        while(iter.hasNext()) {
            FieldPart f = iter.next();
            if (f instanceof MarkupFieldPart)
                continue;

            if (XMLQuote)
                bareContent.append(XML.xmlEscape(f.getContent(), inAttribute));
            else
                bareContent.append(f.getContent());
        }
        return bareContent.toString();
    }

    public String toString() {
        return getContent();
    }

    /**
     * Fetch the object which (the String representation of) this HitField was
     * built from. This may be null as setting the original is optional.
     */
    public Object getOriginal() {
        return original;
    }

    /**
     * Optionally set the object which this HitField should represent.
     */
    public void setOriginal(Object original) {
        this.original = original;
    }

}
