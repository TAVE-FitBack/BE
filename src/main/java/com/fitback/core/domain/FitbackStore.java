package com.fitback.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class FitbackStore {

    private final Map<String, Map<UUID, Map<String, Object>>> collections = new ConcurrentHashMap<>();
    private final Map<String, Object> singletons = new ConcurrentHashMap<>();

    public Map<String, Object> create(String collection, Map<String, Object> values) {
        Map<String, Object> entity = new LinkedHashMap<>(values);
        UUID id = UUID.randomUUID();
        entity.put("id", id.toString());
        entity.putIfAbsent("createdAt", Instant.now().toString());
        collections.computeIfAbsent(collection, ignored -> new ConcurrentHashMap<>()).put(id, entity);
        return copy(entity);
    }

    public List<Map<String, Object>> list(String collection) {
        return collections.getOrDefault(collection, Map.of()).values().stream().map(this::copy).toList();
    }

    public Map<String, Object> get(String collection, String id) {
        Map<String, Object> entity = collections.getOrDefault(collection, Map.of()).get(uuid(id));
        if (entity == null || Boolean.TRUE.equals(entity.get("deleted"))) {
            throw new EntityNotFoundException(collection + " not found: " + id);
        }
        return copy(entity);
    }

    public Map<String, Object> update(String collection, String id, Map<String, Object> changes) {
        Map<String, Object> entity = collections.getOrDefault(collection, Map.of()).get(uuid(id));
        if (entity == null) {
            throw new EntityNotFoundException(collection + " not found: " + id);
        }
        entity.putAll(changes);
        entity.put("updatedAt", Instant.now().toString());
        return copy(entity);
    }

    public void softDelete(String collection, String id) {
        update(collection, id, Map.of("deleted", true, "isActive", false));
    }

    public Map<String, Object> singleton(String name) {
        Object value = singletons.get(name);
        return value instanceof Map<?, ?> map ? copy(cast(map)) : new LinkedHashMap<>();
    }

    public Map<String, Object> singleton(String name, Map<String, Object> value) {
        Map<String, Object> copy = copy(value);
        singletons.put(name, copy);
        return copy(copy);
    }

    public List<Map<String, Object>> matching(String collection, String key, Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> entity : collections.getOrDefault(collection, Map.of()).values()) {
            if (!Boolean.TRUE.equals(entity.get("deleted")) && String.valueOf(entity.get(key)).equals(String.valueOf(value))) {
                result.add(copy(entity));
            }
        }
        return result;
    }

    public void removeMatching(String collection, String key, Object value) {
        collections.getOrDefault(collection, Map.of()).entrySet().removeIf(entry ->
                String.valueOf(entry.getValue().get(key)).equals(String.valueOf(value)));
    }

    public long count(String collection) {
        return list(collection).stream().filter(entity -> !Boolean.TRUE.equals(entity.get("deleted"))).count();
    }

    private UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException("invalid id: " + id);
        }
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    public static class EntityNotFoundException extends RuntimeException {
        public EntityNotFoundException(String message) {
            super(message);
        }
    }
}
