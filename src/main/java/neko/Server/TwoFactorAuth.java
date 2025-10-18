package neko.Server;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapCanvas;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.image.BufferedImage;

// Google Authenticator
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;

// ZXing库
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class TwoFactorAuth {
    private final EmailBind plugin;
    
    // 2FA配置信息
    private String twofaPermission;
    private String twofaTable;
    
    // Google Authenticator
    private GoogleAuthenticator gAuth;
    
    // 存储需要2FA验证的玩家
    private Map<UUID, Boolean> playersRequiring2FA = new ConcurrentHashMap<>();
    // 存储正在等待2FA验证码输入的玩家
    private Map<UUID, Boolean> playersWaitingFor2FA = new ConcurrentHashMap<>();
    // 存储玩家的2FA二维码地图视图
    private Map<UUID, MapView> playerQRCodeMaps = new ConcurrentHashMap<>();
    
    public TwoFactorAuth(EmailBind plugin) {
        this.plugin = plugin;
        this.gAuth = new GoogleAuthenticator();
    }
    
    // 初始化2FA配置
    public void loadConfig() {
        twofaPermission = plugin.getConfig().getString("twofa.permission", "emailbind.twofa");
        twofaTable = plugin.getConfig().getString("twofa.table", "twofa_secrets");
    }
    
    // 检查玩家是否需要2FA验证
    public void checkPlayer2FARequirement(Player player) {
        // 检查玩家是否拥有2FA权限
        if (player.hasPermission(twofaPermission)) {
            UUID playerUUID = player.getUniqueId();
            playersRequiring2FA.put(playerUUID, true);
            
            // 检查玩家是否已经有2FA密钥
            if (!has2FAKey(player)) {
                // 玩家还没有2FA密钥，需要设置
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        setup2FA(player);
                    }
                }.runTask(plugin);
            } else {
                // 玩家已经有2FA密钥，需要验证
                playersWaitingFor2FA.put(playerUUID, true);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                        player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §d★ 检测到您需要进行双重身份验证 ★");
                        player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §b★ 请输入Google Authenticator验证码 ★");
                        player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §a★ 以完成双重身份验证喵～ ★");
                        player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    }
                }.runTask(plugin);
            }
        }
    }
    
    // IP变化时强制2FA验证
    public void force2FAForIPChange(Player player) {
        // 检查玩家是否拥有2FA权限
        if (player.hasPermission(twofaPermission)) {
            UUID playerUUID = player.getUniqueId();
            // 检查玩家是否已经有2FA密钥
            if (has2FAKey(player)) {
                // 玩家已经有2FA密钥，需要验证
                playersWaitingFor2FA.put(playerUUID, true);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                        player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §d★ 检测到您的登录IP发生变化 ★");
                        player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §b★ 请输入Google Authenticator验证码 ★");
                        player.sendMessage(ChatColor.RED + "§l§o[猫娘提醒] §a★ 以完成双重身份验证喵～ ★");
                        player.sendMessage(ChatColor.RED + "§l----------------------------------------");
                    }
                }.runTask(plugin);
            }
        }
    }
    
    // 检查玩家是否有2FA密钥
    private boolean has2FAKey(Player player) {
        try {
            Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + plugin.getHost() + ":" + plugin.getPort() + "/" + plugin.getDatabase(), 
                plugin.getUsername(), 
                plugin.getPassword()
            );
            
            PreparedStatement statement = connection.prepareStatement(
                "SELECT secret FROM " + twofaTable + " WHERE username = ?"
            );
            statement.setString(1, player.getName());
            
            ResultSet result = statement.executeQuery();
            boolean hasKey = result.next() && result.getString("secret") != null;
            
            connection.close();
            return hasKey;
        } catch (SQLException e) {
            plugin.getLogger().severe("数据库查询出错: " + e.getMessage());
            return false;
        }
    }
    
    // 设置玩家2FA
    private void setup2FA(Player player) {
        // 生成新的2FA密钥
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();
        
        // 保存密钥到数据库
        save2FAKey(player, secret);
        
        // 生成二维码URL
        String qrUrl = generateQRCodeURL(player.getName(), secret);
        
        // 在游戏内显示二维码地图
        showQRCodeMap(player, qrUrl);
        
        // 发送设置信息给玩家
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.GREEN + "§l----------------------------------------");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §d★ 检测到您需要进行双重身份验证 ★");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §b★ 请在Google Authenticator中添加以下账户 ★");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §a★ 账户名: " + player.getName() + " ★");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §a★ 密钥: " + secret + " ★");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §c★ 二维码书本已添加到您的物品栏 ★");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §c★ 右键点击书本查看二维码并扫描 ★");
                player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §c★ 设置完成后输入验证码完成绑定 ★");
                player.sendMessage(ChatColor.GREEN + "§l----------------------------------------");
            }
        }.runTask(plugin);
    }
    
    // 生成Google Authenticator二维码URL
    private String generateQRCodeURL(String playerName, String secret) {
        // Google Authenticator二维码URL格式
        String issuer = "喵之国度";
        try {
            String url = "otpauth://totp/" + issuer + ":" + playerName + 
                        "?secret=" + secret + 
                        "&issuer=" + issuer;
            return url;
        } catch (Exception e) {
            plugin.getLogger().severe("生成二维码URL失败: " + e.getMessage());
            return "";
        }
    }
    
    // 生成二维码图像
    private BufferedImage generateQRCodeImage(String text, int width, int height) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return image;
    }
    
    // 显示二维码（使用书本显示）
    private void showQRCodeMap(Player player, String qrUrl) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // 生成二维码图像（较小尺寸以便转换为文本）
                    BufferedImage qrImage = generateQRCodeImage(qrUrl, 25, 25);
                    
                    // 转换为文本形式的二维码
                    StringBuilder qrText = new StringBuilder();
                    qrText.append("请使用Google Authenticator扫描以下二维码:\n\n");
                    
                    // 添加顶部边框
                    qrText.append("██");
                    for (int x = 0; x < qrImage.getWidth() + 2; x++) {
                        qrText.append("██");
                    }
                    qrText.append("██\n");
                    
                    // 转换图像为文本
                    for (int y = 0; y < qrImage.getHeight(); y++) {
                        qrText.append("██"); // 左边框
                        for (int x = 0; x < qrImage.getWidth(); x++) {
                            int rgb = qrImage.getRGB(x, y);
                            // 检查是否为黑色像素
                            if ((rgb & 0xFFFFFF) == 0) {
                                qrText.append("██"); // 黑色像素
                            } else {
                                qrText.append("  "); // 白色像素
                            }
                        }
                        qrText.append("██\n"); // 右边框
                    }
                    
                    // 添加底部边框
                    qrText.append("██");
                    for (int x = 0; x < qrImage.getWidth() + 2; x++) {
                        qrText.append("██");
                    }
                    qrText.append("██\n\n");
                    
                    // 添加配置信息
                    String secret = qrUrl.split("secret=")[1].split("&")[0];
                    qrText.append("如果无法扫描二维码，请手动添加以下信息:\n");
                    qrText.append("账户名: ").append(player.getName()).append("\n");
                    qrText.append("密钥: ").append(secret).append("\n");
                    qrText.append("类型: TOTP\n算法: SHA1\n位数: 6\n间隔: 30秒");
                    
                    // 创建书本物品
                    ItemStack book = new ItemStack(org.bukkit.Material.WRITTEN_BOOK);
                    org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
                    bookMeta.setTitle("2FA二维码");
                    bookMeta.setAuthor("喵之国度");
                    bookMeta.addPage(qrText.toString());
                    book.setItemMeta(bookMeta);
                    
                    // 给玩家书本
                    player.getInventory().addItem(book);
                    
                    // 发送提示信息
                    player.sendMessage(ChatColor.GREEN + "§l----------------------------------------");
                    player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §d★ 2FA设置信息 ★");
                    player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §b★ 二维码书本已添加到您的物品栏 ★");
                    player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §a★ 右键点击书本查看二维码并扫描 ★");
                    player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §a★ 或在聊天框查看配置信息手动添加 ★");
                    player.sendMessage(ChatColor.GREEN + "§l----------------------------------------");
                    
                    // 同时也发送配置信息到聊天框
                    player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §c★ 账户名: " + player.getName() + " ★");
                    player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §c★ 密钥: " + secret + " ★");
                    
                    // 也尝试创建地图物品（如果可用）
                    try {
                        // 创建地图视图
                        MapView mapView = Bukkit.createMap(player.getWorld());
                        
                        // 移除默认渲染器
                        mapView.getRenderers().clear();
                        
                        // 添加自定义渲染器
                        mapView.addRenderer(new QRCodeMapRenderer(qrImage));
                        
                        // 创建地图物品
                        ItemStack mapItem = new ItemStack(org.bukkit.Material.FILLED_MAP);
                        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
                        mapMeta.setMapView(mapView);
                        mapMeta.setDisplayName(ChatColor.GREEN + "2FA二维码");
                        mapItem.setItemMeta(mapMeta);
                        
                        // 给玩家地图物品
                        player.getInventory().setItemInMainHand(mapItem);
                        
                        // 存储地图视图以便后续清理
                        playerQRCodeMaps.put(player.getUniqueId(), mapView);
                        
                        // 告知玩家地图已提供
                        player.sendMessage(ChatColor.GREEN + "§l§o[猫娘提醒] §c★ 地图物品已放在您的手中（如果服务器支持） ★");
                    } catch (Exception e) {
                        // 如果地图不工作，只提供书本和配置信息
                        plugin.getLogger().warning("地图显示不可用，使用书本显示二维码: " + e.getMessage());
                    }
                    
                } catch (WriterException e) {
                    plugin.getLogger().severe("生成二维码失败: " + e.getMessage());
                }
            }
        }.runTaskLater(plugin, 20L); // 延迟1秒执行，确保玩家完全加入游戏
    }
    
    // 保存2FA密钥到数据库
    private void save2FAKey(Player player, String secret) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Connection connection = DriverManager.getConnection(
                        "jdbc:mysql://" + plugin.getHost() + ":" + plugin.getPort() + "/" + plugin.getDatabase(), 
                        plugin.getUsername(), 
                        plugin.getPassword()
                    );
                    
                    // 创建表（如果不存在）
                    PreparedStatement createTable = connection.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS " + twofaTable + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "username VARCHAR(255) NOT NULL UNIQUE, " +
                        "secret VARCHAR(255) NOT NULL, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
                    );
                    createTable.executeUpdate();
                    
                    // 插入或更新密钥
                    PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO " + twofaTable + " (username, secret) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE secret = ?"
                    );
                    statement.setString(1, player.getName());
                    statement.setString(2, secret);
                    statement.setString(3, secret);
                    
                    statement.executeUpdate();
                    
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().severe("数据库更新出错: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    // 验证2FA验证码
    public boolean verify2FA(Player player, String code) {
        try {
            Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + plugin.getHost() + ":" + plugin.getPort() + "/" + plugin.getDatabase(), 
                plugin.getUsername(), 
                plugin.getPassword()
            );
            
            PreparedStatement statement = connection.prepareStatement(
                "SELECT secret FROM " + twofaTable + " WHERE username = ?"
            );
            statement.setString(1, player.getName());
            
            ResultSet result = statement.executeQuery();
            
            if (result.next()) {
                String secret = result.getString("secret");
                int codeValue = Integer.parseInt(code);
                boolean isValid = gAuth.authorize(secret, codeValue);
                
                connection.close();
                return isValid;
            }
            
            connection.close();
            return false;
        } catch (SQLException | NumberFormatException e) {
            plugin.getLogger().severe("2FA验证出错: " + e.getMessage());
            return false;
        }
    }
    
    // 清理二维码地图
    public void cleanupQRCodeMap(UUID playerUUID) {
        MapView mapView = playerQRCodeMaps.get(playerUUID);
        if (mapView != null) {
            // 移除自定义渲染器
            mapView.getRenderers().clear();
            mapView.addRenderer(new MapRenderer() {
                @Override
                public void render(MapView map, MapCanvas canvas, Player player) {
                    // 清空地图
                }
            });
            playerQRCodeMaps.remove(playerUUID);
        }
    }
    
    // 获取需要2FA验证的玩家
    public Map<UUID, Boolean> getPlayersRequiring2FA() {
        return playersRequiring2FA;
    }
    
    // 获取正在等待2FA验证码输入的玩家
    public Map<UUID, Boolean> getPlayersWaitingFor2FA() {
        return playersWaitingFor2FA;
    }
    
    // 移除玩家的2FA状态
    public void removePlayer2FA(UUID playerUUID) {
        playersRequiring2FA.remove(playerUUID);
        playersWaitingFor2FA.remove(playerUUID);
        cleanupQRCodeMap(playerUUID);
    }
    
    // 检查玩家是否正在设置2FA（而不是验证2FA）
    public boolean isSettingUp2FA(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) {
            return false;
        }
        // 如果玩家需要2FA但没有密钥，则正在设置2FA
        // 如果玩家需要2FA且有密钥但在等待验证，则正在验证2FA
        return playersRequiring2FA.containsKey(playerUUID) && 
               !has2FAKey(player);
    }
    
    // 自定义地图渲染器类
    private class QRCodeMapRenderer extends MapRenderer {
        private final BufferedImage qrImage;
        private boolean rendered = false;
        
        public QRCodeMapRenderer(BufferedImage qrImage) {
            this.qrImage = qrImage;
        }
        
        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (rendered) return;
            
            try {
                // 在地图上绘制二维码
                canvas.drawImage(0, 0, qrImage);
                rendered = true;
            } catch (Exception e) {
                plugin.getLogger().severe("渲染二维码地图时出错: " + e.getMessage());
            }
        }
    }
}