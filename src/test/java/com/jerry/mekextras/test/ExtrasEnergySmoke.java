package com.jerry.mekextras.test;

import com.jerry.mekextras.MekanismExtras;
import com.jerry.mekextras.common.registries.ExtraBlocks;
import com.jerry.mekextras.common.registries.ExtraFluids;
import com.jerry.mekextras.common.tile.TileEntityExtraEnergyCube;

import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.config.DataType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/** Runs only in the isolated development server, never in the released jar. */
@EventBusSubscriber(modid = MekanismExtras.MOD_ID)
public class ExtrasEnergySmoke {

    private static final Path STAGE_FILE = Path.of("extras-qa-stage.txt");
    private static final Path RESULT_FILE = Path.of("extras-qa-result.txt");

    private static final BlockPos CUBE_POS = new BlockPos(8, 121, 0);
    private static final BlockPos BASIC_POS = new BlockPos(9, 121, 0);
    private static final int CHARGE = 2_000_000;
    private static final long TRANSFER_TOTAL = 2_000;
    private static final int TRANSFER_PULL = 500;
    private static final int MAX_PULLS = 100;

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        MekanismExtras.LOGGER.info("EXTRAS QA PASS: {}", label);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("mekextras.extrasSmoke")) return;
        boolean phaseTwo = Files.exists(STAGE_FILE);
        try {
            var level = event.getServer().overworld();
            // Runtime chain asserts: our EMI-only distribution and the More Machine companion.
            require(ModList.get().isLoaded("emi"), "EMI is loaded in the QA runtime");
            require(ModList.get().isLoaded("mekmm"), "Mekanism: More Machine companion is loaded");
            require(!ModList.get().isLoaded("jei"), "JEI is absent from the EMI-only QA runtime");
            require(!ModList.get().isLoaded("kotlinforforge"), "KotlinForForge is absent from the Java-only QA runtime");
            require(ModList.get().getMods().stream().filter(info -> info.getModId().equals(MekanismExtras.MOD_ID)).count() == 1,
                    "exactly one mekanism_extras mod container is registered");
            // Registration asserts: extras block + chemical registered inside our mod namespace.
            var cubeBlock = ExtraBlocks.ABSOLUTE_ENERGY_CUBE.get();
            require(BuiltInRegistries.BLOCK.keySet().contains(Identifier.parse("mekanism_extras:absolute_energy_cube")),
                    "absolute energy cube is registered in the mekanism_extras namespace");
            require(ExtraFluids.RICH_NAQUADAH_FUEL.value() != null, "rich naquadah fuel liquid-chemical is registered");
            if (!phaseTwo) {
                // Clean fixture area, then place an extras energy cube and a vanilla cube next to it.
                for (var side : Direction.values()) {
                    level.setBlockAndUpdate(CUBE_POS.relative(side), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(BASIC_POS.relative(side), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(CUBE_POS, cubeBlock.defaultBlockState());
                level.setBlockAndUpdate(BASIC_POS, MekanismBlocks.BASIC_ENERGY_CUBE.get().defaultBlockState());
                var cube = level.getBlockEntity(CUBE_POS);
                require(cube instanceof TileEntityExtraEnergyCube, "extras cube block entity instantiates");
                var extrasCube = (TileEntityExtraEnergyCube) cube;
                var typed = extrasCube.getEnergyContainerTyped();
                require(typed.getAmountAsLong() == 0, "fresh extras cube starts empty");
                var basicCube = (TileEntityEnergyCube) level.getBlockEntity(BASIC_POS);
                require(basicCube.energyContainer().getAmountAsLong() == 0, "fresh vanilla cube starts empty");
                // Configure sides explicitly: the default side config keeps every side disabled.
                var config = extrasCube.getConfig().getConfig(TransmissionType.ENERGY);
                require(config != null, "extras cube exposes an ENERGY side config");
                require(config.setDataType(DataType.INPUT, RelativeSide.FRONT), "extras cube FRONT side accepts a wired INPUT role");
                require(config.setDataType(DataType.OUTPUT, RelativeSide.RIGHT), "extras cube RIGHT side accepts a wired OUTPUT role");
                var frontDir = RelativeSide.FRONT.getDirection(extrasCube.getDirection());
                var outputDir = RelativeSide.RIGHT.getDirection(extrasCube.getDirection());
                var input = level.getCapability(Capabilities.Energy.BLOCK, CUBE_POS, frontDir);
                require(input != null, "extras cube exposes a sided energy handler on the INPUT side");
                try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    int inserted = input.insert(CHARGE, transaction);
                    transaction.commit();
                    require(inserted == CHARGE, "sided insert accepted the whole charge: " + inserted);
                }
                var stored = typed.getAmountAsLong();
                require(stored == CHARGE, "extras cube stores the configured amount: " + stored);
                var basicConfig = basicCube.getConfig().getConfig(TransmissionType.ENERGY);
                require(basicConfig != null, "vanilla cube exposes an ENERGY side config");
                require(basicConfig.setDataType(DataType.INPUT, RelativeSide.FRONT), "vanilla cube FRONT side accepts a wired INPUT role");
                var basicDir = RelativeSide.FRONT.getDirection(basicCube.getDirection());
                var basicInput = level.getCapability(Capabilities.Energy.BLOCK, BASIC_POS, basicDir);
                require(basicInput != null, "vanilla cube exposes a sided energy handler on the INPUT side");
                // Energy transfer from the extras tier cube into a vanilla-tier cube through the
                // configured sides, respecting per-transaction rate limits.
                long moved = 0;
                for (int i = 0; i < MAX_PULLS && moved < TRANSFER_TOTAL; i++) {
                    var source = level.getCapability(Capabilities.Energy.BLOCK, CUBE_POS, outputDir);
                    require(source != null, "extras cube exposes a sided energy handler on the OUTPUT side (pull " + i + ")");
                    try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                        int pulled = source.extract(TRANSFER_PULL, transaction);
                        int pushed = pulled > 0 ? basicInput.insert(pulled, transaction) : 0;
                        if (pulled > 0 && pulled == pushed) {
                            transaction.commit();
                            moved += pushed;
                        }
                    }
                }
                require(moved == TRANSFER_TOTAL, "energy crossed capability boundaries between the two tiers: moved " + moved);
                require(typed.getAmountAsLong() == stored - moved, "extras cube lost exactly the transferred energy");
                require(basicCube.energyContainer().getAmountAsLong() == moved, "vanilla tier cube gained exactly the transferred energy");
                MekanismExtras.LOGGER.info("EXTRAS QA DIAGNOSTIC (pre-halt): extras amount={} basic amount={}",
                        typed.getAmountAsLong(), basicCube.energyContainer().getAmountAsLong());
                Files.writeString(STAGE_FILE, typed.getAmountAsLong() + "|" + basicCube.energyContainer().getAmountAsLong());
                Files.writeString(RESULT_FILE, "PHASE1\n");
                MekanismExtras.LOGGER.info("EXTRAS QA PHASE1 COMPLETE (transferred {} FE)", moved);
            } else {
                // Phase 2: read the tiles exactly as they were loaded from disk. Nothing is
                // re-placed or re-charged here, so the numbers prove disk persistence.
                var loadedCube = level.getBlockEntity(CUBE_POS);
                var loadedBasic = level.getBlockEntity(BASIC_POS);
                var stage = Files.readString(STAGE_FILE).trim().split("\\|", -1);
                var recordedCube = Long.parseLong(stage[0]);
                var recordedBasic = Long.parseLong(stage[1]);
                require(loadedCube instanceof TileEntityExtraEnergyCube, "extras cube block entity persisted");
                require(loadedBasic instanceof TileEntityEnergyCube, "vanilla cube block entity persisted");
                var persistedExtras = ((TileEntityExtraEnergyCube) loadedCube).getEnergyContainerTyped().getAmountAsLong();
                var persistedBasic = ((TileEntityEnergyCube) loadedBasic).energyContainer().getAmountAsLong();
                MekanismExtras.LOGGER.info("EXTRAS QA DIAGNOSTIC (loaded): extras amount={} basic amount={}",
                        persistedExtras, persistedBasic);
                require(persistedExtras == recordedCube, "extras cube amount survives a dedicated server restart");
                require(persistedBasic == recordedBasic, "vanilla cube amount survives a dedicated server restart");
                Files.writeString(RESULT_FILE, "PASS\n");
                MekanismExtras.LOGGER.info("EXTRAS QA PHASE2 COMPLETE (restart persistence verified)");
            }
        } catch (Throwable error) {
            MekanismExtras.LOGGER.error("EXTRAS QA FUNCTIONAL FAILED (phase two=" + phaseTwo + ")", error);
        } finally {
            event.getServer().halt(false);
        }
    }
}
