package com.tk.ai.video.module.storyboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.storyboard.entity.StoryboardShotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface StoryboardShotMapper extends BaseMapper<StoryboardShotEntity> {
    @Select("SELECT * FROM storyboard_shots WHERE storyboard_id = #{storyboardId} ORDER BY shot_no")
    List<StoryboardShotEntity> findByStoryboardId(UUID storyboardId);
}
