package com.tk.ai.video.module.quota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.quota.entity.QuotaRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface QuotaRecordMapper extends BaseMapper<QuotaRecordEntity> {

    @Select("SELECT * FROM quota_records WHERE idempotency_key = #{idempotencyKey}")
    Optional<QuotaRecordEntity> findByIdempotencyKey(String idempotencyKey);
}
