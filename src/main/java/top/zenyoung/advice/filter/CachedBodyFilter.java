package top.zenyoung.advice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * 缓存请求报文体(支持 JSON Body 重复读取)
 */
@Slf4j
public class CachedBodyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@Nonnull final HttpServletRequest request, @Nonnull final HttpServletResponse response, @Nonnull final FilterChain filterChain) throws ServletException, IOException {
        //
        final String method = request.getMethod();
        log.info("CachedBodyFilter,  用 ContentCachingRequestWrapper 包装原始请求 {}:{}", method, request.getRequestURI());
        // 用 ContentCachingRequestWrapper 包装原始请求
        final ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        filterChain.doFilter(wrapper, response);
    }
}
