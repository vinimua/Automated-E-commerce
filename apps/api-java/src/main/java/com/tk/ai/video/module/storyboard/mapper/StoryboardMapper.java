package com.tk.ai.video.module.storyboard.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.storyboard.entity.StoryboardEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface StoryboardMapper extends BaseMapper<StoryboardEntity> {
    /** Use LambdaQueryWrapper so autoResultMap applies and JSONB columns deserialize correctly. */
    default Optional<StoryboardEntity> findByTaskId(UUID taskId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<StoryboardEntity>()
                .eq(StoryboardEntity::getTaskId, taskId)));
    }
}
