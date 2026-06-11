package com.tk.ai.video.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM users WHERE email = #{email}")
    Optional<UserEntity> findByEmail(String email);

    @Select("SELECT * FROM users WHERE id = #{id}")
    Optional<UserEntity> findById(UUID id);
}
