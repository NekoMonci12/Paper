package io.papermc.generator.utils.experimental;

import com.google.common.base.Suppliers;
import io.papermc.generator.Main;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BundleItem;
import org.bukkit.MinecraftExperimental;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExperimentalHelper {

    private static final Map<String, Supplier<Registry<? extends FeatureElement>>> FILTERED_REGISTRIES = FeatureElement.FILTERED_REGISTRIES.stream()
        .collect(Collectors.toMap((key) -> {
            if (key == Registries.ENTITY_TYPE) {
                return "entity";
            }
            return key.location().getPath();
        }, key -> Suppliers.memoize(() -> Main.REGISTRY_ACCESS.registryOrThrow(key))));

    private static final Set<String> EXPERIMENTAL_SOUND_EXCEPTIONS = Set.of(
        "event.mob_effect.trial_omen",
        "event.mob_effect.raid_omen"
        // add missing keys here for the next "big" update and toggle the boolean
    );

    // sounds is not dependent on feature flag
    // but some depends on flag locked element!
    public static FeatureFlagSet findSoundRelatedFeatureFlags(ResourceLocation key) {
        String path = key.getPath();
        // the below way is not perfect, but it tries its best
        if (EXPERIMENTAL_SOUND_EXCEPTIONS.contains(path)) {
            return FlagSets.NEXT_UPDATE.get();
        }

        String[] fragments = path.split("\\.");
        if (fragments.length < 2) {
            return null;
        }

        Supplier<Registry<? extends FeatureElement>> filteredRegistry = FILTERED_REGISTRIES.get(fragments[0]);
        if (filteredRegistry == null) {
            return null;
        }

        Optional<FeatureFlagSet> requiredFeatures = getRequiredFeatures(filteredRegistry.get(), fragments[1]);
        if (fragments[fragments.length - 2].equals("imitate")) { // parrot and note block
            requiredFeatures = requiredFeatures.or(() -> getRequiredFeatures(BuiltInRegistries.ENTITY_TYPE, fragments[fragments.length - 1]));
        }

        return requiredFeatures.orElse(null);
    }

    private static final Set<VillagerTrades.TreasureMapForEmeralds> EXPERIMENTAL_TRADES;
    static {
        Set<VillagerTrades.TreasureMapForEmeralds> experimentalTrades = new HashSet<>();

        collectExperimentalTreasureMapTrades(VillagerTrades.EXPERIMENTAL_TRADES, experimentalTrades);
        collectExperimentalTreasureMapTrades(VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES.stream()
            .flatMap(tradeList -> Stream.of(tradeList.getLeft()))
            .toArray(VillagerTrades.ItemListing[]::new), experimentalTrades);

        EXPERIMENTAL_TRADES = Collections.unmodifiableSet(experimentalTrades);
    }

    // map decoration is not dependent on feature flag
    // but some depends on flag locked element!
    private static void collectExperimentalTreasureMapTrades(Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> tradeMap, Set<VillagerTrades.TreasureMapForEmeralds> trades) {
        for (Int2ObjectMap<VillagerTrades.ItemListing[]> tradePerIndex : tradeMap.values()) {
            collectExperimentalTreasureMapTrades(tradePerIndex, trades);
        }
    }

    private static void collectExperimentalTreasureMapTrades(Int2ObjectMap<VillagerTrades.ItemListing[]> tradeMap, Set<VillagerTrades.TreasureMapForEmeralds> trades) {
        for (VillagerTrades.ItemListing[] tradeList : tradeMap.values()) {
            collectExperimentalTreasureMapTrades(tradeList, trades);
        }
    }

    private static void collectExperimentalTreasureMapTrades(VillagerTrades.ItemListing[] tradeList, Set<VillagerTrades.TreasureMapForEmeralds> trades) {
        for (VillagerTrades.ItemListing trade : tradeList) {
            if (trade instanceof VillagerTrades.TreasureMapForEmeralds treasureMapTrade) {
                trades.add(treasureMapTrade);
            } else if (trade instanceof VillagerTrades.TypeSpecificTrade typeSpecificTrade) {
                collectExperimentalTreasureMapTradesPerVillageType(typeSpecificTrade, trades);
            }
        }
    }

    private static void collectExperimentalTreasureMapTradesPerVillageType(VillagerTrades.TypeSpecificTrade specificTrade, Set<VillagerTrades.TreasureMapForEmeralds> trades) {
        for (VillagerTrades.ItemListing trade : specificTrade.trades().values()) {
            if (trade instanceof VillagerTrades.TreasureMapForEmeralds treasureMapTrade) {
                trades.add(treasureMapTrade);
            }
        }
    }

    public static FeatureFlagSet findMapDecorationRelatedFeatureFlags(ResourceLocation key) {
        for (VillagerTrades.TreasureMapForEmeralds trade : EXPERIMENTAL_TRADES) {
            if (!trade.destinationType.is(key)) {
                continue;
            }

            String featureFlag = Main.EXPERIMENTAL_TAGS.perFeatureFlag().get(trade.destination);
            if (featureFlag != null) {
                return FeatureFlagSet.of(getFlagFromName(featureFlag));
            }
            break;
        }
        return null;
    }

    private static Optional<FeatureFlagSet> getRequiredFeatures(Registry<? extends FeatureElement> registry, String key) {
        Optional<? extends FeatureElement> optionalElement = registry.getOptional(new ResourceLocation(ResourceLocation.DEFAULT_NAMESPACE, key));
        return optionalElement.map(element -> {
            if (element instanceof BundleItem) {
                return FlagSets.BUNDLE.get(); // special case since the item is not locked itself just in the creative menu
            }
            if (FeatureFlags.isExperimental(element.requiredFeatures())) {
                return element.requiredFeatures();
            }
            return null;
        });
    }

    public static FeatureFlag onlyOneFlag(FeatureFlagSet featureFlags) {
        FeatureFlag result = null;
        for (FeatureFlag flag : FeatureFlags.REGISTRY.names.values()) {
            if (featureFlags.contains(flag)) {
                if (result != null) {
                    throw new UnsupportedOperationException("Don't know how to compact two feature flags into one!");
                }
                result = flag;
            }
        }

        return result;
    }

    public static MinecraftExperimental.Requires toBukkitAnnotationMember(FeatureFlag flag) {
        final MinecraftExperimental.Requires requires;
        if (flag == FeatureFlags.UPDATE_1_21) {
            requires = MinecraftExperimental.Requires.UPDATE_1_21;
        } else if (flag == FeatureFlags.BUNDLE) {
            requires = MinecraftExperimental.Requires.BUNDLE;
        } else if (flag == FeatureFlags.TRADE_REBALANCE) {
            requires = MinecraftExperimental.Requires.TRADE_REBALANCE;
        } else {
            throw new UnsupportedOperationException("Don't know that feature flag");
        }

        return requires;
    }

    public static FeatureFlag getFlagFromName(String name) {
        return FeatureFlags.REGISTRY.names.get(new ResourceLocation(ResourceLocation.DEFAULT_NAMESPACE, name));
    }
}
