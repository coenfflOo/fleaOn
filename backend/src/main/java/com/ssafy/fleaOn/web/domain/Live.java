package com.ssafy.fleaOn.web.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ssafy.fleaOn.web.util.LocalDateTimeDeserializer;
import com.ssafy.fleaOn.web.util.LocalDateTimeSerializer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Entity
@Table(name = "live")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Live {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "live_id", updatable = false)
    private int liveId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "live_date", nullable = false)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime liveDate;

    @Column(name = "thumbnail")
    private String liveThumbnail;

    @Column(name = "trade_place", nullable = false)
    private String tradePlace;

    @Column(name = "is_live", nullable = false)
    private int isLive; // 0: 방송 예정, 1: 방송 중, 2: 방송 종료

    @Column(name = "view_count")
    private int viewCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code", nullable = false)
    private RegionInfo regionInfo;

    @Builder
    public Live(String title, LocalDateTime liveDate, String liveThumbnail, String tradePlace, User seller, RegionInfo regionInfo, int isLive, int viewCount) {
        this.title = title;
        this.liveDate = liveDate != null ? liveDate : LocalDateTime.now(); // 기본값 설정
        this.liveThumbnail = liveThumbnail;
        this.tradePlace = tradePlace;
        this.seller = seller;
        this.regionInfo = regionInfo;
        this.isLive = isLive;
        this.viewCount = viewCount;
    }

    public void update(String title, LocalDateTime liveDate, String liveThumbnail, String tradePlace, RegionInfo regionInfo) {
        this.title = title;
        this.liveDate = liveDate;
        this.liveThumbnail = liveThumbnail;
        this.tradePlace = tradePlace;
        this.regionInfo = regionInfo;
    }

    public void on() {
        this.isLive = 1;
    }

    public void off() {
        this.isLive = 2;
    }

    public void connect(){
        this.viewCount++;
    }

    public void disconnect(){
        this.viewCount--;
    }
}
