package com.tk.ai.video.module.creativestate.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.creativestate.entity.CreativeStateEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface CreativeStateMapper extends BaseMapper<CreativeStateEntity> {

    /** Use LambdaQueryWrapper so autoResultMap applies and JSONB columns deserialize correctly. */
    default Optional<CreativeStateEntity> findByTaskId(UUID taskId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<CreativeStateEntity>()
                .eq(CreativeStateEntity::getTaskId, taskId)));
    }
}
