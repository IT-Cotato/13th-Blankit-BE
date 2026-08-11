package com.cotato.blankit.domain.timetable.service;

import com.cotato.blankit.domain.timetable.dto.response.TimetableResponse;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EverytimeParseService {

    private static final String API_URL = "https://api.everytime.kr/find/timetable/table/friend";
    private static final String DEFAULT_COLOR = "#7B5EA7";
    private static final int MAX_EVERYTIME_DAY = 6;
    private static final int MAX_TIME_SLOT = 287; // 287 * 5min = 23:55

    public List<TimetableResponse> parse(String url) {
        String identifier = extractIdentifier(url);
        Document doc = fetchXml(identifier);
        return extractTimetableItems(doc);
    }

    String extractIdentifier(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !"everytime.kr".equals(uri.getHost())) {
                throw new CustomException(ErrorCode.INVALID_EVERYTIME_URL);
            }
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/@") || path.length() <= 2) {
                throw new CustomException(ErrorCode.INVALID_EVERYTIME_URL);
            }
            return path.substring(2);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_EVERYTIME_URL);
        }
    }

    private Document fetchXml(String identifier) {
        try {
            String body = Jsoup.connect(API_URL)
                    .method(Connection.Method.POST)
                    .data("identifier", identifier)
                    .data("friendInfo", "true")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36")
                    .header("Origin", "https://everytime.kr")
                    .header("Referer", "https://everytime.kr/")
                    .timeout(10_000)
                    .execute()
                    .body();
            return Jsoup.parse(body, "", Parser.xmlParser());
        } catch (IOException e) {
            throw new CustomException(ErrorCode.EVERYTIME_FETCH_FAILED);
        }
    }

    List<TimetableResponse> extractTimetableItems(Document doc) {
        List<TimetableResponse> result = new ArrayList<>();
        Elements subjects = doc.select("subject");

        for (Element subject : subjects) {
            String title = subject.select("name").attr("value");
            String place = subject.select("place").attr("value");
            String placeValue = place.isBlank() ? null : place;

            for (Element data : subject.select("time > data")) {
                TimetableResponse item = parseTimeElement(title, placeValue, data);
                if (item != null) {
                    result.add(item);
                }
            }
        }

        return result;
    }

    private TimetableResponse parseTimeElement(String title, String place, Element data) {
        try {
            int day = Integer.parseInt(data.attr("day"));
            int startSlots = Integer.parseInt(data.attr("starttime"));
            int endSlots = Integer.parseInt(data.attr("endtime"));

            if (day < 0 || day > MAX_EVERYTIME_DAY) return null;
            if (startSlots < 0 || startSlots > MAX_TIME_SLOT) return null;
            if (endSlots < 0 || endSlots > MAX_TIME_SLOT) return null;
            if (startSlots >= endSlots) return null;

            // 에브리타임: 0=월, 1=화, ..., 5=토, 6=일 → 프로젝트: 0=일, 1=월, ..., 6=토
            int dayOfWeek = (day + 1) % 7;
            LocalTime startTime = LocalTime.of(0, 0).plusMinutes(startSlots * 5L);
            LocalTime endTime = LocalTime.of(0, 0).plusMinutes(endSlots * 5L);

            String timePlace = data.attr("place");
            String finalPlace = timePlace.isBlank() ? place : timePlace;

            return new TimetableResponse(null, dayOfWeek, startTime, endTime, title, finalPlace, DEFAULT_COLOR);
        } catch (Exception e) {
            return null;
        }
    }
}
