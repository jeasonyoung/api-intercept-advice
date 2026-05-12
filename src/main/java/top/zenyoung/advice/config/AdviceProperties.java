package top.zenyoung.advice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.transaction.annotation.Isolation;

/**
 * 配置
 */
@Data
@ConfigurationProperties("api-intercept-advice")
public class AdviceProperties {
    /**
     * 是否启用
     */
    private boolean enabled = false;
    /**
     * JDBC初始化
     */
    private final Jdbc jdbc = new Jdbc();

    @Data
    public static class Jdbc {
        private static final String DEFAULT_SCHEMA_LOCATION = "classpath:top/zenyoung/advice/schema-@@platform@@.sql";

        /**
         * Transaction isolation level to use when creating job meta-data for new jobs.
         */
        private Isolation isolationLevelForCreate;
        /**
         * Path to the SQL file to use to initialize the database schema.
         */
        private String schema = DEFAULT_SCHEMA_LOCATION;
        /**
         * 数据库平台，默认跟随
         */
        private String platform;

        /**
         * 数据表前缀
         */
        private String tablePrefix;
        /**
         * 数据库初始化模式
         */
        private DatabaseInitializationMode initializeSchema = DatabaseInitializationMode.EMBEDDED;
    }
}
