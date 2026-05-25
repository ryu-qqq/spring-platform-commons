package com.ryuqqq.platform.common.vo;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ryuqqq.platform.common.domain.Versioned;

import static org.assertj.core.api.Assertions.assertThat;

class CommonVoTest {

    private enum TestSearchField implements SearchField {
        NAME("name");

        private final String fieldName;

        TestSearchField(String fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        public String fieldName() {
            return fieldName;
        }
    }

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

        static TestSortKey defaultKey() {
            return CREATED_AT;
        }
    }

    @Nested
    @DisplayName("PageRequest")
    class PageRequestTest {

        @Test
        @DisplayName("offset은 page × size")
        void offset() {
            PageRequest request = PageRequest.of(2, 10);

            assertThat(request.offset()).isEqualTo(20L);
        }

        @Test
        @DisplayName("firstPage 기본 size는 20")
        void firstPageDefaultSize() {
            assertThat(PageRequest.firstPage().size()).isEqualTo(20);
            assertThat(PageRequest.firstPage().page()).isZero();
        }
    }

    @Nested
    @DisplayName("QueryContext")
    class QueryContextTest {

        @Test
        @DisplayName("defaultOf는 DESC + firstPage")
        void defaultOf() {
            QueryContext<TestSortKey> context = QueryContext.defaultOf(TestSortKey.defaultKey());

            assertThat(context.sortKey()).isEqualTo(TestSortKey.CREATED_AT);
            assertThat(context.sortDirection()).isEqualTo(SortDirection.DESC);
            assertThat(context.page()).isZero();
            assertThat(context.size()).isEqualTo(20);
            assertThat(context.offset()).isZero();
        }

        @Test
        @DisplayName("페이징 편의 메서드는 PageRequest에 위임")
        void pagingDelegation() {
            QueryContext<TestSortKey> context =
                    QueryContext.of(TestSortKey.CREATED_AT, SortDirection.ASC, PageRequest.of(3, 5));

            assertThat(context.page()).isEqualTo(3);
            assertThat(context.size()).isEqualTo(5);
            assertThat(context.offset()).isEqualTo(15L);
        }
    }

    @Nested
    @DisplayName("DateRange")
    class DateRangeTest {

        @Test
        @DisplayName("from·to 모두 null이면 empty")
        void emptyWhenBothNull() {
            DateRange range = DateRange.of(null, null);

            assertThat(range.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("한쪽만 있어도 empty 아님")
        void notEmptyWhenEitherPresent() {
            Instant now = Instant.parse("2026-05-25T00:00:00Z");

            assertThat(DateRange.of(now, null).isEmpty()).isFalse();
            assertThat(DateRange.of(null, now).isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("SearchField / SortKey")
    class MarkerInterfacesTest {

        @Test
        @DisplayName("도메인 enum이 fieldName을 노출")
        void fieldName() {
            assertThat(TestSearchField.NAME.fieldName()).isEqualTo("name");
            assertThat(TestSortKey.CREATED_AT.fieldName()).isEqualTo("createdAt");
        }
    }

    @Nested
    @DisplayName("Versioned")
    class VersionedTest {

        @Test
        @DisplayName("refreshVersion으로 version 갱신")
        void refreshVersion() {
            VersionedHolder holder = new VersionedHolder(0L);

            holder.refreshVersion(3L);

            assertThat(holder.version()).isEqualTo(3L);
        }
    }

    private static final class VersionedHolder implements Versioned {

        private long version;

        private VersionedHolder(long version) {
            this.version = version;
        }

        @Override
        public long version() {
            return version;
        }

        @Override
        public void refreshVersion(long version) {
            this.version = version;
        }
    }
}
