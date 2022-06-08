// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Superclass for all inner nodes in a {@link ConfigInstance}.
 * <p>
 * This class cannot have non-private members because such members
 * will interfere with the members in the generated subclass.
 *
 * @author gjoranv
 */
public abstract class InnerNode extends Node {

    /**
     * Creates a new InnerNode.
     */
    public InnerNode() {
    }

    @Override
    public String toString() {
        return mkString(ConfigInstance.serialize(this), "\n");
    }

    private static <T> String mkString(Collection<T> collection, String sep) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (T elem : collection) {
            if (! first)
                sb.append(sep);
            first = false;
            sb.append(elem);
        }
        return sb.toString();
     }

    /**
     * Overrides {@link Node#postInitialize(String)}.
     * Perform post initialization on this nodes children.
     *
     * @param configId The config id of this instance.
     */
    @Override
    public void postInitialize(String configId) {
        Map<String, Node> children = getChildrenWithVectorsFlattened();
        for (Node node : children.values()) {
            node.postInitialize(configId);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if ( !(other instanceof InnerNode) || (other.getClass() != this.getClass()))
            return false;

        /* This implementation requires getChildren() to return elements in order.
         Hence we should make it final. Or make equals independent of order. */
        Collection<Object> children = getChildren().values();
        Collection<Object> otherChildren = ((InnerNode)other).getChildren().values();

        Iterator<Object> e1 = children.iterator();
        Iterator<Object> e2 = otherChildren.iterator();
        while(e1.hasNext() && e2.hasNext()) {
            Object o1 = e1.next();
            Object o2 = e2.next();
            if (!(o1 == null ? o2 == null : o1.equals(o2)))
                return false;
        }
        return !(e1.hasNext() || e2.hasNext());
    }

    @Override
    public int hashCode() {
        int res = 17;
        for (Object child : getChildren().values())
            res = 31 * res + child.hashCode();
        return res;
     }

    protected final Map<String, Object> getChildren() {
        HashMap<String, Object> ret = new LinkedHashMap<>();
        Field[] fields = getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            Object fieldValue;
            try {
                fieldValue = field.get(this);
            } catch (IllegalAccessException e) {
                throw new ConfigurationRuntimeException(e);
            }
            if (fieldValue instanceof Node
                    || fieldValue instanceof NodeVector<?>
                    || fieldValue instanceof Map<?,?>)
                ret.put(field.getName(), fieldValue);
        }
        return ret;
    }

    /**
     * Returns a flat map of this node's direct children, including all NodeVectors' elements.
     * Keys are the node name, including index for vector elements, e.g. 'arr[0]'.
     */
    @SuppressWarnings("unchecked")
    protected final Map<String, Node> getChildrenWithVectorsFlattened() {
        Map<String, Node> ret = new LinkedHashMap<>();

        Map<String, Object> children = getChildren();
        for (Map.Entry<String, Object> childEntry : children.entrySet()) {
            String name = childEntry.getKey();
            Object child = childEntry.getValue();
            if (child instanceof NodeVector) {
                addNodeVectorEntries(ret, name, (NodeVector<?>) child);
            } else if (child instanceof Map<?,?>) {
                addMapEntries(ret, name, (Map<String, Node>) child);
            } else if (child instanceof Node) {
                ret.put(name, (Node)child);
            }
        }
        return ret;
    }

    private static void addMapEntries(Map<String, Node> ret, String name, Map<String, Node> map) {
        for (Map.Entry<String, Node> entry : map.entrySet())
            ret.put(name + "{" + entry.getKey() + "}", entry.getValue());
    }


    private static void addNodeVectorEntries(Map<String, Node> ret, String name, NodeVector<?> vector) {
        for (int j = 0; j < vector.length(); j++)
            ret.put(name + "[" + j + "]", (Node) vector.get(j));
    }

    protected static Map<String, LeafNode<?>> getAllDescendantLeafNodes(InnerNode node) {
         return getAllDescendantLeafNodes("", node);
     }

     /**
      * Generates a map of all leaf nodes, with full.paths[3] in key
      *
      * @param parentName Name of the parent node, can be empty.
      * @param node The node to get leaf nodes for.
      * @return map of leaf nodes
      */
     private static Map<String, LeafNode<?>> getAllDescendantLeafNodes(String parentName, InnerNode node) {
         Map<String, LeafNode<?>> ret = new LinkedHashMap<>();
         String prefix = parentName.isEmpty() ? "" : parentName + ".";
         Map<String, Node> children = node.getChildrenWithVectorsFlattened();
         for (Map.Entry<String, Node> childEntry : children.entrySet()) {
             String name = childEntry.getKey();
             String prefixedName = prefix + name;
             name.replaceAll("\\[.*", "");
             Node child = childEntry.getValue();
             if (child instanceof LeafNode) {
                 ret.put(prefixedName, (LeafNode<?>) child);
             } else if (child instanceof InnerNode) {
                 ret.putAll(getAllDescendantLeafNodes(prefixedName, (InnerNode) child));
             }
         }
         return ret;
     }

}
