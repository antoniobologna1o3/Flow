package com.netherfront;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the packaged mods.toml.
 *
 * <p>Forge 1.20.1 (FML 47) rejects the entire mod file if a dependency block is
 * missing {@code mandatory}, and the {@code type = "required"} spelling used by
 * NeoForge and 1.21+ does not satisfy it. That failure only shows up when the
 * game actually loads the jar, which a compile and a unit run will happily miss,
 * so it is asserted here instead.
 *
 * <p>Reads the processed resource from the classpath, so it checks the file as
 * it is actually packaged, with the Gradle tokens already substituted.
 */
class ModsTomlTest {

    private static List<String> readPackagedModsToml() throws IOException {
        try (InputStream in = ModsTomlTest.class.getResourceAsStream("/META-INF/mods.toml")) {
            assertNotNull(in, "mods.toml is not on the classpath; was processResources run?");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line.trim());
                }
                return lines;
            }
        }
    }

    /** The dependency blocks, each as its list of key lines. */
    private static List<List<String>> dependencyBlocks(List<String> lines) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        for (String line : lines) {
            if (line.startsWith("[[dependencies.")) {
                current = new ArrayList<>();
                blocks.add(current);
            } else if (line.startsWith("[[") || line.startsWith("[")) {
                current = null;
            } else if (current != null && !line.isEmpty() && !line.startsWith("#")) {
                current.add(line);
            }
        }
        return blocks;
    }

    @Test
    void everyDependencyDeclaresMandatory() throws IOException {
        List<List<String>> blocks = dependencyBlocks(readPackagedModsToml());
        assertFalse(blocks.isEmpty(), "expected at least one dependency block");

        for (List<String> block : blocks) {
            String modId = block.stream()
                    .filter(l -> l.startsWith("modId"))
                    .findFirst()
                    .orElse("(unknown)");
            assertTrue(block.stream().anyMatch(l -> l.startsWith("mandatory")),
                    "dependency " + modId + " is missing 'mandatory'; FML 47 rejects the whole mod file");
        }
    }

    @Test
    void noDependencyUsesTheNeoForgeTypeSpelling() throws IOException {
        for (List<String> block : dependencyBlocks(readPackagedModsToml())) {
            assertTrue(block.stream().noneMatch(l -> l.startsWith("type")),
                    "dependency uses type=\"required\"; that is the NeoForge/1.21+ spelling "
                            + "and Forge 1.20.1 does not accept it");
        }
    }

    @Test
    void reignOfNetherIsOptional() throws IOException {
        List<List<String>> blocks = dependencyBlocks(readPackagedModsToml());
        List<String> ron = blocks.stream()
                .filter(b -> b.stream().anyMatch(l -> l.contains("reignofnether")))
                .findFirst()
                .orElse(null);
        assertNotNull(ron, "expected a reignofnether dependency block");
        // The whole design depends on the mod loading without Reign of Nether.
        assertTrue(ron.stream().anyMatch(l -> l.replace(" ", "").equals("mandatory=false")),
                "reignofnether must be mandatory=false so Netherfront loads standalone");
    }

    @Test
    void allGradleTokensWereSubstituted() throws IOException {
        for (String line : readPackagedModsToml()) {
            assertFalse(line.contains("${"),
                    "unexpanded token in packaged mods.toml: " + line);
        }
    }

    @Test
    void declaresTheExpectedModId() throws IOException {
        List<String> lines = readPackagedModsToml();
        assertTrue(lines.stream().anyMatch(l -> l.equals("modId=\"netherfront\"")),
                "mods.toml should declare modId=\"netherfront\"");
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("modLoader=\"javafml\"")),
                "mods.toml should use the javafml mod loader");
    }
}
