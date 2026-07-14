package com.linpj.novel.create;

import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@Slf4j
public class Main {
//    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @GetMapping
    public BaseResponse<String> health() {
        log.info("start test");
        System.out.println("test");
        System.out.println(ErrorCode.FORBIDDEN_ERROR.getMessage());
        return ResultUtils.success(null,"health");
    }
}
