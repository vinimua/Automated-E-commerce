package com.tk.ai.video.module.storyboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.storyboard.entity.VideoPlanEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface VideoPlanMapper extends BaseMapper<VideoPlanEntity> {
    @Select("SELECT * FROM video_plans WHERE task_id = #{taskId}")
    List<VideoPlanEntity> findByTaskId(UUID taskId);
}
