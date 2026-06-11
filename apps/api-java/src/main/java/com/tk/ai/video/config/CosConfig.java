package com.tk.ai.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "cos")
public class CosConfig {

    private String secretId;
    private String secretKey;
    private String region;
    private String bucket;
    private String cdnDomain;
    private int presignedUrlExpiration = 10;
    private int maxFileSizeMb = 10;
    private String allowedFolderPrefix = "tk-ai-video/";
    private List<String> allowedDomains;
}
