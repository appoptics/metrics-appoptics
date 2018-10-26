package com.librato.metrics.reporter;

@Deprecated
public interface Supplier<T> {
    T get();
}
