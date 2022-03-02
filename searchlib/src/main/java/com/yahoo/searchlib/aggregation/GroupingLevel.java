// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.ExpressionNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

public class GroupingLevel extends Identifiable {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 93, GroupingLevel.class);

    // The maximum number of groups allowed at this level.
    private long maxGroups = -1;

    // The precision used for estimation. This is number of groups returned up when using orderby that need more info to get it correct.
    private long precision = -1;

    // The classifier expression; the result of this is the group key.
    private ExpressionNode classify = null;

    // The prototype of the groups to create for each class.
    private Group collect = new Group();

    /** Returns the precision (i.e number of groups) returned up from this level. */
    public long getPrecision() {
        return precision;
    }

    /** Returns the maximum number of groups allowed at this level. */
    public long getMaxGroups() {
        return maxGroups;
    }

    /** Sets the maximum number of groups allowed at this level. */
    public GroupingLevel setMaxGroups(long max) {
        maxGroups = max;
        if (precision < maxGroups) {
            precision = maxGroups;
        }
        return this;
    }

    /**
     * Sets the precision (i.e number of groups) returned up from this level.
     *
     * @param precision the precision to set
     * @return this, to allow chaining
     */
    public GroupingLevel setPrecision(long precision) {
        this.precision = precision;
        return this;
    }

    /** Returns the expression used to classify hits into groups. */
    public ExpressionNode getExpression() {
        return classify;
    }

    /** Sets the expression used to classify hits into groups. */
    public GroupingLevel setExpression(ExpressionNode exp) {
        classify = exp;
        return this;
    }

    /**
     * <p>Sets the prototype to use when creating groups at this level.</p>
     *
     * @param group The group prototype.
     * @return This, to allow chaining.
     */
    public GroupingLevel setGroupPrototype(Group group) {
        this.collect = group;
        return this;
    }

    /**
     * <p>Returns the prototype to use when creating groups at this level.</p>
     *
     * @return The group prototype.
     */
    public Group getGroupPrototype() {
        return collect;
    }

    /**
     * <p>Tell if ordering will need results collected in children.</p>
     *
     * @return If deeper resultcollection is needed.
     */
    public boolean needResultCollection() {
        return !collect.isRankedByRelevance();
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        buf.putLong(null, maxGroups);
        buf.putLong(null, precision);
        serializeOptional(buf, classify);
        collect.serializeWithId(buf);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        maxGroups = buf.getLong(null);
        precision = buf.getLong(null);
        classify = (ExpressionNode)deserializeOptional(buf);
        collect.deserializeWithId(buf);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (int)maxGroups + (int)precision + collect.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        GroupingLevel rhs = (GroupingLevel)obj;
        if (maxGroups != rhs.maxGroups) {
            return false;
        }
        if (precision != rhs.precision) {
            return false;
        }
        if (!equals(classify, rhs.classify)) {
            return false;
        }
        if (!collect.equals(rhs.collect)) {
            return false;
        }
        return true;
    }

    @Override
    public GroupingLevel clone() {
        GroupingLevel obj = (GroupingLevel)super.clone();
        if (classify != null) {
            obj.classify = classify.clone();
        }
        obj.collect = collect.clone();
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("maxGroups", maxGroups);
        visitor.visit("precision", precision);
        visitor.visit("classify", classify);
        visitor.visit("collect", collect);
    }
}
