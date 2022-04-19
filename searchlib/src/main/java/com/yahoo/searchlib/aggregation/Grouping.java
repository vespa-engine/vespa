// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.BucketResultNode;
import com.yahoo.searchlib.expression.NullResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Grouping extends Identifiable {

    // Force load all of expression and aggregation when using this class.
    static {
        com.yahoo.searchlib.aggregation.ForceLoad.forceLoad();
        com.yahoo.searchlib.expression.ForceLoad.forceLoad();
    }

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 91, Grouping.class);

    // The client id for this grouping request.
    private int id = 0;

    // Whether this grouping is valid.
    private boolean valid = true;

    // Whether to group all hits or only those with hits. Only applicable for streaming search.
    private boolean all = false;

    // How many hits to group per backend node.
    private long topN = -1;

    // The level to start grouping in backend. This also instantiates the next level, if any.
    private int firstLevel = 0;

    // The last level to group in backend.
    private int lastLevel = 0;

    private boolean forceSinglePass = false;

    // Details for each level except root.
    private List<GroupingLevel> groupingLevels = new ArrayList<>();

    // Actual root group, does not require level details.
    private Group root = new Group();

    private boolean postMergeCompleted = false;

    /** Constructs an empty result node. <b>NOTE:</b> This instance is broken until non-optional member data is set. */
    public Grouping() {
        super();
    }

    /** Constructs an instance of this class with given client id. */
    public Grouping(int id) {
        super();
        setId(id);
    }

    /** Merges the content of the given grouping <b>into</b> this. */
    public void merge(Grouping rhs) {
        root.merge(firstLevel, 0, rhs.root);
    }

    /**
     * Invoked after merging is done. It is intended used for resolving any dependencies or derivates
     * that might have changes due to the merge.
     */
    public void postMerge() {
        if (postMergeCompleted) return;
        root.postMerge(groupingLevels, firstLevel, 0);
        postMergeCompleted = true;
    }

    /** Returns the client id of this grouping request. */
    public int getId() {
        return id;
    }

    /**
     * Sets the client id for this grouping request.
     *
     * @param id the identifier to set
     * @return this, to allow chaining
     */
    public Grouping setId(int id) {
        this.id = id;
        return this;
    }

    /** Returns whether this grouping request is valid. */
    public boolean valid() {
        return valid;
    }

    /**
     * Returns whether to perform grouping on the entire document corpus instead of only those matching the
     * search criteria. Please see note on {@link #setAll(boolean)}.
     */
    public boolean getAll() {
        return all;
    }

    /**
     * Sets whether to perform grouping on the entire document corpus instead of only those matching the
     * search criteria. <b>NOTE:</b> This is only possible with streaming search.
     *
     * @param all true to group all documents
     * @return this, to allow chaining
     */
    public Grouping setAll(boolean all) {
        this.all = all;
        return this;
    }

    /** Returns the number of candidate documents to group. */
    public long getTopN() {
        return topN;
    }

    /**
     * Sets the number of candidate documents to group.
     *
     * @param topN the number to set
     * @return this, to allow chaining
     */
    public Grouping setTopN(long topN) {
        this.topN = topN;
        return this;
    }

    /** Returns the first level to start grouping work. See note on {@link #setFirstLevel(int)}. */
    public int getFirstLevel() {
        return firstLevel;
    }

    /**
     * Sets the first level to start grouping work. All the necessary work above this group level is expected to be
     * already done.
     *
     * @param level the level to set
     * @return this, to allow chaining
     */
    public Grouping setFirstLevel(int level) {
        firstLevel = level;
        return this;
    }

    /** Returns the last level to do grouping work. See note on {@link #setLastLevel(int)}. */
    public int getLastLevel() {
        return lastLevel;
    }

    /**
     * Sets the last level to do grouping work. Executing a level will instantiate the {@link Group} objects for the
     * next level, if there is any. This means that grouping work ends at this level, but also instantiates the groups
     * for level (lastLevel + 1).
     *
     * @param level the level to set
     * @return this, to allow chaining
     */
    public Grouping setLastLevel(int level) {
        lastLevel = level;
        return this;
    }

    /** Returns the list of grouping levels that make up this grouping request. */
    public List<GroupingLevel> getLevels() {
        return groupingLevels;
    }

    /**
     * Appends the given grouping level specification to the list of levels.
     *
     * @param level the level to add
     * @return this, to allow chaining
     * @throws NullPointerException if <code>level</code> argument is null
     */
    public Grouping addLevel(GroupingLevel level) {
        groupingLevels.add(Objects.requireNonNull(level));
        return this;
    }

    /** Returns the root group. */
    public Group getRoot() {
        return root;
    }

    /**
     * Sets the root group.
     *
     * @param root the group to set as root
     * @return this, to allow chaining
     * @throws NullPointerException if <code>root</code> argument is null
     */
    public Grouping setRoot(Group root) {
        this.root = Objects.requireNonNull(root);
        return this;
    }

    /** Returns whether single pass execution of grouping is forced. */
    public boolean getForceSinglePass() {
        return forceSinglePass;
    }

    /**
     * Sets whether or not grouping should be forced to execute in a single pass.
     * If false, this <code>Grouping</code>
     * might still execute in a single pass due to other constraints.
     *
     * @param forceSinglePass true to force execution in single pass
     * @return this, to allow chaining
     */
    public Grouping setForceSinglePass(boolean forceSinglePass) {
        this.forceSinglePass = forceSinglePass;
        return this;
    }

    /** Returns whether grouping should be executed in a single pass. */
    public boolean useSinglePass() {
        return needDeepResultCollection() || getForceSinglePass();
    }

    /**
     * Returns whether ordering will need results collected in children.
     * In that case we will probably just do a single pass.
     */
    public boolean needDeepResultCollection() {
        if (forceSinglePass) {
            return true;
        }
        for (GroupingLevel level : groupingLevels) {
            if (level.needResultCollection()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        buf.putInt(null, id);
        byte tmp = valid ? (byte)1 : (byte)0;
        buf.putByte(null, tmp);
        tmp = all ? (byte)1 : (byte)0;
        buf.putByte(null, tmp);
        buf.putLong(null, topN);
        buf.putInt(null, firstLevel);
        buf.putInt(null, lastLevel);
        buf.putInt(null, groupingLevels.size());
        for (GroupingLevel level : groupingLevels) {
            level.serializeWithId(buf);
        }
        root.serializeWithId(buf);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        id = buf.getInt(null);
        byte tmp = buf.getByte(null);
        valid = (tmp != 0);
        tmp = buf.getByte(null);
        all = (tmp != 0);
        topN = buf.getLong(null);
        firstLevel = buf.getInt(null);
        lastLevel = buf.getInt(null);
        int numLevels = buf.getInt(null);
        for (int i = 0; i < numLevels; i++) {
            GroupingLevel level = new GroupingLevel();
            level.deserializeWithId(buf);
            groupingLevels.add(level);
        }
        root.deserializeWithId(buf);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + id + (valid ? 66 : 99) + (all ? 666 : 999) + (int)topN + groupingLevels.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        Grouping rhs = (Grouping)obj;
        if (id != rhs.id) {
            return false;
        }
        if (valid != rhs.valid) {
            return false;
        }
        if (all != rhs.all) {
            return false;
        }
        if (topN != rhs.topN) {
            return false;
        }
        if (firstLevel != rhs.firstLevel) {
            return false;
        }
        if (lastLevel != rhs.lastLevel) {
            return false;
        }
        if (!groupingLevels.equals(rhs.groupingLevels)) {
            return false;
        }
        if (!root.equals(rhs.root)) {
            return false;
        }
        return true;
    }

    @Override
    public Grouping clone() {
        Grouping obj = (Grouping)super.clone();
        obj.groupingLevels = new ArrayList<>();
        for (GroupingLevel level : groupingLevels) {
            obj.groupingLevels.add(level.clone());
        }
        obj.root = root.clone();
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("id", id);
        visitor.visit("valid", valid);
        visitor.visit("all", all);
        visitor.visit("topN", topN);
        visitor.visit("firstLevel", firstLevel);
        visitor.visit("lastLevel", lastLevel);
        visitor.visit("groupingLevels", groupingLevels);
        visitor.visit("root", root);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        selectGroups(predicate, operation, root, firstLevel, lastLevel, 0);
    }

    public void unifyNull() {
        class FindGroup implements ObjectPredicate {

            @Override
            public boolean check(Object obj) {
                return obj instanceof Group;
            }
        }
        class UnifyNullGroupId implements ObjectOperation {

            @Override
            public void execute(Object obj) {
                Group group = (Group)obj;
                ResultNode id = group.getId();
                if (id instanceof BucketResultNode && ((BucketResultNode)id).empty()) {
                    group.setId(new NullResultNode());
                }
            }
        }
        selectMembers(new FindGroup(), new UnifyNullGroupId());
    }

    /**
     * This is a helper function to perform recursive traversal of all groups contained in this grouping object. It
     * is invoked by the {@link #selectMembers(ObjectPredicate, ObjectOperation)} method and itself. This method will
     * only evaluate the groups that belong to active levels.
     *
     * @param predicate the object predicate to evaluate
     * @param operation the operation to execute when the predicate is true
     * @param group     the group to evaluate
     * @param first     the first active level
     * @param last      the last active level
     * @param current   the level being evaluated
     */
    private static void selectGroups(ObjectPredicate predicate, ObjectOperation operation,
                                     Group group, int first, int last, int current) {
        if (current > last) {
            return;
        }
        if (current >= first) {
            group.select(predicate, operation);
        }
        for (Group child : group.getChildren()) {
            selectGroups(predicate, operation, child, first, last, current + 1);
        }
    }

}
