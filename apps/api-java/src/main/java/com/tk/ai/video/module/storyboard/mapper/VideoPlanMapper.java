package com.tk.ai.video.module.storyboard.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.storyboard.entity.VideoPlanEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface VideoPlanMapper extends BaseMapper<VideoPlanEntity> {
    /** Use LambdaQueryWrapper so autoResultMap applies and JSONB columns deserialize correctly. */
    default List<VideoPlanEntity> findByTaskId(UUID taskId) {
        return selectList(new LambdaQueryWrapper<VideoPlanEntity>()
                .eq(VideoPlanEntity::getTaskId, taskId));
    }

    default Optional<VideoPlanEntity> findOwnedPlan(UUID planId, UUID taskId, UUID userId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<VideoPlanEntity>()
                .eq(VideoPlanEntity::getId, planId)
                .eq(VideoPlanEntity::getTaskId, taskId)
                .eq(VideoPlanEntity::getUserId, userId)));
    }
}
