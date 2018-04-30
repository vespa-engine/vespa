// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import com.yahoo.io.ByteWriter;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.XMLField;
import com.yahoo.search.Result;
import com.yahoo.text.Utf8String;
import com.yahoo.text.XMLWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.logging.Logger;


/**
 * A wrapper for a template set, suitable for subclassing.
 *
 * <p>
 * A subclass of UserTemplate must implement header(), footer(), hit(),
 * hitFooter(), error() and noHits().
 *
 * @deprecated use a renderer instead
 * @author Steinar Knutsen
 */
@SuppressWarnings("deprecation")
@Deprecated // TODO: Remove on Vespa 7
public abstract class UserTemplate<T extends Writer> extends GenericTemplateSet {

    // &amp;
    private static final byte[] ampersand = new byte[] { 38, 97, 109, 112, 59 };

    // &lt;
    private static final byte[] lessThan = new byte[] { 38, 108, 116, 59 };
    // &gt;
    private static final byte[] greaterThan = new byte[] { 38, 103, 116, 59 };

    // \\u00
    private static final byte[] quotePrefix = new byte[] { 92, 117, 48, 48 };

    private static final Logger log = Logger.getLogger(UserTemplate.class.getName());

    /**
     * The signature of this constructor is the one which is invoked
     * in a production setting.
     */
    public UserTemplate(String name, String mimeType,
                        String encoding) {
        super(name, mimeType, encoding);
    }

    public UserTemplate(String name) {
        this(name,
                DEFAULT_MIMETYPE,
                DEFAULT_ENCODING
        );
    }

    /**
     * This is called once before each result is rendered using this template.
     * The returned writer is used in all subsequent calls. Use this if another (wrapper)
     * writer of the raw incoming writer is desired in the implementation of this template.
     * The class of the returned type must be given as a type argument to the template class,
     * to be able to implement methods taking this wrapper writer as the argument type.
     * This default implementation returns an XMLWriter.
     */
    @SuppressWarnings("unchecked")
    public T wrapWriter(Writer writer) {
        //FIXME: Hack
        return (T) XMLWriter.from(writer, 10, -1);
    }

    /**
     * Creates a new context suitable for this template.
     * The context may be reused for several evaluations, but not multiple
     * concurrent evaluations
     */
    public Context createContext() {
        return new MapContext();
    }


    /**
     * For internal use only
     * TODO: get rid of this method *
     */
    public boolean isDefaultTemplateSet() {
        return getClass().equals(TemplateSet.getDefault().getClass());
    }

    /**
     * Render the result set header.
     *
     * <p>
     * The result set is available in the context object under the name
     * "result".
     *
     * @param context
     *                wrapper which will contain, among other thing, the result
     *                set instance
     * @param writer
     *                the destination for rendering the result
     * @throws IOException
     *                 may be propagated from the writer
     */
    public abstract void header(Context context, T writer)
            throws IOException;

    /**
     * Render the result set footer.
     *
     * <p>
     * The result set is available in the context object under the name
     * "result".
     *
     * @param context
     *                wrapper which will contain, among other thing, the result
     *                set instance
     * @param writer
     *                the destination for rendering the result
     * @throws IOException
     *                 may be propagated from the writer
     */
    public abstract void footer(Context context, T writer)
            throws IOException;

    /**
     * Render a single top level hit.
     *
     * <p>
     * The result set is available in the context object under the name
     * "result". The hit itself as "hit", the index of the hit as "hitno", and
     * all the fields under their normal names.
     *
     * @param context
     *                wrapper which will contain, among other thing, the hit
     *                instance
     * @param writer
     *                the destination for rendering the hit
     * @throws IOException
     *                 may be propagated from the writer
     */
    public abstract void hit(Context context, T writer) throws IOException;

    /**
     * Render a footer for a single top level hit. A typical implementation may
     * do nothing.
     *
     * <p>
     * The result set is available in the context object under the name
     * "result". The hit itself as "hit", the index of the hit as "hitno", and
     * all the fields under their normal names.
     *
     * @param context
     *                wrapper which will contain, among other thing, the hit
     *                instance
     * @param writer
     *                the destination for rendering the hit
     * @throws IOException
     *                 may be propagated from the writer
     */
    public abstract void hitFooter(Context context, T writer)
            throws IOException;

    /**
     * Render the error message for a result set.
     *
     * <p>
     * The result set is available in the context object under the name
     * "result".
     *
     * @param context
     *                wrapper which will contain, among other thing, main error
     *                and result set instances.
     * @param writer
     *                the destination for rendering the hit
     * @throws IOException
     *                 may be propagated from the writer
     */
    public abstract void error(Context context, T writer)
            throws IOException;

    /**
     * Invoked when the result set has no hits.
     *
     * <p>
     * The result set is available in the context object under the name
     * "result".
     *
     * @param context
     *                wrapper which will contain, among other thing, the result
     *                set instance
     * @param writer
     *                the destination for rendering the hit
     * @throws IOException
     *                 may be propagated from the writer
     */
    public abstract void noHits(Context context, T writer)
            throws IOException;

    /**
     * Override this to add custom rendering for the query context of the result.
     * Only called when the query context is present.
     *
     * <p>
     * The result set is available in the context object under the name
     * "result". The query context is retrieved from the result by calling
     * result.getQuery.getContext(false)
     *
     * @param context
     *                wrapper which will contain, among other things, the result
     *                set instance
     * @param writer
     *                the destination for rendering the hit
     * @throws IOException
     *                 may be propagated from the writer
     */
    public void queryContext(Context context, T writer) throws IOException {
        Result result = (Result) context.get("result");
        result.getContext(false).render(writer);
    }

    /**
     * Dump UTF-8 byte array to writer, but escape low ASCII codes except
     * TAB, NL and CR, and escape ampersand, less than and greater than.
     *
     * <p>
     * It is presumed the writer is buffered (which is the case in normal
     * result rendering), as the method may perform a large number of write
     * operations.
     *
     * <p>
     * public only for testing.
     */
    public static void dumpAndXMLQuoteUTF8(ByteWriter writer, byte[] utf) throws java.io.IOException {
        int startDump = 0;

        for (int i = 0; i < utf.length; ++i) {
            byte b = utf[i];
            if (b < 0) {
                // Not ASCII, above character 127
                // Don't try to do something smart with UNICODE characters,
                // just pass them through.
            } else if (b < 32) {
                switch (b) {
                case 9:
                case 10:
                case 13:
                    break;
                default:
                    writer.append(utf, startDump, i - startDump);
                    startDump = i + 1;
                    quoteByte(writer, b);
                    break;
                }
            } else {
                // printable ASCII
                // quote special characters, otherwise do nothing
                switch (b) {
                // case 34: // double quote
                //     writer.append(utf, startDump, i - startDump);
                //     startDump = i + 1;
                //     writer.append(doubleQuote);
                //     break;
                case 38: // ampersand
                    writer.append(utf, startDump, i - startDump);
                    startDump = i + 1;
                    writer.append(ampersand);
                    break;
                case 60: // less than
                    writer.append(utf, startDump, i - startDump);
                    startDump = i + 1;
                    writer.append(lessThan);
                    break;
                case 62: // greater than
                    writer.append(utf, startDump, i - startDump);
                    startDump = i + 1;
                    writer.append(greaterThan);
                    break;
                }
            }
        }
        if (startDump < utf.length) {
            writer.append(utf, startDump, utf.length - startDump);
        }
    }

    /**
     * If the field is available as a UTF-8 byte array,
     * dump it to the writer.
     */
    public static boolean dumpBytes(ByteWriter writer,
                                    FastHit hit,
                                    String fieldName) throws java.io.IOException {
        return false;
    }

    private static void quoteByte(ByteWriter writer, byte b) throws java.io.IOException {
        byte[] quoted = new byte[2];
        writer.append(quotePrefix);
        quoted[0] = (byte) ((b >>> 4) + 0x30);
        if (quoted[0] > 0x39) {
            quoted[0] = (byte) (quoted[0] + 7);
        }
        quoted[1] = (byte) ((b & 0x0f) + 0x30);
        if (quoted[1] > 0x39) {
            quoted[1] = (byte) (quoted[1] + 7);
        }
        writer.append(quoted);
    }
}
