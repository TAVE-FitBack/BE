package com.fitback.core.application.port;

import java.util.List;
import java.util.Map;

public interface TenantDataRepository {
    Map<String, Object> create(String collection, Map<String, Object> values);
    List<Map<String, Object>> list(String collection);
    Map<String, Object> get(String collection, String id);
    Map<String, Object> update(String collection, String id, Map<String, Object> changes);
    void softDelete(String collection, String id);
    Map<String, Object> singleton(String name);
    Map<String, Object> singleton(String name, Map<String, Object> value);
    List<Map<String, Object>> matching(String collection, String key, Object value);
    void removeMatching(String collection, String key, Object value);
    Map<String, Object> updateAcrossTenants(String collection, String id, Map<String, Object> changes);
    Map<String, Object> updateAcrossTenantsMatching(String collection, String key, Object value, Map<String, Object> changes);
    long count(String collection);
}
