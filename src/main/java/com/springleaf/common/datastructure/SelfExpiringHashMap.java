package com.springleaf.common.datastructure;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class SelfExpiringHashMap<K, V> implements SelfExpiringMap<K, V>{
    private final Map<K, V> internalMap;

    private final Map<K, ExpiringKey<K>> expiringKeys;

    private final DelayQueue<ExpiringKey> delayQueue = new DelayQueue<ExpiringKey>();

    private final long maxLifeTimeMillis;

    public SelfExpiringHashMap() {
        internalMap = new ConcurrentHashMap<K, V>();
        expiringKeys = new WeakHashMap<K, ExpiringKey<K>>();
        this.maxLifeTimeMillis = Long.MAX_VALUE;
    }

    public SelfExpiringHashMap(long defaultMaxLifeTimeMillis) {
        internalMap = new ConcurrentHashMap<K, V>();
        expiringKeys = new WeakHashMap<K, ExpiringKey<K>>();
        this.maxLifeTimeMillis = defaultMaxLifeTimeMillis;
    }

    public SelfExpiringHashMap(long defaultMaxLifeTimeMillis, int initialCapacity) {
        internalMap = new ConcurrentHashMap<K, V>(initialCapacity);
        expiringKeys = new WeakHashMap<K, ExpiringKey<K>>(initialCapacity);
        this.maxLifeTimeMillis = defaultMaxLifeTimeMillis;
    }

    public SelfExpiringHashMap(long defaultMaxLifeTimeMillis, int initialCapacity, float loadFactor) {
        internalMap = new ConcurrentHashMap<K, V>(initialCapacity, loadFactor);
        expiringKeys = new WeakHashMap<K, ExpiringKey<K>>(initialCapacity, loadFactor);
        this.maxLifeTimeMillis = defaultMaxLifeTimeMillis;
    }


    @Override
    public int size() {
        cleanup();
        return internalMap.size();
    }


    @Override
    public boolean isEmpty() {
        cleanup();
        return internalMap.isEmpty();
    }


    @Override
    public boolean containsKey(Object key) {
        cleanup();
        return internalMap.containsKey((K) key);
    }


    @Override
    public boolean containsValue(Object value) {
        cleanup();
        return internalMap.containsValue((V) value);
    }

    @Override
    public V get(Object key) {
        cleanup();
        renewKey((K) key);
        return internalMap.get((K) key);
    }


    @Override
    public V put(K key, V value) {
        return this.put(key, value, maxLifeTimeMillis);
    }


    @Override
    public V put(K key, V value, long lifeTimeMillis) {
        cleanup();
        ExpiringKey delayedKey = new ExpiringKey(key, lifeTimeMillis);
        ExpiringKey oldKey = expiringKeys.put((K) key, delayedKey);
        if(oldKey != null) {
            expireKey(oldKey);
            expiringKeys.put((K) key, delayedKey);
        }
        delayQueue.offer(delayedKey);
        return internalMap.put(key, value);
    }


    @Override
    public V remove(Object key) {
        V removedValue = internalMap.remove((K) key);
        expireKey(expiringKeys.remove((K) key));
        return removedValue;
    }


    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean renewKey(K key) {
        ExpiringKey<K> delayedKey = expiringKeys.get((K) key);
        if (delayedKey != null) {
            delayedKey.renew();
            return true;
        }
        return false;
    }

    private void expireKey(ExpiringKey<K> delayedKey) {
        if (delayedKey != null) {
            delayedKey.expire();
            cleanup();
        }
    }


    @Override
    public void clear() {
        delayQueue.clear();
        expiringKeys.clear();
        internalMap.clear();
    }


    @Override
    public Set<K> keySet() {
        return internalMap.keySet();
//        throw new UnsupportedOperationException();
    }


    @Override
    public Collection<V> values() {
        return internalMap.values();
//        throw new UnsupportedOperationException();
    }


    @Override
    public Set<Entry<K, V>> entrySet() {
        return internalMap.entrySet();
//        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    private void cleanup() {
        ExpiringKey<K> delayedKey = delayQueue.poll();
        while (delayedKey != null) {
            internalMap.remove(delayedKey.getKey());
            expiringKeys.remove(delayedKey.getKey());
            delayedKey = delayQueue.poll();
        }
    }

    private class ExpiringKey<K> implements Delayed {

        private long startTime = System.currentTimeMillis();
        private final long maxLifeTimeMillis;
        private final K key;

        public ExpiringKey(K key, long maxLifeTimeMillis) {
            this.maxLifeTimeMillis = maxLifeTimeMillis;
            this.key = key;
        }

        public K getKey() {
            return key;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ExpiringKey<K> other = (ExpiringKey<K>) obj;
            if (this.key != other.key && (this.key == null || !this.key.equals(other.key))) {
                return false;
            }
            return true;
        }


        @Override
        public int hashCode() {
            int hash = 7;
            hash = 31 * hash + (this.key != null ? this.key.hashCode() : 0);
            return hash;
        }


        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(getDelayMillis(), TimeUnit.MILLISECONDS);
        }

        private long getDelayMillis() {
            return (startTime + maxLifeTimeMillis) - System.currentTimeMillis();
        }

        public void renew() {
            startTime = System.currentTimeMillis();
        }

        public void expire() {
            startTime = Long.MIN_VALUE;
        }


        @Override
        public int compareTo(Delayed that) {
            return Long.compare(this.getDelayMillis(), ((ExpiringKey) that).getDelayMillis());
        }
    }
}
