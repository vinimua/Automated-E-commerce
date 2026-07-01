package com.tk.ai.video.module.creativestate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.creativestate.entity.CreativeStateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface CreativeStateMapper extends BaseMapper<CreativeStateEntity> {

    @Select("SELECT * FROM creative_states WHERE task_id = #{taskId}")
    Optional<CreativeStateEntity> findByTaskId(UUID taskId);
}
