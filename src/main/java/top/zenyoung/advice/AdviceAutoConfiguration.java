package top.zenyoung.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import top.zenyoung.advice.config.AdviceProperties;
import top.zenyoung.advice.filter.CachedBodyFilter;
import top.zenyoung.advice.service.AdviceService;
import top.zenyoung.advice.service.impl.AdviceServiceImpl;

import javax.annotation.Nonnull;

/**
 * API拦截-自动注册
 */
@Configuration
@Import(AdviceAutoConfiguration.class)
public class AdviceAutoConfiguration {

    @Bean
    public CachedBodyFilter cachedBodyFilter() {
        return new CachedBodyFilter();
    }

    @Bean
    public AdviceService adviceService(@Nonnull final JdbcOperations jdbcTemplate, @Nonnull final AdviceProperties adviceProperties) {
        return new AdviceServiceImpl(jdbcTemplate, adviceProperties);
    }

    @Bean
    public ResponseAdvice responseAdvice(@Nonnull final AdviceProperties properties, @Nonnull final AdviceService service, @Nonnull final ObjectMapper objectMapper) {
        return new ResponseAdvice(properties, service, objectMapper);
    }
}
