package neko.Server;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EmailBind extends JavaPlugin implements Listener {

    // 数据库连接信息
    private String host = "localhost";
    private int port = 3306;
    private String database = "authme";
    private String username = "authme";
    private String password = "12345";
    private String table = "authme";
    
    // 存储未绑定邮箱的玩家
    private Map<UUID, Boolean> unbindedPlayers = new HashMap<>();
    
    // 邮箱绑定GUI
    private Inventory bindEmailInventory;
    
    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        loadConfig();
        
        // 初始化数据库
        initializeDatabase();
        
        // 初始化GUI
        initializeGUI();
        
        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // 启动定时检查任务
        startCheckTask();
        
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
    }
    
    // 初始化数据库连接
    private void initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            getLogger().severe("找不到MySQL驱动: " + e.getMessage());
        }
    }
    
    // 初始化GUI
    private void initializeGUI() {
        bindEmailInventory = Bukkit.createInventory(null, 27, ChatColor.RED + "请绑定邮箱");
        
        // 创建说明物品
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "邮箱绑定说明");
        infoItem.setItemMeta(infoMeta);
        bindEmailInventory.setItem(13, infoItem);
        
        // 创建绑定按钮
        ItemStack bindItem = new ItemStack(Material.EMERALD);
        ItemMeta bindMeta = bindItem.getItemMeta();
        bindMeta.setDisplayName(ChatColor.GREEN + "点击绑定邮箱");
        bindItem.setItemMeta(bindMeta);
        bindEmailInventory.setItem(22, bindItem);
    }
    
    // 启动定时检查任务
    private void startCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerEmail(player);
                }
            }
        }.runTaskTimer(this, 0L, 600L); // 每30秒检查一次
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
                            unbindedPlayers.put(player.getUniqueId(), true);
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.openInventory(bindEmailInventory);
                                }
                            }.runTask(EmailBind.this);
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
        unbindedPlayers.remove(player.getUniqueId());
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
    
    // 玩家聊天事件
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (unbindedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "请先绑定邮箱才能进行此操作!");
        }
    }
    
    // 玩家命令事件
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (unbindedPlayers.containsKey(player.getUniqueId())) {
            String message = event.getMessage().substring(1); // 移除 '/'
            // 允许某些命令如邮箱绑定命令
            if (!message.startsWith("bindemail")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "请先绑定邮箱才能使用命令!");
            }
        }
    }
    
    // GUI点击事件
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().equals(bindEmailInventory)) {
            event.setCancelled(true);
            
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            
            if (clickedItem != null && clickedItem.getType() == Material.EMERALD) {
                // 点击绑定按钮
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "请使用命令 /bindemail <邮箱地址> 来绑定邮箱!");
            }
        }
        
        // 阻止未绑定玩家操作背包
        Player player = (Player) event.getWhoClicked();
        if (unbindedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    // GUI拖拽事件
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().equals(bindEmailInventory)) {
            event.setCancelled(true);
        }
        
        // 阻止未绑定玩家操作背包
        Player player = (Player) event.getWhoClicked();
        if (unbindedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    // 处理命令
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bindemail")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以使用此命令!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "用法: /bindemail <邮箱地址>");
                return true;
            }
            
            String email = args[0];
            
            // 简单验证邮箱格式
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                player.sendMessage(ChatColor.RED + "邮箱格式不正确!");
                return true;
            }
            
            // 更新数据库中的邮箱
            updatePlayerEmail(player, email);
            return true;
        }
        
        return false;
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
                                unbindedPlayers.remove(player.getUniqueId());
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