package com.tio.instaff.protection;

import com.tio.instaff.InStaff;
import com.tio.instaff.config.InStaffConfig;
import com.tio.instaff.util.FileStorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Defensive crash interception handler for corrupted ticking entities and block entities.
 * Safely strips failing objects, logs incident telemetry to instaff/quarantine.log,
 * and guarantees zero recursive exceptions or server crash propagation.
 */
public final class ChunkQuarantineHandler {

    private static final String LOG_FILE = "quarantine.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOG_LOCK = new Object();

    private ChunkQuarantineHandler() {
    }

    private static Path getLogPath() {
        return FileStorageUtil.getDataDirectory().resolve(LOG_FILE);
    }

    /**
     * Safely quarantines and discards a corrupted entity.
     *
     * @param entity Corrupted entity
     * @param cause  Exception caught during tick
     * @return true if entity was stripped, false otherwise
     */
    public static boolean quarantineEntity(@Nullable Entity entity, @Nullable Throwable cause) {
        if (entity == null || !InStaffConfig.isQuarantineCorruptedEntities()) {
            return false;
        }

        try {
            Level level = entity.level();
            String dim = (level != null) ? level.dimension().location().toString() : "unknown_dimension";
            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();
            String entityType = entity.getType().getDescriptionId();

            String message = String.format("Entity: %s at [%.2f, %.2f, %.2f] in %s", entityType, x, y, z, dim);
            logIncident("ENTITY", message, cause);

            entity.discard();
            InStaff.LOGGER.warn("Quarantined and discarded corrupted entity: {}", message);
            return true;
        } catch (Throwable fatal) {
            // Defensive shield: never allow quarantine logic itself to crash the server
            InStaff.LOGGER.error("Catastrophic error inside quarantineEntity handler", fatal);
            return false;
        }
    }

    /**
     * Safely quarantines and removes a corrupted block entity.
     *
     * @param blockEntity Corrupted block entity
     * @param cause       Exception caught during tick
     * @return true if block entity was stripped, false otherwise
     */
    public static boolean quarantineBlockEntity(@Nullable BlockEntity blockEntity, @Nullable Throwable cause) {
        if (blockEntity == null || !InStaffConfig.isQuarantineCorruptedEntities()) {
            return false;
        }

        try {
            Level level = blockEntity.getLevel();
            BlockPos pos = blockEntity.getBlockPos();
            String dim = (level != null) ? level.dimension().location().toString() : "unknown_dimension";
            String blockType = blockEntity.getBlockState().getBlock().getDescriptionId();

            String message = String.format("BlockEntity: %s at [%d, %d, %d] in %s", blockType, pos.getX(), pos.getY(), pos.getZ(), dim);
            logIncident("BLOCK_ENTITY", message, cause);

            if (level != null) {
                level.removeBlockEntity(pos);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }

            InStaff.LOGGER.warn("Quarantined and replaced corrupted block entity with air: {}", message);
            return true;
        } catch (Throwable fatal) {
            // Defensive shield: never allow quarantine logic itself to crash the server
            InStaff.LOGGER.error("Catastrophic error inside quarantineBlockEntity handler", fatal);
            return false;
        }
    }

    private static void logIncident(@NotNull String category, @NotNull String message, @Nullable Throwable cause) {
        if (!InStaffConfig.isLogQuarantineEvents()) {
            return;
        }

        synchronized (LOG_LOCK) {
            Path logPath = getLogPath();
            try {
                Path parent = logPath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                String timestamp = LocalDateTime.now().format(FORMATTER);
                String causeString = (cause != null) ? (cause.getClass().getName() + ": " + cause.getMessage()) : "No stacktrace";

                String entry = String.format("[%s] [%s] %s | Cause: %s%n", timestamp, category, message, causeString);
                Files.writeString(logPath, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                InStaff.LOGGER.error("Failed to append to quarantine log: {}", logPath, e);
            }
        }
    }
}
