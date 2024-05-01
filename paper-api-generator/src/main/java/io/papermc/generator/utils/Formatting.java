package io.papermc.generator.utils;

import io.papermc.generator.rewriter.ClassNamed;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import org.apache.commons.lang3.math.NumberUtils;

public final class Formatting {

    private static final Pattern ILLEGAL_FIELD_CHARACTERS = Pattern.compile("[.-/]");

    public static String formatKeyAsField(String path) {
        return ILLEGAL_FIELD_CHARACTERS.matcher(path.toUpperCase(Locale.ENGLISH)).replaceAll("_");
    }

    public static String formatTagFieldPrefix(String name, ResourceKey<? extends Registry<?>> registryKey) {
        if (registryKey == Registries.BLOCK) {
            return "";
        }
        if (registryKey == Registries.GAME_EVENT) {
            return "GAME_EVENT_"; // Paper doesn't follow the format (should be GAME_EVENTS_)
        }
        return name.toUpperCase(Locale.ENGLISH) + "_";
    }

    public static Optional<String> formatTagKey(String tagDir, String resourcePath) {
        int tagsIndex = resourcePath.indexOf(tagDir);
        int dotIndex = resourcePath.lastIndexOf('.');
        if (tagsIndex == -1 || dotIndex == -1) {
            return Optional.empty();
        }
        return Optional.of(resourcePath.substring(tagsIndex + tagDir.length() + 1, dotIndex)); // namespace/tags/registry_key/[tag_key].json
    }

    public static int countOccurrences(String value, char match) {
        int count = 0;
        for (int i = 0, len = value.length(); i < len; i++) {
            if (value.charAt(i) == match) {
                count++;
            }
        }
        return count;
    }

    public static String incrementalIndent(String unit, ClassNamed classNamed) {
        if (classNamed.knownClass() == null) {
            return unit.repeat(countOccurrences(classNamed.dottedNestedName(), '.') + 1);
        }

        Class<?> parent = classNamed.knownClass().getEnclosingClass();
        StringBuilder indentBuilder = new StringBuilder(unit);
        while (parent != null) {
            indentBuilder.append(unit);
            parent = parent.getEnclosingClass();
        }
        return indentBuilder.toString();
    }

    public static String quoted(String value) {
        return "\"" + value + "\"";
    }

    public static String floatStr(float value) {
        return Float.toString(value) + 'F';
    }

    public static String stripWordOfCamelCaseName(String name, String word, boolean onlyOnce) {
        String newName = name;
        int startIndex = 0;
        while (true) {
            int baseIndex = newName.indexOf(word, startIndex);
            if (baseIndex == -1) {
                return newName;
            }

            if ((baseIndex > 0 && !Character.isLowerCase(newName.charAt(baseIndex - 1))) ||
                (baseIndex + word.length() < newName.length() && !Character.isUpperCase(newName.charAt(baseIndex + word.length())))) {
                startIndex = baseIndex + word.length();
                continue;
            }

            newName = newName.substring(0, baseIndex) + newName.substring(baseIndex + word.length());
            startIndex = baseIndex;
            if (onlyOnce) {
                break;
            }
        }
        return newName;
    }

    public static Comparator<String> ALPHABETIC_KEY_ORDER = alphabeticKeyOrder(path -> path);

    public static <T> Comparator<T> alphabeticKeyOrder(Function<T, String> mapper) {
        return (o1, o2) -> {
            String path1 = mapper.apply(o1);
            String path2 = mapper.apply(o2);

            OptionalInt trailingInt1 = tryParseTrailingInt(path1);
            OptionalInt trailingInt2 = tryParseTrailingInt(path2);

            if (trailingInt1.isPresent() && trailingInt2.isPresent()) {
                return Integer.compare(trailingInt1.getAsInt(), trailingInt2.getAsInt());
            }

            return path1.compareTo(path2);
        };
    }

    private static OptionalInt tryParseTrailingInt(String path) {
        int delimiterIndex = path.lastIndexOf('_');
        if (delimiterIndex != -1) {
            String score = path.substring(delimiterIndex + 1);
            if (NumberUtils.isDigits(score)) {
                return OptionalInt.of(Integer.parseInt(score));
            }
        }
        return OptionalInt.empty();
    }

    private Formatting() {
    }
}
