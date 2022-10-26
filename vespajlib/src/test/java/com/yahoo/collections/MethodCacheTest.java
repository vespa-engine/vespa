package com.yahoo.collections;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodCacheTest {

    @Test
    void testCache() throws Exception {

        URL url = MethodCacheTest.class.getClassLoader().getResource("dummy").toURI().resolve(".").toURL();

        class MyLoader extends URLClassLoader {
            MyLoader() { super(new URL[] { url }, MethodCacheTest.class.getClassLoader()); }
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals(Dummy.class.getName())) synchronized (getClassLoadingLock(name)) { return findClass(name); }
                else return super.loadClass(name);
            }
        }

        try (MyLoader myLoader = new MyLoader()) {
            Class<?> applicationClass = Dummy.class;
            Class<?> customClass = myLoader.loadClass(Dummy.class.getName());

            assertNotSame(applicationClass, customClass);
            assertSame(applicationClass.getName(), customClass.getName());

            MethodCache methods = new MethodCache("clone");
            AtomicBoolean updatedCache = new AtomicBoolean();
            Object applicationDummy = applicationClass.getConstructor().newInstance();
            Object customDummy = customClass.getConstructor().newInstance();

            Method applicationMethod = methods.get(applicationDummy, __ -> updatedCache.set(true));
            assertTrue(updatedCache.getAndSet(false), "cache was updated");

            Method cachedApplicationMethod = methods.get(applicationDummy, __ -> updatedCache.set(true));
            assertFalse(updatedCache.getAndSet(false), "cache was updated");

            Method customMethod = methods.get(customDummy, __ -> updatedCache.set(true));
            assertTrue(updatedCache.getAndSet(false), "cache was updated");

            Method cachedCustomMethod = methods.get(customDummy, __ -> updatedCache.set(true));
            assertFalse(updatedCache.getAndSet(false), "cache was updated");

            assertSame(applicationMethod, cachedApplicationMethod);
            assertNotSame(applicationMethod, customMethod);
            assertSame(customMethod, cachedCustomMethod);

            cachedApplicationMethod.invoke(applicationDummy);
            cachedCustomMethod.invoke(customDummy);
            assertThrows(IllegalArgumentException.class, () -> applicationMethod.invoke(customDummy));
            assertThrows(IllegalArgumentException.class, () -> customMethod.invoke(applicationDummy));

            Object noDummy = new NoDummy();
            Method noMethod = methods.get(noDummy, __ -> updatedCache.set(true));
            assertTrue(updatedCache.getAndSet(false), "cache was updated");
            assertNull(noMethod);

            Method cachedNoMethod = methods.get(noDummy, __ -> updatedCache.set(true));
            assertFalse(updatedCache.getAndSet(false), "cache was updated");
            assertNull(cachedNoMethod);
        }
    }

    public static class NoDummy implements Cloneable { }

    public static class Dummy implements Cloneable {
        public Object clone() throws CloneNotSupportedException { return super.clone(); }
    }

}
