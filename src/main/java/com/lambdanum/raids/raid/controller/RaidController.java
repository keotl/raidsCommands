package com.lambdanum.raids.raid.controller;

import com.lambdanum.raids.application.PlayerTeleportService;
import com.lambdanum.raids.infrastructure.injection.ComponentLocator;
import com.lambdanum.raids.infrastructure.utils.minecraft.MinecraftBroadcastLogger;
import com.lambdanum.raids.infrastructure.utils.minecraft.RegionCloner;
import com.lambdanum.raids.model.Raid;
import com.lambdanum.raids.raid.controller.objective.ObjectivePoller;
import com.lambdanum.raids.raid.controller.objective.ObjectiveSubscriber;
import com.lambdanum.raids.raid.controller.party.Party;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

public class RaidController implements ObjectiveSubscriber {

    private final MinecraftBroadcastLogger logger = ComponentLocator.INSTANCE.get(MinecraftBroadcastLogger.class);
    private final RegionCloner regionCloner = ComponentLocator.INSTANCE.get(RegionCloner.class);
    private final PlayerTeleportService teleportService = ComponentLocator.INSTANCE.get(PlayerTeleportService.class);

    private Raid raid;
    private final int dimension;
    private Party party;

    private List<ObjectivePoller> objectivePollers = new ArrayList<>();

    private RaidStatus status = RaidStatus.STARTING_UP;

    public RaidController(Raid raid, int playDimension, Party party) {
        this.raid = raid;
        this.dimension = playDimension;
        this.party = party;

        System.out.println("Initialized raidController on dimension :" + dimension);
    }

    public boolean isRaidActive() {
        return status == RaidStatus.STARTING_UP || party.areAllMembersInDimension(dimension);
    }

    public void startMapInitialization(MinecraftServer minecraftServer) {

            WorldServer targetWorld = minecraftServer.getWorld(dimension);
            WorldServer cleanRaidMap = minecraftServer.getWorld(raid.backupDimension);

            party.setGameType(GameType.SPECTATOR);
            party.teleportAllPlayers(teleportService, dimension, raid.spawn);


            regionCloner.cloneRegionToOtherDimension(cleanRaidMap, targetWorld, raid.region);
            logger.log("done initializing raid map '" + raid.name + "' on dimension " + dimension);

            party.setGameType(GameType.ADVENTURE);
            party.teleportAllPlayers(teleportService, dimension, raid.spawn);

            logger.log("teleported players to play dimension " + dimension);
            status = RaidStatus.RUNNING;

            executeStartupScript(minecraftServer, raid.startupScript);
    }

    private void executeStartupScript(MinecraftServer minecraftServer, String[] startupScript) {
        for (String command : startupScript) {
            minecraftServer.commandManager.executeCommand(minecraftServer, command);
        }
    }

    @Override
    public void notifyOnWatchedCondition() {
        // Something has happened inside this raid.
        logger.log("something has happened");
    }

    public String getRaidName() {
        return raid.name;
    }

    public void addObjective(ObjectivePoller objective) {
        objectivePollers.add(objective);
        new Thread(objective).start();
    }
}
