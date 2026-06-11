package com.tk.ai.video.module.videotask.enums;

import java.util.Set;

public enum VideoType {
    PAIN_POINT_SOLUTION("pain_point_solution"),
    BEFORE_AFTER("before_after"),
    REVIEW("review"),
    PRODUCT_SHOWCASE("product_showcase"),
    UGC_STYLE("ugc_style"),
    TUTORIAL("tutorial");

    private final String value;

    VideoType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** V1 videoType freeze: only these types are allowed for regular users. */
    public static final Set<String> V1_ALLOWED = Set.of(
            "pain_point_solution", "before_after", "review"
    );
}
