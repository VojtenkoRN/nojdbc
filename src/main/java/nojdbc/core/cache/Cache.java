package nojdbc.core.cache;

import java.time.OffsetDateTime;
import java.util.HashMap;

class Cache<T, V> {

    private final OffsetDateTime createTime = OffsetDateTime.now();
    private final HashMap<T, V> idToValues = new HashMap<>();

    public void add(T id, V value) {
        idToValues.put(id, value);
    }

    public boolean containId(T id) {
        return idToValues.containsKey(id);
    }

    public V get(T id) {
        return idToValues.get(id);
    }

    public boolean isValid(int validThresholdSeconds) {
        return OffsetDateTime.now().minusSeconds(validThresholdSeconds).isBefore(createTime);
    }

}
