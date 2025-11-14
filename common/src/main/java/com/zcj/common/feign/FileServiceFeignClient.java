package com.zcj.common.feign;

import com.zcj.common.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@Tag(name = "用户头像服务接口-OpenFeign远程调用")
@FeignClient(name = "service-file")
public interface FileServiceFeignClient {
    @PutMapping("/api/avatar/addRef")
    @Operation(summary = "头像文件引用数量+1", description = "更新数据库文件引用状态")
    Result<Void> addRef(@RequestParam(value = "fileName") String fileName,
                        @RequestParam(value = "userId", required = false) Long userId);

    @PutMapping("/api/avatar/deleteRef")
    @Operation(summary = "头像文件引用数量-1", description = "更新数据库文件引用状态")
    Result<Void> deleteRef(@RequestParam(value = "fileName") String fileName);

    @PutMapping("/api/avatar/changeRef")
    @Operation(summary = "更改头像引用", description = "更新数据库文件引用状态")
    Result<Void> changeRef(@RequestParam(value = "delFileName") String delFilaName,
                           @RequestParam(value = "addFileName") String addFileName);
}
