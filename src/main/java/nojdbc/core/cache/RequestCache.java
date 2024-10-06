package nojdbc.core.cache;

import nojdbc.core.pojo.Pair;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.isNull;

public final class RequestCache {

    private static final Object MONITOR = new Object();

    private static volatile RequestCache instance;

    private final AtomicBoolean useThreshold = new AtomicBoolean(true);
    private final AtomicInteger validThresholdSeconds = new AtomicInteger(2);

    //HashMap<Pair<Class<G>, Class<V>>, Cache<T,G<V>>>
    private final ThreadLocal<HashMap<Pair<Class<?>, Class<?>>, Cache<?, ?>>> cacheThreadLocal = ThreadLocal.withInitial(HashMap::new);

    private RequestCache() {
    }

    /**
     * Получение экземпляра сервиса кэширования.
     *
     * @return сервис кэширования.
     */
    public static RequestCache getInstance() {
        if (instance == null) {
            synchronized (MONITOR) {
                if (instance == null) {
                    instance = new RequestCache();
                }
            }
        }
        return instance;
    }

    /**
     * Проверка наличия значения в кэше.
     *
     * @param generic    класс идентификатора.
     * @param valueClass класс значения.
     * @param id         идентификатор.
     * @param <T>        тип объекта идентификатора.
     * @param <V>        тип объекта значения.
     * @return true - если значение существует и действительно, иначе false.
     */
    public <T, V> boolean hasValue(Class<?> generic, Class<V> valueClass, T id) {
        final var cache = cacheThreadLocal.get();
        final Cache<T, V> classCache = (Cache<T, V>) cache.get(Pair.of(generic, valueClass));
        return !isNull(classCache)
              && (!useThreshold.get() || classCache.isValid(validThresholdSeconds.get()))
              && classCache.containId(id);
    }

    /**
     * Получение значения из кэша.
     *
     * @param generic    класс идентификатора.
     * @param valueClass класс значения.
     * @param id         идентификатор.
     * @param <T>        тип объекта идентификатора.
     * @param <V>        тип объекта значения.
     * @return значение.
     */
    public <T, V> V get(Class<?> generic, Class<?> valueClass, T id) {
        final var cache = cacheThreadLocal.get();
        final Cache<T, V> classCache = (Cache<T, V>) cache.get(Pair.of(generic, valueClass));
        if (isNull(classCache)
              || (useThreshold.get() && !classCache.isValid(validThresholdSeconds.get()))
              || !classCache.containId(id)
        ) {
            return null;
        }
        return classCache.get(id);
    }

    /**
     * Создание записи в кэше.
     *
     * @param generic    класс идентификатора.
     * @param valueClass класс значения.
     * @param id         идентификатор.
     * @param value      значение.
     * @param <T>        тип объекта идентификатора.
     * @param <V>        тип объекта значения.
     */
    public <T, V> void put(Class<?> generic, Class<?> valueClass, T id, V value) {
        final var cache = cacheThreadLocal.get();
        Cache<T, V> classCache = (Cache<T, V>) cache.get(Pair.of(generic, valueClass));
        if (isNull(classCache)
              || (useThreshold.get() && !classCache.isValid(validThresholdSeconds.get()))
        ) {
            classCache = new Cache<>();
            cache.put(Pair.of(generic, valueClass), classCache);
        }
        classCache.add(id, value);
    }

    /**
     * Установка логического значения использования порога хранения кэшированного ответа.
     *
     * @param useThreshold логическое значение использования порога хранения кэшированного ответа.
     */
    public void setUseThreshold(boolean useThreshold) {
        this.useThreshold.set(useThreshold);
    }

    /**
     * Установка значения хранения кэшированного ответа в секундах.
     *
     * @param seconds значение хранения кэшированного ответа в секундах.
     */
    public void setValidThresholdSeconds(int seconds) {
        this.validThresholdSeconds.set(seconds);
    }

    /**
     * Очищение кэша.
     */
    public void clean() {
        cacheThreadLocal.remove();
    }

}
