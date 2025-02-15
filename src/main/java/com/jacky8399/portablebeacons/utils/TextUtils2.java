package com.jacky8399.portablebeacons.utils;

import com.google.gson.JsonElement;
import com.jacky8399.portablebeacons.PortableBeacons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

public class TextUtils2 {
    public static final MethodHandle NATIVE_SERIALIZE, NATIVE_DESERIALIZE;
    public static final MethodHandle DISPLAY_NAME_GETTER, DISPLAY_NAME_SETTER;
    public static final GsonComponentSerializer PLUGIN_SERIALIZER = GsonComponentSerializer.builder().build();
    public static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    static {
        MethodHandle serialize = null, deserialize = null;
        MethodHandle displayNameGetter = null, displayNameSetter = null;
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
        } catch (ReflectiveOperationException ex) {
            PortableBeacons.LOGGER.log(Level.WARNING, "Adventure serializer not found. JSON formatting will be unavailable.");
        }
        NATIVE_SERIALIZE = serialize;
        NATIVE_DESERIALIZE = deserialize;
        DISPLAY_NAME_GETTER = displayNameGetter;
        DISPLAY_NAME_SETTER = displayNameSetter;
    }

    @SuppressWarnings("unchecked")
    public static <T> T asNativeComponent(Component component) {
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


    public static List<? extends Component> getLore(ItemMeta itemMeta) {
        if (!itemMeta.hasLore()) return null;
        if (NATIVE_SERIALIZE != null) {
            return itemMeta.lore().stream().map(TextUtils2::asPluginComponent).toList();
        } else {
            return itemMeta.getLore().stream().map(LEGACY_SERIALIZER::deserialize).toList();
        }
    }

    public static void setDisplayName(ItemMeta itemMeta, Component displayName) {
        if (NATIVE_DESERIALIZE != null) {
            try {
                DISPLAY_NAME_SETTER.invoke(itemMeta, asNativeComponent(displayName));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            itemMeta.setDisplayName(LEGACY_SERIALIZER.serialize(displayName));
        }
    }

    public static void setLore(ItemMeta itemMeta, Collection<? extends Component> lore) {
        if (NATIVE_DESERIALIZE != null) {
            itemMeta.lore(asNativeComponentList(lore));
        } else {
            itemMeta.setLore(lore.stream().map(LEGACY_SERIALIZER::serialize).toList());
        }
    }
}
