// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

/**
 * Wrapper for dimension values.
 *
 * @author steinar
 */
public abstract class Value {
    private static final String UNSUPPORTED_VALUE_TYPE = "Unsupported value type.";

    /**
     * Marker for the type of the contained value of a Value instance.
     */
    public enum Discriminator {
        LONG, DOUBLE, STRING;
    }

    /**
     * Get the long wrapped by a Value if one exists.
     *
     * @throws UnsupportedOperationException if LONG is not returned by {{@link #getType()}.
     */
    public long longValue() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(UNSUPPORTED_VALUE_TYPE);
    }

    /**
     * Get the double wrapped by a Value if one exists.
     *
     * @throws UnsupportedOperationException if DOUBLE is not returned by {{@link #getType()}.
     */
    public double doubleValue() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(UNSUPPORTED_VALUE_TYPE);
    }

    /**
     * Get the string wrapped by a Value if one exists.
     *
     * @throws UnsupportedOperationException if STRING is not returned by {{@link #getType()}.
     */
    public String stringValue() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(UNSUPPORTED_VALUE_TYPE);
    }

    /**
     * Show the (single) supported standard type representation of a Value instance.
     */
    public abstract Discriminator getType();

    private static class LongValue extends Value {
        private final long value;

        LongValue(long value) {
            this.value = value;
        }

        @Override
        public long longValue() {
            return value;
        }

        @Override
        public Discriminator getType() {
            return Discriminator.LONG;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (value ^ (value >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            LongValue other = (LongValue) obj;
            if (value != other.value) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("LongValue [value=").append(value).append("]");
            return builder.toString();
        }
    }

    private static class DoubleValue extends Value {
        private final double value;

        DoubleValue(double value) {
            this.value = value;
        }

        @Override
        public double doubleValue() {
            return value;
        }

        @Override
        public Discriminator getType() {
            return Discriminator.DOUBLE;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            long temp;
            temp = Double.doubleToLongBits(value);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            DoubleValue other = (DoubleValue) obj;
            if (Double.doubleToLongBits(value) != Double.doubleToLongBits(other.value)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("DoubleValue [value=").append(value).append("]");
            return builder.toString();
        }
    }

    private static class StringValue extends Value {
        private final String value;

        StringValue(String value) {
            this.value = value;
        }

        @Override
        public String stringValue() {
            return value;
        }

        @Override
        public Discriminator getType() {
            return Discriminator.STRING;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            StringValue other = (StringValue) obj;
            if (value == null) {
                if (other.value != null) {
                    return false;
                }
            } else if (!value.equals(other.value)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("StringValue [value=").append(value).append("]");
            return builder.toString();
        }
    }

    /**
     * Helper method to wrap a long as a Value. The instance returned may or may
     * not be unique.
     *
     * @param value
     *            the value to wrap
     * @return an immutable wrapper
     */
    public static Value of(long value) {
        return new LongValue(value);
    }

    /**
     * Helper method to wrap a double as a Value. The instance returned may or
     * may not be unique.
     *
     * @param value
     *            the value to wrap
     * @return an immutable wrapper
     * */
    public static Value of(double value) {
        return new DoubleValue(value);
    }

    /**
     * Helper method to wrap a string as a Value. The instance returned may or
     * may not be unique.
     *
     * @param value
     *            the value to wrap
     * @return an immutable wrapper
     */
    public static Value of(String value) {
        return new StringValue(value);
    }

}
