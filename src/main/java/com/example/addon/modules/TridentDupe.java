/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.example.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TridentDupe extends Module {
    // Coded by Killet Laztec & Ionar :3
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("重复延迟")
        .description("每个复制周期之间的延迟,通常不需要增加")
        .defaultValue(0)
        .build()
    );

    private final Setting<Double> chargeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("充能延迟")
        .description("三叉戟充能与投掷之间的延迟。如果出现异常/卡顿可增加")
        .defaultValue(5)
        .build()
    );

    private final Setting<Boolean> dropTridents = sgGeneral.add(new BoolSetting.Builder()
        .name("掉落三叉戟")
        .description("将三叉戟放入你的最后一个快捷栏槽位")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> durabilityManagement = sgGeneral.add(new BoolSetting.Builder()
        .name("耐久性管理")
        .description("（适合挂机）尝试复制快捷栏中耐久度最高的三叉戟")
        .defaultValue(true)
        .build()
    );

    public TridentDupe() {
        super(com.example.addon.TridentDupe.CATEGORY, "三叉戟复制", "在第一个快捷栏中重复三叉戟/ / Killet / / Laztec / / Ionar");
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onSendPacket(PacketEvent.Send event) {

        if (event.packet instanceof ClientTickEndC2SPacket
            || event.packet instanceof PlayerMoveC2SPacket
            || event.packet instanceof CloseHandledScreenC2SPacket)
            return;

        if (!(event.packet instanceof ClickSlotC2SPacket)
            && !(event.packet instanceof PlayerActionC2SPacket))
        {
            return;
        }
        if (!cancel)
            return;

//        MutableText packetStr = Text.literal(event.packet.toString()).formatted(Formatting.WHITE);
//        System.out.println(packetStr);

        event.cancel();
    }

    @Override
    public void onActivate()
    {
        if (mc.player == null)
            return;

        scheduledTasks.clear();
        dupe();

    }

    private void dupe()
    {
        int lowestHotbarSlot = 0;
        int lowestHotbarDamage = 1000;
        for (int i = 0; i < 9; i++)
        {
            if (mc.player.getInventory().getStack((i)).getItem() == Items.TRIDENT || mc.player.getInventory().getStack((i)).getItem() == Items.BOW)
            {
                int currentHotbarDamage = mc.player.getInventory().getStack((i)).getDamage();
                if (lowestHotbarDamage > currentHotbarDamage) { lowestHotbarSlot = i; lowestHotbarDamage = currentHotbarDamage;}

            }
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        cancel = true;

        int finalLowestHotbarSlot = lowestHotbarSlot;
        scheduleTask(() -> {
            cancel = false;

            if(durabilityManagement.get()) {
                if(finalLowestHotbarSlot != 0) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, (44), 0, SlotActionType.SWAP, mc.player);
                    if(dropTridents.get())mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 44, 0, SlotActionType.THROW, mc.player);
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, (36 + finalLowestHotbarSlot), 0, SlotActionType.SWAP, mc.player);
                }
            }

            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 3, 0, SlotActionType.SWAP, mc.player);

            PlayerActionC2SPacket packet2 = new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, 0);
            mc.getNetworkHandler().sendPacket(packet2);

            if(dropTridents.get()) mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 44, 0, SlotActionType.THROW, mc.player);

            cancel = true;
            scheduleTask2(this::dupe, delay.get() * 100);
        }, chargeDelay.get() * 100);
    }


    private boolean cancel = true;

    private final List<Pair<Long, Runnable>> scheduledTasks = new ArrayList<>();
    private final List<Pair<Double, Runnable>> scheduledTasks2 = new ArrayList<>();

    public void scheduleTask(Runnable task, double tridentThrowTime) {
        // throw trident
        long executeTime = System.currentTimeMillis() + (int) tridentThrowTime;
        scheduledTasks.add(new Pair<>(executeTime, task));
    }

    public void scheduleTask2(Runnable task, double delayMillis) {
        // dupe loop
        double executeTime = System.currentTimeMillis() + delayMillis;
        scheduledTasks2.add(new Pair<>(executeTime, task));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        long currentTime = System.currentTimeMillis();
        {
            Iterator<Pair<Long, Runnable>> iterator = scheduledTasks.iterator();

            while (iterator.hasNext()) {
                Pair<Long, Runnable> entry = iterator.next();
                if (entry.getLeft() <= currentTime) {
                    entry.getRight().run();
                    iterator.remove(); // Remove executed task from the list
                }
            }
        }

        {
            Iterator<Pair<Double, Runnable>> iterator = scheduledTasks2.iterator();

            while (iterator.hasNext()) {
                Pair<Double, Runnable> entry = iterator.next();
                if (entry.getLeft() <= currentTime) {
                    entry.getRight().run();
                    iterator.remove(); // Remove executed task from the list
                }
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        toggle();
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen) {
            toggle();
        }
    }
}
