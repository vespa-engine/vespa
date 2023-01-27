// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.text.XML;

import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;

/**
 * Class for writing XML in a simplified way.
 * <p>
 * Give a writer for it to write the XML directly to. If none is given a
 * StringWriter is used so you can call toString() on the class to get the
 * XML.
 * <p>
 * You build XML by calling beginTag(name), addAttribute(id, value), endTag().
 * Remember to close all your tags, or you'll get an exception when calling
 * toString(). If writing directly to a writer, call isFinalized to verify that
 * all tags have been closed.
 * <p>
 * The XML escaping tools only give an interface for escape from and to a string
 * value. Thus writing of all data here is also just available through strings.
 *
 * @author <a href="humbe@yahoo-inc.com">Haakon Humberset</a>
 */
@Deprecated
public class XmlStream {

    // Utility class to hold attributes internally until it's time to write them
    private static class Attribute {
        final String name;
        final String value;

        public Attribute(String name, Object value) {
            this.name = name;
            this.value = value.toString();
        }
    }

    private final StringWriter writer; // Writer to output XML to.
    private final Deque<String> tags = new ArrayDeque<String>();
    private String indent = "";
    // We write tags lazily for several reasons:
    //   - To allow recursive methods to have both parents and child add
    //     attributes to last tag, without giving child responsibility of
    //     closing or creating the tag.
    //   - Be able to check content before adding whitespace, such that we
    //     can add newlines if content is new tags for instance.
    // The cached variables here will be written with the flush() method.
    private String cachedTag = null;
    private final List<Attribute> cachedAttribute = new ArrayList<Attribute>();
    private final List<String> cachedContent = new ArrayList<String>();

    /**
     * Create an XmlStream writing to a StringWriter.
     * Fetch XML through toString() once you're done creating it.
     */
    public XmlStream() {
        writer = new StringWriter();
    }

    /**
     * Set an indent to use for pretty printing of XML. Default is no indent.
     *
     * @param indent the initial indentation
     */
    public void setIndent(String indent) {
        this.indent = indent;
    }

    /**
     * Check if all tags have been properly closed.
     *
     * @return true if all tags are closed
     */
    public boolean isFinalized() {
        return (tags.isEmpty() && cachedTag == null);
    }

    public String toString() {
        if (!isFinalized()) {
            throw new IllegalStateException("There are still" + " tag(s) that are not closed.");
        }
        StringWriter sw = writer; // Ensure we have string writer
        return sw.toString();
    }

    /**
     * Add a new XML tag with the given name.
     *
     * @param name the tag name
     */
    public void beginTag(String name) {
        if (!XML.isName(name)) {
            throw new IllegalArgumentException("The name '" + name
                    + "' cannot be used as an XML tag name. Legal names must adhere to"
                    + "http://www.w3.org/TR/2006/REC-xml11-20060816/#sec-common-syn");
        }
        if (cachedTag != null) flush(false);
        cachedTag = name;
    }

    /**
     * Add a new XML attribute to the last tag started.
     * The tag cannot already have had content added to it, or been ended.
     * If a null value is added, the attribute will be skipped.
     *
     * @param key   the attribute name
     * @param value the attribute value
     */
    public void addAttribute(String key, Object value) {
        if (value == null) {
            return;
        }
        if (cachedTag == null) {
            throw new IllegalStateException("There is no open tag to add attributes to.");
        }
        if (!XML.isName(key)) {
            throw new IllegalArgumentException("The name '" + key
                    + "' cannot be used as an XML attribute name. Legal names must adhere to"
                    + " http://www.w3.org/TR/2006/REC-xml11-20060816/#sec-common-syn");
        }
        cachedAttribute.add(new Attribute(key, value));
    }

    /**
     * Add content to the last tag.
     *
     * @param content the content to add to the last tag
     */
    public void addContent(String content) {
        if (cachedTag != null) {
            cachedContent.add(XML.xmlEscape(content, false));
        } else if (tags.isEmpty()) {
            throw new IllegalStateException("There is no open tag to add content to.");
        } else {
            for (int i = 0; i < tags.size(); ++i) {
                writer.write(indent);
            }
            writer.write(XML.xmlEscape(content, false));
            writer.write('\n');
        }
    }

    /**
     * Ends the last tag created.
     *
     */
    public void endTag() {
        if (cachedTag != null) {
            flush(true);
        } else if (tags.isEmpty()) {
            throw new IllegalStateException("Cannot end non-existing tag");
        } else {
            for (int i = 1; i < tags.size(); ++i) {
                writer.write(indent);
            }
            writer.write("</");
            writer.write(tags.removeFirst());
            writer.write(">\n");
        }
    }

    // Utility function to write whatever is cached.
    private void flush(boolean endTag) {
        if (cachedTag == null) {
            throw new IllegalStateException("Cannot write non-existing tag");
        }
        for (int i = 0; i < tags.size(); ++i) {
            writer.write(indent);
        }
        writer.write('<');
        writer.write(cachedTag);
        for (ListIterator<Attribute> it = cachedAttribute.listIterator(); it.hasNext();) {
            Attribute attr = it.next();
            writer.write(' ');
            writer.write(attr.name);
            writer.write("=\"");
            writer.write(XML.xmlEscape(attr.value, true));
            writer.write('"');
        }
        cachedAttribute.clear();
        if (cachedContent.isEmpty() && endTag) {
            writer.write("/>\n");
        } else if (cachedContent.isEmpty()) {
            writer.write(">\n");
            tags.addFirst(cachedTag);
        } else {
            writer.write(">");
            if (!endTag) {
                writer.write('\n');
                for (int i = 0; i <= tags.size(); ++i) {
                    writer.write(indent);
                }
            }
            for (String content : cachedContent) {
                writer.write(content);
            }
            cachedContent.clear();
            if (endTag) {
                writer.write("</");
                writer.write(cachedTag);
                writer.write(">\n");
            } else {
                writer.write('\n');
                tags.addFirst(cachedTag);
            }
        }
        cachedTag = null;
    }

}
