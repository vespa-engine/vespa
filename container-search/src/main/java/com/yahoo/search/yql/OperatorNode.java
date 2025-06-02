// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Represents a use of an operator against concrete arguments. The types of arguments depend on the operator.
 * The extension point of this scheme is the Operator rather than new types of Nodes.
 * Operators SHOULD take a fixed number of arguments -- wrap variable argument counts in Lists.
 */
final class OperatorNode<T extends Operator> {

    public static <T extends Operator> OperatorNode<T> create(T operator, Object... args) {
        operator.checkArguments(args == null ? EMPTY_ARGS : args);
        return new OperatorNode<T>(operator, args);
    }

    public static <T extends Operator> OperatorNode<T> create(Location loc, T operator, Object... args) {
        operator.checkArguments(args == null ? EMPTY_ARGS : args);
        return new OperatorNode<T>(loc, operator, args);
    }

    public static <T extends Operator> OperatorNode<T> create(Location loc, Map<String, Object> annotations, T operator, Object... args) {
        operator.checkArguments(args == null ? EMPTY_ARGS : args);
        return new OperatorNode<T>(loc, annotations, operator, args);
    }

    private static final Object[] EMPTY_ARGS = new Object[0];

    private final Location location;
    private final T operator;
    private Map<String, Object> annotations = ImmutableMap.of();
    private final Object[] args;

    private OperatorNode(T operator, Object... args) {
        this.location = null;
        this.operator = operator;
        if (args == null) {
            this.args = EMPTY_ARGS;
        } else {
            this.args = args;
        }
    }

    private OperatorNode(Location loc, T operator, Object... args) {
        this.location = loc;
        this.operator = operator;
        if (args == null) {
            this.args = EMPTY_ARGS;
        } else {
            this.args = args;
        }
    }

    private OperatorNode(Location loc, Map<String, Object> annotations, T operator, Object... args) {
        this.location = loc;
        this.operator = operator;
        this.annotations = ImmutableMap.copyOf(annotations);
        if (args == null) {
            this.args = EMPTY_ARGS;
        } else {
            this.args = args;
        }
    }

    public T getOperator() {
        return operator;
    }

    public Object[] getArguments() {
        // this is only called by a test right now, but ImmutableList.copyOf won't tolerate null elements
        if (args.length == 0) {
            return args;
        }
        Object[] copy = new Object[args.length];
        System.arraycopy(args, 0, copy, 0, args.length);
        return copy;
    }

    public <T> T getArgument(int i) {
        return (T) args[i];
    }

    public <T> T getArgument(int i, Class<T> clazz) {
        return clazz.cast(getArgument(i));
    }

    public Location getLocation() {
        return location;
    }

    public Object getAnnotation(String name) {
        return annotations.get(name);
    }

    public OperatorNode<T> putAnnotation(String name, Object value) {
        if (annotations.isEmpty()) {
            annotations = Maps.newLinkedHashMap();
        } else if (annotations instanceof ImmutableMap) {
            annotations = Maps.newLinkedHashMap(annotations);
        }
        annotations.put(name, value);
        return this;
    }

    public Map<String, Object> getAnnotations() {
        // TODO: this should be a read-only view?
        return ImmutableMap.copyOf(annotations);
    }

    public OperatorNode<T> transform(Function<Object, Object> argumentTransform) {
        if (args.length == 0) {
            // nothing to transform, so no change is possible
            return this;
        }
        Object[] newArgs = new Object[args.length];
        boolean changed = false;
        for (int i = 0; i < args.length; ++i) {
            Object target = args[i];
            if (target instanceof List) {
                List<Object> newList = Lists.newArrayListWithExpectedSize(((List) target).size());
                for (Object val : (List) target) {
                    newList.add(argumentTransform.apply(val));
                }
                newArgs[i] = newList;
                // this will always 'change' the tree, maybe fix later
            } else {
                newArgs[i] = argumentTransform.apply(args[i]);
            }
            changed = changed || newArgs[i] != args[i];
        }
        if (changed) {
            return new OperatorNode<>(location, annotations, operator, newArgs);
        }
        return this;
    }

    public void visit(OperatorVisitor visitor) {
        if (visitor.enter(this)) {
            for (Object target : args) {
                if (target instanceof List) {
                    for (Object val : (List) target) {
                        if (val instanceof OperatorNode) {
                            ((OperatorNode) val).visit(visitor);
                        }
                    }
                } else if (target instanceof OperatorNode) {
                    ((OperatorNode) target).visit(visitor);

                }
            }
        }
        visitor.exit(this);
    }

    // we are aware only of types used in our logical operator trees -- OperatorNode, List, and constant values
    private static final Function<Object, Object> COPY = new Function<>() {
        @Override
        public Object apply(Object input) {
            if (input instanceof List) {
                List<Object> newList = Lists.newArrayListWithExpectedSize(((List) input).size());
                for (Object val : (List) input) {
                    newList.add(COPY.apply(val));
                }
                return newList;
            } else if (input instanceof OperatorNode) {
                return ((OperatorNode) input).copy();
            } else if (input instanceof String || input instanceof Number || input instanceof Boolean) {
                return input;
            } else {
                // this may be annoying but COPY not understanding how to COPY and quietly reusing
                // when it may not be immutable could be dangerous
                throw new IllegalArgumentException("Unexpected value type in OperatorNode tree: " + input);
            }
        }
    };

    public OperatorNode<T> copy() {
        Object[] newArgs = new Object[args.length];
        for (int i = 0; i < args.length; ++i) {
            newArgs[i] = COPY.apply(args[i]);
        }
        return new OperatorNode<>(location, ImmutableMap.copyOf(annotations), operator, newArgs);
    }

    public void toString(StringBuilder output) {
        output.append("(")
              .append(operator.name());
        if(location != null) {
            output.append(" L")
                  .append(location.getCharacterOffset())
                  .append(":")
                  .append(location.getLineNumber());
        }
        if(annotations != null && !annotations.isEmpty()) {
            output.append(" {");
            Joiner.on(", ").withKeyValueSeparator("=")
                    .appendTo(output, annotations);
            output.append("}");
        }
        boolean first = true;
        for(Object arg : args) {
            if(!first) {
                output.append(",");
            }
            first = false;
            output.append(" ");
            if(arg instanceof OperatorNode) {
                ((OperatorNode) arg).toString(output);
            } else if(arg instanceof Iterable) {
                output.append("[");
                Joiner.on(", ").appendTo(output, (Iterable)arg);
                output.append("]");
            } else {
                output.append(arg.toString());
            }
        }
        output.append(")");
    }

    public String toString() {
        StringBuilder output = new StringBuilder();
        toString(output);
        return output.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperatorNode that = (OperatorNode) o;

        if (!annotations.equals(that.annotations)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(args, that.args)) return false;
        if (!operator.equals(that.operator)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = operator.hashCode();
        result = 31 * result + annotations.hashCode();
        result = 31 * result + Arrays.hashCode(args);
        return result;
    }
}
