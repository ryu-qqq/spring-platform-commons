package com.ryuqqq.platform.common.vo;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ryuqqq.platform.common.domain.Versioned;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
        @DisplayName("defaultOf는 DESC + firstPage + includeDeleted false")
        void defaultOf() {
            QueryContext<TestSortKey> context = QueryContext.defaultOf(TestSortKey.defaultKey());

            assertThat(context.sortKey()).isEqualTo(TestSortKey.CREATED_AT);
            assertThat(context.sortDirection()).isEqualTo(SortDirection.DESC);
            assertThat(context.page()).isZero();
            assertThat(context.size()).isEqualTo(20);
            assertThat(context.offset()).isZero();
            assertThat(context.includeDeleted()).isFalse();
        }

        @Test
        @DisplayName("includeDeleted 전달 가능")
        void includeDeleted() {
            QueryContext<TestSortKey> context = QueryContext.of(
                    TestSortKey.CREATED_AT, SortDirection.ASC, PageRequest.firstPage(), true);

            assertThat(context.includeDeleted()).isTrue();
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
        @DisplayName("version()으로 낙관적 락 버전 노출 (읽기 전용)")
        void exposesVersion() {
            VersionedHolder holder = new VersionedHolder(3L);

            assertThat(holder.version()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("CursorPageRequest / CursorQueryContext")
    class CursorPagingTest {

        @Test
        @DisplayName("firstPage cursor는 null")
        void firstPageCursorNull() {
            CursorPageRequest<Long> request = CursorPageRequest.firstPage();

            assertThat(request.isFirstPage()).isTrue();
            assertThat(request.size()).isEqualTo(20);
        }

        @Test
        @DisplayName("CursorQueryContext defaultOf는 DESC + firstPage")
        void cursorQueryContextDefault() {
            CursorQueryContext<TestSortKey, Long> context =
                    CursorQueryContext.defaultOf(TestSortKey.defaultKey());

            assertThat(context.sortDirection()).isEqualTo(SortDirection.DESC);
            assertThat(context.isFirstPage()).isTrue();
            assertThat(context.includeDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("PageMeta / SliceMeta")
    class ResponseMetaTest {

        @Test
        @DisplayName("PageMeta totalPages·hasNext")
        void pageMeta() {
            PageMeta meta = PageMeta.of(0, 10, 25);

            assertThat(meta.totalPages()).isEqualTo(3);
            assertThat(meta.hasNext()).isTrue();
            assertThat(meta.hasPrevious()).isFalse();
        }

        @Test
        @DisplayName("SliceMeta nextCursor")
        void sliceMeta() {
            SliceMeta<Long> meta = SliceMeta.of(20, true, 99L);

            assertThat(meta.hasNext()).isTrue();
            assertThat(meta.nextCursor()).isEqualTo(99L);
        }
    }

    @Nested
    @DisplayName("DeletionStatus")
    class DeletionStatusTest {

        @Test
        @DisplayName("active → markDeleted → restore")
        void lifecycle() {
            Instant now = Instant.parse("2026-05-25T12:00:00Z");
            DeletionStatus active = DeletionStatus.active();

            assertThat(active.isActive()).isTrue();

            DeletionStatus deleted = active.markDeleted(now);

            assertThat(deleted.deleted()).isTrue();
            assertThat(deleted.deletedAt()).isEqualTo(now);

            assertThat(deleted.restore().isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("불변식 검증")
    class InvariantValidationTest {

        @Test
        @DisplayName("PageRequest는 음수 page를 거부한다")
        void pageRequestRejectsNegativePage() {
            assertThatIllegalArgumentException().isThrownBy(() -> PageRequest.of(-1, 10));
        }

        @Test
        @DisplayName("PageRequest는 0 이하 size를 거부한다")
        void pageRequestRejectsNonPositiveSize() {
            assertThatIllegalArgumentException().isThrownBy(() -> PageRequest.of(0, 0));
        }

        @Test
        @DisplayName("CursorPageRequest는 0 이하 size를 거부한다")
        void cursorPageRequestRejectsNonPositiveSize() {
            assertThatIllegalArgumentException().isThrownBy(() -> CursorPageRequest.of(null, 0));
        }

        @Test
        @DisplayName("PageMeta는 음수 page·0 이하 size·음수 totalCount를 거부한다")
        void pageMetaRejectsInvalidValues() {
            assertThatIllegalArgumentException().isThrownBy(() -> PageMeta.of(-1, 10, 0));
            assertThatIllegalArgumentException().isThrownBy(() -> PageMeta.of(0, 0, 0));
            assertThatIllegalArgumentException().isThrownBy(() -> PageMeta.of(0, 10, -1));
        }

        @Test
        @DisplayName("DeletionStatus는 deleted=true인데 deletedAt이 null이면 거부한다")
        void deletionStatusRejectsDeletedWithoutTimestamp() {
            assertThatIllegalArgumentException().isThrownBy(() -> new DeletionStatus(true, null));
        }

        @Test
        @DisplayName("DeletionStatus는 active인데 deletedAt이 있으면 거부한다")
        void deletionStatusRejectsActiveWithTimestamp() {
            Instant now = Instant.parse("2026-05-25T12:00:00Z");
            assertThatIllegalArgumentException().isThrownBy(() -> new DeletionStatus(false, now));
        }
    }

    private static final class VersionedHolder implements Versioned {

        private final long version;

        private VersionedHolder(long version) {
            this.version = version;
        }

        @Override
        public long version() {
            return version;
        }
    }
}
