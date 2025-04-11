package com.jacky8399.portablebeacons.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.VirtualComponentRenderer;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;

public record PlaceholderTagInfo(@NotNull String variable, @NotNull String... args) implements VirtualComponentRenderer<PlaceholderTagInfo> {
    @Override
    public @UnknownNullability ComponentLike apply(@NotNull PlaceholderTagInfo context) {
        return null;
    }

    @Override
    public @NotNull String fallbackString() {
        return variable + (args.length != 0 ? ":" + String.join(":", args) : "");
    }

    public static final TagResolver RESOLVER = new TagResolver() {
        @Override
        public Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) throws ParsingException {
            var args = new ArrayList<String>();
            while (arguments.hasNext())
                args.add(arguments.pop().value());
            PlaceholderTagInfo info = new PlaceholderTagInfo(name, args.toArray(new String[0]));
            return Tag.inserting(Component.virtual(PlaceholderTagInfo.class, info));
        }

        @Override
        public boolean has(@NotNull String name) {
            return true;
        }
    };
}
