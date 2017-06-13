// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.vespa;

import com.yahoo.log.LogLevel;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Relevance;
import com.yahoo.text.XML;
import com.yahoo.text.DoubleParser;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * Parse Vespa XML results and create Result instances.
 *
 * <p> TODO: Ripe for a rewrite or major refactoring.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
@SuppressWarnings("deprecation")
public class ResultBuilder extends DefaultHandler {
    private static final String ERROR = "error";

    private static final String FIELD = "field";

    private static Logger log = Logger.getLogger(ResultBuilder.class.getName());

    /** Namespaces feature id (http://xml.org/sax/features/namespaces). */
    protected static final String NAMESPACES_FEATURE_ID = "http://xml.org/sax/features/namespaces";

    /**
     * Namespace prefixes feature id
     * (http://xml.org/sax/features/namespace-prefixes).
     */
    protected static final String NAMESPACE_PREFIXES_FEATURE_ID = "http://xml.org/sax/features/namespace-prefixes";

    /** Validation feature id (http://xml.org/sax/features/validation). */
    protected static final String VALIDATION_FEATURE_ID = "http://xml.org/sax/features/validation";

    /**
     * Schema validation feature id
     * (http://apache.org/xml/features/validation/schema).
     */
    protected static final String SCHEMA_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/schema";

    /**
     * Dynamic validation feature id
     * (http://apache.org/xml/features/validation/dynamic).
     */
    protected static final String DYNAMIC_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/dynamic";

    // default settings

    /** Default parser name. */
    protected static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";

    /** Default namespaces support (false). */
    protected static final boolean DEFAULT_NAMESPACES = false;

    /** Default namespace prefixes (false). */
    protected static final boolean DEFAULT_NAMESPACE_PREFIXES = false;

    /** Default validation support (false). */
    protected static final boolean DEFAULT_VALIDATION = false;

    /** Default Schema validation support (false). */
    protected static final boolean DEFAULT_SCHEMA_VALIDATION = false;

    /** Default dynamic validation support (false). */
    protected static final boolean DEFAULT_DYNAMIC_VALIDATION = false;

    private StringBuilder fieldContent;

    private String fieldName;

    private int fieldLevel = 0;

    private boolean hasLiteralTags = false;

    private Map<String, Object> hitFields = new HashMap<>();
    private String hitType;
    private String hitRelevance;
    private String hitSource;

    private int offset = 0;

    private List<Tag> tagStack = new ArrayList<>();

    private final XMLReader parser;

    private Query query;

    private Result result;

    private static enum ResultPart {
        ROOT, ERRORDETAILS, HIT, HITGROUP;
    }

    Deque<ResultPart> location = new ArrayDeque<>(10);

    private String currentErrorCode;

    private String currentError;

    private Deque<HitGroup> hitGroups = new ArrayDeque<>(5);

    private static class Tag {
        public final String name;

        /**
         * Offset is a number which is generated for all data and tags inside
         * fields, used to determine whether a tag was closed without enclosing
         * any characters or other tags.
         */
        public final int offset;

        public Tag(final String name, final int offset) {
            this.name = name;
            this.offset = offset;
        }

        @Override
        public String toString() {
            return name + '(' + Integer.valueOf(offset) + ')';
        }
    }

    /** Default constructor. */
    public ResultBuilder() throws RuntimeException {
        this(createParser());
    }

    public ResultBuilder(XMLReader parser) {
        this.parser = parser;
        this.parser.setContentHandler(this);
        this.parser.setErrorHandler(this);
    }

    public static XMLReader createParser() {
        ClassLoader savedContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(org.apache.xerces.parsers.SAXParser.class.getClassLoader());

        try {
            XMLReader reader = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
            setParserFeatures(reader);
            return reader;
        } catch (Exception e) {
            throw new RuntimeException("error: Unable to instantiate parser ("
                    + DEFAULT_PARSER_NAME + ")", e);
        } finally {
            Thread.currentThread().setContextClassLoader(savedContextClassLoader);
        }
    }

    private static void setParserFeatures(XMLReader reader) {
        try {
            reader.setFeature(NAMESPACES_FEATURE_ID, DEFAULT_NAMESPACES);
        } catch (SAXException e) {
            log.log(LogLevel.WARNING, "warning: Parser does not support feature ("
                    + NAMESPACES_FEATURE_ID + ")");
        }
        try {
            reader.setFeature(NAMESPACE_PREFIXES_FEATURE_ID,
                    DEFAULT_NAMESPACE_PREFIXES);
        } catch (SAXException e) {
            log.log(LogLevel.WARNING, "warning: Parser does not support feature ("
                    + NAMESPACE_PREFIXES_FEATURE_ID + ")");
        }
        try {
            reader.setFeature(VALIDATION_FEATURE_ID, DEFAULT_VALIDATION);
        } catch (SAXException e) {
            log.log(LogLevel.WARNING, "warning: Parser does not support feature ("
                    + VALIDATION_FEATURE_ID + ")");
        }
        try {
            reader.setFeature(SCHEMA_VALIDATION_FEATURE_ID,
                    DEFAULT_SCHEMA_VALIDATION);
        } catch (SAXNotRecognizedException e) {
            log.log(LogLevel.WARNING, "warning: Parser does not recognize feature ("
                    + SCHEMA_VALIDATION_FEATURE_ID + ")");

        } catch (SAXNotSupportedException e) {
            log.log(LogLevel.WARNING, "warning: Parser does not support feature ("
                    + SCHEMA_VALIDATION_FEATURE_ID + ")");
        }

        try {
            reader.setFeature(DYNAMIC_VALIDATION_FEATURE_ID,
                    DEFAULT_DYNAMIC_VALIDATION);
        } catch (SAXNotRecognizedException e) {
            log.log(LogLevel.WARNING, "warning: Parser does not recognize feature ("
                    + DYNAMIC_VALIDATION_FEATURE_ID + ")");

        } catch (SAXNotSupportedException e) {
            log.log(LogLevel.WARNING, "warning: Parser does not support feature ("
                    + DYNAMIC_VALIDATION_FEATURE_ID + ")");
        }
    }

    @Override
    public void startDocument() throws SAXException {
        reset();
        result = new Result(query);
        hitGroups.addFirst(result.hits());
        location.addFirst(ResultPart.ROOT);
        return;
    }

    private void reset() {
        result = null;
        fieldLevel = 0;
        hasLiteralTags = false;
        tagStack = null;
        fieldContent = null;
        offset = 0;
        currentError = null;
        currentErrorCode = null;
        hitGroups.clear();
        location.clear();
    }

    @Override
    public void startElement(String uri, String local, String raw,
            Attributes attrs) throws SAXException {
        // "Everybody" wants this switch to be moved into the
        // enum class instead, but in this case, I find the classic
        // approach more readable.
        switch (location.peekFirst()) {
        case HIT:
            if (fieldLevel > 0) {
                tagInField(raw, attrs, FIELD);
                ++offset;
                return;
            }
            if (FIELD.equals(raw)) {
                ++fieldLevel;
                fieldName = attrs.getValue("name");
                fieldContent = new StringBuilder();
                hasLiteralTags = false;
            }
            break;
        case ERRORDETAILS:
            if (fieldLevel > 0) {
                tagInField(raw, attrs, ERROR);
                ++offset;
                return;
            }
            if (ERROR.equals(raw)) {
                if (attrs != null) {
                    currentErrorCode = attrs.getValue("code");
                    currentError = attrs.getValue("error");
                }
                ++fieldLevel;
                fieldContent = new StringBuilder();
                hasLiteralTags = false;
            }
            break;
        case HITGROUP:
            if ("hit".equals(raw)) {
                startHit(attrs);
            } else if ("group".equals(raw)) {
                startHitGroup(attrs);
            }
            break;
        case ROOT:
            if ("hit".equals(raw)) {
                startHit(attrs);
            } else if ("errordetails".equals(raw)) {
                location.addFirst(ResultPart.ERRORDETAILS);
            } else if ("result".equals(raw)) {
                if (attrs != null) {
                    String total = attrs.getValue("total-hit-count");
                    if (total != null) {
                        result.setTotalHitCount(Long.valueOf(total));
                    }
                }
            } else if ("group".equals(raw)) {
                startHitGroup(attrs);
            } else if (ERROR.equals(raw)) {
                if (attrs != null) {
                    currentErrorCode = attrs.getValue("code");
                    fieldContent = new StringBuilder();
                }
            }
            break;
        }
        ++offset;
    }

    private void startHitGroup(Attributes attrs) {
        HitGroup g = new HitGroup();
        Set<String> types = g.types();

        final String source;
        if (attrs != null) {
            String groupType = attrs.getValue("type");
            if (groupType != null) {
                for (String s : groupType.split(" ")) {
                    if (s.length() > 0) {
                        types.add(s);
                    }
                }
            }

            source = attrs.getValue("source");
        } else {
            source = null;
        }

        g.setId((source != null) ? source : "dummy");

        hitGroups.peekFirst().add(g);
        hitGroups.addFirst(g);
        location.addFirst(ResultPart.HITGROUP);
    }

    private void startHit(Attributes attrs) {
        hitFields.clear();
        location.addFirst(ResultPart.HIT);
        if (attrs != null) {
            hitRelevance = attrs.getValue("relevancy");
            hitSource = attrs.getValue("source");
            hitType = attrs.getValue("type");
        } else {
            hitRelevance = null;
            hitSource = null;
            hitType = null;
        }
    }

    private void tagInField(String tag, Attributes attrs, String enclosingTag) {
        if (!hasLiteralTags) {
            hasLiteralTags = true;
            String fieldTillNow = XML.xmlEscape(fieldContent.toString(), false);
            fieldContent = new StringBuilder(fieldTillNow);
            tagStack = new ArrayList<>();
        }
        if (enclosingTag.equals(tag)) {
            ++fieldLevel;
        }
        if (tagStack.size() > 0) {
            Tag prevTag = tagStack.get(tagStack.size() - 1);
            if (prevTag != null && (prevTag.offset + 1) == offset) {
                fieldContent.append(">");
            }
        }
        fieldContent.append("<").append(tag);
        if (attrs != null) {
            int attrCount = attrs.getLength();
            for (int i = 0; i < attrCount; i++) {
                fieldContent.append(" ").append(attrs.getQName(i))
                        .append("=\"").append(
                                XML.xmlEscape(attrs.getValue(i), true)).append(
                                "\"");
            }
        }
        tagStack.add(new Tag(tag, offset));
    }

    private void endElementInField(String qName, String enclosingTag) {
        Tag prevTag = tagStack.get(tagStack.size() - 1);
        if (qName.equals(prevTag.name) && offset == (prevTag.offset + 1)) {
            fieldContent.append(" />");
        } else {
            fieldContent.append("</").append(qName).append('>');
        }
        if (prevTag.name.equals(qName)) {
            tagStack.remove(tagStack.size() - 1);
        }
    }

    private void endElementInHitField(String qName) {
        if (FIELD.equals(qName) && --fieldLevel == 0) {
            Object content;
            if (hasLiteralTags) {
                content = new XMLString(fieldContent.toString());
            } else {
                content = fieldContent.toString();
            }
            hitFields.put(fieldName, content);
            if ("collapseId".equals(fieldName)) {
                hitFields.put(fieldName, Integer.valueOf(content.toString()));
            }
            fieldName = null;
            fieldContent = null;
            tagStack = null;
        } else {
            Tag prevTag = tagStack.get(tagStack.size() - 1);
            if (qName.equals(prevTag.name) && offset == (prevTag.offset + 1)) {
                fieldContent.append(" />");
            } else {
                fieldContent.append("</").append(qName).append('>');
            }
            if (prevTag.name.equals(qName)) {
                tagStack.remove(tagStack.size() - 1);
            }
        }
    }
    @Override
    public void characters(char ch[], int start, int length)
            throws SAXException {

        switch (location.peekFirst()) {
        case ERRORDETAILS:
        case HIT:
            if (fieldLevel > 0) {
                if (hasLiteralTags) {
                    if (tagStack.size() > 0) {
                        Tag tag = tagStack.get(tagStack.size() - 1);
                        if (tag != null && (tag.offset + 1) == offset) {
                            fieldContent.append(">");
                        }
                    }
                    fieldContent.append(
                            XML.xmlEscape(new String(ch, start, length), false));
                } else {
                    fieldContent.append(ch, start, length);
                }
            }
            break;
        default:
            if (fieldContent != null) {
                fieldContent.append(ch, start, length);
            }
            break;
        }
        ++offset;
    }

    @Override
    public void ignorableWhitespace(char ch[], int start, int length)
            throws SAXException {
        return;
    }

    @Override
    public void processingInstruction(String target, String data)
            throws SAXException {
        return;
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {
        switch (location.peekFirst()) {
        case HITGROUP:
            if ("group".equals(qName)) {
                hitGroups.removeFirst();
                location.removeFirst();
            }
            break;
        case HIT:
            if (fieldLevel > 0) {
                endElementInHitField(qName);
            }  else if ("hit".equals(qName)) {
                 //assert(hitKeys.size() == hitValues.size());
                 //We try to get either uri or documentID and use that as id
                 Object docId = extractDocumentID();
                 Hit newHit = new Hit(docId.toString());
                 if (hitRelevance != null) newHit.setRelevance(new Relevance(DoubleParser.parse(hitRelevance)));
                 if(hitSource != null) newHit.setSource(hitSource);
                 if(hitType != null) {
                     for(String type: hitType.split(" ")) {
                         newHit.types().add(type);
                     }
                 }
                 for(Map.Entry<String, Object> field : hitFields.entrySet()) {
                     newHit.setField(field.getKey(), field.getValue());
                 }

                 hitGroups.peekFirst().add(newHit);
                 location.removeFirst();
            }
            break;
        case ERRORDETAILS:
            if (fieldLevel == 1 && ERROR.equals(qName)) {
                ErrorMessage error = new ErrorMessage(Integer.valueOf(currentErrorCode),
                        currentError,
                        fieldContent.toString());
                hitGroups.peekFirst().addError(error);
                currentError = null;
                currentErrorCode = null;
                fieldContent = null;
                tagStack = null;
                fieldLevel = 0;
            } else if (fieldLevel > 0) {
                endElementInField(qName, ERROR);
            } else if ("errordetails".equals(qName)) {
                location.removeFirst();
            }
            break;
        case ROOT:
            if (ERROR.equals(qName)) {
                ErrorMessage error = new ErrorMessage(Integer.valueOf(currentErrorCode),
                        fieldContent.toString());
                hitGroups.peekFirst().setError(error);
                currentErrorCode = null;
                fieldContent = null;
            }
            break;
        default:
            break;
        }
        ++offset;
    }

    private Object extractDocumentID() {
        Object docId = null;
        if (hitFields.containsKey("uri")) {
            docId = hitFields.get("uri");
        } else {
            final String documentId = "documentId";
            if (hitFields.containsKey(documentId)) {
                docId = hitFields.get(documentId);
            } else {
                final String lcDocumentId = toLowerCase(documentId);
                for (Map.Entry<String, Object> e : hitFields.entrySet()) {
                    String key = e.getKey();
                    // case insensitive matching, checking length first hoping to avoid some lowercasing
                    if (documentId.length() == key.length() && lcDocumentId.equals(toLowerCase(key))) {
                        docId = e.getValue();
                        break;
                    }
                }
            }
        }
        if (docId == null) {
            docId = "dummy";
            log.info("Results from vespa backend did not contain either uri or documentId");
        }
        return docId;
    }

    @Override
    public void warning(SAXParseException ex) throws SAXException {
        printError("Warning", ex);
    }

    @Override
    public void error(SAXParseException ex) throws SAXException {
        printError("Error", ex);
    }

    @Override
    public void fatalError(SAXParseException ex) throws SAXException {
        printError("Fatal Error", ex);
        // throw ex;
    }

    /** Prints the error message. */
    protected void printError(String type, SAXParseException ex) {
        StringBuilder errorMessage = new StringBuilder();

        errorMessage.append(type);
        if (ex != null) {
            String systemId = ex.getSystemId();
            if (systemId != null) {
                int index = systemId.lastIndexOf('/');
                if (index != -1)
                    systemId = systemId.substring(index + 1);
                errorMessage.append(' ').append(systemId);
            }
        }
        errorMessage.append(':')
            .append(ex.getLineNumber())
            .append(':')
            .append(ex.getColumnNumber())
            .append(": ")
            .append(ex.getMessage());
        log.log(LogLevel.WARNING, errorMessage.toString());

    }

    public Result parse(String identifier, Query query) {
        Result toReturn;

        setQuery(query);
        try {
            parser.parse(identifier);
        } catch (SAXParseException e) {
            // ignore
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Error parsing result from Vespa",e);
            Exception se = e;
            if (e instanceof SAXException) {
                se = ((SAXException) e).getException();
            }
            if (se != null)
                se.printStackTrace(System.err);
            else
                e.printStackTrace(System.err);
        }
        toReturn = result;
        reset();
        return toReturn;
    }

    public Result parse(InputSource input, Query query) {
        Result toReturn;

        setQuery(query);
        try {
            parser.parse(input);
        } catch (SAXParseException e) {
            // ignore
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Error parsing result from Vespa",e);
            Exception se = e;
            if (e instanceof SAXException) {
                se = ((SAXException) e).getException();
            }
            if (se != null)
                se.printStackTrace(System.err);
            else
                e.printStackTrace(System.err);
        }
        toReturn = result;
        reset();
        return toReturn;
    }


    private void setQuery(Query query) {
        this.query = query;
    }
}
