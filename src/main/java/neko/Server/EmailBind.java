package neko.Server;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MinecraftFont;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;



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
    
    // 2FA管理器
    private TwoFactorAuth twoFactorAuth;
    
    // 临时邮箱域名列表
    private static final String[] TEMP_EMAIL_DOMAINS = {
        "10minutemail.com", "mailinator.com", "temp-mail.org", "maildrop.cc",
        "guerrillamail.com", "sharklasers.com", "getnada.com", "mintemail.com",
        "trashmail.com", "mailnesia.com", "dispostable.com", "tempmailaddress.com",
        "mytemp.email", "emailondeck.com", "throwawaymail.com", "spoofmail.de",
        "fakeinbox.com", "instantemailaddress.com", "spamgourmet.com", "getairmail.com",
        "tempail.com", "spam4.me", "33mail.com", "moakt.com", "mailsac.com",
        "mailnull.com", "tempmailo.com", "dropmail.me", "owlymail.com", "fakemail.net",
        "luxusmail.org", "edumail.icu", "emailtemporal.org", "spambox.us", "tempemail.co",
        "mail7.io", "temp-mail.io", "tmail.ai", "notmailinator.com", "anonaddy.com",
        "emailsensei.com", "burnermail.io", "dismail.de", "guerrillamail.net", "mailcatch.com",
        "nada.ltd", "mail-tester.com", "fakebox.org", "tempinbox.com", "gettempmail.com",
        "mailpoof.com", "yopmail.com"
    };
    
    // 存储未绑定邮箱的玩家
    private Map<UUID, Boolean> unbindedPlayers = new HashMap<>();
    // 存储玩家输入的邮箱地址
    private Map<UUID, String> playerEmailInputs = new HashMap<>();
    // 存储玩家的验证码
    private Map<UUID, String> playerVerificationCodes = new HashMap<>();
    // 存储正在等待验证码输入的玩家
    private Map<UUID, Boolean> playerWaitingForCode = new HashMap<>();
    // 存储玩家的提示任务
    private Map<UUID, BukkitRunnable> playerReminderTasks = new HashMap<>();
    // 存储玩家的IP地址
    private Map<UUID, String> playerIPs = new ConcurrentHashMap<>();
    
    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        loadConfig();
        
        // 释放默认文件（始终释放）
        saveResource("bind.html", true);
        
        // 初始化数据库
        initializeDatabase();
        
        // 初始化2FA管理器
        twoFactorAuth = new TwoFactorAuth(this);
        twoFactorAuth.loadConfig();
        
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
        }.runTaskTimer(this, 0L, 100L); // 每5秒检查一次
        
        getLogger().info("EmailBind插件已启用!");
    }

    @Override
    public void onDisable() {
        getLogger().info("EmailBind插件已禁用!");
    }
    
    // 获取数据库配置的公共方法
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
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
        // 检查是否已经存在提示任务
        if (playerReminderTasks.containsKey(playerUUID)) {
            return;
        }
        
        BukkitRunnable task = new BukkitRunnable() {
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
                    // 检查玩家是否正在等待验证码输入
                    if (playerWaitingForCode.containsKey(playerUUID) && playerWaitingForCode.get(playerUUID)) {
                        // 发送等待验证码的提示
                        ChatColor currentColor = colors[colorIndex % colors.length];
                        colorIndex++;
                        player.sendMessage(currentColor + "§l----------------------------------------");
                        player.sendMessage(currentColor + "§l§o[猫娘提醒] §a★ 邮箱验证码已经发送啦！ ★");
                        player.sendMessage(currentColor + "§l§o[猫娘提醒] §b★ 快去查看邮件和垃圾箱 ★");
                        player.sendMessage(currentColor + "§l§o[猫娘提醒] §d★ 输入验证码完成绑定喵～ ★");
                        player.sendMessage(currentColor + "§l----------------------------------------");
                    } else {
                        // 发送邮箱未绑定的提示
                        ChatColor currentColor = colors[colorIndex % colors.length];
                        colorIndex++;
                        player.sendMessage(currentColor + "§l----------------------------------------");
                        player.sendMessage(currentColor + "§l§o[猫娘提醒] §d★ 亲爱的主人，您的邮箱还没有绑定哦！ ★");
                        player.sendMessage(currentColor + "§l§o[猫娘提醒] §b★ 请在聊天框输入您的邮箱地址 ★");
                        player.sendMessage(currentColor + "§l§o[猫娘提醒] §a★ 我会为您发送验证码完成绑定喵～ ★");
                        player.sendMessage(currentColor + "§l----------------------------------------");
                    }
                } else {
                    // 玩家已离线或已绑定，取消任务
                    playerReminderTasks.remove(playerUUID);
                    this.cancel();
                }
            }
        };
        
        task.runTaskTimer(this, 0L, 100L); // 5秒间隔（100 ticks）
        playerReminderTasks.put(playerUUID, task);
    }
    
    // 取消提示任务
    private void cancelReminderTask(UUID playerUUID) {
        BukkitRunnable task = playerReminderTasks.get(playerUUID);
        if (task != null) {
            task.cancel();
            playerReminderTasks.remove(playerUUID);
        }
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
                        "SELECT email, ip FROM " + table + " WHERE username = ?"
                    );
                    statement.setString(1, player.getName());
                    
                    ResultSet result = statement.executeQuery();
                    
                    if (result.next()) {
                        String email = result.getString("email");
                        String lastIP = result.getString("ip");
                        String currentIP = player.getAddress().getAddress().getHostAddress();
                        
                        // 存储当前IP地址
                        playerIPs.put(player.getUniqueId(), currentIP);
                        
                        if (email == null || email.isEmpty()) {
                            // 邮箱未绑定
                            UUID playerUUID = player.getUniqueId();
                            unbindedPlayers.put(playerUUID, true);
                            // 启动重复提示任务
                            startReminderTask(player, playerUUID);
                        } else {
                            // 邮箱已绑定
                            unbindedPlayers.remove(player.getUniqueId());
                            // 取消提示任务
                            cancelReminderTask(player.getUniqueId());
                            
                            // 只有在邮箱绑定完成后才检查2FA
                            // 检查玩家是否需要2FA验证
                            twoFactorAuth.checkPlayer2FARequirement(player);
                            
                            // 检查IP是否变化，如果变化则强制2FA验证
                            if (lastIP != null && !lastIP.equals(currentIP)) {
                                twoFactorAuth.force2FAForIPChange(player);
                            }
                        }
                    } else {
                        // 玩家不存在于authme表中
                        unbindedPlayers.remove(player.getUniqueId());
                        // 取消提示任务
                        cancelReminderTask(player.getUniqueId());
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
        playerIPs.remove(playerUUID);
        // 清理2FA相关资源
        if (twoFactorAuth != null) {
            twoFactorAuth.removePlayer2FA(playerUUID);
        }
        // 取消提示任务
        cancelReminderTask(playerUUID);
    }
    
    // 玩家交互事件
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        // 检查是否未绑定邮箱
        if (unbindedPlayers.containsKey(playerUUID)) {
            event.setCancelled(true);
        }
        // 检查是否正在验证2FA（而不是设置2FA）
        else if (twoFactorAuth.getPlayersRequiring2FA().containsKey(playerUUID) && 
                twoFactorAuth.getPlayersWaitingFor2FA().containsKey(playerUUID) && 
                !twoFactorAuth.isSettingUp2FA(playerUUID)) {
            event.setCancelled(true);
        }
    }
    
    // 玩家丢弃物品事件
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        // 检查是否未绑定邮箱
        if (unbindedPlayers.containsKey(playerUUID)) {
            event.setCancelled(true);
        }
        // 检查是否正在验证2FA（而不是设置2FA）
        else if (twoFactorAuth.getPlayersRequiring2FA().containsKey(playerUUID) && 
                twoFactorAuth.getPlayersWaitingFor2FA().containsKey(playerUUID) && 
                !twoFactorAuth.isSettingUp2FA(playerUUID)) {
            event.setCancelled(true);
        }
    }
    
    // 玩家拾取物品事件
    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        // 检查是否未绑定邮箱
        if (unbindedPlayers.containsKey(playerUUID)) {
            event.setCancelled(true);
        }
        // 检查是否正在验证2FA（而不是设置2FA）
        else if (twoFactorAuth.getPlayersRequiring2FA().containsKey(playerUUID) && 
                twoFactorAuth.getPlayersWaitingFor2FA().containsKey(playerUUID) && 
                !twoFactorAuth.isSettingUp2FA(playerUUID)) {
            event.setCancelled(true);
        }
    }
    
    // 玩家移动事件
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        
        // 检查是否未绑定邮箱
        if (unbindedPlayers.containsKey(playerUUID)) {
            // 限制未绑定邮箱玩家的移动，但允许视角转动
            if (event.getFrom().getX() != event.getTo().getX() || 
                event.getFrom().getY() != event.getTo().getY() || 
                event.getFrom().getZ() != event.getTo().getZ()) {
                // 只取消位置变化，允许视角转动
                event.setCancelled(true);
            }
        }
        // 检查是否正在等待2FA验证码输入（不包括设置2FA时）
        else if (twoFactorAuth.getPlayersRequiring2FA().containsKey(playerUUID) && 
                twoFactorAuth.getPlayersWaitingFor2FA().containsKey(playerUUID) && 
                !twoFactorAuth.isSettingUp2FA(playerUUID)) {
            // 限制正在验证2FA的玩家移动，但允许视角转动
            if (event.getFrom().getX() != event.getTo().getX() || 
                event.getFrom().getY() != event.getTo().getY() || 
                event.getFrom().getZ() != event.getTo().getZ()) {
                // 只取消位置变化，允许视角转动
                event.setCancelled(true);
            }
        }
    }
    
    
    
    // 玩家传送事件
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        // 检查是否未绑定邮箱
        if (unbindedPlayers.containsKey(playerUUID)) {
            // 取消传送
            event.setCancelled(true);
        }
        // 检查是否正在验证2FA（而不是设置2FA）
        else if (twoFactorAuth.getPlayersRequiring2FA().containsKey(playerUUID) && 
                twoFactorAuth.getPlayersWaitingFor2FA().containsKey(playerUUID) && 
                !twoFactorAuth.isSettingUp2FA(playerUUID)) {
            // 取消传送
            event.setCancelled(true);
        }
    }
    
    // 玩家命令事件
    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        // 检查是否未绑定邮箱
        if (unbindedPlayers.containsKey(playerUUID)) {
            // 未绑定邮箱的玩家不允许执行命令
            event.setCancelled(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §c★ 请先绑定邮箱再执行命令呢！ ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §e★ 在聊天框输入您的邮箱地址 ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §6★ 完成绑定后即可正常使用命令喵～ ★");
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                }
            }.runTask(EmailBind.this);
        }
        // 检查是否正在验证2FA（而不是设置2FA）
        else if (twoFactorAuth.getPlayersRequiring2FA().containsKey(playerUUID) && 
                twoFactorAuth.getPlayersWaitingFor2FA().containsKey(playerUUID) && 
                !twoFactorAuth.isSettingUp2FA(playerUUID)) {
            // 玩家正在等待2FA验证，不允许执行命令
            event.setCancelled(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §c★ 请先完成2FA验证再执行命令呢！ ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §e★ 输入Google Authenticator中的6位验证码 ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §6★ 完成验证后即可正常使用命令喵～ ★");
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                }
            }.runTask(EmailBind.this);
        }
    }
    
    // 玩家聊天和2FA验证事件
    @EventHandler
    public void onPlayerChatOr2FA(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String message = event.getMessage();
        
        // 检查玩家是否正在等待2FA验证码输入
        if (twoFactorAuth.getPlayersWaitingFor2FA().containsKey(playerUUID) && 
            twoFactorAuth.getPlayersWaitingFor2FA().get(playerUUID)) {
            event.setCancelled(true);
            
            // 验证2FA验证码
            if (message.matches("^\\d{6}$")) { // 6位数字验证码
                if (twoFactorAuth.verify2FA(player, message)) {
                    // 2FA验证成功
                    twoFactorAuth.removePlayer2FA(playerUUID);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendMessage(ChatColor.GREEN + "§l----------------------------------------");
                            player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §a★ 2FA验证成功！ ★");
                            player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §b★ 欢迎回来，管理员大人～ ★");
                            player.sendMessage(ChatColor.GREEN + "§l----------------------------------------");
                        }
                    }.runTask(EmailBind.this);
                } else {
                    // 2FA验证失败
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                            player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §c★ 呜喵～2FA验证码错误呢！ ★");
                            player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §e★ 请检查Google Authenticator中的验证码 ★");
                            player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §6★ 重新输入一次试试看吧喵～ ★");
                            player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                        }
                    }.runTask(EmailBind.this);
                }
                return;
            } else {
                // 输入的不是6位数字验证码
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                        player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §c★ 呜喵～请输入6位数字验证码呢！ ★");
                        player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §e★ 打开Google Authenticator查看 ★");
                        player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §6★ 重新输入一次试试看吧喵～ ★");
                        player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    }
                }.runTask(EmailBind.this);
                return;
            }
        }
        
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
                    // 发送三行格式错误提示（二次元风格）
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §c★ 呜喵～验证码错误呢！ ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §e★ 请仔细核对邮件中的6位验证码 ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §6★ 重新输入一次试试看吧喵～ ★");
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                }
                return;
            }
            
            // 验证邮箱格式
            if (message.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                // 检查是否为临时邮箱
                if (isTempEmail(message)) {
                    // 发送临时邮箱错误提示
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §c★ 呜喵～不能使用临时邮箱呢！ ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §e★ 请使用正规邮箱服务商的邮箱 ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §6★ 重新输入一次试试看吧喵～ ★");
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    return;
                }
                
                // 检查邮箱是否已被其他账号绑定
                if (isEmailAlreadyUsed(message, player.getName())) {
                    // 发送邮箱已使用错误提示
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §c★ 呜喵～这个邮箱已经被绑定了呢！ ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §e★ 请使用未绑定的邮箱地址 ★");
                    player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §6★ 重新输入一次试试看吧喵～ ★");
                    player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    return;
                }
                
                playerEmailInputs.put(playerUUID, message);
                // 生成并发送验证码
                String code = generateVerificationCode();
                playerVerificationCodes.put(playerUUID, code);
                sendVerificationCodeEmail(message, code);
                playerWaitingForCode.put(playerUUID, true);
                
                // 发送三行验证码发送成功提示（二次元风格）
                player.sendMessage(ChatColor.GREEN + "§l----------------------------------------");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §a★ 邮箱验证码已经发送啦！ ★");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §b★ 快去查看邮件和垃圾箱 ★");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §d★ 输入6位验证码完成绑定喵～ ★");
                player.sendMessage(ChatColor.GREEN + "§l----------------------------------------");
                
                // 停止原有的重复提示任务
                cancelReminderTask(playerUUID);
            } else {
                // 发送三行格式错误提示（二次元风格）
                player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §c★ 呜喵～邮箱格式错误呢！ ★");
                player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §e★ 请检查邮箱地址是否正确 ★");
                player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §6★ 重新输入一次试试看吧喵～ ★");
                player.sendMessage(ChatColor.RED + "§l----------------------------------------");
            }
            return;
        }
    }
    
    // 检查是否为临时邮箱
    private boolean isTempEmail(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase();
        for (String tempDomain : TEMP_EMAIL_DOMAINS) {
            if (domain.equals(tempDomain)) {
                return true;
            }
        }
        return false;
    }
    
    // 检查邮箱是否已被其他账号绑定
    private boolean isEmailAlreadyUsed(String email, String playerName) {
        try {
            Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database, 
                username, 
                password
            );
            
            PreparedStatement statement = connection.prepareStatement(
                "SELECT username FROM " + table + " WHERE email = ? AND username != ?"
            );
            statement.setString(1, email);
            statement.setString(2, playerName);
            
            ResultSet result = statement.executeQuery();
            boolean emailUsed = result.next();
            
            connection.close();
            return emailUsed;
        } catch (SQLException e) {
            getLogger().severe("数据库查询出错: " + e.getMessage());
            return false;
        }
    }
    
    // 生成随机验证码(6位数字)
    private String generateVerificationCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
    
    // 读取HTML模板文件
    private String readHtmlTemplate(String filePath) {
        try {
            StringBuilder content = new StringBuilder();
            java.nio.file.Path path = java.nio.file.Paths.get(filePath);
            java.util.List<String> lines = java.nio.file.Files.readAllLines(path);
            for (String line : lines) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (Exception e) {
            getLogger().severe("读取HTML模板文件失败: " + e.getMessage() + "，请检查插件配置目录是否存在bind.html文件");
            // 如果无法读取文件，返回空字符串而不是默认模板
            return "";
        }
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
                    String htmlContent = readHtmlTemplate(templatePath);
                    
                    // 检查HTML内容是否为空
                    if (htmlContent.isEmpty()) {
                        getLogger().severe("HTML模板内容为空，请检查bind.html文件");
                        return;
                    }
                    
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
    
    
    
    // 更新玩家邮箱
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
                                // 取消提示任务
                                cancelReminderTask(playerUUID);
                                // 清理二维码地图（由TwoFactorAuth处理）
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
    
    
}