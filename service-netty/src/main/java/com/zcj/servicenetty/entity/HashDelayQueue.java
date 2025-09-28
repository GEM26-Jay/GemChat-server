package com.zcj.servicenetty.entity;

import cn.hutool.core.collection.ConcurrentHashSet;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;

public class HashDelayQueue <T extends Delayed & UniqueIdentifiable> {

    DelayQueue<T> delayQueue = new DelayQueue<>();
    ConcurrentHashSet<String> hashSet = new ConcurrentHashSet<>();
    final Object lock = new Object();

    public void put(T t) {
        delayQueue.add(t);
        hashSet.add(t.getUniqueId());
    }

    public T poll() {
        T t = delayQueue.poll();
        while(t!=null && !hashSet.contains(t.getUniqueId())) {
            t = delayQueue.poll();
        }
        return t;
    }

    public T take() throws InterruptedException {
        T t = delayQueue.take();
        while (!hashSet.contains(t.getUniqueId())) {
            t = delayQueue.take();
        }
        return t;
    }

    public void remove(String identity) {
        hashSet.remove(identity);
    }

    public void remove(T t) {
        hashSet.remove(t.getUniqueId());
    }

    public boolean isEmpty() {
        return hashSet.isEmpty();
    }
}
