package top.zenyoung.advice.service;

import org.springframework.http.HttpMethod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

/**
 * url拦截-服务接口
 */
public interface AdviceService {

    /**
     * 预检查拦截
     *
     * @param method 请求方式
     * @param url    请求url
     * @return 是否已配置拦截
     */
    boolean checkPreIntercept(@Nonnull final HttpMethod method, @Nonnull final String url);

    /**
     * 检查拦截
     *
     * @param method 请求方式
     * @param url    请求url
     * @param args   请求参数
     * @return 拦截ID
     */
    String checkIntercept(@Nonnull final HttpMethod method, @Nonnull final String url, @Nullable final Map<String, Collection<String>> args);

    /**
     * 获取拦截数据
     *
     * @param id 拦截ID
     * @return 拦截数据字符串
     */
    String getRespJsonById(@Nonnull final String id);
}
