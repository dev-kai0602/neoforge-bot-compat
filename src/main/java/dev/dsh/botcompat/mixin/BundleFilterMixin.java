package dev.dsh.botcompat.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.Lists;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.Packet;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * 避免「过滤后空 bundle」导致连接被编码器异常打断。
 *
 * <p>NeoForge 在把一组包（bundle）发往客户端前，会用
 * {@code NetworkRegistry.filterGameBundlePackets} 过滤掉客户端没有协商过的 mod 负载。
 * 对机器人这种零通道客户端，<b>整个 bundle 会被过滤成空列表</b>，
 * netty 侧随后抛出：
 * <pre>
 * io.netty.handler.codec.EncoderException: PacketBundleUnpacker must produce at least one message.
 * </pre>
 * 客户端看到的则是 {@code disconnect.genericReason}：
 * {@code "Internal Exception: ... PacketBundleUnpacker must produce at least one message."}
 *
 * <p>本 Mixin 在过滤结果为空的<b>且原始 bundle 非空</b>时，退回到未过滤的原始列表：
 * 让 bundle 保持非空，多出来的 mod 负载由标准客户端自行忽略。
 * 真实的 NeoForge 客户端（有通道集合）过滤结果通常非空，行为不变。
 */
@Mixin(NetworkRegistry.class)
public abstract class BundleFilterMixin {

    @Inject(method = "filterGameBundlePackets", at = @At("RETURN"), cancellable = true)
    private static void botcompat$keepBundleNonEmpty(
            ChannelHandlerContext context,
            Iterable<Packet<?>> packets,
            CallbackInfoReturnable<List<Packet<?>>> cir) {
        List<Packet<?>> filtered = cir.getReturnValue();
        if (filtered != null && filtered.isEmpty()) {
            List<Packet<?>> original = Lists.newArrayList(packets.iterator());
            if (!original.isEmpty()) {
                cir.setReturnValue(original);
            }
        }
    }
}
