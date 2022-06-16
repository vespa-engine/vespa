// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.context;

import com.yahoo.processing.execution.Execution;
import com.yahoo.search.Query;
import com.yahoo.search.rendering.XmlRenderer;
import com.yahoo.text.XMLWriter;
import com.yahoo.yolean.trace.TraceNode;

import java.io.Writer;
import java.util.Iterator;


/**
 * A proxy to the Execution.trace() which exists for legacy reasons.
 * Calls to this is forwarded to owningQuery.getModel().getExecution().trace().
 *
 * @author  Steinar Knutsen
 * @author  bratseth
 */
public class QueryContext implements Cloneable {

    public static final String ID = "context";
    private Query owner;

    public QueryContext(int ignored,Query owner) {
        this.owner=owner;
    }

    //---------------- Public API ---------------------------------------------------------------------------------

    /** Adds a context message to this context */
    public void trace(String message, int traceLevel) {
        trace((Object)message, traceLevel);
    }

    public void trace(Object message, int traceLevel) {
        owner.getModel().getExecution().trace().trace(message,traceLevel);
    }
    /**
     * Adds a key-value which will be logged to the access log for this query (by doing toString() on the value).
     * Multiple values may be set to the same key. A value cannot be removed once set.
     */
    public void logValue(String key, Object value) {
        owner.getModel().getExecution().trace().logValue(key, value.toString());
    }

    /** Returns the values to be written to the access log for this */
    public Iterator<Execution.Trace.LogValue> logValueIterator() {
        return owner.getModel().getExecution().trace().logValueIterator();
    }

    /**
     * Adds a property key-value to this context.
     * If the same name is set multiple times, the behavior is thus:
     * <ul>
     *   <li>Within a single context (thread/query clone), the last value set is used</li>
     *   <li>Across multiple traces, the <i>last</i> value from the <i>last</i> deepest nested thread/clone is used.
     *       In the case of multiple threads writing the value concurrently to their clone, it is of course undefined
     *       which one will be used.</li>
     * </ul>
     *
     * @param name the name of the property
     * @param value the value of the property, or null to set this property to null
     */
    public void setProperty(String name,Object value) {
        owner.getModel().getExecution().trace().setProperty(name,value);
    }

    /**
     * Returns a property set anywhere in this context.
     * Note that even though this call is itself "thread robust", the object values returned
     * may in some scenarios not be written behind a synchronization barrier, so when accessing
     * objects which are not inherently thread safe, synchronization should be considered.
     * <p>
     * Note that this method have a time complexity which is proportional to
     * the number of cloned/created queries times the average number of properties in each.
     */
    public Object getProperty(String name) {
        return owner.getModel().getExecution().trace().getProperty(name);
    }

    /** Returns a short string description of this (includes the first few messages only, and no newlines) */
    @Override
    public String toString() {
        return owner.getModel().getExecution().trace().toString();
    }

    public boolean render(Writer writer) throws java.io.IOException {
        if (owner.getTrace().getLevel()!=0) {
            XMLWriter xmlWriter=XMLWriter.from(writer);
            xmlWriter.openTag("meta").attribute("type",ID);
            TraceNode traceRoot=owner.getModel().getExecution().trace().traceNode().root();
            traceRoot.accept(new XmlRenderer.RenderingVisitor(xmlWriter, owner.getStartTime()));
            xmlWriter.closeTag();
        }
        return true;
    }

    public QueryContext cloneFor(Query cloneOwner) {
        try {
            QueryContext clone=(QueryContext)super.clone();
            clone.owner=cloneOwner;
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns the execution trace this delegates to */
    public Execution.Trace getTrace() { return owner.getModel().getExecution().trace(); }

    @Override
    public QueryContext clone() {
        try {
            return (QueryContext)super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
    
}
