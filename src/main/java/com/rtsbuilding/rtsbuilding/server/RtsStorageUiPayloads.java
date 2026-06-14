package com.rtsbuilding.rtsbuilding.server;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintMessage;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePageMessage;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession.PageResult;

/**
 * 存储 UI 交互的 Payload 发送工具类。
 * 1.7.10 版 —— 将 S2C 消息转换为 IMessage 发送。
 */
public final class RtsStorageUiPayloads {

    private static final int STORAGE_PAGE_SIZE = 88; // 8×11 网格

    private RtsStorageUiPayloads() {}

    public static void sendStoragePage(EntityPlayerMP player, int page, Object storageData) {
        RtsStorageSession session;
        if (storageData instanceof RtsStorageSession) {
            session = (RtsStorageSession) storageData;
        } else {
            session = RtsStorageManager.getSession(player);
        }

        // 获取分页数据
        PageResult pageResult = session.queryPage("name_asc", "all", "", page, STORAGE_PAGE_SIZE);

        // 转换为 ItemStack 列表
        List<ItemStack> stacks = session.toItemStacks(pageResult.items);

        RtsNetworkManager.NETWORK
            .sendTo(new S2CRtsStoragePageMessage(pageResult.page, pageResult.totalPages, 0, stacks), player);

        RtsbuildingMod.LOGGER.debug(
            "sendStoragePage: page {}/{} sent to {}, {} items",
            pageResult.page + 1,
            pageResult.totalPages,
            player.getDisplayName(),
            stacks.size());
    }

    public static void sendRemoteMenuHint(EntityPlayerMP player, boolean open) {
        if (!open) {
            // 关闭远程菜单：坐标 -1 表示关闭
            RtsNetworkManager.NETWORK.sendTo(new S2CRtsRemoteMenuHintMessage(-1, -1, -1), player);
            return;
        }

        // 获取玩家链接存储的坐标
        RtsStorageSession session = RtsStorageManager.getSession(player);
        if (session.isAe2Linked()) {
            RtsNetworkManager.NETWORK.sendTo(
                new S2CRtsRemoteMenuHintMessage(session.getLinkedX(), session.getLinkedY(), session.getLinkedZ()),
                player);
            RtsbuildingMod.LOGGER.debug("sendRemoteMenuHint: sent AE2 link hint for {}", player.getDisplayName());
        }
    }
}
