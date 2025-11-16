package com.zcj.common.dto;

import com.zcj.common.entity.ChatMessage;
import com.zcj.common.entity.Protocol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendRequestDTO {
    private List<Long> ids;
    private ChatMessage message;
}
