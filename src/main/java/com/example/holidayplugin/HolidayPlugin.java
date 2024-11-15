package com.example.holidayplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class HolidayPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private String serverVersion;
    private String nextHolidayName;
    private LocalDate nextHolidayDate;
    private int daysUntilNextHoliday;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("ny").setExecutor(this); // 注册/ny命令
        serverVersion = getServerVersion();
        getLogger().info("HolidayPlugin has been enabled! Running on version: " + serverVersion);

        // 在插件启动时加载下一个节日信息
        loadNextHolidayInfo();
    }

    @Override
    public void onDisable() {
        getLogger().info("HolidayPlugin has been disabled!");
    }

    // 在玩家加入时检查是否是节日并启动相关操作
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LocalDate currentDate = LocalDate.now();

        // 检查是否今天是节日或即将到来
        if (nextHolidayDate != null) {
            if (currentDate.isEqual(nextHolidayDate)) {
                // 当天是节日
                sendTitle(player, nextHolidayName, "快乐！");
            } else if (daysUntilNextHoliday <= 3 && daysUntilNextHoliday > 0) {
                // 仅在距离节日少于等于3天时提示玩家
                player.sendMessage(ChatColor.AQUA + "距离下一个节日 (" + nextHolidayName + ") 还有 " + daysUntilNextHoliday + " 天！");
                if (daysUntilNextHoliday == 1) {
                    // 如果明天是节日，提示玩家并准备倒计时
                    player.sendMessage(ChatColor.GOLD + "明天就是 " + nextHolidayName + " 了！准备好倒计时！");
                    startCountdown(player);
                }
            }
        }
    }

    // 加载下一个节日信息
    private void loadNextHolidayInfo() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // 使用API获取下一个节日信息
                URL url = new URL("https://date.appworlds.cn/next");
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                if (connection.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // 解析JSON并保存节日信息
                    parseHolidayJson(response.toString());

                    // 加载下一个节日的天数
                    loadNextHolidayDays();
                } else {
                    getLogger().warning("Failed to fetch holiday info: HTTP error code " + connection.getResponseCode());
                }
            } catch (Exception e) {
                getLogger().warning("Error occurred while fetching holiday info: " + e.getMessage());
            }
        });
    }

    // 加载下一个节日距离天数
    private void loadNextHolidayDays() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // 使用API获取距离下一个节日的天数
                URL url = new URL("https://date.appworlds.cn/next/days");
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                if (connection.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // 解析JSON并保存节日天数信息
                    parseHolidayDaysJson(response.toString());
                } else {
                    getLogger().warning("Failed to fetch holiday days info: HTTP error code " + connection.getResponseCode());
                }
            } catch (Exception e) {
                getLogger().warning("Error occurred while fetching holiday days info: " + e.getMessage());
            }
        });
    }

    // 解析节日信息
    private void parseHolidayJson(String jsonResponse) {
        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(jsonResponse);
            if (jsonObject.getInt("code") == 200) {
                org.json.JSONObject data = jsonObject.getJSONObject("data");
                String name = data.getString("name");
                String dateStr = data.getString("date");
                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                nextHolidayName = name;
                nextHolidayDate = date;

                getLogger().info("Successfully loaded next holiday: " + nextHolidayName + " on " + nextHolidayDate);
            } else {
                getLogger().warning("Failed to fetch next holiday: " + jsonObject.getString("msg"));
            }
        } catch (Exception e) {
            getLogger().warning("Failed to parse holiday JSON: " + e.getMessage());
        }
    }

    // 解析节日距离天数信息
    private void parseHolidayDaysJson(String jsonResponse) {
        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(jsonResponse);
            if (jsonObject.getInt("code") == 200) {
                org.json.JSONObject data = jsonObject.getJSONObject("data");
                daysUntilNextHoliday = data.getInt("days");

                getLogger().info("Successfully loaded days until next holiday: " + daysUntilNextHoliday);
            } else {
                getLogger().warning("Failed to fetch holiday days info: " + jsonObject.getString("msg"));
            }
        } catch (Exception e) {
            getLogger().warning("Failed to parse holiday days JSON: " + e.getMessage());
        }
    }

    // 向玩家发送Title消息
    private void sendTitle(Player player, String title, String subtitle) {
        try {
            if (isVersionAtLeast("v1_8")) {
                player.sendTitle(title, subtitle, 10, 70, 20);
            } else {
                player.sendMessage(ChatColor.GOLD + title + " " + subtitle); // 如果版本过旧，则用聊天信息代替
            }
        } catch (NoSuchMethodError e) {
            player.sendMessage(ChatColor.GOLD + title + " " + subtitle); // 如果有问题，用消息代替
        }
    }

    // 倒计时任务
    private void startCountdown(Player player) {
        new BukkitRunnable() {
            int countdown = 30; // 倒计时30秒

            @Override
            public void run() {
                if (countdown <= 0) {
                    // 倒计时结束，播放烟花并广播
                    playFireworks(player);
                    Bukkit.broadcastMessage(ChatColor.GOLD + nextHolidayName + " 快乐！🎉");
                    cancel();
                    return;
                }

                // 给玩家显示倒计时信息并播放音效
                try {
                    if (isVersionAtLeast("v1_8")) {
                        player.sendTitle(ChatColor.RED + "倒计时", countdown + "秒", 0, 20, 10);
                    } else {
                        player.sendMessage(ChatColor.RED + "倒计时: " + countdown + " 秒");
                    }

                    // 播放倒计时声音
                    if (countdown <= 10) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    } else if (countdown % 5 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }
                } catch (NoSuchMethodError e) {
                    player.sendMessage(ChatColor.RED + "倒计时: " + countdown + " 秒");
                }
                countdown--;
            }
        }.runTaskTimer(this, 0, 20); // 每20个tick更新一次倒计时
    }

    // 播放烟花效果
    private void playFireworks(Player player) {
        try {
            if (isVersionAtLeast("v1_9")) {
                player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation(), 10);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            } else if (isVersionAtLeast("v1_8")) {
                player.getWorld().playSound(player.getLocation(), Sound.valueOf("FIREWORK_LAUNCH"), 1.0f, 1.0f);
            } else {
                player.sendMessage(ChatColor.YELLOW + "你的服务器不支持烟花效果，祝你节日快乐！");
            }
        } catch (NoSuchFieldError | IllegalArgumentException e) {
            player.sendMessage(ChatColor.YELLOW + "你的服务器不支持烟花效果，祝你节日快乐！");
        }
    }

    // 获取服务器版本
    private String getServerVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    // 判断服务器版本是否至少是某个版本
    private boolean isVersionAtLeast(String version) {
        String currentVersion = getServerVersion();
        return currentVersion.compareTo(version) >= 0;
    }
}
