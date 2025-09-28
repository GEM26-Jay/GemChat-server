package com.zcj.servicechat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zcj.common.entity.ChatMessage;
import com.zcj.common.dto.ChatMessageSyncDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    List<ChatMessage> selectBatchBySessionAndLastId(
            @Param("list") List<ChatMessageSyncDTO> syncList
    );

    // 批量插入方法
    int batchInsert(@Param("list") List<ChatMessage> messageList);

}
