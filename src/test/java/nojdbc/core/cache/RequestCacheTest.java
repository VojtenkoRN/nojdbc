package nojdbc.core.cache;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestCacheTest {
    @Test
    void testOptional() throws InterruptedException {
        var genericTested = Optional.class;
        var otherGeneric = Set.class;
        var classV = String.class;
        var value = Optional.of("hello");
        var key = 10;
        var otherKey = 1;

        testGeneric(genericTested, otherGeneric, classV, value, key, otherKey);
    }

    @Test
    void testSet() throws InterruptedException {
        var genericTested = Set.class;
        var otherGeneric = List.class;
        var classV = Integer.class;
        var value = Set.of(100);
        var key = "key";
        var otherKey = "other key";

        testGeneric(genericTested, otherGeneric, classV, value, key, otherKey);
    }

    private <K, V> void testGeneric(Class<?> genericTested, Class<?> otherGeneric, Class<?> classV, V value, K key, K otherKey) throws InterruptedException {
        RequestCache cache = RequestCache.getInstance();

        cache.put(genericTested, classV, key, value);
        Object res = cache.get(genericTested, classV, key);
        assertEquals(res, value);
        assertTrue(cache.hasValue(genericTested, classV, key));

        assertNull(cache.get(genericTested, classV, otherKey));
        assertFalse(cache.hasValue(genericTested, classV, otherKey));

        assertNull(cache.get(otherGeneric, classV, key));
        assertFalse(cache.hasValue(otherGeneric, classV, key));

        AtomicReference<Object> atomicReference = new AtomicReference<>();
        new Thread(() -> atomicReference.set(cache.get(genericTested, classV, key))).join();
        assertNull(atomicReference.get());
        AtomicBoolean atomicBoolean = new AtomicBoolean();
        new Thread(() -> atomicBoolean.set(cache.hasValue(genericTested, classV, key))).join();
        assertFalse(atomicBoolean.get());

        cache.clean();
        assertFalse(cache.hasValue(genericTested, classV, key));
        res = cache.get(genericTested, classV, key);
        assertNull(res);
    }

    @Test
    void testTimeout() throws InterruptedException {
        RequestCache cache = RequestCache.getInstance();
        var genericTested = Optional.class;
        var classV = String.class;
        var value = Optional.of("hello");
        var key = 10;

        cache.setUseThreshold(true);
        cache.setValidThresholdSeconds(1);

        cache.put(genericTested, classV, key, value);
        Thread.sleep(2000);

        assertFalse(cache.hasValue(genericTested, classV, key));
        Object res = cache.get(genericTested, classV, key);
        assertNull(res);

        cache.clean();
        assertFalse(cache.hasValue(genericTested, classV, key));
        res = cache.get(genericTested, classV, key);
        assertNull(res);
    }

}