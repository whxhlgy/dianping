package com.hmdp;

import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import cn.hutool.core.lang.UUID;
import java.io.FileWriter;
import java.io.IOException;

@SpringBootTest
public class HmDianPingApplicationTests {
    @Test
    public void generateUUID() {
        try {
            FileWriter writer = new FileWriter("/Users/zhongjunjie/Desktop/tokes.txt");
            for (int i = 0; i < 1000; i++) {
                String uuid = UUID.randomUUID().toString(true);
                writer.write(uuid + "\n");
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
