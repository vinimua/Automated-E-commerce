package com.tk.ai.video.module.quota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.quota.entity.UserQuotaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface UserQuotaMapper extends BaseMapper<UserQuotaEntity> {

    @Select("SELECT * FROM user_quotas WHERE user_id = #{userId} FOR UPDATE")
    Optional<UserQuotaEntity> selectByUserIdForUpdate(UUID userId);

    @Select("SELECT * FROM user_quotas WHERE user_id = #{userId}")
    Optional<UserQuotaEntity> findByUserId(UUID userId);
}
