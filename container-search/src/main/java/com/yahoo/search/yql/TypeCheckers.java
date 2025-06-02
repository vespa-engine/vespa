// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.TypeLiteral;

import java.lang.reflect.ParameterizedType;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TypeCheckers {

    public static final TypeLiteral<List<String>> LIST_OF_STRING = new TypeLiteral<List<String>>() {
    };
    public static final TypeLiteral<List<List<String>>> LIST_OF_LIST_OF_STRING = new TypeLiteral<List<List<String>>>() {
    };
    public static final TypeLiteral<List<OperatorNode<SequenceOperator>>> SEQUENCES = new TypeLiteral<List<OperatorNode<SequenceOperator>>>() {
    };
    public static final TypeLiteral<List<OperatorNode<ExpressionOperator>>> EXPRS = new TypeLiteral<List<OperatorNode<ExpressionOperator>>>() {
    };
    public static final TypeLiteral<List<List<OperatorNode<ExpressionOperator>>>> LIST_OF_EXPRS = new TypeLiteral<List<List<OperatorNode<ExpressionOperator>>>>() {
    };
    public static final ImmutableSet<Class<?>> LITERAL_TYPES = ImmutableSet.<Class<?>>builder()
            .add(String.class)
            .add(Integer.class)
            .add(Double.class)
            .add(Boolean.class)
            .add(Float.class)
            .add(Byte.class)
            .add(Long.class)
            .add(List.class)
            .add(Map.class)
            .build();

    private TypeCheckers() {
    }

    public static ArgumentsTypeChecker make(Operator target, Object... types) {
        // Class<?> extends Operator -> NodeTypeChecker
        if (types == null) {
            types = new Object[0];
        }
        List<OperatorTypeChecker> checkers = Lists.newArrayListWithCapacity(types.length);
        for (int i = 0; i < types.length; ++i) {
            checkers.add(createChecker(target, i, types[i]));
        }
        return new ArgumentsTypeChecker(target, checkers);
    }

    // this is festooned with instance checkes before all the casting
    @SuppressWarnings("unchecked")
    private static OperatorTypeChecker createChecker(Operator parent, int idx, Object value) {
        if (value instanceof TypeLiteral) {
            TypeLiteral<?> lit = (TypeLiteral<?>) value;
            Class<?> raw = lit.getRawType();
            if (List.class.isAssignableFrom(raw)) {
                Preconditions.checkArgument(lit.getType() instanceof ParameterizedType, "TypeLiteral without a ParameterizedType for List");
                ParameterizedType type = (ParameterizedType) lit.getType();
                TypeLiteral<?> arg = TypeLiteral.get(type.getActualTypeArguments()[0]);
                if (OperatorNode.class.isAssignableFrom(arg.getRawType())) {
                    Preconditions.checkArgument(arg.getType() instanceof ParameterizedType, "Type spec must be List<OperatorNode<?>>");
                    Class<?> rawType = (Class<?>) TypeLiteral.get(((ParameterizedType) arg.getType()).getActualTypeArguments()[0]).getRawType();
                    Class<? extends Operator> optype = (Class<? extends Operator>) rawType;
                    return new OperatorNodeListTypeChecker(parent, idx, optype, ImmutableSet.<Operator>of());
                } else {
                    return new JavaListTypeChecker(parent, idx, arg.getRawType());
                }
            }
            throw new IllegalArgumentException("don't know how to handle TypeLiteral " + value);
        }
        if (value instanceof Class) {
            Class<?> clazz = (Class<?>) value;
            if (Operator.class.isAssignableFrom(clazz)) {
                return new NodeTypeChecker(parent, idx, (Class<? extends Operator>) clazz, ImmutableSet.<Operator>of());
            } else {
                return new JavaTypeChecker(parent, idx, clazz);
            }
        } else if (value instanceof Operator) {
            Operator operator = (Operator) value;
            Class<? extends Operator> clazz = operator.getClass();
            Set<? extends Operator> allowed;
            if (Enum.class.isInstance(value)) {
                Class<? extends Enum> enumClazz = (Class<? extends Enum>) clazz;
                allowed = (Set<? extends Operator>) EnumSet.of(enumClazz.cast(value));
            } else {
                allowed = ImmutableSet.of(operator);
            }
            return new NodeTypeChecker(parent, idx, clazz, allowed);
        } else if (value instanceof EnumSet) {
            EnumSet<?> v = (EnumSet<?>) value;
            Enum elt = Iterables.get(v, 0);
            if (elt instanceof Operator) {
                Class<? extends Operator> opclass = (Class<? extends Operator>) elt.getClass();
                Set<? extends Operator> allowed = (Set<? extends Operator>) v;
                return new NodeTypeChecker(parent, idx, opclass, allowed);
            }
        } else if (value instanceof Set) {
            // Set<Class<?>>
            return new JavaUnionTypeChecker(parent, idx, (Set<Class<?>>) value);
        }
        throw new IllegalArgumentException("I don't know how to create a checker from " + value);
    }

}
