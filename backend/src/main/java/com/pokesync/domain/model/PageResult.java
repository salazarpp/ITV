package com.pokesync.domain.model;

public record PageResult<T>(long count, java.util.List<T> results) {
    public PageResult { results = java.util.List.copyOf(results); }
}
