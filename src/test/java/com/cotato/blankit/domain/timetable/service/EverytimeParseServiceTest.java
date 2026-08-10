package com.cotato.blankit.domain.timetable.service;

import com.cotato.blankit.domain.timetable.dto.response.TimetableResponse;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EverytimeParseServiceTest {

    private EverytimeParseService service;

    @BeforeEach
    void setUp() {
        service = new EverytimeParseService();
    }

    // ── extractIdentifier ──────────────────────────────────────────────

    @Test
    @DisplayName("URL에서 identifier를 올바르게 추출한다")
    void extractIdentifier_success() {
        String identifier = service.extractIdentifier("https://everytime.kr/@abc123XYZ");
        assertThat(identifier).isEqualTo("abc123XYZ");
    }

    @Test
    @DisplayName("@ 기호가 없는 URL은 INVALID_EVERYTIME_URL 예외를 던진다")
    void extractIdentifier_noAt_throwsException() {
        assertThatThrownBy(() -> service.extractIdentifier("https://everytime.kr/abc123"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_EVERYTIME_URL);
    }

    @Test
    @DisplayName("@ 이후 값이 없는 URL은 INVALID_EVERYTIME_URL 예외를 던진다")
    void extractIdentifier_emptyAfterAt_throwsException() {
        assertThatThrownBy(() -> service.extractIdentifier("https://everytime.kr/@"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_EVERYTIME_URL);
    }

    @Test
    @DisplayName("query string이 포함된 URL에서 query를 제외한 identifier만 추출한다")
    void extractIdentifier_withQueryString_extractsOnlyIdentifier() {
        assertThat(service.extractIdentifier("https://everytime.kr/@abc123?param=value"))
                .isEqualTo("abc123");
    }

    @Test
    @DisplayName("fragment가 포함된 URL에서 fragment를 제외한 identifier만 추출한다")
    void extractIdentifier_withFragment_extractsOnlyIdentifier() {
        assertThat(service.extractIdentifier("https://everytime.kr/@abc123#section"))
                .isEqualTo("abc123");
    }

    @Test
    @DisplayName("@ 기호가 두 개인 URL에서 첫 번째 identifier만 추출한다")
    void extractIdentifier_withDoubleAt_extractsFirstIdentifier() {
        assertThat(service.extractIdentifier("https://everytime.kr/@abc123@extra"))
                .isEqualTo("abc123@extra");
    }

    @Test
    @DisplayName("추가 path segment가 있는 URL에서는 path 전체가 추출된다")
    void extractIdentifier_withExtraPath_extractsFullPath() {
        assertThat(service.extractIdentifier("https://everytime.kr/@abc123/extra"))
                .isEqualTo("abc123/extra");
    }

    // ── extractTimetableItems ──────────────────────────────────────────

    @Test
    @DisplayName("정상 XML을 파싱하면 시간표 항목이 반환된다")
    void extractTimetableItems_success() {
        Document doc = parseXml("""
                <response>
                  <table>
                    <subject id="1">
                      <name value="알고리즘"/>
                      <time value="월 1 2 3">
                        <data day="0" starttime="108" endtime="142" place="공학관 101호"/>
                      </time>
                      <place value="공학관 101호"/>
                    </subject>
                  </table>
                </response>
                """);

        List<TimetableResponse> result = service.extractTimetableItems(doc);

        assertThat(result).hasSize(1);
        TimetableResponse item = result.get(0);
        assertThat(item.timetableId()).isNull();
        assertThat(item.dayOfWeek()).isEqualTo(1);
        assertThat(item.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(item.endTime()).isEqualTo(LocalTime.of(11, 50));
        assertThat(item.title()).isEqualTo("알고리즘");
        assertThat(item.place()).isEqualTo("공학관 101호");
        assertThat(item.color()).isEqualTo("#7B5EA7");
    }

    @Test
    @DisplayName("data 요소가 없는 과목(시간 미입력)은 건너뛴다")
    void extractTimetableItems_noDataElement_skipped() {
        Document doc = parseXml("""
                <response>
                  <table>
                    <subject id="1">
                      <name value="모바일프로그래밍"/>
                      <time value="수"/>
                      <place value="8308"/>
                    </subject>
                  </table>
                </response>
                """);

        assertThat(service.extractTimetableItems(doc)).isEmpty();
    }

    @Test
    @DisplayName("요일 매핑 - 에브리타임 day=4(금) → dayOfWeek=5")
    void extractTimetableItems_dayMapping_friday() {
        Document doc = parseXml("""
                <response>
                  <table>
                    <subject id="1">
                      <name value="자료구조"/>
                      <time value="금 1 2 3">
                        <data day="4" starttime="108" endtime="142" place=""/>
                      </time>
                      <place value=""/>
                    </subject>
                  </table>
                </response>
                """);

        assertThat(service.extractTimetableItems(doc).get(0).dayOfWeek()).isEqualTo(5);
    }

    @Test
    @DisplayName("요일 매핑 - 에브리타임 day=6(일) → dayOfWeek=0")
    void extractTimetableItems_dayMapping_sunday() {
        Document doc = parseXml("""
                <response>
                  <table>
                    <subject id="1">
                      <name value="교양"/>
                      <time value="일 1 2">
                        <data day="6" starttime="108" endtime="130" place=""/>
                      </time>
                      <place value=""/>
                    </subject>
                  </table>
                </response>
                """);

        assertThat(service.extractTimetableItems(doc).get(0).dayOfWeek()).isEqualTo(0);
    }

    @Test
    @DisplayName("data의 place가 비어있으면 subject의 place를 사용한다")
    void extractTimetableItems_placeFallbackToSubject() {
        Document doc = parseXml("""
                <response>
                  <table>
                    <subject id="1">
                      <name value="운영체제"/>
                      <time value="화 1 2 3">
                        <data day="1" starttime="108" endtime="142" place=""/>
                      </time>
                      <place value="공학관 201호"/>
                    </subject>
                  </table>
                </response>
                """);

        assertThat(service.extractTimetableItems(doc).get(0).place()).isEqualTo("공학관 201호");
    }

    @Test
    @DisplayName("data와 subject 모두 place가 비어있으면 null을 반환한다")
    void extractTimetableItems_bothPlaceEmpty_returnsNull() {
        Document doc = parseXml("""
                <response>
                  <table>
                    <subject id="1">
                      <name value="교양"/>
                      <time value="수 1 2">
                        <data day="2" starttime="108" endtime="130" place=""/>
                      </time>
                      <place value=""/>
                    </subject>
                  </table>
                </response>
                """);

        assertThat(service.extractTimetableItems(doc).get(0).place()).isNull();
    }

    @Test
    @DisplayName("한 과목에 여러 data 요소가 있으면 모두 개별 항목으로 반환한다")
    void extractTimetableItems_multipleDataElements() {
        Document doc = parseXml("""
                <response>
                  <table>
                    <subject id="1">
                      <name value="전공필수"/>
                      <time value="월 1 2 수 1 2">
                        <data day="0" starttime="108" endtime="130" place="101호"/>
                        <data day="2" starttime="108" endtime="130" place="101호"/>
                      </time>
                      <place value="101호"/>
                    </subject>
                  </table>
                </response>
                """);

        List<TimetableResponse> result = service.extractTimetableItems(doc);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).dayOfWeek()).isEqualTo(1); // 월
        assertThat(result.get(1).dayOfWeek()).isEqualTo(3); // 수
    }

    @Test
    @DisplayName("subject가 없는 빈 XML은 빈 리스트를 반환한다")
    void extractTimetableItems_emptyTable_returnsEmptyList() {
        Document doc = parseXml("""
                <response>
                  <table/>
                </response>
                """);

        assertThat(service.extractTimetableItems(doc)).isEmpty();
    }

    private Document parseXml(String xml) {
        return Jsoup.parse(xml, "", Parser.xmlParser());
    }
}
