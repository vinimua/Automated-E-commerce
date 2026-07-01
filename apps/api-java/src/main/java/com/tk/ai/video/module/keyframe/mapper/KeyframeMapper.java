package com.tk.ai.video.module.keyframe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.keyframe.entity.KeyframeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface KeyframeMapper extends BaseMapper<KeyframeEntity> {

    @Select("SELECT * FROM keyframes WHERE task_id = #{taskId} ORDER BY shot_no, version")
    List<KeyframeEntity> findByTaskId(UUID taskId);

    @Select("SELECT * FROM keyframes WHERE id = #{id} AND task_id = #{taskId}")
    Optional<KeyframeEntity> findByIdAndTaskId(UUID id, UUID taskId);

    @Select("SELECT * FROM keyframes WHERE task_id = #{taskId} AND version = #{version} ORDER BY shot_no")
    List<KeyframeEntity> findByTaskIdAndVersion(UUID taskId, int version);

    @Select("SELECT COUNT(*) FROM keyframes WHERE task_id = #{taskId} AND version = #{version} AND status != 'confirmed'")
    long countUnconfirmedByTaskIdAndVersion(UUID taskId, int version);
}
