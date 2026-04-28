/*
 * SkinsRestorer
 * Copyright (C) 2026  SkinsRestorer Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.skinsrestorer.shared.storage;

import ch.jalu.configme.SettingsManager;
import lombok.RequiredArgsConstructor;
import net.skinsrestorer.api.PropertyUtils;
import net.skinsrestorer.api.connections.model.MineSkinResponse;
import net.skinsrestorer.api.exception.DataRequestException;
import net.skinsrestorer.api.exception.MineSkinException;
import net.skinsrestorer.api.property.*;
import net.skinsrestorer.api.storage.SkinStorage;
import net.skinsrestorer.shared.config.StorageConfig;
import net.skinsrestorer.shared.connections.MineSkinAPIImpl;
import net.skinsrestorer.shared.connections.RecommendationsState;
import net.skinsrestorer.shared.connections.responses.RecommenationResponse;
import net.skinsrestorer.shared.log.SRLogger;
import net.skinsrestorer.shared.storage.adapter.AdapterReference;
import net.skinsrestorer.shared.storage.adapter.StorageAdapter;
import net.skinsrestorer.shared.storage.model.cache.MojangCacheData;
import net.skinsrestorer.shared.storage.model.skin.*;
import net.skinsrestorer.shared.subjects.messages.ComponentHelper;
import net.skinsrestorer.shared.subjects.messages.ComponentString;
import net.skinsrestorer.shared.utils.SRHelpers;
import net.skinsrestorer.shared.utils.UUIDUtils;
import net.skinsrestorer.shared.utils.ValidationUtil;

import javax.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SkinStorageImpl implements SkinStorage {
    public static final String RECOMMENDATION_PREFIX = "sr-recommendation-";
    private final SRLogger logger;
    private final CacheStorageImpl cacheStorage;
    private final MineSkinAPIImpl mineSkinAPI;
    private final SettingsManager settings;
    private final AdapterReference adapterReference;
    private final RecommendationsState recommendationsState;

    public void preloadDefaultSkins() {
        if (!settings.getProperty(StorageConfig.DEFAULT_SKINS_ENABLED)) {
            return;
        }

        List<String> toRemove = new ArrayList<>();
        List<String> defaultSkins = new ArrayList<>(settings.getProperty(StorageConfig.DEFAULT_SKINS));
        defaultSkins.forEach(skin -> {
            if ("<random>".equalsIgnoreCase(skin)) {
                return;
            }

            try {
                findOrCreateSkinData(skin);
            } catch (DataRequestException | MineSkinException e) {
                logger.debug("DefaultSkin '%s' could not be found or requested! Removing from list..".formatted(skin), e);
                toRemove.add(skin);
            }
        });

        if (!toRemove.isEmpty()) {
            defaultSkins.removeAll(toRemove);
            settings.setProperty(StorageConfig.DEFAULT_SKINS, defaultSkins);
        }

        if (defaultSkins.isEmpty()) {
            logger.warning("[WARNING] No more working DefaultSkin left... disabling feature");
            settings.setProperty(StorageConfig.DEFAULT_SKINS_ENABLED, false);
        }
    }

    @Override
    public Optional<SkinProperty> updatePlayerSkinData(UUID uuid) throws DataRequestException {
        // External API calls (Mojang/Eclipse) have been removed.
        // Skin updates are no longer fetched from external services.
        try {
            return adapterReference.get().getPlayerSkinData(uuid).map(PlayerSkinData::getProperty);
        } catch (StorageAdapter.StorageException e) {
            logger.warning("Failed to update skin data for %s".formatted(uuid), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<MojangSkinDataResult> getPlayerSkin(String nameOrUniqueId, boolean allowExpired) throws DataRequestException {
        return getPlayerSkin(nameOrUniqueId, allowExpired, false);
    }

    private Optional<MojangSkinDataResult> getPlayerSkin(String nameOrUniqueId, boolean allowExpired, boolean skipDbLookup) throws DataRequestException {
        Optional<UUID> uuidParseResult = UUIDUtils.tryParseUniqueId(nameOrUniqueId);
        if (ValidationUtil.invalidMinecraftUsername(nameOrUniqueId) && uuidParseResult.isEmpty()) {
            return Optional.empty();
        }

        // Without Mojang/Eclipse API, only cached player skin data is available
        try {
            if (uuidParseResult.isEmpty()) {
                Optional<MojangCacheData> cached = cacheStorage.getCachedData(nameOrUniqueId, allowExpired);
                if (cached.isPresent()) {
                    Optional<UUID> optionalUUID = cached.get().getUniqueId();

                    if (optionalUUID.isEmpty()) {
                        return Optional.empty();
                    }

                    UUID uuid = optionalUUID.get();
                    Optional<PlayerSkinData> playerSkinData = adapterReference.get().getPlayerSkinData(uuid);
                    return playerSkinData.map(data ->
                            MojangSkinDataResult.of(uuid, data.getProperty()));
                }
            } else {
                UUID uuid = uuidParseResult.get();
                Optional<PlayerSkinData> playerSkinData = adapterReference.get().getPlayerSkinData(uuid);
                return playerSkinData.map(data ->
                        MojangSkinDataResult.of(uuid, data.getProperty()));
            }
        } catch (StorageAdapter.StorageException e) {
            logger.warning("Failed to get skin from cache for %s".formatted(nameOrUniqueId), e);
        }

        return Optional.empty();
    }

    @Override
    public void setPlayerSkinData(UUID uuid, String lastKnownName, SkinProperty property, long timestamp) {
        adapterReference.get().setPlayerSkinData(uuid, PlayerSkinData.of(uuid, lastKnownName, property, timestamp));
    }

    @Override
    public void setURLSkinData(String url, String mineSkinId, SkinProperty property, SkinVariant skinVariant) {
        adapterReference.get().setURLSkinData(url, URLSkinData.of(url, mineSkinId, property, skinVariant));
    }

    @Override
    public void setURLSkinIndex(String url, SkinVariant skinVariant) {
        adapterReference.get().setURLSkinIndex(url, URLIndexData.of(url, skinVariant));
    }

    @Override
    public void setCustomSkinData(String skinName, SkinProperty property) {
        skinName = CustomSkinData.sanitizeCustomSkinName(skinName);

        adapterReference.get().setCustomSkinData(skinName, CustomSkinData.of(skinName, null, property));
    }

    public void setCustomSkinDisplayName(String skinName, ComponentString displayName) throws StorageAdapter.StorageException {
        skinName = CustomSkinData.sanitizeCustomSkinName(skinName);

        CustomSkinData customSkinData = adapterReference.get().getCustomSkinData(skinName)
                .orElseThrow(() -> new IllegalArgumentException("Skin not found"));

        adapterReference.get().setCustomSkinData(skinName, CustomSkinData.of(skinName, displayName, customSkinData.getProperty()));
    }

    @Override
    public Optional<InputDataResult> findSkinData(String input, SkinVariant skinVariantHint) {
        input = SRHelpers.sanitizeSkinInput(input);
        SkinInput skinInput = SkinInput.parse(input);
        input = skinInput.name();
        SkinType typeHint = skinInput.typeHint();

        try {
            if (ValidationUtil.validSkinUrl(input)) {
                SkinVariant skinVariant;
                if (skinVariantHint != null) {
                    skinVariant = skinVariantHint;
                } else {
                    Optional<URLIndexData> variant = adapterReference.get().getURLSkinIndex(input);
                    if (variant.isEmpty()) {
                        return Optional.empty();
                    }

                    skinVariant = variant.get().getSkinVariant();
                }

                return adapterReference.get().getURLSkinData(input, skinVariant).map(data ->
                        InputDataResult.of(SkinIdentifier.ofURL(data.getUrl(), skinVariant),
                                data.getProperty()));
            } else {
                Optional<InputDataResult> result = HardcodedSkins.getHardcodedSkin(input);

                if (result.isPresent()) {
                    return result;
                }

                if (typeHint != SkinType.PLAYER) {
                    Optional<CustomSkinData> customSkinData = adapterReference.get().getCustomSkinData(input);

                    if (customSkinData.isPresent()) {
                        return customSkinData.map(data ->
                                InputDataResult.of(SkinIdentifier.ofCustom(data.getSkinName()), data.getProperty()));
                    }
                }

                if (typeHint != SkinType.CUSTOM) {
                    Optional<UUID> uuid = cacheStorage.getUUID(input, false);

                    if (uuid.isPresent()) {
                        Optional<PlayerSkinData> playerSkinData = adapterReference.get().getPlayerSkinData(uuid.get());

                        if (playerSkinData.isPresent()) {
                            return playerSkinData.map(data ->
                                    InputDataResult.of(SkinIdentifier.ofPlayer(uuid.get()), data.getProperty()));
                        }
                    }
                }
            }
        } catch (StorageAdapter.StorageException | DataRequestException e) {
            logger.warning("Failed to find skin data for %s".formatted(input), e);
        }

        return Optional.empty();
    }

    public ComponentString resolveSkinName(SkinIdentifier identifier) {
        return switch (identifier.getSkinType()) {
            case PLAYER -> {
                try {
                    yield ComponentHelper.convertPlainToJson(adapterReference.get().getPlayerSkinData(identifier.getPlayerUniqueId())
                            .map(PlayerSkinData::getLastKnownName)
                            .orElse(identifier.getIdentifier()));
                } catch (StorageAdapter.StorageException e) {
                    logger.warning("Failed to get skin data for %s".formatted(identifier), e);
                    yield ComponentHelper.convertPlainToJson(identifier.getIdentifier());
                }
            }
            case URL, LEGACY -> ComponentHelper.convertPlainToJson(identifier.getIdentifier());
            case CUSTOM -> {
                if (identifier.getIdentifier().startsWith(RECOMMENDATION_PREFIX)) {
                    RecommenationResponse.SkinInfo skinInfo = recommendationsState.getRecommendation(identifier.getIdentifier().substring(RECOMMENDATION_PREFIX.length()));
                    if (skinInfo != null) {
                        yield ComponentHelper.convertPlainToJson(skinInfo.getSkinName());
                    }
                }

                try {
                    yield adapterReference.get().getCustomSkinData(identifier.getIdentifier())
                            .flatMap(c -> Optional.ofNullable(c.getDisplayName()))
                            .orElse(ComponentHelper.convertPlainToJson(identifier.getIdentifier()));
                } catch (StorageAdapter.StorageException e) {
                    logger.warning("Failed to get skin data for %s".formatted(identifier), e);
                    yield ComponentHelper.convertPlainToJson(identifier.getIdentifier());
                }
            }
        };
    }

    @Override
    public Optional<InputDataResult> findOrCreateSkinData(String input, SkinVariant skinVariantHint) throws DataRequestException, MineSkinException {
        input = SRHelpers.sanitizeSkinInput(input);

        // findSkinData handles prefix parsing internally for lookups
        Optional<InputDataResult> skinData = findSkinData(input, skinVariantHint);

        if (skinData.isPresent()) {
            return skinData;
        }

        // Strip the prefix for the creation logic below
        SkinInput skinInput = SkinInput.parse(input);
        input = skinInput.name();
        SkinType typeHint = skinInput.typeHint();

        // Create new skin data
        if (input.startsWith(RECOMMENDATION_PREFIX)) {
            String skinId = input.substring(RECOMMENDATION_PREFIX.length());
            RecommenationResponse.SkinInfo skinInfo = recommendationsState.getRecommendation(skinId);

            if (skinInfo == null) {
                return Optional.empty();
            }

            SkinProperty skinProperty = skinInfo.getSkinProperty();
            setCustomSkinData(input, skinProperty);

            return Optional.of(InputDataResult.of(SkinIdentifier.ofCustom(input), skinProperty));
        } else if (ValidationUtil.validSkinUrl(input)) {
            MineSkinResponse response = mineSkinAPI.genSkin(input, skinVariantHint);

            setURLSkinByResponse(input, response);

            return Optional.of(InputDataResult.of(SkinIdentifier.ofURL(input, response.getGeneratedVariant()), response.getProperty()));
        } else if (typeHint != SkinType.CUSTOM) {
            // Check cache first
            Optional<MojangSkinDataResult> cached = getPlayerSkin(input, false, true);
            if (cached.isPresent()) {
                return cached.map(result ->
                        InputDataResult.of(SkinIdentifier.ofPlayer(result.getUniqueId()), result.getSkinProperty()));
            }

            // Not in cache, generate from player name via MineSkin API
            MineSkinResponse response = mineSkinAPI.genSkinFromName(input, skinVariantHint);
            setCustomSkinData(input, response.getProperty());
            return Optional.of(InputDataResult.of(SkinIdentifier.ofCustom(input), response.getProperty()));
        }

        return Optional.empty();
    }

    @Override
    public Optional<SkinProperty> getSkinDataByIdentifier(SkinIdentifier identifier) {
        try {
            return switch (identifier.getSkinType()) {
                case PLAYER -> adapterReference.get().getPlayerSkinData(identifier.getPlayerUniqueId())
                        .map(PlayerSkinData::getProperty);
                case URL ->
                        adapterReference.get().getURLSkinData(identifier.getIdentifier(), identifier.getSkinVariant())
                                .map(URLSkinData::getProperty);
                case CUSTOM -> {
                    if (identifier.getIdentifier().startsWith(RECOMMENDATION_PREFIX)) {
                        String skinId = identifier.getIdentifier().substring(RECOMMENDATION_PREFIX.length());
                        RecommenationResponse.SkinInfo skinInfo = recommendationsState.getRecommendation(skinId);

                        if (skinInfo == null) {
                            yield Optional.empty();
                        }

                        yield Optional.of(skinInfo.getSkinProperty());
                    }

                    Optional<SkinProperty> skinProperty = adapterReference.get().getCustomSkinData(identifier.getIdentifier())
                            .map(CustomSkinData::getProperty);
                    if (skinProperty.isPresent()) {
                        yield skinProperty;
                    } else {
                        yield HardcodedSkins.getHardcodedSkin(identifier.getIdentifier())
                                .map(InputDataResult::getProperty);
                    }
                }
                case LEGACY -> adapterReference.get().getLegacySkinData(identifier.getIdentifier())
                        .map(LegacySkinData::getProperty);
            };

        } catch (StorageAdapter.StorageException e) {
            logger.warning("Failed to get skin data for %s".formatted(identifier), e);
            return Optional.empty();
        }
    }

    @Override
    public void removeSkinData(SkinIdentifier identifier) {
        switch (identifier.getSkinType()) {
            case PLAYER -> adapterReference.get().removePlayerSkinData(identifier.getPlayerUniqueId());
            case URL ->
                    adapterReference.get().removeURLSkinData(identifier.getIdentifier(), identifier.getSkinVariant());
            case CUSTOM -> adapterReference.get().removeCustomSkinData(identifier.getIdentifier());
            case LEGACY -> adapterReference.get().removeLegacySkinData(identifier.getIdentifier());
        }
    }

    public boolean purgeOldSkins(int days) {
        long targetPurgeTimestamp = Instant.now().minus(days, ChronoUnit.DAYS).getEpochSecond();

        try {
            adapterReference.get().purgeStoredOldSkins(targetPurgeTimestamp);
            return true;
        } catch (StorageAdapter.StorageException e) {
            logger.severe("Failed to purge old skins", e);
            return false;
        }
    }
}
