package com.jacky8399.portablebeacons.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.VirtualComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.translation.Translator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LocaleTranslator {
    public final Locale locale;
    private final Map<String, Component> messages;
    public LocaleTranslator(Locale locale, Map<String, Component> messages) {
        this.locale = locale;
        this.messages = Map.copyOf(messages);
    }

    private static final MiniMessage MM = MiniMessage.builder()
            .editTags(builder -> builder.resolver(PlaceholderTagInfo.RESOLVER))
            .emitVirtuals(true)
            .build();
    public static LocaleTranslator fromFile(Path file) {
        String localeName = file.getFileName().toString().split("\\.", 2)[0];
        Locale locale = Translator.parseLocale(localeName);
        try {
            return fromReader(locale, Files.newBufferedReader(file));
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(file.toString(), ex);
        }
    }

    public static LocaleTranslator fromReader(Locale locale, Reader reader) {
        try (reader) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);

            Map<String, Component> messages = new HashMap<>();
            for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
                if (entry.getValue() instanceof String string) {
                    messages.put(entry.getKey(), MM.deserialize(string));
                }
            }
            return new LocaleTranslator(locale, messages);
        } catch (IOException ex) {
            throw new IllegalArgumentException(locale.toString(), ex);
        }
    }

    @Nullable
    public Component translate(String key, Context context) {
        Component component = messages.get(key);
        if (component != null) {
            return RENDERER.render(component, context);
        }
        return null;
    }

    private static final Renderer RENDERER = new Renderer();
    private static final class Renderer extends TranslatableComponentRenderer<Context> {
        @Override
        protected @NotNull Component renderVirtual(@NotNull VirtualComponent component, @NotNull Context context) {
            if (component.contextType() != PlaceholderTagInfo.class)
                return component;

            PlaceholderTagInfo tag = ((PlaceholderTagInfo) component.renderer());
            Placeholder placeholder = context.getPlaceholder(tag.variable());
            if (placeholder != null) {
                try {
                    return placeholder.apply(tag, context);
                } catch (IllegalArgumentException ex) {
                    return Component.text("(" + tag.fallbackString() + ": " + ex.getMessage() + ")", NamedTextColor.DARK_RED);
                } catch (Exception ex) {
                    return Component.text("(" + tag.fallbackString() + ": " + ex + ")", NamedTextColor.DARK_RED);
                }
            }
            return component;
        }

        // we can just use the fast path since we never supply translations
        @Override
        protected @NotNull Component renderTranslatable(@NotNull TranslatableComponent component, @NotNull Context context) {
            final TranslatableComponent.Builder builder = Component.translatable()
                    .key(component.key()).fallback(component.fallback());
            if (!component.arguments().isEmpty()) {
                final List<TranslationArgument> args = new ArrayList<>(component.arguments());
                for (int i = 0, size = args.size(); i < size; i++) {
                    final TranslationArgument arg = args.get(i);
                    if (arg.value() instanceof Component) {
                        args.set(i, TranslationArgument.component(this.render(((Component) arg.value()), context)));
                    }
                }
                builder.arguments(args);
            }
            return this.mergeStyleAndOptionallyDeepRender(component, builder, context);
        }
    }
}
