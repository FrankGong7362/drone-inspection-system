package com.tongji.uav;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 无人机巡检任务管理与路径规划系统 —— 启动入口。
 */
@SpringBootApplication
@MapperScan("com.tongji.uav.mapper")
public class UavApplication {

    public static void main(String[] args) {
        SpringApplication.run(UavApplication.class, args);
    }
}
