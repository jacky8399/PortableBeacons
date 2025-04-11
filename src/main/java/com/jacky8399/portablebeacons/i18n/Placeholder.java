package com.jacky8399.portablebeacons.i18n;

import net.kyori.adventure.text.Component;

public interface Placeholder {
    Component apply(PlaceholderTagInfo tag, Context context);
}
