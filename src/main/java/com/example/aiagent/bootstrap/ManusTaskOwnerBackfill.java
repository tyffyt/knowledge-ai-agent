package com.example.aiagent.bootstrap;

import com.example.aiagent.model.ManusTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Manus 存量任务属主回填
 * 用户隔离（任务按属主过滤）上线前的历史任务没有 username 字段，回填为 admin 使创建者重启后仍可见；
 * 回填条件为 username 字段不存在，天然幂等，回填过的文档不再命中
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManusTaskOwnerBackfill implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Query query = new Query(Criteria.where("username").exists(false));
            Update update = new Update().set("username", "admin");
            var result = mongoTemplate.updateMulti(query, update, ManusTask.class);
            if (result.getModifiedCount() > 0) {
                log.info("Manus 存量任务属主回填完成: count={}", result.getModifiedCount());
            }
        } catch (Exception e) {
            log.warn("Manus 存量任务属主回填跳过: {}", e.getMessage());
        }
    }
}
