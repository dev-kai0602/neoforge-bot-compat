package dev.dsh.botcompat;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * BotCompat —— 让标准协议客户端（Azalea / Mineflayer 等无头机器人）连接 NeoForge 服务器。
 *
 * <p>做两件事：
 * <ol>
 *   <li><b>放行协议层</b>：NeoForge 在多个位置拒绝没有 mod 通道的客户端（网络协商、自定义
 *       FeatureFlags、负载发送校验、bundle 过滤），见 {@code mixin} 包下的四个 Mixin。</li>
 *   <li><b>导出注册表</b>：见 {@link RegistryDump}。机器人拿到
 *       {@code botcompat-registry.json} 后，就能把超出原版范围的方块状态 ID
 *       还原成名字、碰撞箱与硬度，从而真正“看懂”模组方块。</li>
 * </ol>
 *
 * <p>授权：GPL-3.0-or-later。
 */
@Mod(BotCompat.MOD_ID)
public final class BotCompat {
    public static final String MOD_ID = "botcompat";

    private static final Logger LOGGER = LogUtils.getLogger();

    public BotCompat(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(RegistryDump.class);
        LOGGER.warn("[BotCompat] 已加载：放行标准协议客户端，并会在服务端启动后导出注册表。");
    }
}
