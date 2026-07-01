package com.tk.ai.video.module.repairevent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.repairevent.entity.RepairEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface RepairEventMapper extends BaseMapper<RepairEventEntity> {

    @Select("SELECT * FROM repair_events WHERE task_id = #{taskId} ORDER BY created_at DESC")
    List<RepairEventEntity> findByTaskId(UUID taskId);
}
