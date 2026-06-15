package com.fitback.core.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fitback.core.application.port.TenantDataRepository;

@Component
public class InMemoryTenantDataRepository implements TenantDataRepository {

    private final Map<String, Map<UUID, Map<String, Object>>> collections = new ConcurrentHashMap<>();
    private final Map<String, Object> singletons = new ConcurrentHashMap<>();

    public Map<String, Object> create(String collection, Map<String, Object> values) {
        Map<String, Object> entity = new LinkedHashMap<>(values);
        UUID id = UUID.randomUUID();
        entity.put("id", id.toString());
        entity.putIfAbsent("createdAt", Instant.now().toString());
        collections.computeIfAbsent(collectionKey(collection), ignored -> new ConcurrentHashMap<>()).put(id, entity);
        return copy(entity);
    }

    public List<Map<String, Object>> list(String collection) {
        return collections.getOrDefault(collectionKey(collection), Map.of()).values().stream()
                .filter(entity -> !Boolean.TRUE.equals(entity.get("deleted")))
                .map(this::copy)
                .toList();
    }

    public Map<String, Object> get(String collection, String id) {
        Map<String, Object> entity = collections.getOrDefault(collectionKey(collection), Map.of()).get(uuid(id));
        if (entity == null || Boolean.TRUE.equals(entity.get("deleted"))) {
            throw new EntityNotFoundException(collection + " not found: " + id);
        }
        return copy(entity);
    }

    public Map<String, Object> update(String collection, String id, Map<String, Object> changes) {
        Map<String, Object> entity = collections.getOrDefault(collectionKey(collection), Map.of()).get(uuid(id));
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
        Object value = singletons.get(singletonKey(name));
        return value instanceof Map<?, ?> map ? copy(cast(map)) : new LinkedHashMap<>();
    }

    public Map<String, Object> singleton(String name, Map<String, Object> value) {
        Map<String, Object> copy = copy(value);
        singletons.put(singletonKey(name), copy);
        return copy(copy);
    }

    public List<Map<String, Object>> matching(String collection, String key, Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> entity : collections.getOrDefault(collectionKey(collection), Map.of()).values()) {
            if (!Boolean.TRUE.equals(entity.get("deleted")) && String.valueOf(entity.get(key)).equals(String.valueOf(value))) {
                result.add(copy(entity));
            }
        }
        return result;
    }

    public void removeMatching(String collection, String key, Object value) {
        collections.getOrDefault(collectionKey(collection), Map.of()).entrySet().removeIf(entry ->
                String.valueOf(entry.getValue().get(key)).equals(String.valueOf(value)));
    }

    public Map<String, Object> updateAcrossTenants(String collection, String id, Map<String, Object> changes) {
        UUID entityId = uuid(id);
        for (Map.Entry<String, Map<UUID, Map<String, Object>>> entry : collections.entrySet()) {
            if (entry.getKey().endsWith(":" + collection) && entry.getValue().containsKey(entityId)) {
                entry.getValue().get(entityId).putAll(changes);
                return copy(entry.getValue().get(entityId));
            }
        }
        throw new EntityNotFoundException(collection + " not found: " + id);
    }

    public Map<String, Object> updateAcrossTenantsMatching(String collection, String key, Object value,
            Map<String, Object> changes) {
        for (Map.Entry<String, Map<UUID, Map<String, Object>>> entry : collections.entrySet()) {
            if (entry.getKey().endsWith(":" + collection)) {
                for (Map<String, Object> entity : entry.getValue().values()) {
                    if (String.valueOf(entity.get(key)).equals(String.valueOf(value))) {
                        entity.putAll(changes);
                        return copy(entity);
                    }
                }
            }
        }
        throw new EntityNotFoundException(collection + " not found by " + key);
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

    private String collectionKey(String collection) {
        if (collection.equals("users") || collection.equals("refreshTokens") || collection.equals("passwordResetTokens")) {
            return "global:" + collection;
        }
        return tenantId() + ":" + collection;
    }

    private String singletonKey(String name) {
        return tenantId() + ":" + name;
    }

    private String tenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() ? "anonymous" : authentication.getName();
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
