package dev.dsh.botcompat.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * 让「没有协商出任何 mod 通道」的客户端不再被 {@code checkPacket} 抛异常踢掉。
 *
 * <p>{@code NetworkRegistry.checkPacket} 的规则是：准备发给客户端的自定义负载，
 * 如果既不是原版内建负载、命名空间也不是 {@code minecraft}、且
 * {@code hasChannel(...)} 为 false，就直接抛
 * {@code UnsupportedOperationException("Payload %s may not be sent to the client!")}。
 *
 * <p>对机器人这种「零通道」客户端，这个异常会被抛出在完全无关的地方——
 * 例如 <b>Sable</b> 模组在玩家加入时无条件发送 {@code sable:udp_activation}，
 * 异常在 {@code PlayerList.onPlayerJoin} 里冒泡，最终表现为
 * {@code "Couldn't place player in world"} + {@code Invalid player data}，
 * 机器人“登录成功”之后立刻被踢。
 *
 * <p>本 Mixin 只对<b>没有任何协商通道</b>的连接跳过这项检查：
 * 这类连接本来就不可能通过检查，跳过等价于“把该负载发出去，由客户端自行忽略”。
 * 真实的 NeoForge 客户端（有通道集合）完全不受影响，mod 不匹配时的报错仍然照常。
 */
@Mixin(NetworkRegistry.class)
public abstract class CheckPacketMixin {

    /** 服务端 -> 客户端方向。 */
    @Inject(
        method = "checkPacket(Lnet/minecraft/network/protocol/Packet;"
            + "Lnet/minecraft/network/protocol/common/ServerCommonPacketListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void botcompat$skipForChannelLessServer(
            Packet<?> packet, ServerCommonPacketListener listener, CallbackInfo ci) {
        if (botcompat$isChannelLess(listener.getConnection())) {
            ci.cancel();
        }
    }

    /** 客户端 -> 服务端方向（集成服/客户端侧，保持对称）。 */
    @Inject(
        method = "checkPacket(Lnet/minecraft/network/protocol/Packet;"
            + "Lnet/minecraft/network/protocol/common/ClientCommonPacketListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void botcompat$skipForChannelLessClient(
            Packet<?> packet, ClientCommonPacketListener listener, CallbackInfo ci) {
        if (botcompat$isChannelLess(listener.getConnection())) {
            ci.cancel();
        }
    }

    /**
     * 连接是否「一个 mod 通道都没有」。
     *
     * <p>只看协商出来的通道表；临时通道（adhoc）在机器人场景下也为空。
     */
    private static boolean botcompat$isChannelLess(Connection connection) {
        NetworkPayloadSetup setup = ChannelAttributes.getPayloadSetup(connection);
        if (setup == null) {
            return true;
        }
        return botcompat$empty(setup, ConnectionProtocol.CONFIGURATION)
            && botcompat$empty(setup, ConnectionProtocol.PLAY);
    }

    private static boolean botcompat$empty(NetworkPayloadSetup setup, ConnectionProtocol protocol) {
        Map<ResourceLocation, ?> channels = setup.getChannels(protocol);
        return channels == null || channels.isEmpty();
    }
}
