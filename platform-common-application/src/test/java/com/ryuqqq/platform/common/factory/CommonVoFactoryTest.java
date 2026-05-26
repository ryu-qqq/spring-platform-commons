package com.ryuqqq.platform.common.factory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ryuqqq.platform.common.vo.CursorPageRequest;
import com.ryuqqq.platform.common.vo.CursorQueryContext;
import com.ryuqqq.platform.common.vo.PageRequest;
import com.ryuqqq.platform.common.vo.QueryContext;
import com.ryuqqq.platform.common.vo.SortDirection;
import com.ryuqqq.platform.common.vo.SortKey;

import static org.assertj.core.api.Assertions.assertThat;

class CommonVoFactoryTest {

    private CommonVoFactory factory;

    private enum TestSortKey implements SortKey {
        CREATED_AT("createdAt");

        private final String fieldName;

        TestSortKey(String fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        public String fieldName() {
            return fieldName;
        }
    }

    @BeforeEach
    void setUp() {
        factory = new CommonVoFactory();
    }

    @Nested
    @DisplayName("createQueryContext")
    class CreateQueryContextTest {

        @Test
        @DisplayName("adapter-in이 넘긴 VO로 QueryContext 조립")
        void assemblesFromValidatedVos() {
            PageRequest pageRequest = PageRequest.of(1, 10);
            QueryContext<TestSortKey> context = factory.createQueryContext(
                    TestSortKey.CREATED_AT, SortDirection.ASC, pageRequest, true);

            assertThat(context.sortKey()).isEqualTo(TestSortKey.CREATED_AT);
            assertThat(context.sortDirection()).isEqualTo(SortDirection.ASC);
            assertThat(context.page()).isEqualTo(1);
            assertThat(context.size()).isEqualTo(10);
            assertThat(context.includeDeleted()).isTrue();
        }

        @Test
        @DisplayName("includeDeleted 기본 false")
        void excludeDeletedByDefault() {
            QueryContext<TestSortKey> context = factory.createQueryContext(
                    TestSortKey.CREATED_AT, SortDirection.DESC, PageRequest.firstPage());

            assertThat(context.includeDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("createCursorQueryContext")
    class CreateCursorQueryContextTest {

        @Test
        @DisplayName("adapter-in이 넘긴 cursor VO로 CursorQueryContext 조립")
        void assemblesFromValidatedVos() {
            CursorPageRequest<Long> pageRequest = CursorPageRequest.of(42L, 15);
            CursorQueryContext<TestSortKey, Long> context = factory.createCursorQueryContext(
                    TestSortKey.CREATED_AT, SortDirection.DESC, pageRequest);

            assertThat(context.cursor()).isEqualTo(42L);
            assertThat(context.size()).isEqualTo(15);
            assertThat(context.isFirstPage()).isFalse();
            assertThat(context.includeDeleted()).isFalse();
        }
    }
}
