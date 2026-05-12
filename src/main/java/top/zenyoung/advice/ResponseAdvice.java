package top.zenyoung.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.ContentCachingRequestWrapper;
import top.zenyoung.advice.config.AdviceProperties;
import top.zenyoung.advice.service.AdviceService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * URL拦截处理器
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ResponseAdvice implements ResponseBodyAdvice<Object> {
    private final AdviceProperties properties;
    private final AdviceService service;

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(@Nonnull final MethodParameter returnType,
                            @Nonnull final Class<? extends HttpMessageConverter<?>> converterType) {
        if(properties.isEnabled()) {
            return Object.class.isAssignableFrom(returnType.getParameterType());
        }
        return false;
    }

    private Map<String, Collection<String>> extractAllParams(@Nonnull final HttpServletRequest request) {
        final Map<String, Collection<String>> params = Maps.newConcurrentMap();
        // 1. QueryString 和 Form 参数（包括 application/x-www-form-urlencoded）
        final Map<String, String[]> paramMap = request.getParameterMap();
        if (!CollectionUtils.isEmpty(paramMap)) {
            for (final Map.Entry<String, String[]> param : paramMap.entrySet()) {
                final String[] vals;
                if (Objects.nonNull(vals = param.getValue()) && vals.length > 0) {
                    final Collection<String> items = params.computeIfAbsent(param.getKey(), k -> Lists.newArrayList());
                    items.addAll(Sets.newHashSet(vals));
                }
            }
        }
        // 2. JSON Body 参数（如果 Content-Type 是 application/json）
        final String contentType = request.getContentType();
        if (!Strings.isNullOrEmpty(contentType) && contentType.contains("application/json")) {
            final String body = getRequestBody(request);
            if (!Strings.isNullOrEmpty(body)) {
                log.info("JSON 报文体 {}:{} => {}", request.getMethod(), request.getRequestURI(), body);
                try {
                    final MapType mapType = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
                    final Map<String, Object> jsonMap = objectMapper.readValue(body, mapType);
                    if (!CollectionUtils.isEmpty(jsonMap)) {
                        for (final Map.Entry<String, Object> entry : jsonMap.entrySet()) {
                            final Object val = entry.getValue();
                            if (Objects.nonNull(val) && ((val instanceof String) || (val instanceof Number) || (val instanceof Boolean))) {
                                final Collection<String> items = params.computeIfAbsent(entry.getKey(), k -> Lists.newArrayList());
                                items.add(val + "");
                            }
                        }
                    }
                } catch (Throwable e) {
                    log.warn("JSON Body解析失败: {}", body, e);
                }
            }
        }
        return params;
    }

    private String getRequestBody(@Nonnull final HttpServletRequest request) {
        if (log.isInfoEnabled()) {
            log.info("请求报文体类型: {}", request.getClass());
        }
        byte[] buf = null;
        String encoding = null;
        if (request instanceof ContentCachingRequestWrapper) {
            final ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
            buf = wrapper.getContentAsByteArray();
            encoding = wrapper.getCharacterEncoding();
        }
        // 检查数据
        if (Objects.nonNull(buf) && buf.length > 0) {
            try {
                if (Strings.isNullOrEmpty(encoding)) {
                    return new String(buf);
                }
                return new String(buf, encoding);
            } catch (UnsupportedEncodingException e) {
                return new String(buf);
            }
        }
        return null;
    }

    private Class<?> getDataTypeFromReturnType(@Nonnull final MethodParameter returnType) {
        // 获取方法返回类型的 ResolvableType（例如 ResultVO<User>）
        final ResolvableType resolvableType = ResolvableType.forMethodParameter(returnType);
        // 获取 ResultVO 的第一个泛型参数（即 T）
        final ResolvableType genericType = resolvableType.getGeneric(0);
        // 解析为原始 Class
        return genericType.resolve();
    }


    @Override
    public Object beforeBodyWrite(@Nullable final Object body,
                                  @Nonnull final MethodParameter returnType,
                                  @Nonnull final MediaType selectedContentType,
                                  @Nonnull final Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @Nonnull final ServerHttpRequest request, @Nonnull final ServerHttpResponse response) {
        // 是否启用处理
        if (!properties.isEnabled()) {
            return body;
        }
        // 请求方式
        final HttpMethod httpMethod = request.getMethod();
        // 只拦截GET/POST请求
        if (Objects.nonNull(body) && (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.POST)) {
            // 请求URL
            final String url = request.getURI().getPath();
            // 预拦截处理
            if (!service.checkPreIntercept(httpMethod, url)) {
                log.info("预未拦截 {}:{}", httpMethod, url);
                return body;
            }
            // 获取原始 HttpServletRequest
            HttpServletRequest servletRequest = null;
            if (request instanceof ServletServerHttpRequest) {
                servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            }
            if (Objects.isNull(servletRequest)) {
                log.warn("无法获取原始HttpServletRequest: [{}]{}", httpMethod, url);
                return body;
            }
            // 获取所有请求参数(querystring + form + JSON body)
            final Map<String, Collection<String>> params = extractAllParams(servletRequest);
            // 检查拦截ID
            final String id = service.checkIntercept(httpMethod, url, params);
            if (Strings.isNullOrEmpty(id)) {
                log.info("未拦截 {}:{}", httpMethod, url);
                return body;
            }
            // 加载数据
            final String respJson = service.getRespJsonById(id);
            if (!Strings.isNullOrEmpty(respJson)) {
                log.info("拦截 {}:{}=>{}", httpMethod, url, id);
                // 获取 ResultVO 中泛型参数 T 的实际类型
                final Class<?> cls = getDataTypeFromReturnType(returnType);
                log.info("拦截数据类型: {}", cls.getName());
                try {
                    final Object data = objectMapper.readValue(respJson, cls);
                    if (Objects.nonNull(data)) {
                        return data;
                    }
                } catch (Throwable e) {
                    log.warn("反序列化拦截数据失败[cls: {}]: {}", cls, respJson, e);
                }
            }
        }
        return body;
    }
}
