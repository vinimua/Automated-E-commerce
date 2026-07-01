package com.tk.ai.video.module.videoclip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.videoclip.entity.VideoClipEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface VideoClipMapper extends BaseMapper<VideoClipEntity> {

    @Select("SELECT * FROM video_clips WHERE task_id = #{taskId} ORDER BY shot_no, version")
    List<VideoClipEntity> findByTaskId(UUID taskId);

    @Select("SELECT * FROM video_clips WHERE id = #{id} AND task_id = #{taskId}")
    Optional<VideoClipEntity> findByIdAndTaskId(UUID id, UUID taskId);

    @Select("SELECT * FROM video_clips WHERE task_id = #{taskId} AND version = #{version} ORDER BY shot_no")
    List<VideoClipEntity> findByTaskIdAndVersion(UUID taskId, int version);

    @Select("SELECT COUNT(*) FROM video_clips WHERE task_id = #{taskId} AND version = #{version} AND status != 'confirmed'")
    long countUnconfirmedByTaskIdAndVersion(UUID taskId, int version);
}
