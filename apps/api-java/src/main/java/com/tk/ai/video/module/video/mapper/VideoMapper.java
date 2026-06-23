package com.tk.ai.video.module.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.video.entity.VideoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface VideoMapper extends BaseMapper<VideoEntity> {
    @Select("SELECT * FROM videos WHERE task_id = #{taskId} ORDER BY created_at DESC LIMIT 1")
    Optional<VideoEntity> findLatestByTaskId(UUID taskId);
}
