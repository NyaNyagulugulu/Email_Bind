package neko.Server;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.sql.*;
import java.util.*;

public final class EmailBind extends JavaPlugin implements Listener {

    // 数据库连接信息
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String table;

    // SMTP配置信息
    private String smtpHost;
    private int smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private boolean smtpSSL;
    private boolean smtpTLS;

    // 存储未绑定邮箱的玩家
    private Map<UUID, Boolean> unbindedPlayers = new HashMap<>();
    // 存储玩家输入的邮箱地址
    private Map<UUID, String> playerEmailInputs = new HashMap<>();
    // 存储玩家的验证码
    private Map<UUID, String> playerVerificationCodes = new HashMap<>();
    // 存储正在等待验证码输入的玩家
    private Map<UUID, Boolean> playerWaitingForCode = new HashMap<>();
    
    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        loadConfig();
        
        // 初始化数据库
        initializeDatabase();
        
        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // 启动定时检查任务
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerEmail(player);
                }
            }
        }.runTaskTimer(this, 0L, 600L); // 每30秒检查一次
        
        getLogger().info("EmailBind插件已启用!");
    }

    @Override
    public void onDisable() {
        getLogger().info("EmailBind插件已禁用!");
    }
    
    // 加载配置
    private void loadConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
        
        host = getConfig().getString("database.host", host);
        port = getConfig().getInt("database.port", port);
        database = getConfig().getString("database.database", database);
        username = getConfig().getString("database.username", username);
        password = getConfig().getString("database.password", password);
        table = getConfig().getString("database.table", table);
        
        smtpHost = getConfig().getString("smtp.host", smtpHost);
        smtpPort = getConfig().getInt("smtp.port", smtpPort);
        smtpUsername = getConfig().getString("smtp.username", smtpUsername);
        smtpPassword = getConfig().getString("smtp.password", smtpPassword);
        smtpSSL = getConfig().getBoolean("smtp.ssl", smtpSSL);
        smtpTLS = getConfig().getBoolean("smtp.tls", smtpTLS);
    }
    
    // 初始化数据库连接
    private void initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            getLogger().severe("找不到MySQL驱动: " + e.getMessage());
        }
    }
    
    // 启动重复提示任务
    private void startReminderTask(Player player, UUID playerUUID) {
        new BukkitRunnable() {
            // 颜色数组
            private final ChatColor[] colors = {
                ChatColor.RED, ChatColor.YELLOW, ChatColor.GREEN, 
                ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE, 
                ChatColor.GOLD, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA, 
                ChatColor.DARK_PURPLE, ChatColor.DARK_RED
            };
            private int colorIndex = 0;
            
            @Override
            public void run() {
                // 检查玩家是否仍然在线且未绑定
                if (player.isOnline() && unbindedPlayers.containsKey(playerUUID)) {
                    // 循环使用不同颜色
                    ChatColor currentColor = colors[colorIndex % colors.length];
                    colorIndex++;
                    
                    // 发送三行实质性内容的二次元猫娘风格提示
                    player.sendMessage(currentColor + "§l§o[猫娘提醒] §d★ 亲爱的主人，您的邮箱还没有绑定哦！ ★");
                    player.sendMessage(currentColor + "§l§o[猫娘提醒] §b★ 请在聊天框输入您的邮箱地址 ★");
                    player.sendMessage(currentColor + "§l§o[猫娘提醒] §a★ 我会为您发送验证码完成绑定喵～ ★");
                } else {
                    // 玩家已离线或已绑定，取消任务
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 10L); // 0.5秒间隔（10 ticks）
    }
    
    // 检查玩家邮箱绑定状态
    private void checkPlayerEmail(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Connection connection = DriverManager.getConnection(
                        "jdbc:mysql://" + host + ":" + port + "/" + database, 
                        username, 
                        password
                    );
                    
                    PreparedStatement statement = connection.prepareStatement(
                        "SELECT email FROM " + table + " WHERE username = ?"
                    );
                    statement.setString(1, player.getName());
                    
                    ResultSet result = statement.executeQuery();
                    
                    if (result.next()) {
                        String email = result.getString("email");
                        if (email == null || email.isEmpty()) {
                            // 邮箱未绑定
                            UUID playerUUID = player.getUniqueId();
                            unbindedPlayers.put(playerUUID, true);
                            // 启动重复提示任务
                            startReminderTask(player, playerUUID);
                        } else {
                            // 邮箱已绑定
                            unbindedPlayers.remove(player.getUniqueId());
                        }
                    } else {
                        // 玩家不存在于authme表中
                        unbindedPlayers.remove(player.getUniqueId());
                    }
                    
                    connection.close();
                } catch (SQLException e) {
                    getLogger().severe("数据库查询出错: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(this);
    }
    
    // 玩家加入服务器事件
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 延迟检查，确保玩家完全加入
        new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayerEmail(player);
            }
        }.runTaskLater(this, 20L);
    }
    
    // 玩家退出服务器事件
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        unbindedPlayers.remove(playerUUID);
        playerEmailInputs.remove(playerUUID);
        playerVerificationCodes.remove(playerUUID);
        playerWaitingForCode.remove(playerUUID);
    }
    
    // 玩家交互事件
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (unbindedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    // 玩家丢弃物品事件
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (unbindedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    // 玩家拾取物品事件
    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (unbindedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    // 玩家移动事件
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (unbindedPlayers.containsKey(player.getUniqueId())) {
            // 限制移动
            event.setCancelled(true);
        }
    }
    
    // 玩家传送事件
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (unbindedPlayers.containsKey(player.getUniqueId())) {
            // 取消传送
            event.setCancelled(true);
        }
    }
    
    // 玩家退出服务器事件
    
    // 玩家聊天事件
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String message = event.getMessage();
        
        // 检查玩家是否未绑定邮箱
        if (unbindedPlayers.containsKey(playerUUID)) {
            event.setCancelled(true);
            
            // 检查玩家是否正在等待验证码输入
            if (playerWaitingForCode.containsKey(playerUUID) && playerWaitingForCode.get(playerUUID)) {
                // 验证验证码
                String storedCode = playerVerificationCodes.get(playerUUID);
                if (storedCode != null && storedCode.equals(message)) {
                    // 验证成功，更新数据库中的邮箱
                    String email = playerEmailInputs.get(playerUUID);
                    updatePlayerEmail(player, email);
                    playerWaitingForCode.remove(playerUUID);
                } else {
                    player.sendMessage(ChatColor.RED + "§l[猫娘提醒] " + ChatColor.YELLOW + "验证码错误，请重新输入！喵～");
                }
                return;
            }
            
            // 验证邮箱格式
            if (message.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                playerEmailInputs.put(playerUUID, message);
                // 生成并发送验证码
                String code = generateVerificationCode();
                playerVerificationCodes.put(playerUUID, code);
                sendVerificationCodeEmail(message, code);
                playerWaitingForCode.put(playerUUID, true);
                player.sendMessage(ChatColor.GREEN + "§l[猫娘提醒] " + ChatColor.AQUA + "验证码已发送，请将验证码输入到聊天框！喵～");
            } else {
                player.sendMessage(ChatColor.RED + "§l[猫娘提醒] " + ChatColor.YELLOW + "邮箱格式不正确，请重新输入！喵～");
            }
            return;
        }
    }
    private void updatePlayerEmail(Player player, String email) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Connection connection = DriverManager.getConnection(
                        "jdbc:mysql://" + host + ":" + port + "/" + database, 
                        username, 
                        password
                    );
                    
                    PreparedStatement statement = connection.prepareStatement(
                        "UPDATE " + table + " SET email = ? WHERE username = ?"
                    );
                    statement.setString(1, email);
                    statement.setString(2, player.getName());
                    
                    int result = statement.executeUpdate();
                    
                    if (result > 0) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.sendMessage(ChatColor.GREEN + "邮箱绑定成功!");
                                UUID playerUUID = player.getUniqueId();
                                unbindedPlayers.remove(playerUUID);
                                playerEmailInputs.remove(playerUUID);
                                playerVerificationCodes.remove(playerUUID);
                                playerWaitingForCode.remove(playerUUID);
                            }
                        }.runTask(EmailBind.this);
                    } else {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.sendMessage(ChatColor.RED + "邮箱绑定失败，请联系管理员!");
                            }
                        }.runTask(EmailBind.this);
                    }
                    
                    connection.close();
                } catch (SQLException e) {
                    getLogger().severe("数据库更新出错: " + e.getMessage());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendMessage(ChatColor.RED + "邮箱绑定失败，请联系管理员!");
                        }
                        }.runTask(EmailBind.this);
                }
            }
        }.runTaskAsynchronously(this);
    }
    
    // 生成随机验证码(16位数字)
    private String generateVerificationCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
    
    // 通过SMTP发送验证码(HTML格式)
    private void sendVerificationCodeEmail(String toEmail, String code) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // 设置邮件属性
                    Properties props = new Properties();
                    props.put("mail.smtp.host", smtpHost);
                    props.put("mail.smtp.port", String.valueOf(smtpPort));
                    props.put("mail.smtp.auth", "true");
                    
                    if (smtpSSL) {
                        props.put("mail.smtp.ssl.enable", "true");
                    }
                    
                    if (smtpTLS) {
                        props.put("mail.smtp.starttls.enable", "true");
                    }
                    
                    // 创建会话
                    Session session = Session.getInstance(props, new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(smtpUsername, smtpPassword);
                        }
                    });
                    
                    // 创建邮件
                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(smtpUsername));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                    message.setSubject("邮箱绑定验证码");
                    
                    // 读取HTML模板
                    String templatePath = getDataFolder().getAbsolutePath() + "/bind.html";
                    String htmlContent = readFileToString(templatePath);
                    
                    // 替换模板中的占位符
                    htmlContent = htmlContent.replace("%playername%", "玩家");
                    htmlContent = htmlContent.replace("%servername%", "喵之国度");
                    htmlContent = htmlContent.replace("%generatedcode%", code);
                    htmlContent = htmlContent.replace("%minutesvalid%", "10");
                    
                    // 创建HTML内容
                    MimeBodyPart htmlPart = new MimeBodyPart();
                    htmlPart.setContent(htmlContent, "text/html; charset=utf-8");
                    
                    // 创建邮件内容
                    Multipart multipart = new MimeMultipart();
                    multipart.addBodyPart(htmlPart);
                    
                    // 设置邮件内容
                    message.setContent(multipart);
                    
                    // 发送邮件
                    Transport.send(message);
                    
                    getLogger().info("验证码已发送至邮箱: " + toEmail);
                } catch (Exception e) {
                    getLogger().severe("发送验证码邮件失败: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(this);
    }
    
    // 读取文件内容为字符串
    private String readFileToString(String filePath) {
        try {
            StringBuilder content = new StringBuilder();
            java.nio.file.Path path = java.nio.file.Paths.get(filePath);
            java.util.List<String> lines = java.nio.file.Files.readAllLines(path);
            for (String line : lines) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (Exception e) {
            getLogger().warning("读取HTML模板文件失败，使用默认模板: " + e.getMessage());
            // 默认HTML模板
            return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: auto; background-color: #f5f5f5; padding: 20px;\">" +
                   "<h1 style=\"color: #333; border-bottom: 2px solid #ddd; padding-bottom: 10px;\">邮箱绑定验证码</h1>" +
                   "<div style=\"background: #fff; padding: 25px; border-radius: 10px; margin-top: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);\">" +
                   "<p style=\"font-size: 18px; color: #666;\">您正在绑定邮箱到服务器账户，以下是您的验证码：</p>" +
                   "<div style=\"background: #f0f0f0; padding: 15px; border-radius: 5px; margin: 15px 0; text-align: center;\">" +
                   "<p style=\"font-size: 24px; color: #333; margin: 0; font-weight: bold;\">%generatedcode%</p>" +
                   "</div>" +
                   "<p style=\"color: #999;\">此验证码将在10分钟后过期，请尽快在游戏内输入完成绑定。</p>" +
                   "<div style=\"background: #e6f3ff; padding: 15px; border-radius: 5px; margin-top: 20px;\">" +
                   "<p>使用说明：</p>" +
                   "<p style=\"margin: 10px 0;\">请在游戏聊天框中输入以下命令：<br>" +
                   "<code style=\"background: #fff; padding: 8px; border-radius: 3px; display: inline-block;\">%generatedcode%</code></p>" +
                   "</div>" +
                   "</div>" +
                   "<footer style=\"margin-top: 30px; text-align: center;\">" +
                   "<p style=\"color: #999;\">~ 感谢您使用我们的服务器 ~</p>" +
                   "<div style=\"font-size: 12px; color: #ccc;\"><p>如有问题请联系管理员</p></div>" +
                   "</footer>" +
                   "</div>".replace("%generatedcode%", generateVerificationCode());
        }
    }
}