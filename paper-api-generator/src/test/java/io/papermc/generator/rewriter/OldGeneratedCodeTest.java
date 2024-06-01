package io.papermc.generator.rewriter;

import io.papermc.generator.Rewriters;
import io.papermc.generator.rewriter.parser.StringReader;
import io.papermc.generator.rewriter.registration.PaperPatternSourceSetRewriter;
import io.papermc.generator.rewriter.registration.PatternSourceSetRewriter;
import io.papermc.generator.rewriter.replace.CommentMarker;
import io.papermc.generator.rewriter.replace.SearchReplaceRewriter;
import io.papermc.generator.rewriter.utils.Annotations;
import io.papermc.paper.generated.GeneratedFrom;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;

import static io.papermc.generator.rewriter.replace.CommentMarker.EMPTY_MARKER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OldGeneratedCodeTest {

    private static final String CURRENT_VERSION;

    static {
        SharedConstants.tryDetectVersion();
        CURRENT_VERSION = SharedConstants.getCurrentVersion().getName();
    }

    public static void main(String[] args) throws IOException {
        PaperPatternSourceSetRewriter apiSourceSet = new PaperPatternSourceSetRewriter(Path.of(args[0]));
        PaperPatternSourceSetRewriter serverSourceSet = new PaperPatternSourceSetRewriter(Path.of(args[1]));

        Rewriters.bootstrap(apiSourceSet, serverSourceSet);
        checkOutdated(apiSourceSet);
        checkOutdated(serverSourceSet);
    }

    private static void checkOutdated(PaperPatternSourceSetRewriter sourceSetRewriter) throws IOException {
        for (Map.Entry<SourceFile, SourceRewriter> entry : sourceSetRewriter.getRewriters().entrySet()) {
            SourceRewriter rewriter = entry.getValue();
            if (!(rewriter instanceof SearchReplaceRewriter srt) || !srt.hasGeneratedComment()) {
                continue;
            }

            SourceFile file = entry.getKey();
            Path path = sourceSetRewriter.getAlternateOutput().resolve(file.path());
            if (Files.notExists(path)) { // todo (softspoon): remove after
                continue;
            }

            Set<SearchReplaceRewriter> rewriters = new HashSet<>(srt.getRewriters());
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                int lineCount = 0;
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    lineCount++;
                    if (line.isEmpty()) {
                        continue;
                    }

                    StringReader lineIterator = new StringReader(line);
                    CommentMarker marker = SearchReplaceRewriter.searchMarker(lineIterator, null, file.indentUnit(), rewriters, true);
                    if (marker != EMPTY_MARKER) {
                        int startIndentSize = marker.indent();
                        if (startIndentSize % file.indentUnit().size() != 0) {
                            continue;
                        }

                        String nextLine = reader.readLine();
                        if (nextLine == null) {
                            break;
                        }
                        lineCount++;
                        if (nextLine.isEmpty()) {
                            continue;
                        }

                        StringReader nextLineIterator = new StringReader(nextLine);
                        int indentSize = nextLineIterator.skipChars(file.indentUnit().character());
                        if (indentSize != startIndentSize) {
                            continue;
                        }

                        String generatedComment = "// %s ".formatted(Annotations.annotationStyle(GeneratedFrom.class));
                        if (nextLineIterator.trySkipString(generatedComment) && nextLineIterator.canRead()) {
                            String generatedVersion = nextLineIterator.getRemaining();
                            assertEquals(CURRENT_VERSION, generatedVersion,
                                "Code at line %s in %s is marked as being generated in version %s when the current version is %s".formatted(
                                    lineCount, srt.getRewrittenClass().canonicalName(),
                                    generatedVersion, CURRENT_VERSION));

                            if (!marker.owner().getOptions().multipleOperation()) {
                                rewriters.remove(marker.owner());
                                if (rewriters.isEmpty()) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
