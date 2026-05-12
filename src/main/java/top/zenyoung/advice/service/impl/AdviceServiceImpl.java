package top.zenyoung.advice.service.impl;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.CollectionUtils;
import top.zenyoung.advice.config.AdviceProperties;
import top.zenyoung.advice.model.AdviceConfigModel;
import top.zenyoung.advice.service.AdviceService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * url拦截-服务接口实现
 */
@Slf4j
@RequiredArgsConstructor
public class AdviceServiceImpl implements AdviceService {
    private final Cache<String, String> cache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).build();
    private final Map<String, Object> locks = Maps.newHashMap();

    private static final String tableName = "api_advice_config";

    private final JdbcOperations jdbcTemplate;
    private final AdviceProperties adviceProperties;

    private String getTableName() {
        return adviceProperties.getJdbc().getTablePrefix() + tableName;
    }

    private Map<String, Collection<String>> parseArgs(@Nullable final String val) {
        final Map<String, Collection<String>> retMap = Maps.newHashMap();
        if (!Strings.isNullOrEmpty(val)) {
            final String sep = "=";
            final Splitter splitter = Splitter.on(sep).omitEmptyStrings().trimResults();
            Splitter.on("&").omitEmptyStrings().trimResults().splitToStream(val).forEach(v -> {
                if (!Strings.isNullOrEmpty(v) && v.contains(sep)) {
                    final Iterator<String> entryFields = splitter.split(v).iterator();
                    // key
                    String key = null;
                    if (entryFields.hasNext()) {
                        key = entryFields.next();
                    }
                    // Val
                    if (entryFields.hasNext()) {
                        final String data = entryFields.next();
                        if (!Strings.isNullOrEmpty(data)) {
                            final Collection<String> items = retMap.computeIfAbsent(key, k -> Lists.newArrayList());
                            if (!items.contains(data)) {
                                items.add(data);
                            }
                        }
                    }
                }
            });
            if (log.isInfoEnabled()) {
                log.info("解析配置参数: {} => {}", val, retMap);
            }
        }
        return retMap;
    }

    @Override
    public boolean checkPreIntercept(@Nonnull final HttpMethod method, @Nonnull final String url) {
        final String uri = method + ":" + url;
        try {
            // 检查是否存在
            final String countSql = "select count(0) from " + getTableName() + " where status = 1 and uri = ?";
            final Integer totals = jdbcTemplate.queryForObject(countSql, Integer.class, uri);
            if (Objects.nonNull(totals)) {
                return totals > 0;
            }
        } catch (DataAccessException e) {
            log.warn("checkPreIntercept({})-exp: {}", uri, e.getMessage());
        }
        return false;
    }

    @Override
    public String checkIntercept(@Nonnull final HttpMethod method, @Nonnull final String url, @Nullable final Map<String, Collection<String>> args) {
        final String uri = method + ":" + url;
        List<AdviceConfigModel> models = null;
        try {
            // 查询记录
            final String selSql = "select id, args, start_time, end_time from " + getTableName() + " where status = 1 and uri = ?";
            final RowMapper<AdviceConfigModel> rowMapper = (rs, rowNum) -> {
                final AdviceConfigModel data = new AdviceConfigModel();
                data.setId(rs.getString("id"));
                data.setArgs(rs.getString("args"));
                data.setStartTime(rs.getTimestamp("start_time"));
                data.setEndTime(rs.getTimestamp("end_time"));
                return data;
            };
            models = jdbcTemplate.query(selSql, rowMapper, uri);
        } catch (DataAccessException e) {
            log.warn("checkPreIntercept({}, args: {})-exp: {}", uri, args, e.getMessage());
        }
        // 查询数据
        if (!CollectionUtils.isEmpty(models)) {
            final boolean hasNoArgs = CollectionUtils.isEmpty(args);
            // 当前时间戳
            final long nowStamp = System.currentTimeMillis() / 1000;
            // 时间戳处理
            final Function<Date, Long> parseStamp = date -> {
                if (Objects.nonNull(date)) {
                    return date.getTime() / 1000;
                }
                return -1L;
            };
            // 检查参数是否匹配
            final List<AdviceConfigModel> configs = models.stream()
                    // 参数拦截
                    .filter(item -> {
                        final Map<String, Collection<String>> retArgs = parseArgs(item.getArgs());
                        if (hasNoArgs) {
                            return retArgs.isEmpty();
                        }
                        if (!retArgs.isEmpty()) {
                            for (Map.Entry<String, Collection<String>> entry : retArgs.entrySet()) {
                                final Collection<String> inputVals = args.getOrDefault(entry.getKey(), null);
                                if (CollectionUtils.isEmpty(inputVals)) {
                                    return false;
                                }
                                final Collection<String> vals = entry.getValue();
                                // 检查交集
                                final boolean disjoin = Collections.disjoint(inputVals, vals);
                                if (disjoin) {
                                    return false;
                                }
                            }
                        }
                        return true;
                    })
                    // 日期拦截
                    .filter(item -> {
                        final Date start = item.getStartTime(), end = item.getEndTime();
                        if (Objects.nonNull(start) || Objects.nonNull(end)) {
                            long startStamp = parseStamp.apply(start), endStamp = parseStamp.apply(end);
                            if (startStamp > 0 && endStamp > 0) {
                                return nowStamp >= startStamp && nowStamp <= endStamp;
                            }
                            if (startStamp > 0) {
                                return nowStamp >= startStamp;
                            }
                            return nowStamp <= endStamp;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
            // 检查结果
            if (CollectionUtils.isEmpty(configs)) {
                log.warn("未配置拦截数据: {}[hasNoArgs: {},args: {}]", uri, hasNoArgs, args);
                return null;
            }
            final AdviceConfigModel data = configs.get(0);
            log.info("已拦截配置数据: {} [{}] => {}", uri, args, data.getId());
            // 加载缓存数据
            return data.getId();
        }
        return null;
    }

    @Override
    public String getRespJsonById(@Nullable final String id) {
        if (!Strings.isNullOrEmpty(id)) {
            final String key = "resp_json-" + id;
            synchronized (locks.computeIfAbsent(key, k -> new Object())) {
                try {
                    String respJson = cache.getIfPresent(id);
                    if (Strings.isNullOrEmpty(respJson)) {
                        final String selRespJsonSql = "select resp_json from " + getTableName() + " where id = ?";
                        respJson = jdbcTemplate.queryForObject(selRespJsonSql, String.class, id);
                        if (!Strings.isNullOrEmpty(respJson)) {
                            cache.put(id, respJson);
                        }
                    }
                    return respJson;
                } finally {
                    locks.remove(key);
                }
            }
        }
        return null;
    }
}
