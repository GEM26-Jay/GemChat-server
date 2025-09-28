package com.zcj.servicenetty.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zcj.common.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    // 查询Session的消息中，最大的消息ID
    Long selectMaxMessageIdInSession(Long sessionId);

    // 查询Session中的所有成员ID
    List<Long> selectMemberIdsInSession(Long sessionId);

    void batchInsert(List<ChatMessage> toSave);
}
