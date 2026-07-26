package com.ainsight.user.mapper;

import com.ainsight.user.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 继承 BaseMapper 即获得单表 CRUD 全家桶(insert/selectById/selectOne/selectCount/update/delete...),
 * 无需写任何 SQL。复杂查询后续再加自定义方法 + XML。
 */
public interface UserMapper extends BaseMapper<SysUser> {
}
