package com.tk.ai.video.module.videoclip.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.videoclip.entity.VideoClipEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface VideoClipMapper extends BaseMapper<VideoClipEntity> {

    /** Use MyBatis-Plus wrapper so autoResultMap applies and JSONB metadata deserializes correctly. */
    default List<VideoClipEntity> findByTaskId(UUID taskId) {
        return selectList(new LambdaQueryWrapper<VideoClipEntity>()
                .eq(VideoClipEntity::getTaskId, taskId)
                .orderByAsc(VideoClipEntity::getShotNo, VideoClipEntity::getVersion));
    }

    default Optional<VideoClipEntity> findByIdAndTaskId(UUID id, UUID taskId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<VideoClipEntity>()
                .eq(VideoClipEntity::getId, id)
                .eq(VideoClipEntity::getTaskId, taskId)));
    }

    default Optional<VideoClipEntity> findByTaskIdAndShotNoAndVersion(UUID taskId, int shotNo, int version) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<VideoClipEntity>()
                .eq(VideoClipEntity::getTaskId, taskId)
                .eq(VideoClipEntity::getShotNo, shotNo)
                .eq(VideoClipEntity::getVersion, version)
                .last("LIMIT 1")));
    }

    default List<VideoClipEntity> findByTaskIdAndVersion(UUID taskId, int version) {
        return selectList(new LambdaQueryWrapper<VideoClipEntity>()
                .eq(VideoClipEntity::getTaskId, taskId)
                .eq(VideoClipEntity::getVersion, version)
                .orderByAsc(VideoClipEntity::getShotNo));
    }

    default long countUnconfirmedByTaskIdAndVersion(UUID taskId, int version) {
        return selectCount(new LambdaQueryWrapper<VideoClipEntity>()
                .eq(VideoClipEntity::getTaskId, taskId)
                .eq(VideoClipEntity::getVersion, version)
                .ne(VideoClipEntity::getStatus, "confirmed"));
    }
}
