# EmailBind 插件

一个为Minecraft服务器设计的插件，用于强制玩家绑定邮箱。

## 功能特性

1. **邮箱绑定验证**：
   - 玩家加入服务器后必须绑定邮箱
   - 通过SMTP发送验证码到玩家邮箱
   - 验证码验证后完成邮箱绑定

2. **安全防护**：
   - 临时邮箱检测和阻止
   - 邮箱重复绑定检查
   - 未验证玩家限制所有操作

## 配置说明

### 数据库配置
```yaml
database:
  host: localhost        # 数据库主机地址
  port: 3306             # 数据库端口
  database: authme       # 数据库名称
  username: root         # 数据库用户名
  password: 'wcjs123'    # 数据库密码
  table: authme          # AuthMe表名
```

### 邮箱SMTP配置
```yaml
smtp:
  host: smtp.exmail.qq.com  # SMTP服务器地址
  port: 465                 # SMTP端口
  username: support@cnmsb.xin  # 邮箱用户名
  password: ''              # 邮箱密码或授权码
  ssl: true                 # 是否启用SSL
  tls: false                # 是否启用TLS
```

## 使用说明

1. **邮箱绑定流程**：
   - 玩家加入服务器后会收到邮箱绑定提示
   - 在聊天框输入邮箱地址
   - 系统发送6位验证码到邮箱
   - 输入验证码完成绑定

2. **安全检查**：
   - 自动检测并阻止临时邮箱
   - 防止一个邮箱绑定多个账号
   - 未完成验证的玩家无法进行任何操作

## 依赖库

- Spigot API
- MySQL Connector
- JavaMail API

## 注意事项

1. 确保数据库连接信息正确
2. 配置正确的SMTP服务器信息
3. 插件会自动创建所需的数据库表