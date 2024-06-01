package io.papermc.generator.rewriter.types.simple;

import com.google.common.collect.HashBiMap;
import io.papermc.generator.rewriter.types.EnumRegistryRewriter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.fixes.BannerPatternFormatFix;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import org.bukkit.block.banner.PatternType;

import java.util.Map;

import static io.papermc.generator.utils.Formatting.quoted;

public class PatternTypeRewriter extends EnumRegistryRewriter<BannerPattern, PatternType> {

    // DataConverter(V3818-2): legacy code -> key map is not accessible so get it from DFU
    @Deprecated
    private static final Map<String, String> KEY_TO_LEGACY_CODE = HashBiMap.create(BannerPatternFormatFix.PATTERN_ID_MAP).inverse();

    public PatternTypeRewriter() {
        super(Registries.BANNER_PATTERN, true);
    }

    @Deprecated(forRemoval = true, since = "1.20.5")
    private String retrieveCursedSpigotId(ResourceKey<BannerPattern> key) { // does spigot really create its own legacy code???
        if (key == BannerPatterns.FLOW) {
            return "flw";
        }
        if (key == BannerPatterns.GUSTER) {
            return "gus";
        }
        throw new UnsupportedOperationException();
    }

    private String getLegacyCode(ResourceKey<BannerPattern> key) {
        String rawKey = key.location().toString();
        if (KEY_TO_LEGACY_CODE.containsKey(rawKey)) {
            return KEY_TO_LEGACY_CODE.get(rawKey);
        }

        return this.retrieveCursedSpigotId(key);
    }

    @Override
    protected String rewriteEnumValue(Holder.Reference<BannerPattern> reference) {
        return "%s, %s".formatted(
            quoted(this.getLegacyCode(reference.key())),
            super.rewriteEnumValue(reference)
        );
    }
}
