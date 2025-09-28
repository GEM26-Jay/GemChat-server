package com.zcj.servicechat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zcj.common.entity.ChatGroup;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GroupMapper extends BaseMapper<ChatGroup> {
}
