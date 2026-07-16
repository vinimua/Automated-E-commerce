package com.tk.ai.video.module.videotask.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * Aggregate request for fashion creative task creation.
 * Creates product + video task + initial assets + creative state in one transaction.
 */
@Data
public class CreateFashionTaskRequest {

    // ── Product fields ──

    @NotBlank
    private String name;
    private String description;
    private String productLink;
    private List<@Pattern(regexp = "https?://.+") String> imageUrls;

    @NotBlank
    @Pattern(regexp = "US|UK|JP")
    private String targetMarket;

    @NotBlank
    @Pattern(regexp = "en|zh|ja")
    private String language;

    // ── Video task fields ──

    @NotNull
    @Min(1)
    private Integer duration;

    @Pattern(regexp = "pain_point_solution|before_after|review|product_showcase|ugc_style|tutorial")
    private String videoType;
    private Boolean needSubtitles = true;

    @Pattern(regexp = "PRODUCT_CREATIVE|REFERENCE_STORYBOARD|USER_SCRIPT|CUSTOM_STORYBOARD")
    private String taskMode;
    private String productCategory;
    private Integer shotCount;

    // ── Asset / Creative state fields ──

    /** Required when taskMode is REFERENCE_STORYBOARD. */
    @Pattern(regexp = "https?://.+")
    private String referenceVideoUrl;

    /** Required when taskMode is USER_SCRIPT. */
    private String scriptText;

    /** Required when taskMode is CUSTOM_STORYBOARD. */
    private String storyboardText;

    /** Free-form creative direction preserved verbatim for downstream AI calls. */
    private String creativePrompt;
}
