package com.ryuqqq.platform.common.factory;

import com.ryuqqq.platform.common.vo.CursorPageRequest;
import com.ryuqqq.platform.common.vo.CursorQueryContext;
import com.ryuqqq.platform.common.vo.PageRequest;
import com.ryuqqq.platform.common.vo.QueryContext;
import com.ryuqqq.platform.common.vo.SortDirection;
import com.ryuqqq.platform.common.vo.SortKey;

/**
 * 공통 query VO 조립. {@code SearchCriteria}를 만드는 Query Factory가 위임한다.
 *
 * <p><b>입력 전제 (APP-IN-001):</b> adapter-in mapper가 paging·sort·date 등을 이미 검증·기본값 적용한
 * 도메인 VO / enum만 넘긴다. null 처리·HTTP 문자열 parse·default page/size는 Factory 책임이 아니다.
 *
 * <p>무상태(stateless)다. 빈 등록은 컴포넌트 스캔이 아니라 {@code PlatformCommonApplicationAutoConfiguration}
 * 이 zero-config 로 담당한다 — 소비측 스캔 범위와 무관하게 주입된다.
 */
public class CommonVoFactory {

    /**
     * offset 기반 {@link QueryContext} 조립. 삭제된 행은 기본 제외.
     *
     * @param sortKey 정렬 키 (non-null)
     * @param sortDirection 정렬 방향 (non-null)
     * @param pageRequest 페이징 (non-null)
     */
    public <T extends SortKey> QueryContext<T> createQueryContext(
            T sortKey, SortDirection sortDirection, PageRequest pageRequest) {
        return QueryContext.of(sortKey, sortDirection, pageRequest);
    }

    /**
     * offset 기반 {@link QueryContext} 조립.
     *
     * @param sortKey 정렬 키 (non-null)
     * @param sortDirection 정렬 방향 (non-null)
     * @param pageRequest 페이징 (non-null)
     * @param includeDeleted soft-deleted 행 포함 여부
     */
    public <T extends SortKey> QueryContext<T> createQueryContext(
            T sortKey, SortDirection sortDirection, PageRequest pageRequest, boolean includeDeleted) {
        return QueryContext.of(sortKey, sortDirection, pageRequest, includeDeleted);
    }

    /**
     * 커서 기반 {@link CursorQueryContext} 조립. 삭제된 행은 기본 제외.
     *
     * @param sortKey 정렬 키 (non-null)
     * @param sortDirection 정렬 방향 (non-null)
     * @param pageRequest 커서 페이징 (non-null)
     */
    public <T extends SortKey, C> CursorQueryContext<T, C> createCursorQueryContext(
            T sortKey, SortDirection sortDirection, CursorPageRequest<C> pageRequest) {
        return CursorQueryContext.of(sortKey, sortDirection, pageRequest);
    }

    /**
     * 커서 기반 {@link CursorQueryContext} 조립.
     *
     * @param sortKey 정렬 키 (non-null)
     * @param sortDirection 정렬 방향 (non-null)
     * @param pageRequest 커서 페이징 (non-null)
     * @param includeDeleted soft-deleted 행 포함 여부
     */
    public <T extends SortKey, C> CursorQueryContext<T, C> createCursorQueryContext(
            T sortKey,
            SortDirection sortDirection,
            CursorPageRequest<C> pageRequest,
            boolean includeDeleted) {
        return CursorQueryContext.of(sortKey, sortDirection, pageRequest, includeDeleted);
    }
}
