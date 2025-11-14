create database if not exists gem_chat;

use gem_chat;

create table avatar_box
(
    id          bigint                   not null comment '主键ID'
        primary key,
    name        varchar(255)             not null comment '文件名',
    fingerprint char(64)                 not null comment '文件指纹(SHA256)',
    size        bigint                   not null comment '文件大小(字节)',
    mime_type   varchar(32)              not null comment '文件MIME类型',
    location    varchar(512)             not null comment '文件的物理存储路径',
    refer_count int unsigned default '0' not null comment '被引用次数',
    status      tinyint      default 1   not null comment '0-正在上传，1-成功上传，2-上传失败，3-已删除',
    from_id     bigint                   null comment '文件来源，用户ID',
    created_at  bigint                   not null comment '创建时间戳',
    updated_at  bigint                   not null comment '更新时间戳',
    constraint uk_fingerprint_size
        unique (fingerprint, size, mime_type)
)
    comment '用户头像仓库';

create table chat_group
(
    id          bigint auto_increment comment '群聊ID，主键'
        primary key,
    name        varchar(100)                              not null comment '群聊名',
    create_user bigint                                    not null comment '创建者ID',
    avatar      varchar(255) default 'default_avatar.png' null comment '头像名',
    number      int                                       null comment '成员数量',
    signature   varchar(255) default ''                   null comment '群签名',
    status      tinyint      default 0                    null comment '群组状态：0正常，1删除，2禁用',
    created_at  bigint                                    null comment '创建时间戳',
    updated_at  bigint                                    null comment '更新时间戳'
)
    comment '群聊信息表';

create table chat_message
(
    session_id  bigint            not null comment '会话ID',
    message_id  bigint            not null comment '消息ID，在同一会话内自增',
    type        int               not null comment '消息类型',
    from_id     bigint            not null comment '发送者ID',
    content     text              null comment '消息内容',
    status      tinyint default 0 not null comment '消息状态',
    reply_to_id bigint            null comment '引用消息ID',
    created_at  bigint            not null comment '发送时间戳',
    updated_at  bigint            not null comment '更新时间戳',
    primary key (session_id, message_id)
)
    comment '聊天消息表';

create table chat_session
(
    id                   bigint auto_increment comment '会话ID，主键'
        primary key,
    type                 tinyint default 1 not null comment '会话类型：1-单聊，2-群聊',
    first_id             bigint            not null comment '如果是单聊，则为第一个用户的ID，如果是群聊，则是群聊ID',
    second_id            bigint            null comment '如果是单聊，则为第二个用户的ID，如果是群聊，则为空',
    last_message_id      bigint            null comment '最后一条消息ID',
    last_message_content varchar(500)      null comment '最后一条消息内容摘要',
    last_message_time    bigint            null comment '最后一条消息时间戳',
    status               tinyint default 1 not null comment '会话状态：1-正常，2-已删除',
    created_at           bigint            not null comment '创建时间戳',
    updated_at           bigint            not null comment '更新时间戳',
    constraint uk_single_chat
        unique (first_id, second_id)
)
    comment '聊天会话表';

create table file_box
(
    id           bigint                   not null comment '主键ID'
        primary key,
    name         varchar(255)             not null comment '文件名',
    fingerprint  char(64)                 not null comment '文件指纹(SHA256)',
    size         bigint                   not null comment '文件大小(字节)',
    mime_type    varchar(32)              not null comment '文件MIME类型',
    location     varchar(512)             not null comment '文件的物理存储路径',
    refer_count  int unsigned default '0' not null comment '被引用次数',
    status       tinyint      default 1   not null comment '0-正在上传，1-成功上传，2-上传失败，3-已删除',
    from_id      bigint                   not null comment '文件来源，用户ID',
    from_type    tinyint                  null comment '0-聊天上传，1-云盘上传',
    from_session bigint                   null comment 'from_type=0,则为会话ID，否则为空',
    created_at   bigint                   not null comment '创建时间戳',
    updated_at   bigint                   not null comment '更新时间戳',
    constraint uk_fingerprint_size
        unique (fingerprint, size, mime_type)
)
    comment '文件仓库';

create table friend_request
(
    id          bigint unsigned         not null comment '申请ID，主键'
        primary key,
    from_id     bigint unsigned         not null comment '用户ID',
    to_id       bigint unsigned         not null comment '目标ID',
    from_remark varchar(64)  default '' null comment '好友备注',
    to_remark   varchar(64)             null comment '好友备注',
    statement   varchar(255) default '' null comment '申请陈述',
    status      tinyint      default 1  null comment '申请状态：0正在申请，1已通过，2已拒绝',
    created_at  bigint                  null comment '创建时间',
    updated_at  bigint                  null comment '更新时间'
)
    comment '好友申请表';

create table group_member
(
    group_id   bigint            not null comment '群聊ID',
    user_id    bigint            not null comment '用户ID',
    remark     varchar(50)       null comment '用户备注名',
    status     tinyint default 0 null comment '用户状态：0-正常，1-禁用，2-删除',
    role       tinyint default 3 null comment '用户角色：1-群主，2-管理员，3-普通成员',
    created_at bigint            null comment '创建时间戳',
    updated_at bigint            null comment '更新时间戳',
    primary key (group_id, user_id)
)
    comment '群聊用户关联表';

create table user
(
    id            bigint unsigned                           not null comment '用户ID，主键'
        primary key,
    username      varchar(32)                               not null comment '用户名',
    password_hash char(60)                                  not null comment '加密后的密码（使用BCrypt等算法）',
    email         varchar(128)                              null comment '邮箱',
    phone         varchar(20)                               null comment '手机号',
    avatar        varchar(255) default 'default_avatar.png' null comment '头像URL',
    signature     varchar(255) default ''                   null comment '个性签名',
    gender        tinyint      default 0                    null comment '性别：0未知，1男，2女',
    birthdate     date                                      null comment '出生日期',
    status        tinyint      default 1                    null comment '用户状态：0禁用，1正常，2冻结',
    created_at    bigint                                    null comment '创建时间',
    updated_at    bigint                                    null comment '更新时间'
)
    comment '用户基本信息表';

create table user_friend
(
    id            bigint unsigned        not null comment '关系ID，主键'
        primary key,
    user_id       bigint unsigned        not null comment '用户ID',
    friend_id     bigint unsigned        not null comment '好友ID',
    block_status  tinyint     default 1  null comment '黑名单类型：0正常，1已拉黑，2被拉黑，3相互拉黑',
    delete_status tinyint     default 1  null comment '删除类型：0正常，1已删除，2被删除，3相互删除',
    remark        varchar(64) default '' null comment '好友备注',
    created_at    bigint                 null comment '创建时间',
    updated_at    bigint                 null comment '更新时间',
    constraint uniq_user_friend
        unique (user_id, friend_id)
)
    comment '用户关系表';

create table user_login
(
    id          bigint unsigned   not null comment '日志ID，主键'
        primary key,
    user_id     bigint unsigned   not null comment '用户ID',
    status      tinyint default 1 null comment '状态：1登录成功，2登录失败，3退出登录',
    platform    varchar(64)       null comment '操作系统类型',
    login_ip    varchar(45)       null comment '登录IP',
    device_hash varchar(64)       null comment '设备Hash',
    remark      varchar(255)      null comment '备注（如失败原因）',
    created_at  bigint            null comment '登录时间'
)
    comment '用户登录日志表';
