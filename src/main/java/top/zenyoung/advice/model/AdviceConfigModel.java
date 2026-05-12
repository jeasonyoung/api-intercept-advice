package top.zenyoung.advice.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * url拦截配置
 */
@Data
public class AdviceConfigModel implements Serializable {
    /**
     * 配置ID
     */
    private String id;
    /**
     * 接口地址(GET:url)
     */
    private String uri;
    /**
     * 接口参数(a0=x&a1=y&a3=z)
     */
    private String args;
    /**
     * 响应数据(json格式)
     */
    private String respJson;
    /**
     * 生效开始时间(yyyy-MM-dd HH:mm:ss)
     */
    private Date startTime;
    /**
     * 生效结束时间(yyyy-MM-dd HH:mm:ss)
     */
    private Date endTime;
    /**
     * 状态(0:停用,1:启用)
     */
    private Integer status;
}
