package dev.dsh.botcompat;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * BotCompat —— 允许标准协议客户端（Azalea / Mineflayer 等无头机器人）连接 NeoForge 服务器。
 *
 * <p>NeoForge 在配置阶段要求客户端完成 mod 网络通道协商，标准客户端没有 mod loader，
 * 协商必然失败，服务端随即以
 * {@code neoforge.network.negotiation.failure.vanilla.client.not_supported} 断开连接。
 * 本模组在服务端把这一判定改写为“协商成功”，并清空自定义 FeatureFlags，
 * 从而让原版协议客户端可以进入服务器。
 *
 * <p><b>这不会让机器人获得模组内容。</b>机器人仍然只能识别原版方块、物品与实体；
 * 模组新增内容会以原版默认值（通常是 air）呈现。这样做只是把“连不上”变成“能连上，
 * 但看不到模组内容”。
 *
 * <p>授权：GPL-3.0-or-later。
 */
@Mod(BotCompat.MOD_ID)
public final class BotCompat {
    public static final String MOD_ID = "botcompat";

    private static final Logger LOGGER = LogUtils.getLogger();

    public BotCompat(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.warn("[BotCompat] 已加载：NeoForge 网络协商将被放行，标准协议客户端（机器人）可以连接本服务器。");
        LOGGER.warn("[BotCompat] 注意：机器人只能识别原版方块与物品，模组内容不可见。");
    }
}
