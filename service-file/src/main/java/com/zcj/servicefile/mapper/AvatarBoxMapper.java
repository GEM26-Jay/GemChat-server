package com.zcj.servicefile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zcj.common.entity.AvatarBox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AvatarBoxMapper extends BaseMapper<AvatarBox> {
    void decrementReferCount(String fileName);

    void incrementReferCount(String fileName);
}
