package dev.dsh.botcompat.mixin;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import net.neoforged.neoforge.network.negotiation.NegotiationResult;
import net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * 让「一个 mod 通道都没有」的客户端通过 NeoForge 的网络协商。
 *
 * <p>NeoForge 有两条初始化路径，两条都会因为客户端没有 mod 通道而断开：
 * <ul>
 *   <li>{@code initializeOtherConnection} —— 判定客户端不是 NeoForge 客户端时，
 *       以 {@code neoforge.network.negotiation.failure.vanilla.client.not_supported} 断开</li>
 *   <li>{@code initializeNeoForgeConnection} —— 拿到的客户端通道集合为空时，
 *       以 {@code multiplayer.disconnect.incompatible}（Incompatible client! Please use NeoForge X）断开</li>
 * </ul>
 *
 * <p>本 Mixin 把这两处的 {@code NetworkComponentNegotiator.negotiate(...)} 调用结果改写为
 * 「成功、零通道」，条件是<b>客户端确实没有声明任何通道</b>。
 *
 * <p>之所以加这个条件，是为了不破坏真实 NeoForge 客户端之间的行为：
 * 两个 NeoForge 客户端之间如果 mod 不匹配，客户端通道集合非空，此时本 Mixin 原样返回真实协商结果，
 * 服务端仍会正常报出 mod 不匹配。只有「完全没有 mod 通道」的客户端（机器人 / 原版协议客户端）
 * 才会被放行。
 */
@Mixin(NetworkRegistry.class)
public abstract class NetworkRegistryMixin {

    /** 非 NeoForge 客户端路径。 */
    @Redirect(
        method = "initializeOtherConnection",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/negotiation/NetworkComponentNegotiator;"
                + "negotiate(Ljava/util/List;Ljava/util/List;)"
                + "Lnet/neoforged/neoforge/network/negotiation/NegotiationResult;"
        )
    )
    private static NegotiationResult botcompat$otherPath(
            List<NegotiableNetworkComponent> serverComponents,
            List<NegotiableNetworkComponent> clientComponents) {
        return botcompat$allowChannelLessClient(serverComponents, clientComponents);
    }

    /** NeoForge 客户端路径（客户端给出空通道集合时同样放行）。 */
    @Redirect(
        method = "initializeNeoForgeConnection",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/negotiation/NetworkComponentNegotiator;"
                + "negotiate(Ljava/util/List;Ljava/util/List;)"
                + "Lnet/neoforged/neoforge/network/negotiation/NegotiationResult;"
        )
    )
    private static NegotiationResult botcompat$neoForgePath(
            List<NegotiableNetworkComponent> serverComponents,
            List<NegotiableNetworkComponent> clientComponents) {
        return botcompat$allowChannelLessClient(serverComponents, clientComponents);
    }

    /**
     * 客户端没有任何通道时，视为协商成功且不建立任何通道。
     *
     * <p>其余情况原样返回真实协商结果，保证真实 NeoForge 客户端的 mod 校验不受影响。
     */
    private static NegotiationResult botcompat$allowChannelLessClient(
            List<NegotiableNetworkComponent> serverComponents,
            List<NegotiableNetworkComponent> clientComponents) {
        NegotiationResult real = NetworkComponentNegotiator.negotiate(serverComponents, clientComponents);
        if (real.success() || !clientComponents.isEmpty()) {
            return real;
        }
        return new NegotiationResult(List.of(), true, Map.<ResourceLocation, Component>of());
    }
}
