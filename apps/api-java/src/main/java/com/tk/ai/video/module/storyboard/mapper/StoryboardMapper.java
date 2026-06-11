package com.tk.ai.video.module.storyboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.storyboard.entity.StoryboardEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface StoryboardMapper extends BaseMapper<StoryboardEntity> {
    @Select("SELECT * FROM storyboards WHERE task_id = #{taskId}")
    Optional<StoryboardEntity> findByTaskId(UUID taskId);
}
