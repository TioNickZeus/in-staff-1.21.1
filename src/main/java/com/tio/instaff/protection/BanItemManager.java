package com.tio.instaff.protection;

import com.google.gson.reflect.TypeToken;
import com.tio.instaff.InStaff;
import com.tio.instaff.util.FileStorageUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe manager for restricted/banned items in In-Staff.
 * Supports total confiscation, use-only denial, and placement denial with atomic persistence.
 */
public final class BanItemManager {

    private static final BanItemManager INSTANCE = new BanItemManager();
    private static final String FILE_NAME = "banned_items.json";

    private final Object lock = new Object();
    private final Map<String, BanItemMode> bannedItems = new HashMap<>();

    private BanItemManager() {
        load();
    }

    public static BanItemManager getInstance() {
        return INSTANCE;
    }

    private Path getStoragePath() {
        return FileStorageUtil.getDataDirectory().resolve(FILE_NAME);
    }

    public void load() {
        synchronized (lock) {
            bannedItems.clear();
            Type mapType = new TypeToken<Map<String, BanItemMode>>() {}.getType();
            Map<String, BanItemMode> loaded = FileStorageUtil.loadJson(getStoragePath(), mapType);

            if (loaded != null) {
                bannedItems.putAll(loaded);
            }
            InStaff.LOGGER.info("Ban-Item manager loaded: {} banned item rules.", bannedItems.size());
        }
    }

    public void save() {
        synchronized (lock) {
            FileStorageUtil.saveAtomicJson(getStoragePath(), bannedItems);
        }
    }

    public void banItem(@NotNull String itemId, @NotNull BanItemMode mode) {
        synchronized (lock) {
            bannedItems.put(itemId.toLowerCase().trim(), mode);
            save();
        }
    }

    public boolean unbanItem(@NotNull String itemId) {
        synchronized (lock) {
            boolean removed = bannedItems.remove(itemId.toLowerCase().trim()) != null;
            if (removed) {
                save();
            }
            return removed;
        }
    }

    @Nullable
    public BanItemMode getMode(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        synchronized (lock) {
            return bannedItems.get(itemId.toLowerCase().trim());
        }
    }

    @Nullable
    public BanItemMode getMode(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return getMode(key.toString());
    }

    @Nullable
    public BanItemMode getMode(@Nullable Item item) {
        if (item == null) {
            return null;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return getMode(key.toString());
    }

    public boolean isBanned(@Nullable ItemStack stack) {
        return getMode(stack) != null;
    }

    @NotNull
    public Map<String, BanItemMode> getAllBannedItems() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new HashMap<>(bannedItems));
        }
    }
}
