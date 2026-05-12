package top.zenyoung.advice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.sql.init.OnDatabaseInitializationCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.jdbc.init.PlatformPlaceholderDatabaseDriverResolver;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.util.StringUtils;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.util.List;

/**
 * 拦截配置
 */
// @AutoConfiguration(after = {HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class})
@ConditionalOnClass({DataSource.class, DatabasePopulator.class})
@EnableConfigurationProperties(AdviceProperties.class)
@ConditionalOnBean({DataSource.class})
public class AdviceConfiguration {

    @Configuration(proxyBeanMethods = false)
    @Conditional(OnAdviceDatasourceInitializationCondition.class)
    static class DataSourceInitializerConfiguration {

        @Bean
        @ConditionalOnMissingBean
        AdviceDataSourceScriptDatabaseInitializer batchDataSourceInitializer(@Nonnull final DataSource dataSource, @Nonnull final AdviceProperties properties) {
            return new AdviceDataSourceScriptDatabaseInitializer(dataSource, properties.getJdbc());
        }
    }

    static class OnAdviceDatasourceInitializationCondition extends OnDatabaseInitializationCondition {

        OnAdviceDatasourceInitializationCondition() {
            super("apiInterceptAdvice", "api-intercept-advice.jdbc.initialize-schema", "api-intercept-advice.initialize-schema");
        }

    }

    static class AdviceDataSourceScriptDatabaseInitializer extends DataSourceScriptDatabaseInitializer {

        public AdviceDataSourceScriptDatabaseInitializer(@Nonnull final DataSource dataSource, @Nonnull final AdviceProperties.Jdbc properties) {
            super(dataSource, getSettings(dataSource, properties));
        }

        public static DatabaseInitializationSettings getSettings(@Nonnull final DataSource dataSource, @Nonnull final AdviceProperties.Jdbc properties) {
            final DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
            settings.setSchemaLocations(resolveSchemaLocations(dataSource, properties));
            settings.setMode(properties.getInitializeSchema());
            settings.setContinueOnError(true);
            return settings;
        }

        private static List<String> resolveSchemaLocations(@Nonnull final DataSource dataSource, @Nonnull final AdviceProperties.Jdbc properties) {
            final PlatformPlaceholderDatabaseDriverResolver platformResolver = new PlatformPlaceholderDatabaseDriverResolver();
            if (StringUtils.hasText(properties.getPlatform())) {
                return platformResolver.resolveAll(properties.getPlatform(), properties.getSchema());
            }
            return platformResolver.resolveAll(dataSource, properties.getSchema());
        }
    }
}
