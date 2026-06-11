package com.tk.ai.video.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.auth.entity.RefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {

    @Select("SELECT * FROM refresh_tokens WHERE token_hash = #{tokenHash}")
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}
