// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A stream wrapper which contains utility methods for writing xml.
 * All methods return this for convenience.
 * <p>
 * The methods of this writer can be used in conjunction with writing tags in raw form directly to the writer
 * if some care is taken to close start tags and insert line breaks explicitly. If all content is written
 * using these methods, start tags are closed and newlines inserted automatically as appropriate.
 *
 * @author bratseth
 * @author baldersheim
 */
public class XMLWriter extends ForwardWriter {

    /** Configuration */
    private final int maxIndentLevel, maxLineSeparatorLevel;

    /** The current list of parent tags */
    private final List<Utf8String> openTags = new ArrayList<>();
    private final List<Utf8String> unmodifiableOpenTags = Collections.unmodifiableList(openTags);

    /** Control state */
    private boolean inOpenStartTag, currentIsMultiline, isFirstInParent;

    /** Write markup directly to this with no encoding if it is non-null (an optimization) */
    private final boolean markupIsAscii;
    static private final Utf8String SPACE = new Utf8String(" ");
    static private final Utf8String INDENT = new Utf8String("  ");
    static private final Utf8String ATTRIBUTE_START = new Utf8String("=\"");
    static private final Utf8String ATTRIBUTE_END = new Utf8String("\"");
    static private final Utf8String ENCODING_START = new Utf8String("<?xml version=\"1.0\" encoding=\"");
    static private final Utf8String ENCODING_END = new Utf8String("\" ?>\n");
    static private final Utf8String LF = new Utf8String("\n");
    static private final Utf8String LT = new Utf8String("<");
    static private final Utf8String GT = new Utf8String(">");
    static private final Utf8String ELT = new Utf8String("</");
    static private final Utf8String EGT = new Utf8String("/>");
    /**
     * Creates an XML wrapper of a writer having maxIndentLevel=10 and maxLineSeparatorLevel=1
     *
     * @param writer the writer to which this writers (accessible from this by getWrapped)
     */
    public XMLWriter(Writer writer) {
        this(writer,10);
    }

    /**
     * Creates an XML wrapper of a writer having maxIndentLevel=10 and maxLineSeparatorLevel=1
     *
     * @param writer the writer to which this writers (accessible from this by getWrapped)
     * @param markupIsAscii set to false to make this encode markup (tags, attributes). By default encoding
     *        is skipped if the underlying stream uses utf encoding for performance (yes, this matters)
     */
    public XMLWriter(Writer writer,boolean markupIsAscii) {
        this(writer,10,markupIsAscii);
    }

    /**
     * Creates an XML wrapper of a writer having maxLineSeparatorLevel=1
     *
     * @param writer the writer to which this writers (accessible from this by getWrapped)
     * @param maxIndentLevel the max number of tag levels for which we'll continue to indent, or -1 to
     *        never indent. The top level tag is level 0, etc.
     */
    public XMLWriter(Writer writer,int maxIndentLevel) {
        this(writer,maxIndentLevel,1);
    }

    /**
     * Creates an XML wrapper of a writer having maxLineSeparatorLevel=1
     *
     * @param writer the writer to which this writers (accessible from this by getWrapped)
     * @param maxIndentLevel the max number of tag levels for which we'll continue to indent, or -1 to
     *        never indent. The top level tag is level 0, etc.
     * @param markupIsAscii set to false to make this encode markup (tags, attributes). By default encoding
     *        is skipped if the underlying stream uses utf encoding for performance (yes, this matters)
     */
    public XMLWriter(Writer writer,int maxIndentLevel,boolean markupIsAscii) {
        this(writer,maxIndentLevel,1,markupIsAscii);
    }

    /**
     * Creates an XML wrapper of a writer
     *
     * @param writer the writer to which this writers (accessible from this by getWrapped)
     * @param maxIndentLevel the max number of tag levels for which we'll continue to indent, or -1 to
     *        never indent. The top level tag is level 0, etc.
     * @param maxLineSeparatorLevel the max number of tag levels for which we'll add a blank line separator,
     *        or -1 to never add line separators.
     *        The top level tag is level 0, etc.
     */
    public XMLWriter(Writer writer,int maxIndentLevel,int maxLineSeparatorLevel) {
        this(writer,maxIndentLevel,maxLineSeparatorLevel,true);
    }

    /**
     * Creates an XML wrapper of a writer
     *
     * @param writer the writer to which this writers (accessible from this by getWrapped)
     * @param maxIndentLevel the max number of tag levels for which we'll continue to indent, or -1 to
     *        never indent. The top level tag is level 0, etc.
     * @param maxLineSeparatorLevel the max number of tag levels for which we'll add a blank line separator,
     *        or -1 to never add line separators.
     *        The top level tag is level 0, etc.
     * @param markupIsAscii set to false to make this encode markup (tags, attributes). By default encoding
     *        is skipped if the underlying stream uses utf encoding for performance (yes, this matters)
     */
    public XMLWriter(Writer writer,int maxIndentLevel,int maxLineSeparatorLevel,boolean markupIsAscii) {
        super(writer instanceof GenericWriter ? (GenericWriter)writer : new JavaWriterWriter(writer));
        this.maxIndentLevel=maxIndentLevel;
        this.maxLineSeparatorLevel=maxLineSeparatorLevel;
        this.markupIsAscii = markupIsAscii;
    }

    /** Returns the input writer as-is if it is an XMLWriter instance. Returns new XMLWriter(writer) otherwise */
    @SuppressWarnings("resource")
    public static XMLWriter from(Writer writer, int maxIndentLevel,int maxLineSeparatorLevel) {
        return (writer instanceof XMLWriter)
                ? (XMLWriter)writer
                : new XMLWriter(writer, maxIndentLevel, maxLineSeparatorLevel);
    }

    /** Returns the input writer as-is if it is an XMLWriter instance. Returns new XMLWriter(writer) otherwise */
    @SuppressWarnings("resource")
    public static XMLWriter from(Writer writer) {
        return (writer instanceof XMLWriter)
                ? (XMLWriter)writer
                : new XMLWriter(writer);
    }

    public Writer getWrapped() {
        return (getWriter() instanceof JavaWriterWriter) ? ((JavaWriterWriter)getWriter()).getWriter() : getWriter();
    }

    /** Writes the first line of an XML file */
    public void xmlHeader(String encoding) {
        w(ENCODING_START).w(encoding).w(ENCODING_END);
    }

    public XMLWriter openTag(String s) {
        return openTag(new Utf8String(s));
    }
    public XMLWriter openTag(Utf8String tag) {
        closeStartTag();
        if (openTags.size()>0) {
            w(LF);
            if (isFirstInParent && openTags.size()<=maxLineSeparatorLevel) {
                w(LF);
            }
            indent();
        }
        w(LT).w(tag);
        openTags.add(tag);
        inOpenStartTag=true;
        currentIsMultiline=false;
        isFirstInParent=true;
        return this;
    }

    public XMLWriter closeTag() {
        if (openTags.size()<=0) {
            throw new RuntimeException("Called closeTag() when no tag was open");
        }
        Utf8String lastOpenTag=openTags.remove(openTags.size()-1);

        if (inOpenStartTag) {// this tag has no content - use short form
            w(EGT);
        }
        else {
            if (currentIsMultiline) {
                w(LF).indent();
            }
            w(ELT).w(lastOpenTag).w(GT);
        }
        if (openTags.size()==0 || openTags.size()<=maxLineSeparatorLevel) {
            w(LF);
        }
        inOpenStartTag=false;
        currentIsMultiline=true; // When we go up from a subtag we are at a multiline tag (because it contains subtags)
        isFirstInParent=false;   // the next opened tag will not be first
        return this;
    }

    private XMLWriter indent() {
        for (int i=0; i<openTags.size() && i<maxIndentLevel; i++) {
            w(INDENT);
        }
        return this;
    }

    /**
     * Closes the start tag. Usually, it is not necessary to call this, as the other methods in this will do
     * the right thing as needed. However, this can be called explicitly to allow content or subtags to be written
     * by a regular write call which bypasses the logic in this.
     * If a start tag is not currently open this has no effect.
     */
    public XMLWriter closeStartTag() {
        if (!inOpenStartTag) return this;
        w(GT);
        inOpenStartTag=false;
        return this;
    }

    /**
     * Writes an attribute by XML.xmlEscape(value.toString(),false)
     *
     * @param name the name of the attribute. An exception is thrown if this is null
     * @param value the value of the attribute. The empty string if the attribute is null or empty
     */
    public XMLWriter forceAttribute(Utf8String name, Object value) {
        String stringValue = value!=null ? value.toString() : "";
        allowAttribute();
        return w(SPACE).w(name).w(ATTRIBUTE_START).wTranscode(XML.xmlEscape(stringValue,true)).w(ATTRIBUTE_END);
    }

    public XMLWriter forceAttribute(String name, Object value) {
        return forceAttribute(new Utf8String(name), value);
    }

    private void allowAttribute() {
        if (!inOpenStartTag) {
            throw new RuntimeException("Called writeAttribute() while not in an open start tag");
        }
    }
    /**
     * Writes an attribute by its utf8 value
     *
     * @param name the name of the attribute. An exception is thrown if this is null
     * @param value the value of the attribute. This method does nothing if the value is null or empty
     */
    public XMLWriter attribute(Utf8String name, AbstractUtf8Array value) {
        if (value.isEmpty()) return this;
        allowAttribute();
        return w(SPACE).w(name).w(ATTRIBUTE_START).w(value).w(ATTRIBUTE_END);
    }

    /**
     * Writes an attribute by its utf8 value
     *
     * @param name the name of the attribute. An exception is thrown if this is null
     * @param value the value of the attribute. This method does nothing if the value is null.
     */
    public XMLWriter attribute(Utf8String name, Number value) {
        if (value == null) return this;
        allowAttribute();
        return w(SPACE).w(name).w(ATTRIBUTE_START).w(value).w(ATTRIBUTE_END);
    }

    /**
     * Writes an attribute by its utf8 value
     *
     * @param name the name of the attribute. An exception is thrown if this is null
     * @param value the value of the attribute.
     */
    public XMLWriter attribute(Utf8String name, long value) {
        allowAttribute();
        return w(SPACE).w(name).w(ATTRIBUTE_START).w(value).w(ATTRIBUTE_END);
    }

    /**
     * Writes an attribute by its utf8 value
     *
     * @param name the name of the attribute. An exception is thrown if this is null
     * @param value the value of the attribute.
     */
    public XMLWriter attribute(Utf8String name, double value) {
        allowAttribute();
        return w(SPACE).w(name).w(ATTRIBUTE_START).w(value).w(ATTRIBUTE_END);
    }

    /**
     * Writes an attribute by its utf8 value
     *
     * @param name the name of the attribute. An exception is thrown if this is null
     * @param value the value of the attribute. This method does nothing if the value is null or empty
     */
    public XMLWriter attribute(Utf8String name, boolean value) {
        allowAttribute();
        return w(SPACE).w(name).w(ATTRIBUTE_START).w(value).w(ATTRIBUTE_END);
    }

    /**
     * Writes an attribute by XML.xmlEscape(value.toString(),false)
     *
     * @param name the name of the attribute. An exception is thrown if this is null
     * @param value the value of the attribute. This method does nothing if the value is null or empty
     */
    public XMLWriter attribute(Utf8String name, String value) {
        if ((value == null) || value.isEmpty()) return this;
        allowAttribute();
        return w(SPACE).w(name).w(ATTRIBUTE_START).wTranscode(XML.xmlEscape(value, true)).w(ATTRIBUTE_END);
    }

    public XMLWriter attribute(String name, Object value) {
        if (value==null) return this;
        return attribute(new Utf8String(name), value.toString());
    }

    /**
     * XML escapes and writes the content.toString(). If the content is null this does nothing but closing the start tag.
     *
     * @param content the content - output by XML.xmlEscape(content.toString())
     * @param multiline whether the content should be treated as multiline,
     *        such that the following end tag should appear on a new line
     */
    public XMLWriter content(Object content,boolean multiline) {
        closeStartTag();
        return (content==null)
                ? this
                : escapedContent(XML.xmlEscape(content.toString(),false),multiline);
    }

    /**
     * Writes the given string as-is. The content string <i>must</i> be XML escaped before calling this.
     * If the content is null this does nothing but closing the start tag.
     *
     * @param content the content - output by XML.xmlEscape(content.toString())
     * @param multiline whether the content should be treated as multiline,
     *        such that the following end tag should appear on a new line
     */
    public XMLWriter escapedContent(String content,boolean multiline) {
        closeStartTag();
        if (content==null) return this;
        if (multiline) currentIsMultiline=true;
        return wTranscode(content);
    }

    /**
     * Writes the given US-ASCII only string as-is.
     * If the content is <b>not</b> US-ASCII <i>only</i> this <i>may</i> cause
     * incorrectly encoded content to be written.
     * This is faster than using escapedContent as transcoding is skipped.
     * <p>
     * The content string <i>must</i> be XML escaped before calling this.
     * If the content is null this does nothing but closing the start tag.
     *
     * @param content the content - output by XML.xmlEscape(content.toString())
     * @param multiline whether the content should be treated as multiline,
     *        such that the following end tag should appear on a new line
     */
    public XMLWriter escapedAsciiContent(String content,boolean multiline) {
        closeStartTag();
        if (content==null) return this;
        if (multiline) currentIsMultiline=true;
        return w(content);
    }

    /**
     * Writes the given string. If markup is us ascii (default), and the wrapped writer encodes in UTF, this will write
     * the string <b>as is, with no transcoding</b> (for speed). Hence, this should never be used for just any content.
     *
     * @return this for consistency
     */
    private final XMLWriter w(String s) {
        return markupIsAscii ? w(new Utf8String(s)) : w(s);
    }

    private final XMLWriter w(AbstractUtf8Array utf8) {
        write(utf8);
        return this;
    }
    private final XMLWriter w(long v) {
        write(v);
        return this;
    }
    private final XMLWriter w(boolean v) {
        write(v);
        return this;
    }
    private final XMLWriter w(double v) {
        write(v);
        return this;
    }
    private final XMLWriter w(Number v) {
        write(v.toString());
        return this;
    }

    /** Calls write(s) and returns this. Use this for general content which must be transcoded */
    private final XMLWriter wTranscode(String s) {
        write(s);
        return this;
    }

    /**
     * Returns a read only view of the currently open tags we are within, sorted by highest to
     * lowest in the hierarchy
     * Only used for testing.
     */
    public List<Utf8String> openTags() { return unmodifiableOpenTags; }

    /**
     * Returns true if the immediate parent (i.e the last element in openTags)
     * is the tag with the given name
     */
    public boolean isIn(Utf8String containingTag) {
        return (openTags.size()!=0) && openTags.get(openTags.size()-1).equals(containingTag);
    }

    public boolean isIn(String containingTag) {
        return (openTags.size()!=0) && openTags.get(openTags.size()-1).equals(containingTag);
    }
}
