package com.ainsight.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体,映射 sys_user 表。
 * 列名 created_at 自动映射到 createdAt(MP 默认开启下划线转驼峰)。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希,永不存明文 */
    private String password;

    private String nickname;

    /** USER / ADMIN */
    private String role;

    /** 1=正常 0=禁用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
