package com.tk.ai.video.module.taskasset.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.taskasset.entity.TaskAssetEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface TaskAssetMapper extends BaseMapper<TaskAssetEntity> {

    /** Use MyBatis-Plus wrapper so autoResultMap applies and JSONB metadata deserializes correctly. */
    default List<TaskAssetEntity> findByTaskId(UUID taskId) {
        return selectList(new LambdaQueryWrapper<TaskAssetEntity>()
                .eq(TaskAssetEntity::getTaskId, taskId)
                .orderByAsc(TaskAssetEntity::getCreatedAt));
    }

    default Optional<TaskAssetEntity> findByIdAndTaskId(UUID id, UUID taskId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<TaskAssetEntity>()
                .eq(TaskAssetEntity::getId, id)
                .eq(TaskAssetEntity::getTaskId, taskId)));
    }
}
