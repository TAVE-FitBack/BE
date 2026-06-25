package com.fitback.core.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fitback.core.application.port.EntityNotFoundException;
import com.fitback.core.application.port.TenantDataRepository;
import com.fitback.core.infrastructure.persistence.TenantRecord;
import com.fitback.core.infrastructure.persistence.TenantRecordRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class JpaTenantDataRepository implements TenantDataRepository {

    private static final String SINGLETON_COLLECTION_PREFIX = "__singleton__:";

    private final TenantRecordRepository records;
    private final ObjectMapper mapper = new ObjectMapper();

    public JpaTenantDataRepository(TenantRecordRepository records) {
        this.records = records;
    }

    @Override
    @Transactional
    public Map<String, Object> create(String collection, Map<String, Object> values) {
        Map<String, Object> entity = new LinkedHashMap<>(values);
        UUID id = UUID.randomUUID();
        entity.put("id", id.toString());
        entity.putIfAbsent("createdAt", Instant.now().toString());
        records.save(new TenantRecord(id, tenantId(), collection, toJson(entity)));
        return entity;
    }

    @Override
    public List<Map<String, Object>> list(String collection) {
        return records.findByTenantIdAndCollection(tenantId(), collection).stream()
                .map(record -> fromJson(record.getDataJson()))
                .filter(entity -> !Boolean.TRUE.equals(entity.get("deleted")))
                .toList();
    }

    @Override
    public Map<String, Object> get(String collection, String id) {
        TenantRecord record = records.findByIdAndTenantIdAndCollection(uuid(id), tenantId(), collection)
                .orElseThrow(() -> new EntityNotFoundException(collection + " not found: " + id));
        Map<String, Object> entity = fromJson(record.getDataJson());
        if (Boolean.TRUE.equals(entity.get("deleted"))) {
            throw new EntityNotFoundException(collection + " not found: " + id);
        }
        return entity;
    }

    @Override
    @Transactional
    public Map<String, Object> update(String collection, String id, Map<String, Object> changes) {
        TenantRecord record = records.findByIdAndTenantIdAndCollection(uuid(id), tenantId(), collection)
                .orElseThrow(() -> new EntityNotFoundException(collection + " not found: " + id));
        Map<String, Object> entity = fromJson(record.getDataJson());
        entity.putAll(changes);
        entity.put("updatedAt", Instant.now().toString());
        record.setDataJson(toJson(entity));
        return entity;
    }

    @Override
    @Transactional
    public void softDelete(String collection, String id) {
        update(collection, id, Map.of("deleted", true, "isActive", false));
    }

    @Override
    public Map<String, Object> singleton(String name) {
        return records.findByIdAndTenantIdAndCollection(singletonId(name), tenantId(), singletonCollection(name))
                .map(record -> fromJson(record.getDataJson()))
                .orElseGet(LinkedHashMap::new);
    }

    @Override
    @Transactional
    public Map<String, Object> singleton(String name, Map<String, Object> value) {
        Map<String, Object> entity = new LinkedHashMap<>(value);
        UUID id = singletonId(name);
        TenantRecord record = records.findById(id)
                .orElseGet(() -> new TenantRecord(id, tenantId(), singletonCollection(name), null));
        record.setDataJson(toJson(entity));
        records.save(record);
        return entity;
    }

    @Override
    public List<Map<String, Object>> matching(String collection, String key, Object value) {
        return records.findByTenantIdAndCollection(tenantId(), collection).stream()
                .map(record -> fromJson(record.getDataJson()))
                .filter(entity -> !Boolean.TRUE.equals(entity.get("deleted"))
                        && String.valueOf(entity.get(key)).equals(String.valueOf(value)))
                .toList();
    }

    @Override
    @Transactional
    public void removeMatching(String collection, String key, Object value) {
        List<TenantRecord> matches = records.findByTenantIdAndCollection(tenantId(), collection).stream()
                .filter(record -> String.valueOf(fromJson(record.getDataJson()).get(key)).equals(String.valueOf(value)))
                .toList();
        records.deleteAll(matches);
    }

    @Override
    @Transactional
    public Map<String, Object> updateAcrossTenants(String collection, String id, Map<String, Object> changes) {
        TenantRecord record = records.findById(uuid(id))
                .filter(candidate -> candidate.getCollection().equals(collection))
                .orElseThrow(() -> new EntityNotFoundException(collection + " not found: " + id));
        Map<String, Object> entity = fromJson(record.getDataJson());
        entity.putAll(changes);
        record.setDataJson(toJson(entity));
        return entity;
    }

    @Override
    @Transactional
    public Map<String, Object> updateAcrossTenantsMatching(String collection, String key, Object value,
            Map<String, Object> changes) {
        TenantRecord record = records.findByCollection(collection).stream()
                .filter(candidate -> String.valueOf(fromJson(candidate.getDataJson()).get(key)).equals(String.valueOf(value)))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(collection + " not found by " + key));
        Map<String, Object> entity = fromJson(record.getDataJson());
        entity.putAll(changes);
        record.setDataJson(toJson(entity));
        return entity;
    }

    @Override
    public long count(String collection) {
        return list(collection).size();
    }

    private UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException("invalid id: " + id);
        }
    }

    private String singletonCollection(String name) {
        return SINGLETON_COLLECTION_PREFIX + name;
    }

    private UUID singletonId(String name) {
        return UUID.nameUUIDFromBytes((tenantId() + ":" + name).getBytes(StandardCharsets.UTF_8));
    }

    private String tenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() ? "anonymous" : authentication.getName();
    }

    private String toJson(Map<String, Object> entity) {
        return mapper.writeValueAsString(entity);
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(mapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
    }
}
