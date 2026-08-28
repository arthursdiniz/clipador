package com.clipador.video.domain;

import com.clipador.identity.domain.UserAccount;
import com.clipador.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "video")
public class Video extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserAccount owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private VideoSourceType sourceType;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "original_filename", length = 512)
    private String originalFilename;

    @Column(length = 512)
    private String title;

    @Column(length = 255)
    private String channel;

    @Column(name = "duration_seconds", precision = 12, scale = 3)
    private BigDecimal durationSeconds;

    private Integer width;
    private Integer height;

    @Column(precision = 8, scale = 3)
    private BigDecimal fps;

    @Column(name = "video_codec", length = 64)
    private String videoCodec;

    @Column(name = "audio_codec", length = 64)
    private String audioCodec;

    @Column(name = "detected_language", length = 20)
    private String detectedLanguage;

    @Column(name = "storage_path", length = 1024)
    private String storagePath;

    @Column(name = "thumbnail_url", length = 2048)
    private String thumbnailUrl;

    @Column(columnDefinition = "text")
    private String description;

    protected Video() {}

    private Video(VideoSourceType sourceType, String sourceUrl, String originalFilename, String title) {
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.originalFilename = originalFilename;
        this.title = title;
    }

    public static Video youtube(String normalizedUrl, String title) {
        return new Video(VideoSourceType.YOUTUBE, normalizedUrl, null, title);
    }

    public static Video upload(String originalFilename, String title) {
        return new Video(VideoSourceType.UPLOAD, null, originalFilename, title);
    }

    public void completeIngestion(String storagePath, BigDecimal durationSeconds, int width, int height,
                                  BigDecimal fps, String videoCodec, String audioCodec, String discoveredTitle,
                                  String channel, String thumbnailUrl, String description, String language) {
        this.storagePath = storagePath;
        this.durationSeconds = durationSeconds;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.videoCodec = truncate(videoCodec, 64);
        this.audioCodec = truncate(audioCodec, 64);
        if (this.title == null || this.title.isBlank()) this.title = truncate(discoveredTitle, 512);
        this.channel = truncate(channel, 255);
        this.thumbnailUrl = truncate(thumbnailUrl, 2048);
        this.description = description;
        this.detectedLanguage = truncate(language, 20);
    }

    private String truncate(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
    }

    public UserAccount getOwner() { return owner; }
    public VideoSourceType getSourceType() { return sourceType; }
    public String getSourceUrl() { return sourceUrl; }
    public String getOriginalFilename() { return originalFilename; }
    public String getTitle() { return title; }
    public String getChannel() { return channel; }
    public BigDecimal getDurationSeconds() { return durationSeconds; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public BigDecimal getFps() { return fps; }
    public String getVideoCodec() { return videoCodec; }
    public String getAudioCodec() { return audioCodec; }
    public String getDetectedLanguage() { return detectedLanguage; }
    public String getStoragePath() { return storagePath; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getDescription() { return description; }
}
