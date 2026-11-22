package io.github.jaeya1006.needlesslylargerod.benefit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 공공데이터포털 「대한민국 공공서비스(혜택) 정보」 serviceList 한 건. 필드명이 한글이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GovService(
        @JsonProperty("서비스ID") String id,
        @JsonProperty("서비스명") String name,
        @JsonProperty("서비스목적요약") String summary,
        @JsonProperty("지원대상") String target,
        @JsonProperty("선정기준") String criteria,
        @JsonProperty("지원내용") String content,
        @JsonProperty("상세조회URL") String url,
        @JsonProperty("소관기관명") String agency,
        @JsonProperty("조회수") Long views) {

    /** serviceList 응답 한 페이지. 목록은 data에 들어 있다(odcloud 공통 형식). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(List<GovService> data, Integer totalCount) {
    }
}
