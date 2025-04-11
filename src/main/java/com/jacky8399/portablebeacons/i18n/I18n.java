package com.jacky8399.portablebeacons.i18n;

import com.jacky8399.portablebeacons.PortableBeacons;
import net.kyori.adventure.text.Component;

import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class I18n {
    public static final LocaleTranslator DEFAULT = LocaleTranslator.fromReader(Locale.US,
            new InputStreamReader(Objects.requireNonNull(PortableBeacons.INSTANCE.getResource("lang_en_us.yml"))));

    public static Component translate(String key, Context context) {
        Component result = DEFAULT.translate(key, context);
        if (result != null)
            return result;
        return Component.text(key);
    }

    public static Component formatEnchantment(String enchantment, int level, Context context) {
        String base = "beacon-item.custom-enchantments." + enchantment;
        Component name = translate(base + ".name", context);
        return translate(base + ".format", new Context(context.locale(),
                Map.of("name", new PBPlaceholders.PlaceholderComponent(name),
                        "level", new PBPlaceholders.PlaceholderLevel(level, true))));
    }

}
