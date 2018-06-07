// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.collections.CollectionComparator;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlSerializationHelper;
import com.yahoo.document.serialization.XmlStream;

import java.util.*;

/**
 * FieldValue which encapsulates a Array value
 *
 * @author Einar M R Rosenvinge
 */
public final class Array<T extends FieldValue> extends CollectionFieldValue<T> implements List<T> {

    private List<T> values;

    public Array(DataType type) {
        this(type, 1);
    }

    public Array(DataType type, int initialCapacity) {
        super((ArrayDataType) type);
        this.values = new ArrayList<>(initialCapacity);
    }

    public Array(DataType type, List<T> values) {
        this(type);
        for (T v : values) {
            if (!((ArrayDataType)type).getNestedType().isValueCompatible(v)) {
                throw new IllegalArgumentException("FieldValue " + v +
                        " is not compatible with " + type + ".");
            }
        }
        this.values.addAll(values);
    }

    @Override
    public ArrayDataType getDataType() {
        return (ArrayDataType) super.getDataType();
    }

    @Override
    public Iterator<T> fieldValueIterator() {
        return values.iterator();
    }

    @Override
    public Array<T> clone() {
        Array<T> array = (Array<T>) super.clone();
        array.values = new ArrayList<>(values.size());
        for (T fval : values) {
            array.values.add((T) fval.clone());
        }
        return array;
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public void assign(Object o) {
        if (!checkAssign(o)) {
            return;
        }

        if (o instanceof Array) {
            if (o == this) return;
            Array a = (Array) o;
            values.clear();
            addAll(a.values);
        } else if (o instanceof List) {
            values = new ListWrapper<T>((List) o);
        } else {
            throw new IllegalArgumentException("Class " + o.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    @Override
    public Object getWrappedValue() {
        if (values instanceof ListWrapper) {
            return ((ListWrapper) values).myvalues;
        }
        List tmpWrappedList = new ArrayList();
        for (T value : values) {
            tmpWrappedList.add(value.getWrappedValue());
        }
        return tmpWrappedList;
    }

    public List<T> getValues() {
        return values;
    }

    public FieldValue getFieldValue(int index) {
        return values.get(index);
    }

    @Override
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printArrayXml(this, xml);
    }

    @Override
    public String toString() {
        return values.toString();
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        for (FieldValue val : values) {
            hashCode ^= val.hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Array)) return false;
        if (!super.equals(o)) return false;
        Array a = (Array) o;
        // Compare independent of container used.
        Iterator it1 = values.iterator();
        Iterator it2 = a.values.iterator();
        while (it1.hasNext() && it2.hasNext()) {
            if (!it1.next().equals(it2.next())) return false;
        }
        return !(it1.hasNext() || it2.hasNext());
    }

    // List implementation

    public void add(int index, T o) {
        verifyElementCompatibility(o);
        values.add(index, o);
    }

    public boolean remove(Object o) {
        return values.remove(o);
    }

    public boolean add(T o) {
        verifyElementCompatibility(o);
        return values.add(o);
    }

    @Override
    public boolean contains(Object o) {
        return values.contains(o);
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty(values);
    }

    @Override
    public Iterator<T> iterator() {
        return values.iterator();
    }

    @Override
    public boolean removeValue(FieldValue o) {
        return super.removeValue(o, values);
    }

    @Override
    public int size() {
        return values.size();
    }

    public boolean addAll(Collection<? extends T> c) {
        for (T t : c) {
            verifyElementCompatibility(t);
        }
        return values.addAll(c);
    }

    public boolean containsAll(Collection<?> c) {
        return values.containsAll(c);
    }

    public Object[] toArray() {
        return values.toArray();
    }

    @SuppressWarnings({"unchecked"})
    public <T> T[] toArray(T[] a) {
        return values.toArray(a);
    }

    public boolean addAll(int index, Collection<? extends T> c) {
        for (T t : c) {
            verifyElementCompatibility(t);
        }
        return values.addAll(index, c);
    }

    @SuppressWarnings("deprecation")
    public boolean retainAll(Collection<?> c) {
        return values.retainAll(c);
    }

    @SuppressWarnings("deprecation")
    public boolean removeAll(Collection<?> c) {
        return values.removeAll(c);
    }

    public T get(int index) {
        return values.get(index);
    }

    @SuppressWarnings("deprecation")
    public int indexOf(Object o) {
        return values.indexOf(o);
    }

    @SuppressWarnings("deprecation")
    public int lastIndexOf(Object o) {
        return values.lastIndexOf(o);
    }

    public ListIterator<T> listIterator() {
        return values.listIterator();
    }

    public ListIterator<T> listIterator(final int index) {
        return values.listIterator(index);
    }

    public T remove(int index) {
        return values.remove(index);
    }

    @SuppressWarnings("deprecation")
    public T set(int index, T o) {
        verifyElementCompatibility(o);
        T fval = values.set(index, o);
        return fval;
    }

    public List<T> subList(int fromIndex, int toIndex) {
        return values.subList(fromIndex, toIndex);
    }

    FieldPathIteratorHandler.ModificationStatus iterateSubset(int startPos, int endPos, FieldPath fieldPath, String variable, int nextPos, FieldPathIteratorHandler handler) {
        FieldPathIteratorHandler.ModificationStatus retVal = FieldPathIteratorHandler.ModificationStatus.NOT_MODIFIED;

        LinkedList<Integer> indicesToRemove = new LinkedList<Integer>();

        for (int i = startPos; i <= endPos && i < values.size(); i++) {
            if (variable != null) {
                handler.getVariables().put(variable, new FieldPathIteratorHandler.IndexValue(i));
            }

            FieldValue fv = values.get(i);
            FieldPathIteratorHandler.ModificationStatus status = fv.iterateNested(fieldPath, nextPos, handler);

            if (status == FieldPathIteratorHandler.ModificationStatus.REMOVED) {
                indicesToRemove.addFirst(i);
                retVal = FieldPathIteratorHandler.ModificationStatus.MODIFIED;
            } else if (status == FieldPathIteratorHandler.ModificationStatus.MODIFIED) {
                retVal = status;
            }
        }

        if (variable != null) {
            handler.getVariables().remove(variable);
        }

        for (Integer idx : indicesToRemove) {
            values.remove(idx.intValue());
        }
        return retVal;
    }

    @Override
    FieldPathIteratorHandler.ModificationStatus iterateNested(FieldPath fieldPath, int pos, FieldPathIteratorHandler handler) {
        if (pos < fieldPath.size()) {
            switch (fieldPath.get(pos).getType()) {
                case ARRAY_INDEX:
                    final int elemIndex = fieldPath.get(pos).getLookupIndex();
                    return iterateSubset(elemIndex, elemIndex, fieldPath, null, pos + 1, handler);
                case VARIABLE: {
                    FieldPathIteratorHandler.IndexValue val = handler.getVariables().get(fieldPath.get(pos).getVariableName());
                    if (val != null) {
                        int idx = val.getIndex();

                        if (idx == -1) {
                            throw new IllegalArgumentException("Mismatch between variables - trying to iterate through map and array with the same variable.");
                        }

                        if (idx < values.size()) {
                            return iterateSubset(idx, idx, fieldPath, null, pos + 1, handler);
                        }
                    } else {
                        return iterateSubset(0, values.size() - 1, fieldPath, fieldPath.get(pos).getVariableName(), pos + 1, handler);
                    }
                    break;
                }
                default:
            }
            return iterateSubset(0, values.size() - 1, fieldPath, null, pos, handler);
        } else {
            FieldPathIteratorHandler.ModificationStatus status = handler.modify(this);

            if (status == FieldPathIteratorHandler.ModificationStatus.REMOVED) {
                return status;
            }

            if (handler.onComplex(this)) {
                if (iterateSubset(0, values.size() - 1, fieldPath, null, pos, handler) != FieldPathIteratorHandler.ModificationStatus.NOT_MODIFIED) {
                    status = FieldPathIteratorHandler.ModificationStatus.MODIFIED;
                }
            }

            return status;
        }
    }

    /**
     * This wrapper class is used to wrap a list that isn't a list of field
     * values. This is done, as to not alter behaviour from previous state,
     * where people could add whatever list to a document, and then keep adding
     * stuff to the list afterwards.
     *
     * <p>
     * TODO: Remove this class and only allow instance of Array to be added.
     */
    class ListWrapper<E> implements List<E>, RandomAccess {
        private final List myvalues;

        private Object unwrap(Object o) {
            return (o instanceof FieldValue ? ((FieldValue) o).getWrappedValue() : o);
        }

        public ListWrapper(List wrapped) {
            myvalues = wrapped;
        }

        public int size() {
            return myvalues.size();
        }

        public boolean isEmpty() {
            return myvalues.isEmpty();
        }

        public boolean contains(Object o) {
            return myvalues.contains(unwrap(o));
        }

        public Iterator<E> iterator() {
            return listIterator();
        }

        public Object[] toArray() {
            return toArray(new Object[myvalues.size()]);
        }

        // It's supposed to blow up if given invalid types
        @SuppressWarnings({ "hiding", "unchecked" })
        public <T> T[] toArray(T[] a) {
            final Class<?> componentType = a.getClass().getComponentType();
            T[] out = (T[]) java.lang.reflect.Array.newInstance(componentType, myvalues.size());

            Arrays.setAll(out, (i) -> (T) createFieldValue(myvalues.get(i)));
            return out;
        }

        public boolean add(E o) {
            return myvalues.add(unwrap(o));
        }

        public boolean remove(Object o) {
            return myvalues.remove(unwrap(o));
        }

        public boolean containsAll(Collection<?> c) {
            for (Object o : c) {
                if (!myvalues.contains(unwrap(o))) return false;
            }
            return true;
        }

        public boolean addAll(Collection<? extends E> c) {
            boolean result = false;
            for (Object o : c) {
                result |= myvalues.add(unwrap(o));
            }
            return result;
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            for (Object o : c) {
                myvalues.add(index++, unwrap(o));
            }
            return true;
        }

        public boolean removeAll(Collection<?> c) {
            boolean result = false;
            for (Object o : c) {
                result |= myvalues.remove(unwrap(o));
            }
            return result;
        }

        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("retainAll() not implemented for this type");
        }

        public void clear() {
            myvalues.clear();
        }

        public E get(int index) {
            Object o = myvalues.get(index);
            return (E) (o == null ? null : createFieldValue(o));
        }

        public E set(int index, E element) {
            Object o = myvalues.set(index, unwrap(element));
            return (E) (o == null ? null : createFieldValue(o));
        }

        public void add(int index, E element) {
            myvalues.add(index, unwrap(element));
        }

        public E remove(int index) {
            Object o = myvalues.remove(index);
            return (E) (o == null ? null : createFieldValue(o));
        }

        public int indexOf(Object o) {
            return myvalues.indexOf(unwrap(o));
        }

        public int lastIndexOf(Object o) {
            return myvalues.lastIndexOf(unwrap(o));
        }

        public ListIterator<E> listIterator() {
            return listIterator(0);
        }

        public ListIterator<E> listIterator(final int index) {
            return new ListIterator<E>() {
                ListIterator it = myvalues.listIterator(index);

                public boolean hasNext() {
                    return it.hasNext();
                }

                public E next() {
                    return (E) createFieldValue(it.next());
                }

                public boolean hasPrevious() {
                    return it.hasPrevious();
                }

                public E previous() {
                    return (E) createFieldValue(it.previous());
                }

                public int nextIndex() {
                    return it.nextIndex();
                }

                public int previousIndex() {
                    return it.previousIndex();
                }

                public void remove() {
                    it.remove();
                }

                public void set(E o) {
                    it.set(unwrap(o));
                }

                public void add(E o) {
                    it.add(unwrap(o));
                }
            };
        }

        @SuppressWarnings("deprecation")
        public List<E> subList(int fromIndex, int toIndex) {
            return new ListWrapper<E>(myvalues.subList(fromIndex, toIndex));
        }

        public String toString() {
            return myvalues.toString();
        }

        @Override
        @SuppressWarnings("deprecation, unchecked")
        public boolean equals(Object o) {
            return this == o || o instanceof ListWrapper && myvalues.equals(((ListWrapper) o).myvalues);
        }

        @Override
        public int hashCode() {
            return myvalues.hashCode();
        }
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    @Override
    public int compareTo(FieldValue fieldValue) {
        int comp = super.compareTo(fieldValue);

        if (comp != 0) {
            return comp;
        }

        //types are equal, this must be of this type
        Array otherValue = (Array) fieldValue;
        return CollectionComparator.compare(values, otherValue.values);
    }
}
