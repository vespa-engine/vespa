package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Flavor;

import java.util.Objects;

/**
 * A specification of a set of nodes.
 * This reflects that nodes can be requested either by count and flavor or by type,
 * and encapsulates the differences in logic between these two cases.
 * 
 * @author bratseth
 */
public interface NodeSpec {

    /** The node type this requests */
    NodeType type();
    
    /** Returns whether the given flavor is compatible with this spec */
    boolean isCompatible(Flavor flavor);

    /** Returns whether the given flavor is exactly specified by this node spec */
    boolean matchesExactly(Flavor flavor);

    /** Returns whether this requests a non-stock flavor */
    boolean specifiesNonStockFlavor();

    /** Returns whether the given node count is sufficient to consider this spec fulfilled to the maximum amount */
    boolean saturatedBy(int count);

    /** Returns whether the given node count is sufficient to fulfill this spec */
    boolean fulfilledBy(int count);
    
    /** Returns the amount the given count is above the minimum amount needed to fulfill this request */
    int surplusGiven(int count);
    
    /** Returns a specification of a fraction of all the nodes of this. It is assumed the argument is a valid divisor. */
    NodeSpec fraction(int divisor);

    static NodeSpec from(int nodeCount, Flavor flavor) {
        return new CountNodeSpec(nodeCount, flavor);
    }
    
    static NodeSpec from(NodeType type) {
        return new TypeNodeSpec(type);
    }
    
    /** A node spec specifying a node count and a flavor */
    class CountNodeSpec implements NodeSpec {
        
        private final int count;
        private final Flavor flavor;
        
        public CountNodeSpec(int count, Flavor flavor) {
            Objects.requireNonNull(flavor, "A flavor must be specified");
            this.count = count;
            this.flavor = flavor;
        }

        public Flavor getFlavor() {
            return flavor;
        }

        public int getCount()  { return count; }

        @Override
        public NodeType type() { return NodeType.tenant; }

        @Override
        public boolean isCompatible(Flavor flavor) { return flavor.satisfies(this.flavor); }

        @Override
        public boolean matchesExactly(Flavor flavor) { return flavor.equals(this.flavor); }

        @Override
        public boolean specifiesNonStockFlavor() { return ! flavor.isStock(); }

        @Override
        public boolean fulfilledBy(int count) { return count >= this.count; } 

        @Override
        public boolean saturatedBy(int count) { return fulfilledBy(count); } // min=max for count specs

        @Override
        public int surplusGiven(int count) { return count - this.count; }

        @Override
        public NodeSpec fraction(int divisor) { return new CountNodeSpec(count/divisor, flavor); }

        @Override
        public String toString() { return "request for " + count + " nodes of " + flavor; }
        
    }

    /** A node spec specifying a node type. This will accept all nodes of this type. */
    class TypeNodeSpec implements NodeSpec {
        
        private final NodeType type;
        
        public TypeNodeSpec(NodeType type) {
            this.type = type;
        }

        @Override
        public NodeType type() { return type; }

        @Override
        public boolean isCompatible(Flavor flavor) { return true; }

        @Override
        public boolean matchesExactly(Flavor flavor) { return false; }

        @Override
        public boolean specifiesNonStockFlavor() { return false; }

        @Override
        public boolean fulfilledBy(int count) { return true; }

        @Override
        public boolean saturatedBy(int count) { return false; }

        @Override
        public int surplusGiven(int count) { return 0; }

        @Override
        public NodeSpec fraction(int divisor) { return this; }

        @Override
        public String toString() { return "request for all nodes of type '" + type + "'"; }

    }

}
