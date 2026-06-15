package com.ryuqqq.platform.archrules.support;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.ViolationStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** self-test 전용 in-memory 위반 스토어 — 파일을 건드리지 않고 freezing baseline을 보관한다. */
public final class InMemoryViolationStore implements ViolationStore {

    private final Map<String, List<String>> store = new HashMap<>();

    @Override
    public void initialize(Properties properties) {
        // 메모리 스토어 — 초기화 불필요
    }

    @Override
    public boolean contains(ArchRule rule) {
        return store.containsKey(rule.getDescription());
    }

    @Override
    public void save(ArchRule rule, List<String> violations) {
        store.put(rule.getDescription(), new ArrayList<>(violations));
    }

    @Override
    public List<String> getViolations(ArchRule rule) {
        return new ArrayList<>(store.get(rule.getDescription()));
    }
}
