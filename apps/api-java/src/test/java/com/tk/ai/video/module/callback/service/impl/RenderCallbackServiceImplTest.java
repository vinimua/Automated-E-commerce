package com.tk.ai.video.module.callback.service.impl;

import com.tk.ai.video.module.callback.dto.RenderCallbackRequest;
import com.tk.ai.video.module.log.entity.RenderLogEntity;
import com.tk.ai.video.module.log.mapper.RenderLogMapper;
import com.tk.ai.video.module.video.entity.VideoEntity;
import com.tk.ai.video.module.video.mapper.VideoMapper;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenderCallbackServiceImplTest {

    @Mock
    private VideoTaskMapper videoTaskMapper;
    @Mock
    private VideoMapper videoMapper;
    @Mock
    private RenderLogMapper renderLogMapper;

    @InjectMocks
    private RenderCallbackServiceImpl service;

    @Test
    void fashionLoopRenderSuccessAdvancesToFinalReviewInsteadOfCompleted() {
        UUID taskId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setProductId(productId);
        task.setUserId(userId);
        task.setStatus("rendering");
        task.setTaskMode("PRODUCT_CREATIVE");
        task.setDuration(20);

        RenderCallbackRequest request = new RenderCallbackRequest();
        request.setTaskId(taskId);
        request.setVideoId(videoId);
        request.setRenderTaskId("render-1");
        request.setManifestVersion("1.0.0");
        request.setStatus("completed");
        request.setVideoUrl("https://cdn.example/video.mp4");
        request.setCoverUrl("https://cdn.example/cover.jpg");
        request.setDuration(20);
        request.setResolution("1080x1920");
        request.setRenderLog(Map.of("template", "fashion_v1"));

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(videoMapper.findLatestByTaskId(taskId)).thenReturn(Optional.empty());

        service.handleCallback(request);

        assertThat(task.getStatus()).isEqualTo("waiting_final_review");
        assertThat(task.getProgress()).isEqualTo(95);
        assertThat(task.getCompletedAt()).isNull();
        assertThat(task.getRenderTaskId()).isEqualTo("render-1");

        ArgumentCaptor<VideoEntity> videoCaptor = ArgumentCaptor.forClass(VideoEntity.class);
        verify(videoMapper).insert(videoCaptor.capture());
        assertThat(videoCaptor.getValue().getId()).isEqualTo(videoId);
        assertThat(videoCaptor.getValue().getStatus()).isEqualTo("completed");

        verify(renderLogMapper).insert(any(RenderLogEntity.class));
        verify(videoTaskMapper).updateById(task);
    }
}
