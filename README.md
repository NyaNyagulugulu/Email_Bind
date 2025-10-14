# EmailBind 邮箱绑定插件

一个为Minecraft服务器设计的邮箱绑定插件，确保玩家账号安全。

## 功能特点

- **强制邮箱绑定**：新玩家必须绑定邮箱才能正常使用服务器
- **邮箱验证**：通过SMTP发送验证码到玩家邮箱进行验证
- **二次元猫娘风格**：所有提示信息都采用可爱的猫娘风格
- **权限限制**：未绑定邮箱的玩家将被限制所有操作
- **HTML邮件模板**：支持自定义邮件模板

## 安装说明

1. 将编译好的jar文件放入服务器的`plugins`文件夹
2. 启动服务器，插件会自动生成配置文件
3. 修改`config.yml`中的数据库和SMTP配置
4. 重启服务器使配置生效

## 配置说明

### 数据库配置
```yaml
database:
  host: localhost       # 数据库主机地址
  port: 3306            # 数据库端口
  database: authme      # 数据库名称
  username: root        # 数据库用户名
  password: 'wcjs123'   # 数据库密码
  table: authme         # 表名
```

### SMTP配置
```yaml
smtp:
  host: smtp.exmail.qq.com   # SMTP服务器地址
  port: 465                  # SMTP端口
  username: support@cnmsb.xin # 邮箱用户名
  password: '' # 邮箱密码或授权码
  ssl: false                 # 是否启用SSL
  tls: true                  # 是否启用TLS
```

## 使用方法

1. 玩家加入服务器后，如果未绑定邮箱，会收到持续的提示消息
2. 玩家在聊天框输入邮箱地址
3. 插件会发送验证码到玩家邮箱
4. 玩家输入验证码完成绑定
5. 绑定成功后解除所有限制

## 自定义邮件模板

插件会在配置目录生成`bind.html`文件，您可以自定义邮件模板。
可用的占位符：
- `%playername%`：玩家名称
- `%servername%`：服务器名称
- `%generatedcode%`：验证码
- `%minutesvalid%`：验证码有效期

## 许可证

GNU General Public License v3.0

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

**许可证说明**：本插件使用GPLv3许可证，您可以自由地运行、研究、分享（复制）和修改软件。但如果您分发修改后的版本，必须同样使用GPLv3许可证开源您的修改。