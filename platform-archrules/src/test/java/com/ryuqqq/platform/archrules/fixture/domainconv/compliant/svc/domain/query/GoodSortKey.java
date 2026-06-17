package com.ryuqqq.platform.archrules.fixture.domainconv.compliant.svc.domain.query;

import com.ryuqqq.platform.common.vo.SortKey;

/** Compliant SortKey — enum implements SortKey. */
public enum GoodSortKey implements SortKey {
    CREATED_AT;

    @Override
    public String fieldName() {
        return "createdAt";
    }
}
