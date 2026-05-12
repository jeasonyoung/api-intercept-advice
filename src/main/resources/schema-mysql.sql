create table `api_advice_config` (
    `id`  varchar(64) not null  comment '配置ID',

    `uri`   varchar(512)     not null comment '接口地址(GET:XX)',
    `args`  varchar(1024) default null comment '接口参数(a0=x&a1=y&a3=z)',

    `resp_json` text default null comment '响应数据(json格式)',

    `start_time` datetime default null comment '生效开始时间(yyyy-MM-dd HH:mm:ss)',
    `end_time`   datetime default null comment '生效结束时间(yyyy-MM-dd HH:mm:ss)',

    `status` tinyint unsigned default 1 comment '状态(0:停用,1:启用)',

    `create_date` timestamp default current_timestamp comment '创建时间',
    `create_user_code` varchar(32) default null comment '创建者',

    constraint `pk_api_advice_config` primary key(`id`),

    index `idx_api_advice_config_uri`(`uri`),
    index `idx_api_advice_config_start`(`start_time`),
    index `idx_api_advice_config_end`(`end_time`),
    index `idx_api_advice_config_status`(`status`)
) engine=InnoDB default charset = utf8mb4 comment 'url拦截配置';