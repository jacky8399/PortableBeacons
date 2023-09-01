import com.jacky8399.portablebeacons.ConfigMigrations;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class MigrationTests {
    private static ConfigMigrations.MigrationLogger LOGGER =
            new ConfigMigrations.MigrationLogger(Logger.getLogger("Test"), new ArrayList<>(), new ArrayList<>());;

    @Nested
    class V1 {
        @Test
        public void migrateExpUsageMessage() {
            record Test(String input, String expected) {}
            List<Test> tests = List.of(
                    new Test("A{0}", "A{usage}"),
                    new Test("A{0,number}B", "A{usage}B"),
                    new Test("A{0,number,0.#####}B", "A{usage|0.#####}B"),
                    new Test("A{0,unsupported}B", "A{0,unsupported}B")
            );
            YamlConfiguration config = new YamlConfiguration();
            for (Test test : tests) {
                config.set("beacon-item.effects-toggle.exp-usage-message", test.input);

                ConfigMigrations.V1.migrateExpUsageMessage(LOGGER, config);

                assertEquals(test.expected, config.get("beacon-item.effects-toggle.exp-usage-message"), test.input);
            }
        }

        @Test
        public void migrateEffectNames() {
            record Test(String input, String expectedName, String expectedFormat) {
                String error() {
                    return "For input: " + input + ", migrated: (" + String.join(", ", LOGGER.migrated()) + ")";
                }
            }
            Map<String, Test> tests = Map.of(
                    // default will not be migrated
                    "effects.default.name", new Test("DEFAULT", null, null),
                    "effects.regeneration.name", new Test("&4&4&4REGeneration", null, null),
                    "effects.speed.name", new Test("&4&4&4speedy uwu", "&4&4&4speedy uwu", null),
                    "effects.jump_boost.name", new Test("JUMP BOOST", null, null),
                    "effects.luck.name", new Test("Luck{level}y", "Lucky", null),
                    "effects.night_vision.name", new Test("NIGHT VIS{level|number}ION", null, "number"),
                    "effects.mining_fatigue.name", new Test("{level|number}Slow mining", "Slow mining", "number")
            );


            YamlConfiguration config = new YamlConfiguration();
            tests.forEach((key, test) -> config.set(key, test.input));

            ConfigMigrations.V1.migrateEffectNames(LOGGER, config);

            assertEquals("DEFAULT", config.get("effects.default.name"));
            tests.forEach((key, test) -> {
                String base = key.substring(0, key.lastIndexOf('.'));
                ConfigurationSection section = config.getConfigurationSection(base);
                // simplification is not applicable to default
                if (!base.equals("effects.default") && test.expectedName == null && test.expectedFormat == null) {
                    assertNull(section, () -> test.error() + "\nThe section still contains these entries: " + section.getValues(true));
                } else {
                    assertNotNull(section, test::error);
                }
                if (section != null) {
                    assertEquals(test.expectedName, section.get("name-override"), test::error);
                    assertEquals(test.expectedFormat, section.get("format-override"), test::error);
                }
            });
        }
    }
}
