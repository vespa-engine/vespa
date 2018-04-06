// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.document.GlobalId;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This class represents a single hit from the fastserver4 backend
 *
 * @author havardpe
 */
public class FS4Hit extends Hit {

    public static final int classId = registerClass(0x4000 + 95, FS4Hit.class); // shared with c++
    private int path = 0;
    private GlobalId globalId = new GlobalId(new byte[GlobalId.LENGTH]);
    private int distributionKey = -1;

    /**
     * Constructs an empty result node.
     */
    public FS4Hit() {
    }

    /**
     * Create a hit with the given path and document id.
     *
     * @param path     The mangled search node path.
     * @param globalId The local document id.
     * @param rank     The rank of this hit.
     */
    public FS4Hit(int path, GlobalId globalId, double rank) {
        this(path, globalId, rank, -1);
    }

    /**
     * Create a hit with the given path and document id.
     *
     * @param path     The mangled search node path.
     * @param globalId The local document id.
     * @param rank     The rank of this hit.
     * @param distributionKey The doc stamp.
     */
    public FS4Hit(int path, GlobalId globalId, double rank, int distributionKey) {
        super(rank);
        this.path = path;
        this.globalId = globalId;
        this.distributionKey = distributionKey;
    }

    /**
     * Obtain the (mangled) network path back to the search node returning this hit.
     *
     * @return The mangled search node path.
     */
    public int getPath() {
        return path;
    }

    /**
     * Obtain the global document id on the search node returning this hit.
     *
     * @return The global document id.
     */
    public GlobalId getGlobalId() {
        return globalId;
    }

    /**
     * Obtain the distribution key for the node producing this hit.
     *
     * @return distribution key
     */
    public int getDistributionKey() {
        return distributionKey;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, path);
        buf.put(null, globalId.getRawId());
        buf.putInt(null, distributionKey);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        path = buf.getInt(null);
        globalId = new GlobalId(buf.getBytes(null, GlobalId.LENGTH));
        distributionKey = buf.getInt(null);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + path + globalId.hashCode() + distributionKey;
    }

    @SuppressWarnings({ "EqualsWhichDoesntCheckParameterClass", "RedundantIfStatement" })
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        FS4Hit rhs = (FS4Hit)obj;
        if (path != rhs.path) {
            return false;
        }
        if (!globalId.equals(rhs.globalId)) {
            return false;
        }
        if (distributionKey != rhs.distributionKey) {
            return false;
        }
        return true;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("path", path);
        visitor.visit("globalId", globalId.toString());
        visitor.visit("distributionKey", distributionKey);
    }
}
