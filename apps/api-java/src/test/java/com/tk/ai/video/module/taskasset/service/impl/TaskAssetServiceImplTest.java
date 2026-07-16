package com.tk.ai.video.module.taskasset.service.impl;

import com.tk.ai.video.common.AiServiceClient;
import com.tk.ai.video.common.CreativeContextAssembler;
import com.tk.ai.video.common.BusinessException;
import com.tk.ai.video.common.ResourceForbiddenException;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.taskasset.dto.ConfirmAssetsRequest;
import com.tk.ai.video.module.taskasset.dto.GenerateAssetImageRequest;
import com.tk.ai.video.module.taskasset.dto.RegenerateAssetImageRequest;
import com.tk.ai.video.module.taskasset.entity.TaskAssetEntity;
import com.tk.ai.video.module.taskasset.mapper.TaskAssetMapper;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAssetServiceImplTest {

    @Mock
    private TaskAssetMapper taskAssetMapper;
    @Mock
    private VideoTaskMapper videoTaskMapper;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private CreativeContextAssembler creativeContextAssembler;

    @InjectMocks
    private TaskAssetServiceImpl service;

    @Test
    void deleteAssetAllowsUploadingAndDeletesOwnedAsset() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setStatus("asset_uploading");

        TaskAssetEntity asset = new TaskAssetEntity();
        asset.setId(assetId);
        asset.setTaskId(taskId);
        asset.setUserId(userId);
        asset.setAssetKind("image");
        asset.setAssetRole("product_front");

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(taskAssetMapper.findByIdAndTaskId(assetId, taskId)).thenReturn(Optional.of(asset));

        service.deleteAsset(taskId, assetId, userId);

        verify(taskAssetMapper).deleteById(assetId);
    }

    @Test
    void deleteAssetRejectsDisallowedTaskStatus() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setStatus("keyframe_configuring");

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);

        assertThatThrownBy(() -> service.deleteAsset(taskId, assetId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot delete assets");

        verify(taskAssetMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteAssetRejectsTaskOwnedByAnotherUser() {
        UUID taskId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(ownerId);
        task.setStatus("asset_uploading");

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);

        assertThatThrownBy(() -> service.deleteAsset(taskId, assetId, callerId))
                .isInstanceOf(ResourceForbiddenException.class);

        verify(taskAssetMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteAssetRejectsAssetOutsideTask() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setStatus("waiting_asset_confirmation");

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(taskAssetMapper.findByIdAndTaskId(assetId, taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAsset(taskId, assetId, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(taskAssetMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void confirmAssetsFromUploadingStartsAssetAnalysis() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("asset_uploading");

        TaskAssetEntity asset = new TaskAssetEntity();
        asset.setId(assetId);
        asset.setTaskId(taskId);
        asset.setUserId(userId);
        asset.setProductId(productId);
        asset.setAssetKind("image");
        asset.setAssetRole("product_front");
        asset.setSource("user_upload");
        asset.setUrl("https://example.com/front.png");
        asset.setConfirmed(false);

        ConfirmAssetsRequest request = new ConfirmAssetsRequest();
        request.setAssetIds(List.of(assetId));

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(taskAssetMapper.findByTaskId(taskId)).thenReturn(List.of(asset));

        VideoTaskStatusResponse response = service.confirmAssets(taskId, request, userId);

        assertThat(response.getStatus()).isEqualTo("asset_analyzing");
        assertThat(task.getStatus()).isEqualTo("asset_analyzing");
        assertThat(asset.getConfirmed()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> assetsCaptor =
                (ArgumentCaptor<List<Map<String, Object>>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(aiServiceClient).startAssetAnalysis(
                eq(taskId), eq(productId), eq(userId), any(), assetsCaptor.capture());
        assertThat(assetsCaptor.getValue()).hasSize(1);
        assertThat(assetsCaptor.getValue().get(0))
                .containsEntry("assetId", assetId.toString())
                .containsEntry("assetKind", "image")
                .containsEntry("assetRole", "product_front")
                .containsEntry("url", "https://example.com/front.png");
    }

    @Test
    void confirmAssetsFromUploadingPrefersConfirmedGeneratedResultForAnalysis() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID originalAssetId = UUID.randomUUID();
        UUID generatedAssetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("asset_uploading");

        TaskAssetEntity originalAsset = new TaskAssetEntity();
        originalAsset.setId(originalAssetId);
        originalAsset.setTaskId(taskId);
        originalAsset.setUserId(userId);
        originalAsset.setProductId(productId);
        originalAsset.setAssetKind("image");
        originalAsset.setAssetRole("product_front");
        originalAsset.setSource("user_upload");
        originalAsset.setUrl("https://example.com/original.png");
        originalAsset.setConfirmed(false);

        TaskAssetEntity generatedAsset = new TaskAssetEntity();
        generatedAsset.setId(generatedAssetId);
        generatedAsset.setTaskId(taskId);
        generatedAsset.setUserId(userId);
        generatedAsset.setProductId(productId);
        generatedAsset.setAssetKind("image");
        generatedAsset.setAssetRole("generated_result");
        generatedAsset.setSource("ai_generated");
        generatedAsset.setUrl("https://example.com/generated.png");
        generatedAsset.setConfirmed(false);

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(taskAssetMapper.findByTaskId(taskId)).thenReturn(List.of(originalAsset, generatedAsset));

        service.confirmAssets(taskId, new ConfirmAssetsRequest(), userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> assetsCaptor =
                (ArgumentCaptor<List<Map<String, Object>>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(aiServiceClient).startAssetAnalysis(
                eq(taskId), eq(productId), eq(userId), any(), assetsCaptor.capture());

        assertThat(assetsCaptor.getValue()).hasSize(1);
        assertThat(assetsCaptor.getValue().get(0))
                .containsEntry("assetId", generatedAssetId.toString())
                .containsEntry("assetRole", "generated_result")
                .containsEntry("source", "ai_generated")
                .containsEntry("url", "https://example.com/generated.png");
    }

    @Test
    void generateAssetImageSavesUnconfirmedAiGeneratedAsset() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID sourceAssetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("asset_uploading");

        TaskAssetEntity sourceAsset = new TaskAssetEntity();
        sourceAsset.setId(sourceAssetId);
        sourceAsset.setTaskId(taskId);
        sourceAsset.setUserId(userId);
        sourceAsset.setProductId(productId);
        sourceAsset.setAssetKind("image");
        sourceAsset.setAssetRole("product_front");
        sourceAsset.setSource("user_upload");
        sourceAsset.setUrl("https://example.com/source.png");
        sourceAsset.setConfirmed(false);

        GenerateAssetImageRequest request = new GenerateAssetImageRequest();
        request.setPrompt("Add a butterfly pattern on the back");
        request.setSourceAssetIds(List.of(sourceAssetId));

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(taskAssetMapper.findByTaskId(taskId)).thenReturn(List.of(sourceAsset));
        when(aiServiceClient.generateAssetImage(
                eq(taskId), eq(productId), eq(userId), eq("Add a butterfly pattern on the back"),
                any(), any(), eq("image_variant"), any()))
                .thenReturn(Map.of(
                        "url", "https://example.com/generated.png",
                        "provider", "fake",
                        "model", "fake-v1",
                        "qualityScore", 88
                ));

        service.generateAssetImage(taskId, request, userId);

        ArgumentCaptor<TaskAssetEntity> entityCaptor = ArgumentCaptor.forClass(TaskAssetEntity.class);
        verify(taskAssetMapper).insert(entityCaptor.capture());
        TaskAssetEntity generated = entityCaptor.getValue();
        assertThat(generated.getAssetKind()).isEqualTo("image");
        assertThat(generated.getAssetRole()).isEqualTo("image_variant");
        assertThat(generated.getSource()).isEqualTo("ai_generated");
        assertThat(generated.getUrl()).isEqualTo("https://example.com/generated.png");
        assertThat(generated.getConfirmed()).isFalse();
        assertThat(generated.getMetadata()).containsEntry("provider", "fake");
        assertThat(generated.getMetadata()).containsEntry("generationRound", 1);
    }

    @Test
    void regenerateAssetImageCreatesNewCandidateWithFeedbackContext() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID sourceAssetId = UUID.randomUUID();
        UUID parentAssetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("asset_uploading");

        TaskAssetEntity sourceAsset = new TaskAssetEntity();
        sourceAsset.setId(sourceAssetId);
        sourceAsset.setTaskId(taskId);
        sourceAsset.setUserId(userId);
        sourceAsset.setProductId(productId);
        sourceAsset.setAssetKind("image");
        sourceAsset.setAssetRole("product_front");
        sourceAsset.setSource("user_upload");
        sourceAsset.setUrl("https://example.com/source.png");

        TaskAssetEntity parentAsset = new TaskAssetEntity();
        parentAsset.setId(parentAssetId);
        parentAsset.setTaskId(taskId);
        parentAsset.setUserId(userId);
        parentAsset.setProductId(productId);
        parentAsset.setAssetKind("image");
        parentAsset.setAssetRole("image_variant");
        parentAsset.setSource("ai_generated");
        parentAsset.setUrl("https://example.com/previous.png");
        parentAsset.setMetadata(Map.of(
                "prompt", "Add a butterfly pattern on the back",
                "sourceAssetIds", List.of(sourceAssetId.toString()),
                "generationRound", 1
        ));

        RegenerateAssetImageRequest request = new RegenerateAssetImageRequest();
        request.setFeedback("Make the butterfly smaller and move it to the left chest");

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(taskAssetMapper.findByIdAndTaskId(parentAssetId, taskId)).thenReturn(Optional.of(parentAsset));
        when(taskAssetMapper.findByTaskId(taskId)).thenReturn(List.of(sourceAsset, parentAsset));
        when(aiServiceClient.generateAssetImage(
                eq(taskId), eq(productId), eq(userId), any(),
                any(), any(), eq("image_variant"), any()))
                .thenReturn(Map.of(
                        "url", "https://example.com/regenerated.png",
                        "provider", "fake",
                        "model", "fake-v1",
                        "qualityScore", 91
                ));

        service.regenerateAssetImage(taskId, parentAssetId, request, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> sourceAssetsCaptor =
                (ArgumentCaptor<List<Map<String, Object>>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> generationContextCaptor =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(aiServiceClient).generateAssetImage(
                eq(taskId), eq(productId), eq(userId), any(),
                any(), sourceAssetsCaptor.capture(), eq("image_variant"), generationContextCaptor.capture());
        assertThat(sourceAssetsCaptor.getValue()).hasSize(1);
        assertThat(sourceAssetsCaptor.getValue().get(0))
                .containsEntry("assetId", parentAssetId.toString())
                .containsEntry("url", "https://example.com/previous.png");
        assertThat(generationContextCaptor.getValue())
                .containsEntry("feedback", "Make the butterfly smaller and move it to the left chest")
                .containsKey("previousResult")
                .containsEntry("previousPrompt", "Add a butterfly pattern on the back");

        ArgumentCaptor<TaskAssetEntity> entityCaptor = ArgumentCaptor.forClass(TaskAssetEntity.class);
        verify(taskAssetMapper).insert(entityCaptor.capture());
        TaskAssetEntity generated = entityCaptor.getValue();
        assertThat(generated.getAssetKind()).isEqualTo("image");
        assertThat(generated.getAssetRole()).isEqualTo("image_variant");
        assertThat(generated.getSource()).isEqualTo("ai_generated");
        assertThat(generated.getUrl()).isEqualTo("https://example.com/regenerated.png");
        assertThat(generated.getConfirmed()).isFalse();
        assertThat(generated.getMetadata())
                .containsEntry("parentAssetId", parentAssetId.toString())
                .containsEntry("feedback", "Make the butterfly smaller and move it to the left chest")
                .containsEntry("previousResultUrl", "https://example.com/previous.png")
                .containsEntry("generationRound", 2);
    }

    @Test
    void confirmAssetsFromUploadingRejectsWhenNoProductImageCanBeAnalyzed() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("asset_uploading");

        TaskAssetEntity asset = new TaskAssetEntity();
        asset.setId(assetId);
        asset.setTaskId(taskId);
        asset.setUserId(userId);
        asset.setProductId(productId);
        asset.setAssetKind("video");
        asset.setAssetRole("reference_video");
        asset.setSource("user_upload");
        asset.setUrl("https://example.com/reference.mp4");
        asset.setConfirmed(false);

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(taskAssetMapper.findByTaskId(taskId)).thenReturn(List.of(asset));

        assertThatThrownBy(() -> service.confirmAssets(taskId, new ConfirmAssetsRequest(), userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product image");

        verify(aiServiceClient, never()).startAssetAnalysis(any(), any(), any(), any(), any());
    }
}
