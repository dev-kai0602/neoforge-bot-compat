package dev.dsh.botcompat.mixin;

import java.util.Collections;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.configuration.CheckFeatureFlags;

/**
 * 清空服务端声明的自定义 FeatureFlags。
 *
 * <p>NeoForge 的第二道门槛在 {@link CheckFeatureFlags}：
 * 如果服务端存在自定义 FeatureFlags 而客户端没有通过 mod 渠道声明支持，
 * 服务端会以
 * {@code "This server does not support vanilla clients as it has custom FeatureFlags"}
 * 断开连接。这里让 {@code getModdedFeatureFlags()} 恒返回空集合，
 * 于是该检查走“没有自定义 FeatureFlags”的分支，直接完成配置任务。
 *
 * <p>副作用：真正使用自定义 FeatureFlags 的 mod，其特性标记不再参与协商。
 * 对无头机器人场景没有影响（本来也协商不了）。
 */
@Mixin(CheckFeatureFlags.class)
public abstract class CheckFeatureFlagsMixin {

    @ModifyReturnValue(method = "getModdedFeatureFlags", at = @At("RETURN"))
    private static Set<ResourceLocation> botcompat$clearModdedFeatureFlags(Set<ResourceLocation> original) {
        return Collections.emptySet();
    }
}
