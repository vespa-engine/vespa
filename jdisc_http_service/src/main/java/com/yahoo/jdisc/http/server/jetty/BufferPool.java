// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.yolean.concurrent.ConcurrentResourcePool;
import com.yahoo.yolean.concurrent.ResourceFactory;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;

import java.nio.ByteBuffer;


/**
 * @author <a href="mailto:balder@yahoo-inc.com">Henning</a>
 * @since 6.41
 * TODO: Add tests, currently uses no pooling to chase down nasty jetty bug.
 */

class BufferPool implements ByteBufferPool {
    private static ByteBuffer allocate(int capacity, boolean direct) {
        return direct ? BufferUtil.allocateDirect(capacity) : BufferUtil.allocate(capacity);
    }
    private static abstract class Pool {
        ByteBuffer aquire(int size, boolean direct) {
            return aquireImpl(size, direct);
        }
        void release(ByteBuffer buf) { releaseImpl(buf); }
        protected abstract ByteBuffer aquireImpl(int size, boolean direct);
        protected abstract void releaseImpl(ByteBuffer buf);
    };
    private static class NoPool extends Pool {
        @Override
        protected ByteBuffer aquireImpl(int size, boolean direct) {
            return BufferPool.allocate(size, direct);
        }
        @Override
        protected void releaseImpl(ByteBuffer buf) {
            buf = null;
        }
    }
    private static class ConcurrentPool extends Pool {
        class ByteBufferFactory extends ResourceFactory<ByteBuffer> {
            final int size;
            final boolean direct;
            ByteBufferFactory(int size, boolean direct) {
                this.size = size;
                this.direct = direct;
            }

            @Override
            public ByteBuffer create() {
                return BufferPool.allocate(size, direct);
            }
        }

        private final ConcurrentResourcePool<ByteBuffer> pool;

        ConcurrentPool(int size, boolean direct) {
            pool = new ConcurrentResourcePool<>(new ByteBufferFactory(size, direct));
        }
        @Override
        protected ByteBuffer aquireImpl(int size, boolean direct) {
            return pool.alloc();
        }

        @Override
        protected void releaseImpl(ByteBuffer buf) {
            pool.free(buf);
        }
    }

    private final int minSize2Pool;
    private final int maxSize2Pool;
    private final int numPools;
    private final NoPool noPooling = new NoPool();
    private final Pool [] directPools;
    private final Pool [] heapPools;
    BufferPool() {
        // No pooling.
        this(Integer.MAX_VALUE, 0, 0);
    }
    BufferPool(int minSize2Pool, int maxSize2Pool, int numPools) {
        this.minSize2Pool = minSize2Pool;
        this.maxSize2Pool = maxSize2Pool;
        this.numPools = numPools;
        if ((numPools > 0) && (maxSize2Pool > minSize2Pool)) {
            directPools = new Pool[numPools];
            heapPools = new Pool[numPools];
            int size = (maxSize2Pool - minSize2Pool) / numPools;
            for (int i = 0; i < numPools; i++) {
                int maxSize = minSize2Pool + size*(i+1);
                directPools[i] = new ConcurrentPool(maxSize, true);
                heapPools[i] = new ConcurrentPool(maxSize, false);
            }
        } else {
            directPools = null;
            heapPools = null;
        }
    }

    @Override
    public ByteBuffer acquire(int size, boolean direct) {
        return selectPool(size, direct).aquire(size, direct);
    }

    @Override
    public void release(ByteBuffer buffer) {
        selectPool(buffer.capacity(), buffer.isDirect()).release(buffer);

    }
    private Pool selectPool(int size, boolean direct) {
        if (poolable(size)) {
            int offsetSize = size - minSize2Pool;
            int index = offsetSize * numPools / (maxSize2Pool - minSize2Pool);
            return direct ? directPools[index] : heapPools[index];
        } else {
            return noPooling;
        }
    }
    private boolean poolable(int size) {
        return (size > minSize2Pool) && (size <= maxSize2Pool);
    }
}
