package com.stock.monitor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.stock.monitor.mapper")
@EnableScheduling
public class StockMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockMonitorApplication.class, args);
        System.out.println("============================================");
        System.out.println("  A股实时行情信号跟踪系统启动成功!");
        System.out.println("  访问地址: http://localhost:8080");
        System.out.println("  API基础路径: http://localhost:8080/api");
        System.out.println("============================================");
    }
}
