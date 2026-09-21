package dev.dsh.botcompat;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * 把服务端的注册表导出成 JSON，供标准协议客户端（机器人）离线加载。
 *
 * <p>为什么需要它：原版协议的客户端只带原版注册表，服务端却按「客户端和自己注册表一致」
 * 的前提直接下发数值 ID。模组方块的状态 ID 超出原版范围（1.21.1 原版共 26644 个状态），
 * 机器人拿到这些 ID 既不知道名字、也没有碰撞箱与硬度信息。服务端把这张表导出来，
 * 机器人就能把 ID 还原成「名字 + 形状 + 硬度」。
 *
 * <p>导出内容：
 * <ul>
 *   <li>{@code blocks}: 所有<b>非 minecraft 命名空间</b>的方块状态
 *       （id、方块名、状态名、是否空气、硬度、是否需要正确工具、是否流体、碰撞箱列表）</li>
 *   <li>{@code items}: 所有非原版物品（id、名字）</li>
 *   <li>{@code entity_types}: 所有非原版实体类型（id、名字）</li>
 * </ul>
 */
public final class RegistryDump {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 导出文件名（放在服务端根目录）。 */
    public static final String FILE_NAME = "botcompat-registry.json";

    private RegistryDump() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Path out = Path.of(FILE_NAME).toAbsolutePath();
        try {
            JsonObject root = buildDump();
            try (Writer writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
            LOGGER.info("[BotCompat] 注册表已导出: {}", out);
            LOGGER.info("[BotCompat] 方块状态 {} 项 / 模组物品 {} 项 / 实体 {} 项 / 可放置方块 {} 项",
                    root.getAsJsonArray("blocks").size(),
                    root.getAsJsonArray("items").size(),
                    root.getAsJsonArray("entity_types").size(),
                    root.getAsJsonArray("block_items").size());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[BotCompat] 注册表导出失败", e);
        }
    }

    private static JsonObject buildDump() {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("minecraft_version", "1.21.1");
        root.addProperty("generated_by", "botcompat");

        // ---- 方块状态 ----
        JsonArray blocks = new JsonArray();
        int vanillaMaxStateId = -1;
        for (Map.Entry<ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            Block block = entry.getValue();
            if (!entry.getKey().location().getNamespace().equals("minecraft")) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                vanillaMaxStateId = Math.max(vanillaMaxStateId, Block.getId(state));
            }
        }
        root.addProperty("vanilla_max_block_state_id", vanillaMaxStateId);

        for (Map.Entry<ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            if (entry.getKey().location().getNamespace().equals("minecraft")) {
                continue;
            }
            Block block = entry.getValue();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", Block.getId(state));
                o.addProperty("block", entry.getKey().location().toString());
                o.addProperty("state", state.toString());
                o.addProperty("air", state.isAir());
                o.addProperty("requires_tool", state.requiresCorrectToolForDrops());
                o.addProperty("fluid", !state.getFluidState().isEmpty());
                try {
                    o.addProperty("hardness", state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                } catch (RuntimeException e) {
                    o.addProperty("hardness", 1.0f);
                }
                JsonArray boxes = new JsonArray();
                try {
                    VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    for (AABB aabb : shape.toAabbs()) {
                        JsonArray box = new JsonArray();
                        box.add(round(aabb.minX));
                        box.add(round(aabb.minY));
                        box.add(round(aabb.minZ));
                        box.add(round(aabb.maxX));
                        box.add(round(aabb.maxY));
                        box.add(round(aabb.maxZ));
                        boxes.add(box);
                    }
                } catch (RuntimeException e) {
                    LOGGER.debug("[BotCompat] 取碰撞箱失败: {}", state);
                }
                o.add("boxes", boxes);
                blocks.add(o);
            }
        }
        root.add("blocks", blocks);

        // ---- 物品 ----
        JsonArray items = new JsonArray();
        for (Map.Entry<ResourceKey<net.minecraft.world.item.Item>, net.minecraft.world.item.Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            if (entry.getKey().location().getNamespace().equals("minecraft")) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", BuiltInRegistries.ITEM.getId(entry.getValue()));
            o.addProperty("name", entry.getKey().location().toString());
            items.add(o);
        }
        root.add("items", items);

        // ---- 实体类型 ----
        JsonArray entities = new JsonArray();
        for (Map.Entry<ResourceKey<net.minecraft.world.entity.EntityType<?>>, net.minecraft.world.entity.EntityType<?>> entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            if (entry.getKey().location().getNamespace().equals("minecraft")) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", BuiltInRegistries.ENTITY_TYPE.getId(entry.getValue()));
            o.addProperty("name", entry.getKey().location().toString());
            entities.add(o);
        }
        root.add("entity_types", entities);

        // ---- 方块 -> 能放置它的物品（BlockItem）----
        // 机器人要「放置模组方块」，必须先知道该方块对应哪个物品、进而找到快捷栏槽位。
        JsonArray blockItems = new JsonArray();
        for (Map.Entry<ResourceKey<net.minecraft.world.item.Item>, net.minecraft.world.item.Item> entry
                : BuiltInRegistries.ITEM.entrySet()) {
            if (entry.getValue() instanceof net.minecraft.world.item.BlockItem blockItem) {
                JsonObject o = new JsonObject();
                o.addProperty("item", entry.getKey().location().toString());
                o.addProperty("block", BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString());
                blockItems.add(o);
            }
        }
        root.add("block_items", blockItems);

        return root;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /** 供其它代码查询导出路径。 */
    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add(FILE_NAME);
        return out;
    }
}