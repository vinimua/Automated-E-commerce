package com.tk.ai.video.module.taskasset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.taskasset.entity.TaskAssetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface TaskAssetMapper extends BaseMapper<TaskAssetEntity> {

    @Select("SELECT * FROM task_assets WHERE task_id = #{taskId} ORDER BY created_at")
    List<TaskAssetEntity> findByTaskId(UUID taskId);

    @Select("SELECT * FROM task_assets WHERE id = #{id} AND task_id = #{taskId}")
    Optional<TaskAssetEntity> findByIdAndTaskId(UUID id, UUID taskId);
}
