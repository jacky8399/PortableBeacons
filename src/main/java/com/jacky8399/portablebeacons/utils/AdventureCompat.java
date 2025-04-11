package com.jacky8399.portablebeacons.utils;

import com.google.gson.JsonElement;
import com.jacky8399.portablebeacons.PortableBeacons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgumentLike;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

@SuppressWarnings("deprecation")
public class AdventureCompat {
    public static final MethodHandle NATIVE_SERIALIZE, NATIVE_DESERIALIZE;
    public static final MethodHandle DISPLAY_NAME_GETTER, DISPLAY_NAME_SETTER;
    public static final MethodHandle ITEM_NAME_GETTER, ITEM_NAME_SETTER;
    public static final GsonComponentSerializer PLUGIN_SERIALIZER = GsonComponentSerializer.builder().build();
    public static final @NotNull ComponentFlattener BUNGEE_FLATTENER = ComponentFlattener.basic().toBuilder()
            .mapper(TranslatableComponent.class, translatable -> {
                var bungee = new net.md_5.bungee.api.chat.TranslatableComponent(translatable.key(), translatable.arguments().stream()
                        .map(TranslationArgumentLike::asComponent)
                        .<BaseComponent>mapMulti((adventureComponent, consumer) -> {
                            for (var baseComponent : BungeeComponentSerializer.get().serialize(adventureComponent)) {
                                consumer.accept(baseComponent);
                            }
                        })
                        .toArray()
                );
                bungee.setFallback(translatable.fallback());
                return bungee.toLegacyText();
            })
            .build();
    public static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .flattener(BUNGEE_FLATTENER)
            .build();
    static {
        MethodHandle serialize = null, deserialize = null;
        MethodHandle displayNameGetter = null, displayNameSetter = null;
        MethodHandle itemNameGetter = null, itemNameSetter = null;
        try {
            Class<?> serializerClass = Class.forName(String.join(".", "net", "kyori", "adventure", "text", "serializer", "gson", "GsonComponentSerializer"));
//            Class<?> serializerClass = GsonComponentSerializer.class;
            Class<?> componentClass = Class.forName(String.join(".", "net", "kyori", "adventure", "text", "Component"));
            Method factoryMethod = serializerClass.getMethod("gson");
            Object serializerInstance = factoryMethod.invoke(null);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            serialize = lookup.findVirtual(serializerClass, "serializeToTree", MethodType.methodType(JsonElement.class, componentClass)).bindTo(serializerInstance);
            deserialize = lookup.findVirtual(serializerClass, "deserializeFromTree", MethodType.methodType(componentClass, JsonElement.class)).bindTo(serializerInstance);
            displayNameGetter = lookup.findVirtual(ItemMeta.class, "displayName", MethodType.methodType(componentClass));
            displayNameSetter = lookup.findVirtual(ItemMeta.class, "displayName", MethodType.methodType(void.class, componentClass));
            itemNameGetter = lookup.findVirtual(ItemMeta.class, "itemName", MethodType.methodType(componentClass));
            itemNameSetter = lookup.findVirtual(ItemMeta.class, "itemName", MethodType.methodType(void.class, componentClass));
        } catch (ReflectiveOperationException ex) {
            PortableBeacons.LOGGER.log(Level.WARNING, "Adventure serializer not found. JSON formatting will be unavailable.");
        }
        NATIVE_SERIALIZE = serialize;
        NATIVE_DESERIALIZE = deserialize;
        DISPLAY_NAME_GETTER = displayNameGetter;
        DISPLAY_NAME_SETTER = displayNameSetter;
        ITEM_NAME_GETTER = itemNameGetter;
        ITEM_NAME_SETTER = itemNameSetter;
    }

    @SuppressWarnings("unchecked")
    public static <T> T asNativeComponent(@NotNull Component component) {
        try {
            return (T) NATIVE_DESERIALIZE.invoke(PLUGIN_SERIALIZER.serializeToTree(component));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> List<T> asNativeComponentList(Collection<? extends Component> list) {
        List<T> newList = new ArrayList<>(list.size());
        for (Component component : list) {
            newList.add(asNativeComponent(component));
        }
        return newList;
    }

    public static Component asPluginComponent(Object component) {
        try {
            return PLUGIN_SERIALIZER.deserializeFromTree((JsonElement) NATIVE_SERIALIZE.invoke(component));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Component getDisplayName(ItemMeta itemMeta) {
        if (!itemMeta.hasDisplayName()) throw new IllegalStateException("displayName");
        if (NATIVE_SERIALIZE != null) {
            try {
                return asPluginComponent(DISPLAY_NAME_GETTER.invoke(itemMeta));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            return LEGACY_SERIALIZER.deserialize(itemMeta.getDisplayName());
        }
    }

    public static Component getItemName(ItemMeta itemMeta) {
        if (!itemMeta.hasItemName()) throw new IllegalStateException("itemName");
        if (NATIVE_SERIALIZE != null) {
            try {
                return asPluginComponent(ITEM_NAME_GETTER.invoke(itemMeta));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            return LEGACY_SERIALIZER.deserialize(itemMeta.getItemName());
        }
    }


    public static List<? extends Component> getLore(ItemMeta itemMeta) {
        if (!itemMeta.hasLore()) throw new IllegalStateException("lore");
        if (NATIVE_SERIALIZE != null) {
            return itemMeta.lore().stream().map(AdventureCompat::asPluginComponent).toList();
        } else {
            return itemMeta.getLore().stream().map(LEGACY_SERIALIZER::deserialize).toList();
        }
    }

    public static void setDisplayName(ItemMeta itemMeta, Component displayName) {
        Component realDisplayName = displayName != null ? displayName.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE) : null;
        if (NATIVE_DESERIALIZE != null) {
            try {
                DISPLAY_NAME_SETTER.invoke(itemMeta, realDisplayName != null ? asNativeComponent(realDisplayName) : null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            itemMeta.setDisplayName(realDisplayName != null ? LEGACY_SERIALIZER.serialize(realDisplayName) : null);
        }
    }

    public static void setItemName(ItemMeta itemMeta, Component itemName) {
        if (NATIVE_DESERIALIZE != null) {
            try {
                ITEM_NAME_SETTER.invoke(itemMeta, itemName != null ? asNativeComponent(itemName) : null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            itemMeta.setItemName(itemName != null ? LEGACY_SERIALIZER.serialize(itemName) : null);
        }
    }

    public static void setLore(ItemMeta itemMeta, Collection<? extends Component> lore) {
        List<Component> realLore;
        if (lore != null) {
            realLore = new ArrayList<>(lore.size());
            for (Component component : lore) {
                realLore.add(component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        } else {
            realLore = null;
        }
        if (NATIVE_DESERIALIZE != null) {
            itemMeta.lore(realLore != null ? asNativeComponentList(realLore) : null);
        } else {
            itemMeta.setLore(realLore != null ? realLore.stream().map(LEGACY_SERIALIZER::serialize).toList() : null);
        }
    }
}
